package com.aerodag.core.controller;

import com.aerodag.core.domain.dto.PlanRequest;
import com.aerodag.core.domain.dto.PlanResponse;
import com.aerodag.core.service.planner.PlannerService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/orchestrator")
public class OrchestratorController {

    private final PlannerService plannerService;

    public OrchestratorController(PlannerService plannerService) {
        this.plannerService = plannerService;
    }

    @PostMapping("/plan")
    public ResponseEntity<PlanResponse> createPlan(@Valid @RequestBody PlanRequest request) {
        return ResponseEntity.ok(plannerService.generateAndSaveDag(request.globalObjective()));
    }
}
