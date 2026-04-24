package com.aerodag.core.domain.dto;

import com.aerodag.core.domain.entity.Plan;
import com.aerodag.core.domain.entity.PlanStatus;
import java.util.List;
import java.util.UUID;

public record PlanResponse(
    UUID id, String globalObjective, PlanStatus status, List<NodeResponse> nodes) {
  public static PlanResponse from(Plan plan, List<NodeResponse> nodes) {
    return new PlanResponse(plan.getId(), plan.getGlobalObjective(), plan.getStatus(), nodes);
  }
}
