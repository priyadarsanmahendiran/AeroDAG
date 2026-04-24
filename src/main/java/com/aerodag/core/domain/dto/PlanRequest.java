package com.aerodag.core.domain.dto;

import jakarta.validation.constraints.NotBlank;

public record PlanRequest(@NotBlank String globalObjective) {}
