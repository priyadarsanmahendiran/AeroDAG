package com.aerodag.core.controller;

import com.aerodag.core.domain.dto.PlanRequest;
import com.aerodag.core.domain.dto.PlanResponse;
import com.aerodag.core.service.planner.PlannerService;
import com.aerodag.core.service.telemetry.SseNotificationService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/v1/orchestrator")
public class OrchestratorController {

  private final PlannerService plannerService;
  private final SseNotificationService sseNotificationService;

  public OrchestratorController(
      PlannerService plannerService, SseNotificationService sseNotificationService) {
    this.plannerService = plannerService;
    this.sseNotificationService = sseNotificationService;
  }

  @PostMapping("/plan")
  public ResponseEntity<PlanResponse> createPlan(@Valid @RequestBody PlanRequest request) {
    return ResponseEntity.ok(plannerService.generateAndSaveDag(request.globalObjective()));
  }

  @GetMapping(value = "/stream/{planId}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  public SseEmitter streamPlan(@PathVariable UUID planId) {
    return sseNotificationService.subscribe(planId);
  }
}
