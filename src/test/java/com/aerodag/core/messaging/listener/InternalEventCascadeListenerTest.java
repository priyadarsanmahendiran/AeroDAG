package com.aerodag.core.messaging.listener;

import com.aerodag.core.domain.entity.Node;
import com.aerodag.core.domain.entity.NodeStatus;
import com.aerodag.core.domain.entity.Plan;
import com.aerodag.core.domain.entity.PlanStatus;
import com.aerodag.core.messaging.event.NodeCompletedEvent;
import com.aerodag.core.messaging.publisher.NodeQueuePublisher;
import com.aerodag.core.repository.NodeRepository;
import com.aerodag.core.repository.PlanRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InternalEventCascadeListenerTest {

    @Mock private NodeRepository nodeRepository;
    @Mock private NodeQueuePublisher nodeQueuePublisher;
    @Mock private PlanRepository planRepository;

    private InternalEventCascadeListener listener;
    private UUID planId;

    @BeforeEach
    void setUp() {
        listener = new InternalEventCascadeListener(nodeRepository, nodeQueuePublisher, planRepository);
        planId = UUID.randomUUID();
    }

    @Test
    void onNodeCompleted_pendingNodeWithAllDepsMet_publishedToQueue() {
        UUID completedId = UUID.randomUUID();
        UUID pendingId = UUID.randomUUID();

        Node completed = nodeWithStatus(completedId, "node-1", NodeStatus.COMPLETED, List.of());
        Node pending = nodeWithStatus(pendingId, "node-2", NodeStatus.PENDING, List.of("node-1"));

        when(nodeRepository.findByPlanId(planId)).thenReturn(List.of(completed, pending));

        listener.onNodeCompleted(new NodeCompletedEvent(planId, completedId));

        verify(nodeQueuePublisher).publishReadyNode(pendingId);
    }

    @Test
    void onNodeCompleted_pendingNodeWithUnmetDeps_notPublished() {
        UUID completedId = UUID.randomUUID();
        UUID pendingId = UUID.randomUUID();

        Node completed = nodeWithStatus(completedId, "node-1", NodeStatus.COMPLETED, List.of());
        // depends on both node-1 and node-2, but node-2 is not completed
        Node pending = nodeWithStatus(pendingId, "node-3", NodeStatus.PENDING, List.of("node-1", "node-2"));

        when(nodeRepository.findByPlanId(planId)).thenReturn(List.of(completed, pending));

        listener.onNodeCompleted(new NodeCompletedEvent(planId, completedId));

        verify(nodeQueuePublisher, never()).publishReadyNode(any());
    }

    @Test
    void onNodeCompleted_pendingNodeWithNullDeps_publishedToQueue() {
        UUID completedId = UUID.randomUUID();
        UUID pendingId = UUID.randomUUID();

        Node completed = nodeWithStatus(completedId, "node-1", NodeStatus.COMPLETED, List.of());
        Node pending = nodeWithStatus(pendingId, "node-2", NodeStatus.PENDING, null);

        when(nodeRepository.findByPlanId(planId)).thenReturn(List.of(completed, pending));

        listener.onNodeCompleted(new NodeCompletedEvent(planId, completedId));

        verify(nodeQueuePublisher).publishReadyNode(pendingId);
    }

    @Test
    void onNodeCompleted_allNodesCompleted_marksPlanCompleted() {
        UUID node1Id = UUID.randomUUID();
        UUID node2Id = UUID.randomUUID();
        Plan plan = Plan.builder()
                .id(planId)
                .globalObjective("test")
                .status(PlanStatus.IN_PROGRESS)
                .build();

        Node node1 = nodeWithStatus(node1Id, "node-1", NodeStatus.COMPLETED, List.of());
        Node node2 = nodeWithStatus(node2Id, "node-2", NodeStatus.COMPLETED, List.of("node-1"));

        when(nodeRepository.findByPlanId(planId)).thenReturn(List.of(node1, node2));
        when(planRepository.findById(planId)).thenReturn(Optional.of(plan));

        listener.onNodeCompleted(new NodeCompletedEvent(planId, node2Id));

        ArgumentCaptor<Plan> planCaptor = ArgumentCaptor.forClass(Plan.class);
        verify(planRepository).save(planCaptor.capture());
        assertThat(planCaptor.getValue().getStatus()).isEqualTo(PlanStatus.COMPLETED);
    }

    @Test
    void onNodeCompleted_notAllNodesCompleted_doesNotMarkPlanCompleted() {
        UUID completedId = UUID.randomUUID();
        UUID runningId = UUID.randomUUID();

        Node completed = nodeWithStatus(completedId, "node-1", NodeStatus.COMPLETED, List.of());
        Node running = nodeWithStatus(runningId, "node-2", NodeStatus.RUNNING, List.of("node-1"));

        when(nodeRepository.findByPlanId(planId)).thenReturn(List.of(completed, running));

        listener.onNodeCompleted(new NodeCompletedEvent(planId, completedId));

        verify(planRepository, never()).save(any());
    }

    @Test
    void onNodeCompleted_multipleReadyNodes_allPublished() {
        UUID completedId = UUID.randomUUID();
        UUID pending1Id = UUID.randomUUID();
        UUID pending2Id = UUID.randomUUID();

        Node completed = nodeWithStatus(completedId, "node-1", NodeStatus.COMPLETED, List.of());
        Node pending1 = nodeWithStatus(pending1Id, "node-2", NodeStatus.PENDING, List.of("node-1"));
        Node pending2 = nodeWithStatus(pending2Id, "node-3", NodeStatus.PENDING, List.of("node-1"));

        when(nodeRepository.findByPlanId(planId)).thenReturn(List.of(completed, pending1, pending2));

        listener.onNodeCompleted(new NodeCompletedEvent(planId, completedId));

        verify(nodeQueuePublisher).publishReadyNode(pending1Id);
        verify(nodeQueuePublisher).publishReadyNode(pending2Id);
    }

    private Node nodeWithStatus(UUID id, String nodeId, NodeStatus status, List<String> dependencies) {
        return Node.builder()
                .id(id)
                .nodeId(nodeId)
                .status(status)
                .instruction("instruction")
                .dependencies(dependencies)
                .toolsAllowed(List.of())
                .build();
    }
}
