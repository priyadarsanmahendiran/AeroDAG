package com.aerodag.core.messaging.publisher;

import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class NodeQueuePublisher {

  private static final Logger log = LoggerFactory.getLogger(NodeQueuePublisher.class);
  private static final String QUEUE_NAME = "aerodag:nodes:ready";

  private final RedisTemplate<String, String> redisTemplate;

  public NodeQueuePublisher(RedisTemplate<String, String> redisTemplate) {
    this.redisTemplate = redisTemplate;
  }

  public void publishReadyNode(UUID nodeId) {
    redisTemplate.opsForList().leftPush(QUEUE_NAME, nodeId.toString());
    log.info("Node {} queued on {}", nodeId, QUEUE_NAME);
  }
}
