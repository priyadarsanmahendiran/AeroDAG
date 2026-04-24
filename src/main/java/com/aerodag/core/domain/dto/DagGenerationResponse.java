package com.aerodag.core.domain.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record DagGenerationResponse(
    @JsonProperty("globalObjective") String globalObjective,
    @JsonProperty("nodes") List<DagNodeResponse> nodes) {}
