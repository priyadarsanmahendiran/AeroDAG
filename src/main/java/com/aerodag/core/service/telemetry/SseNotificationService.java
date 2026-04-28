package com.aerodag.core.service.telemetry;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Service
public class SseNotificationService {

  private static final Logger log = LoggerFactory.getLogger(SseNotificationService.class);
  private static final long SSE_TIMEOUT_MS = 30 * 60 * 1000L;

  private final ConcurrentHashMap<UUID, CopyOnWriteArrayList<SseEmitter>> emitters =
      new ConcurrentHashMap<>();
  private final ObjectMapper objectMapper;

  public SseNotificationService(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  public SseEmitter subscribe(UUID planId) {
    SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);
    emitters.computeIfAbsent(planId, id -> new CopyOnWriteArrayList<>()).add(emitter);

    Runnable cleanup = () -> removeEmitter(planId, emitter);
    emitter.onCompletion(cleanup);
    emitter.onTimeout(cleanup);

    log.info("SSE client subscribed to plan {}", planId);
    return emitter;
  }

  public void broadcastNodeUpdate(UUID planId, String nodeId, String status, String resultPayload) {
    List<SseEmitter> planEmitters = emitters.get(planId);
    if (planEmitters == null || planEmitters.isEmpty()) {
      return;
    }

    String json;
    try {
      json =
          objectMapper.writeValueAsString(
              Map.of(
                  "nodeId", nodeId,
                  "status", status,
                  "resultPayload", resultPayload != null ? resultPayload : ""));
    } catch (JsonProcessingException e) {
      log.error("Failed to serialize node-update event for plan {}", planId, e);
      return;
    }

    for (SseEmitter emitter : planEmitters) {
      try {
        emitter.send(SseEmitter.event().name("node-update").data(json));
      } catch (IOException e) {
        log.warn("Dead SSE connection removed for plan {}", planId);
        removeEmitter(planId, emitter);
      }
    }
  }

  private void removeEmitter(UUID planId, SseEmitter emitter) {
    CopyOnWriteArrayList<SseEmitter> planEmitters = emitters.get(planId);
    if (planEmitters != null) {
      planEmitters.remove(emitter);
      if (planEmitters.isEmpty()) {
        emitters.remove(planId);
      }
    }
  }
}
