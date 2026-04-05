package com.elvo.wallet;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.context.annotation.Import;

import com.elvo.wallet.controller.HealthController;
import com.elvo.wallet.exception.GlobalExceptionHandler;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = HealthController.class)
@Import(GlobalExceptionHandler.class)
class InternalPingIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @WithMockUser
    void pingEndpointShouldReturnOk() throws Exception {
        mockMvc.perform(get("/internal/ping"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("UP"));
    }
}
