package com.aerodag.core.messaging.publisher;

import static org.mockito.Mockito.*;

import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.RedisTemplate;

@ExtendWith(MockitoExtension.class)
class NodeQueuePublisherTest {

  @Mock private RedisTemplate<String, String> redisTemplate;

  @Mock private ListOperations<String, String> listOperations;

  private NodeQueuePublisher publisher;

  @BeforeEach
  void setUp() {
    when(redisTemplate.opsForList()).thenReturn(listOperations);
    publisher = new NodeQueuePublisher(redisTemplate);
  }

  @Test
  void publishReadyNode_pushesNodeIdToQueue() {
    UUID nodeId = UUID.randomUUID();

    publisher.publishReadyNode(nodeId);

    verify(listOperations).leftPush("aerodag:nodes:ready", nodeId.toString());
  }

  @Test
  void publishReadyNode_multipleNodes_eachPushedSeparately() {
    UUID nodeId1 = UUID.randomUUID();
    UUID nodeId2 = UUID.randomUUID();

    publisher.publishReadyNode(nodeId1);
    publisher.publishReadyNode(nodeId2);

    verify(listOperations).leftPush("aerodag:nodes:ready", nodeId1.toString());
    verify(listOperations).leftPush("aerodag:nodes:ready", nodeId2.toString());
  }
}
