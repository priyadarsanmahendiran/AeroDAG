package com.aerodag.core.controller;

import com.aerodag.core.domain.dto.PlanRequest;
import com.aerodag.core.domain.dto.PlanResponse;
import com.aerodag.core.domain.entity.PlanStatus;
import com.aerodag.core.service.planner.PlannerService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(OrchestratorController.class)
class OrchestratorControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private PlannerService plannerService;

    @Test
    void createPlan_validRequest_returns200WithPlanResponse() throws Exception {
        UUID planId = UUID.randomUUID();
        String objective = "Build a REST API";
        PlanResponse response = new PlanResponse(planId, objective, PlanStatus.IN_PROGRESS, List.of());

        when(plannerService.generateAndSaveDag(objective)).thenReturn(response);

        mockMvc.perform(post("/api/v1/orchestrator/plan")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new PlanRequest(objective))))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(planId.toString()))
                .andExpect(jsonPath("$.globalObjective").value(objective))
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"));
    }

    @Test
    void createPlan_blankObjective_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/orchestrator/plan")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"globalObjective\": \"\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createPlan_nullObjective_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/orchestrator/plan")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"globalObjective\": null}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createPlan_missingBody_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/orchestrator/plan")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
    }
}
