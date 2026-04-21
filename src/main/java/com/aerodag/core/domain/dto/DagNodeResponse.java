package com.aerodag.core.domain.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record DagNodeResponse(
        @JsonProperty("nodeId") String nodeId,
        @JsonProperty("instruction") String instruction,
        @JsonProperty("dependencies") List<String> dependencies,
        @JsonProperty("toolsAllowed") List<String> toolsAllowed
) {}
