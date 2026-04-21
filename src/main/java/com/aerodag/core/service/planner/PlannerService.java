package com.aerodag.core.service.planner;

import com.aerodag.core.domain.dto.DagGenerationResponse;
import com.aerodag.core.domain.dto.DagNodeResponse;
import com.aerodag.core.domain.dto.NodeResponse;
import com.aerodag.core.domain.dto.PlanResponse;
import com.aerodag.core.domain.entity.Node;
import com.aerodag.core.domain.entity.NodeStatus;
import com.aerodag.core.domain.entity.Plan;
import com.aerodag.core.domain.entity.PlanStatus;
import com.aerodag.core.exception.DagGenerationException;
import com.aerodag.core.messaging.publisher.NodeQueuePublisher;
import com.aerodag.core.repository.NodeRepository;
import com.aerodag.core.repository.PlanRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

@Service
public class PlannerService {

    private static final String SYSTEM_PROMPT = """
            You are an Enterprise AI Orchestrator. Decompose the user's objective into a \
            Directed Acyclic Graph (DAG) of distinct, atomic, executable tasks.

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
    private final NodeQueuePublisher nodeQueuePublisher;
    private final ChatClient chatClient;
    private final ObjectMapper objectMapper;

    public PlannerService(PlanRepository planRepository,
                          NodeRepository nodeRepository,
                          NodeQueuePublisher nodeQueuePublisher,
                          ChatClient.Builder chatClientBuilder,
                          ObjectMapper objectMapper) {
        this.planRepository = planRepository;
        this.nodeRepository = nodeRepository;
        this.nodeQueuePublisher = nodeQueuePublisher;
        this.chatClient = chatClientBuilder.build();
        this.objectMapper = objectMapper;
    }

    @Transactional
    public PlanResponse generateAndSaveDag(String userObjective) {
        String rawJson = chatClient.prompt()
                .system(SYSTEM_PROMPT)
                .user(userObjective)
                .call()
                .content();

        if(Objects.isNull(rawJson)) {
            throw new DagGenerationException("LLM returned null response for DAG generation.");
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
            throw new DagGenerationException(
                    "Failed to parse LLM DAG response. Raw output: " + sanitizedJson, e);
        }

        Plan plan = planRepository.save(
                Plan.builder()
                        .globalObjective(dagResponse.globalObjective())
                        .status(PlanStatus.IN_PROGRESS)
                        .build()
        );

        List<Node> nodes = dagResponse.nodes().stream()
                .map(nr -> mapToNode(nr, plan))
                .toList();
        nodeRepository.saveAll(nodes);

        nodes.stream()
                .filter(n -> n.getDependencies() == null || n.getDependencies().isEmpty())
                .forEach(n -> nodeQueuePublisher.publishReadyNode(n.getId()));

        List<NodeResponse> nodeResponses = nodes.stream()
                .map(n -> new NodeResponse(n.getInstruction()))
                .toList();

        return PlanResponse.from(plan, nodeResponses);
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
