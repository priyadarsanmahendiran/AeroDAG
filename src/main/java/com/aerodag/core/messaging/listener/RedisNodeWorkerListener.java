package com.aerodag.core.messaging.listener;

import com.aerodag.core.domain.entity.Node;
import com.aerodag.core.domain.entity.NodeStatus;
import com.aerodag.core.messaging.event.NodeCompletedEvent;
import com.aerodag.core.messaging.event.NodeFailedEvent;
import com.aerodag.core.repository.NodeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

@Component
public class RedisNodeWorkerListener {

    private static final Logger log = LoggerFactory.getLogger(RedisNodeWorkerListener.class);
    private static final String QUEUE_NAME = "aerodag:nodes:ready";
    private static final String EXECUTOR_SYSTEM_PROMPT =
            "You are an isolated task executor. Execute the following instruction. Return ONLY the direct result payload.";

    private final RedisTemplate<String, String> redisTemplate;
    private final NodeRepository nodeRepository;
    private final ChatClient chatClient;
    private final ApplicationEventPublisher eventPublisher;

    public RedisNodeWorkerListener(RedisTemplate<String, String> redisTemplate,
                                   NodeRepository nodeRepository,
                                   ChatClient.Builder chatClientBuilder,
                                   ApplicationEventPublisher eventPublisher) {
        this.redisTemplate = redisTemplate;
        this.nodeRepository = nodeRepository;
        this.chatClient = chatClientBuilder.build();
        this.eventPublisher = eventPublisher;
    }

    @Async
    @EventListener(ApplicationReadyEvent.class)
    public void startListening() {
        log.info("Node worker started, polling queue: {}", QUEUE_NAME);
        while (!Thread.currentThread().isInterrupted()) {
            try {
                String nodeIdStr = redisTemplate.opsForList()
                        .rightPop(QUEUE_NAME, Duration.ofSeconds(5));
                if (nodeIdStr == null) {
                    continue;
                }
                processNode(UUID.fromString(nodeIdStr));
            } catch (Exception e) {
                log.error("Worker error while polling queue", e);
            }
        }
    }

    private void processNode(UUID nodeId) {
        Node node = nodeRepository.findById(nodeId).orElse(null);
        if (node == null) {
            log.warn("Node {} not found in database, skipping", nodeId);
            return;
        }

        node.setStatus(NodeStatus.RUNNING);
        nodeRepository.save(node);
        log.info("Node {} status -> RUNNING", nodeId);

        try {
            String systemPrompt = buildSystemPrompt(node);
            List<String> tools = node.getToolsAllowed() != null ? node.getToolsAllowed() : List.of();

            ChatClient.ChatClientRequestSpec request = chatClient.prompt()
                    .system(systemPrompt)
                    .user(node.getInstruction());

            if (!tools.isEmpty()) {
                request = request.tools(tools.toArray(new String[0]));
            }

            String result = request.call().content();

            node.setStatus(NodeStatus.COMPLETED);
            node.setResultPayload(result);
            log.info("Node {} status -> COMPLETED", nodeId);
        } catch (Exception e) {
            log.error("LLM execution failed for node {}", nodeId, e);
            node.setStatus(NodeStatus.FAILED);
            node.setResultPayload("Execution failed: " + e.getMessage());
            nodeRepository.save(node);
            eventPublisher.publishEvent(new NodeFailedEvent(node.getPlan().getId(), node.getId(), e.getMessage()));
            return;
        }

        nodeRepository.save(node);
        eventPublisher.publishEvent(new NodeCompletedEvent(node.getPlan().getId(), node.getId()));
    }

    private String buildSystemPrompt(Node node) {
        List<String> depNodeIds = node.getDependencies();
        if (depNodeIds == null || depNodeIds.isEmpty()) {
            return EXECUTOR_SYSTEM_PROMPT;
        }

        List<Node> depNodes = nodeRepository.findByPlanIdAndNodeIdIn(
                node.getPlan().getId(), depNodeIds);

        String contextString = depNodes.stream()
                .filter(dep -> dep.getResultPayload() != null)
                .map(dep -> dep.getNodeId() + ": " + dep.getResultPayload())
                .reduce((a, b) -> a + "\n" + b)
                .orElse("");

        if (contextString.isBlank()) {
            return EXECUTOR_SYSTEM_PROMPT;
        }

        return EXECUTOR_SYSTEM_PROMPT + "\nHere is the data from previous steps: " + contextString + ".";
    }
}
