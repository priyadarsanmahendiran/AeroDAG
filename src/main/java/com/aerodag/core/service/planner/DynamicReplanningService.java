package com.aerodag.core.service.planner;

import com.aerodag.core.domain.dto.DagGenerationResponse;
import com.aerodag.core.domain.dto.DagNodeResponse;
import com.aerodag.core.domain.entity.Node;
import com.aerodag.core.domain.entity.NodeStatus;
import com.aerodag.core.domain.entity.Plan;
import com.aerodag.core.messaging.event.NodeFailedEvent;
import com.aerodag.core.messaging.publisher.NodeQueuePublisher;
import com.aerodag.core.repository.NodeRepository;
import com.aerodag.core.repository.PlanRepository;
import com.aerodag.core.service.telemetry.SseNotificationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DynamicReplanningService {

  private static final Logger log = LoggerFactory.getLogger(DynamicReplanningService.class);

  private static final String REPLANNING_SYSTEM_PROMPT =
      """
            You are an AI orchestrator. The previous plan failed mid-execution. \
            Based on the successes and the failure, generate a NEW DAG (JSON) with new nodes \
            to achieve the original objective.

            Rules:
            - Each node represents one atomic task.
            - "dependencies" lists the nodeIds of tasks that MUST complete before this node runs. \
            Root nodes have an empty list.
            - "toolsAllowed" lists the exact tool names the executor may invoke for this node.
            - The graph MUST be acyclic. No circular dependencies.
            - nodeId values must be unique strings (e.g. "node-1", "node-2").

            Respond ONLY with a valid text matching this exact schema. \
            No markdown, no explanation, no code fences — raw text only as follows,
            {
              "globalObjective": "<the user's objective>",
              "nodes": [
                {
                  "nodeId": "<unique id>",
                  "instruction": "<clear, self-contained task instruction>",
                  "dependencies": ["<nodeId>"],
                  "toolsAllowed": ["<toolName>"]
                }
              ]
            }
            """;

  private final PlanRepository planRepository;
  private final NodeRepository nodeRepository;
  private final PlannerService plannerService;
  private final NodeQueuePublisher nodeQueuePublisher;
  private final SseNotificationService sseNotificationService;
  private final ChatClient chatClient;
  private final ObjectMapper objectMapper;

  public DynamicReplanningService(
      PlanRepository planRepository,
      NodeRepository nodeRepository,
      PlannerService plannerService,
      NodeQueuePublisher nodeQueuePublisher,
      SseNotificationService sseNotificationService,
      ChatClient.Builder chatClientBuilder,
      ObjectMapper objectMapper) {
    this.planRepository = planRepository;
    this.nodeRepository = nodeRepository;
    this.plannerService = plannerService;
    this.nodeQueuePublisher = nodeQueuePublisher;
    this.sseNotificationService = sseNotificationService;
    this.chatClient = chatClientBuilder.build();
    this.objectMapper = objectMapper;
  }

  @Transactional
  @EventListener
  public void onNodeFailed(NodeFailedEvent event) {
    Plan plan = planRepository.findById(event.planId()).orElse(null);
    if (plan == null) {
      log.error("Plan {} not found for replanning", event.planId());
      return;
    }

    List<Node> allNodes = nodeRepository.findByPlanId(event.planId());

    List<Node> pendingNodes =
        allNodes.stream()
            .filter(n -> n.getStatus() == NodeStatus.PENDING)
            .peek(n -> n.setStatus(NodeStatus.CANCELLED))
            .toList();
    nodeRepository.saveAll(pendingNodes);

    Node failedNode =
        allNodes.stream().filter(n -> n.getId().equals(event.nodeId())).findFirst().orElse(null);

    String completedContext =
        allNodes.stream()
            .filter(n -> n.getStatus() == NodeStatus.COMPLETED)
            .map(n -> "- " + n.getInstruction() + ": " + n.getResultPayload())
            .collect(Collectors.joining("\n"));

    String failedInstruction =
        failedNode != null ? failedNode.getInstruction() : event.nodeId().toString();
    String failedNodeId = failedNode != null ? failedNode.getNodeId() : event.nodeId().toString();

    sseNotificationService.broadcastNodeUpdate(
        event.planId(), failedNodeId, NodeStatus.FAILED.name(), event.errorMessage());

    String contextPrompt =
        String.format(
            "Original Objective: %s. The following steps succeeded:\n%s\nHowever, step '%s' failed with error: %s.",
            plan.getGlobalObjective(),
            completedContext.isBlank() ? "(none)" : completedContext,
            failedInstruction,
            event.errorMessage());

    log.info("Triggering replanning for plan {}", event.planId());

    String rawJson =
        chatClient.prompt().system(REPLANNING_SYSTEM_PROMPT).user(contextPrompt).call().content();

    if (rawJson == null) {
      log.error("LLM returned null during replanning for plan {}", event.planId());
      return;
    }

    String sanitizedJson = rawJson.strip();
    if (sanitizedJson.startsWith("```") || sanitizedJson.endsWith("```")) {
      sanitizedJson = sanitizedJson.replaceFirst("^```[a-zA-Z]*\\r?\\n?", "");
      sanitizedJson = sanitizedJson.replaceFirst("```\\s*$", "").strip();
    }

    DagGenerationResponse dagResponse;
    try {
      dagResponse = objectMapper.readValue(sanitizedJson, DagGenerationResponse.class);
    } catch (Exception e) {
      log.error(
          "Failed to parse replanning response for plan {}. Raw: {}",
          event.planId(),
          sanitizedJson,
          e);
      return;
    }

    List<Node> newNodes = dagResponse.nodes().stream().map(nr -> mapToNode(nr, plan)).toList();
    nodeRepository.saveAll(newNodes);

    newNodes.stream()
        .filter(n -> n.getDependencies() == null || n.getDependencies().isEmpty())
        .forEach(n -> nodeQueuePublisher.publishReadyNode(n.getId()));

    log.info(
        "Replanning complete for plan {}. {} new nodes created.", event.planId(), newNodes.size());
  }

  private Node mapToNode(DagNodeResponse nr, Plan plan) {
    return Node.builder()
        .plan(plan)
        .nodeId(nr.nodeId())
        .status(NodeStatus.PENDING)
        .instruction(nr.instruction())
        .dependencies(nr.dependencies())
        .toolsAllowed(nr.toolsAllowed())
        .build();
  }
}
