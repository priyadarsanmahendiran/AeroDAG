package com.aerodag.core.service.planner;

import com.aerodag.core.domain.dto.PlanResponse;
import com.aerodag.core.domain.entity.Node;
import com.aerodag.core.domain.entity.Plan;
import com.aerodag.core.domain.entity.PlanStatus;
import com.aerodag.core.exception.DagGenerationException;
import com.aerodag.core.messaging.publisher.NodeQueuePublisher;
import com.aerodag.core.repository.NodeRepository;
import com.aerodag.core.repository.PlanRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PlannerServiceTest {

    @Mock private PlanRepository planRepository;
    @Mock private NodeRepository nodeRepository;
    @Mock private NodeQueuePublisher nodeQueuePublisher;
    @Mock private ChatClient.Builder chatClientBuilder;

    private ChatClient mockChatClient;
    private PlannerService plannerService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final String VALID_DAG_JSON = """
            {
              "globalObjective": "Do something",
              "nodes": [
                {
                  "nodeId": "node-1",
                  "instruction": "Step one",
                  "dependencies": [],
                  "toolsAllowed": ["web_search"]
                },
                {
                  "nodeId": "node-2",
                  "instruction": "Step two",
                  "dependencies": ["node-1"],
                  "toolsAllowed": []
                }
              ]
            }
            """;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        mockChatClient = mock(ChatClient.class, RETURNS_DEEP_STUBS);
        when(chatClientBuilder.build()).thenReturn(mockChatClient);
        plannerService = new PlannerService(
                planRepository, nodeRepository, nodeQueuePublisher, chatClientBuilder, objectMapper);
    }

    @Test
    void generateAndSaveDag_validResponse_returnsPlanResponse() {
        UUID planId = UUID.randomUUID();
        Plan savedPlan = Plan.builder()
                .id(planId)
                .globalObjective("Do something")
                .status(PlanStatus.IN_PROGRESS)
                .build();

        when(mockChatClient.prompt().system(anyString()).user(anyString()).call().content())
                .thenReturn(VALID_DAG_JSON);
        when(planRepository.save(any(Plan.class))).thenReturn(savedPlan);
        when(nodeRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

        PlanResponse result = plannerService.generateAndSaveDag("Do something");

        assertThat(result.id()).isEqualTo(planId);
        assertThat(result.globalObjective()).isEqualTo("Do something");
        assertThat(result.status()).isEqualTo(PlanStatus.IN_PROGRESS);
        assertThat(result.nodes()).hasSize(2);
    }

    @Test
    void generateAndSaveDag_rootNodesPublishedToQueue() {
        Plan savedPlan = Plan.builder()
                .id(UUID.randomUUID())
                .globalObjective("Do something")
                .status(PlanStatus.IN_PROGRESS)
                .build();

        when(mockChatClient.prompt().system(anyString()).user(anyString()).call().content())
                .thenReturn(VALID_DAG_JSON);
        when(planRepository.save(any(Plan.class))).thenReturn(savedPlan);
        when(nodeRepository.saveAll(anyList())).thenAnswer(inv -> {
            List<Node> nodes = inv.getArgument(0);
            nodes.forEach(n -> n.setId(UUID.randomUUID()));
            return nodes;
        });

        plannerService.generateAndSaveDag("Do something");

        // Only node-1 (no dependencies) should be published; node-2 depends on node-1
        verify(nodeQueuePublisher, times(1)).publishReadyNode(any(UUID.class));
    }

    @Test
    void generateAndSaveDag_jsonInCodeFences_stripsAndParsesSuccessfully() {
        String fencedJson = "```json\n" + VALID_DAG_JSON + "\n```";
        Plan savedPlan = Plan.builder()
                .id(UUID.randomUUID())
                .globalObjective("Do something")
                .status(PlanStatus.IN_PROGRESS)
                .build();

        when(mockChatClient.prompt().system(anyString()).user(anyString()).call().content())
                .thenReturn(fencedJson);
        when(planRepository.save(any(Plan.class))).thenReturn(savedPlan);
        when(nodeRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

        assertThatNoException().isThrownBy(() -> plannerService.generateAndSaveDag("Do something"));
    }

    @Test
    void generateAndSaveDag_plainCodeFences_stripsAndParsesSuccessfully() {
        String fencedJson = "```\n" + VALID_DAG_JSON + "\n```";
        Plan savedPlan = Plan.builder()
                .id(UUID.randomUUID())
                .globalObjective("Do something")
                .status(PlanStatus.IN_PROGRESS)
                .build();

        when(mockChatClient.prompt().system(anyString()).user(anyString()).call().content())
                .thenReturn(fencedJson);
        when(planRepository.save(any(Plan.class))).thenReturn(savedPlan);
        when(nodeRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

        assertThatNoException().isThrownBy(() -> plannerService.generateAndSaveDag("Do something"));
    }

    @Test
    void generateAndSaveDag_nullLlmResponse_throwsDagGenerationException() {
        when(mockChatClient.prompt().system(anyString()).user(anyString()).call().content())
                .thenReturn(null);

        assertThatThrownBy(() -> plannerService.generateAndSaveDag("Do something"))
                .isInstanceOf(DagGenerationException.class)
                .hasMessageContaining("null");
    }

    @Test
    void generateAndSaveDag_invalidJson_throwsDagGenerationException() {
        when(mockChatClient.prompt().system(anyString()).user(anyString()).call().content())
                .thenReturn("this is not json at all");

        assertThatThrownBy(() -> plannerService.generateAndSaveDag("Do something"))
                .isInstanceOf(DagGenerationException.class)
                .hasMessageContaining("Failed to parse");
    }

    @Test
    void generateAndSaveDag_savedNodesHaveCorrectNodeIds() {
        Plan savedPlan = Plan.builder()
                .id(UUID.randomUUID())
                .globalObjective("Do something")
                .status(PlanStatus.IN_PROGRESS)
                .build();

        when(mockChatClient.prompt().system(anyString()).user(anyString()).call().content())
                .thenReturn(VALID_DAG_JSON);
        when(planRepository.save(any(Plan.class))).thenReturn(savedPlan);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Node>> captor = ArgumentCaptor.forClass(List.class);
        when(nodeRepository.saveAll(captor.capture())).thenAnswer(inv -> inv.getArgument(0));

        plannerService.generateAndSaveDag("Do something");

        List<Node> savedNodes = captor.getValue();
        assertThat(savedNodes).extracting(Node::getNodeId).containsExactlyInAnyOrder("node-1", "node-2");
        assertThat(savedNodes).extracting(n -> n.getDependencies())
                .containsExactlyInAnyOrder(List.of(), List.of("node-1"));
    }
}
