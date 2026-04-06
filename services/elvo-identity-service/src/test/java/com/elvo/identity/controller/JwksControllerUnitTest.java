package com.elvo.identity.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import com.elvo.identity.monitoring.SentryExceptionReporter;
import com.elvo.identity.util.TokenService;

@WebMvcTest(JwksController.class)
@AutoConfigureMockMvc(addFilters = false)
class JwksControllerUnitTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private TokenService tokenService;

    @MockBean
    private SentryExceptionReporter sentryExceptionReporter;

    @Test
    void jwksShouldReturnKeyMetadata() throws Exception {
        TokenService.JwkKey key = new TokenService.JwkKey(
                "identity-key-01",
                "RSA",
                "RS256",
                "sig",
                "modulusValue",
                "exponentValue");
        when(tokenService.getJwksDocument()).thenReturn(new TokenService.JwksDocument(List.of(key)));

        mockMvc.perform(get("/.well-known/jwks.json"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.keys[0].kid").value("identity-key-01"))
                .andExpect(jsonPath("$.keys[0].kty").value("RSA"))
                .andExpect(jsonPath("$.keys[0].alg").value("RS256"))
                .andExpect(jsonPath("$.keys[0].use").value("sig"))
                .andExpect(jsonPath("$.keys[0].n").value("modulusValue"))
                .andExpect(jsonPath("$.keys[0].e").value("exponentValue"));
    }
}
