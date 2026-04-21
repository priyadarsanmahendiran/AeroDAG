package com.aerodag.core.messaging.listener;

import com.aerodag.core.domain.entity.Node;
import com.aerodag.core.domain.entity.NodeStatus;
import com.aerodag.core.domain.entity.Plan;
import com.aerodag.core.domain.entity.PlanStatus;
import com.aerodag.core.messaging.event.NodeCompletedEvent;
import com.aerodag.core.repository.NodeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RedisNodeWorkerListenerTest {

    @Mock private RedisTemplate<String, String> redisTemplate;
    @Mock private ListOperations<String, String> listOperations;
    @Mock private NodeRepository nodeRepository;
    @Mock private ChatClient.Builder chatClientBuilder;
    @Mock private ApplicationEventPublisher eventPublisher;

    private ChatClient mockChatClient;
    private RedisNodeWorkerListener listener;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        mockChatClient = mock(ChatClient.class, RETURNS_DEEP_STUBS);
        when(chatClientBuilder.build()).thenReturn(mockChatClient);
        when(redisTemplate.opsForList()).thenReturn(listOperations);
        listener = new RedisNodeWorkerListener(redisTemplate, nodeRepository, chatClientBuilder, eventPublisher);
    }

    @Test
    void processNode_nodeNotFound_skipsWithoutSavingOrPublishing() {
        UUID nodeId = UUID.randomUUID();
        when(nodeRepository.findById(nodeId)).thenReturn(Optional.empty());

        ReflectionTestUtils.invokeMethod(listener, "processNode", nodeId);

        verify(nodeRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void processNode_llmSucceeds_setsCompletedAndPublishesEvent() {
        UUID nodeId = UUID.randomUUID();
        UUID planId = UUID.randomUUID();

        Plan plan = Plan.builder()
                .id(planId)
                .globalObjective("test")
                .status(PlanStatus.IN_PROGRESS)
                .build();

        Node node = Node.builder()
                .id(nodeId)
                .nodeId("node-1")
                .status(NodeStatus.PENDING)
                .instruction("Execute step one")
                .plan(plan)
                .dependencies(List.of())
                .toolsAllowed(List.of())
                .build();

        when(nodeRepository.findById(nodeId)).thenReturn(Optional.of(node));
        when(mockChatClient.prompt().system(anyString()).user(anyString()).call().content())
                .thenReturn("result payload");
        when(nodeRepository.save(any(Node.class))).thenAnswer(inv -> inv.getArgument(0));

        ReflectionTestUtils.invokeMethod(listener, "processNode", nodeId);

        assertThat(node.getStatus()).isEqualTo(NodeStatus.COMPLETED);
        assertThat(node.getResultPayload()).isEqualTo("result payload");

        ArgumentCaptor<NodeCompletedEvent> eventCaptor = ArgumentCaptor.forClass(NodeCompletedEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().planId()).isEqualTo(planId);
        assertThat(eventCaptor.getValue().nodeId()).isEqualTo(nodeId);
    }

    @Test
    void processNode_llmFails_setsStatusFailedAndNoEvent() {
        UUID nodeId = UUID.randomUUID();
        Plan plan = Plan.builder()
                .id(UUID.randomUUID())
                .globalObjective("test")
                .status(PlanStatus.IN_PROGRESS)
                .build();

        Node node = Node.builder()
                .id(nodeId)
                .nodeId("node-1")
                .status(NodeStatus.PENDING)
                .instruction("Execute step one")
                .plan(plan)
                .dependencies(List.of())
                .toolsAllowed(List.of())
                .build();

        when(nodeRepository.findById(nodeId)).thenReturn(Optional.of(node));
        when(mockChatClient.prompt().system(anyString()).user(anyString()).call().content())
                .thenThrow(new RuntimeException("LLM unavailable"));
        when(nodeRepository.save(any(Node.class))).thenAnswer(inv -> inv.getArgument(0));

        ReflectionTestUtils.invokeMethod(listener, "processNode", nodeId);

        assertThat(node.getStatus()).isEqualTo(NodeStatus.FAILED);
        assertThat(node.getResultPayload()).isNull();
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void processNode_savesRunningStatusBeforeLlmCall() {
        UUID nodeId = UUID.randomUUID();
        Plan plan = Plan.builder()
                .id(UUID.randomUUID())
                .globalObjective("test")
                .status(PlanStatus.IN_PROGRESS)
                .build();

        Node node = Node.builder()
                .id(nodeId)
                .nodeId("node-1")
                .status(NodeStatus.PENDING)
                .instruction("Execute step one")
                .plan(plan)
                .dependencies(List.of())
                .toolsAllowed(List.of())
                .build();

        when(nodeRepository.findById(nodeId)).thenReturn(Optional.of(node));
        when(mockChatClient.prompt().system(anyString()).user(anyString()).call().content())
                .thenReturn("result");

        ArgumentCaptor<Node> saveCaptor = ArgumentCaptor.forClass(Node.class);
        when(nodeRepository.save(saveCaptor.capture())).thenAnswer(inv -> inv.getArgument(0));

        ReflectionTestUtils.invokeMethod(listener, "processNode", nodeId);

        // First save must have been RUNNING (before LLM), second COMPLETED (after)
        List<Node> savedNodes = saveCaptor.getAllValues();
        assertThat(savedNodes).hasSize(2);
        assertThat(savedNodes.get(0).getStatus()).isEqualTo(NodeStatus.RUNNING);
        assertThat(savedNodes.get(1).getStatus()).isEqualTo(NodeStatus.COMPLETED);
    }
}
