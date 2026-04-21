package com.aerodag.core.messaging.listener;

import com.aerodag.core.domain.entity.Node;
import com.aerodag.core.domain.entity.NodeStatus;
import com.aerodag.core.domain.entity.PlanStatus;
import com.aerodag.core.messaging.event.NodeCompletedEvent;
import com.aerodag.core.messaging.publisher.NodeQueuePublisher;
import com.aerodag.core.repository.NodeRepository;
import com.aerodag.core.repository.PlanRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class InternalEventCascadeListener {

    private static final Logger log = LoggerFactory.getLogger(InternalEventCascadeListener.class);

    private final NodeRepository nodeRepository;
    private final NodeQueuePublisher nodeQueuePublisher;
    private final PlanRepository planRepository;

    public InternalEventCascadeListener(NodeRepository nodeRepository,
                                        NodeQueuePublisher nodeQueuePublisher,
                                        PlanRepository planRepository) {
        this.nodeRepository = nodeRepository;
        this.nodeQueuePublisher = nodeQueuePublisher;
        this.planRepository = planRepository;
    }

    @EventListener
    @Transactional
    public void onNodeCompleted(NodeCompletedEvent event) {
        List<Node> allNodes = nodeRepository.findByPlanId(event.planId());

        Set<String> completedNodeIds = allNodes.stream()
                .filter(n -> n.getStatus() == NodeStatus.COMPLETED)
                .map(Node::getNodeId)
                .collect(Collectors.toSet());

        allNodes.stream()
                .filter(n -> n.getStatus() == NodeStatus.PENDING)
                .filter(n -> n.getDependencies() == null
                        || completedNodeIds.containsAll(n.getDependencies()))
                .forEach(n -> {
                    log.info("Node {} unblocked, publishing to queue", n.getId());
                    nodeQueuePublisher.publishReadyNode(n.getId());
                });

        boolean allCompleted = allNodes.stream()
                .allMatch(n -> n.getStatus() == NodeStatus.COMPLETED);

        if (allCompleted) {
            planRepository.findById(event.planId()).ifPresent(plan -> {
                plan.setStatus(PlanStatus.COMPLETED);
                planRepository.save(plan);
                log.info("Plan {} status -> COMPLETED", event.planId());
            });
        }
    }
}
