package com.elvo.agent.controller;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.elvo.agent.dto.request.AgentFloatCheckRequest;
import com.elvo.agent.dto.response.AgentFloatCheckResponse;
import com.elvo.agent.service.AgentFloatService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/internal/agents")
@Validated
public class AgentFloatController {

    private final AgentFloatService agentFloatService;

    public AgentFloatController(AgentFloatService agentFloatService) {
        this.agentFloatService = agentFloatService;
    }

    @PostMapping("/float/check")
    public AgentFloatCheckResponse checkFloat(@Valid @RequestBody AgentFloatCheckRequest request) {
        return agentFloatService.checkFloat(request);
    }
}
