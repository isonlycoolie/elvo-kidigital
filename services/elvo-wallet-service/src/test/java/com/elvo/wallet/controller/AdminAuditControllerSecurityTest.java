package com.elvo.wallet.controller;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.when;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.elvo.wallet.audit.AuditEventRecord;
import com.elvo.wallet.audit.ImmutableAuditStorageService;
import com.elvo.wallet.security.InternalServiceAuthorizationMatrix;
import com.elvo.wallet.security.SecurityConfig;

@WebMvcTest(controllers = AdminAuditController.class)
@Import(SecurityConfig.class)
class AdminAuditControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ImmutableAuditStorageService immutableAuditStorageService;

    @MockBean
    private InternalServiceAuthorizationMatrix internalServiceAuthorizationMatrix;

    @Test
    @WithMockUser(roles = {"AUDIT_ADMIN"})
    void readAuditEvents_allowsAuditAdminRole() throws Exception {
        AuditEventRecord record = new AuditEventRecord("wallet.audit", "req-1", "corr-1", Instant.parse("2026-01-01T10:00:00Z"), "{\"ok\":true}");
        record.setPreviousHash(AuditEventRecord.GENESIS_HASH);
        record.setRecordHash("abc123");
        when(immutableAuditStorageService.readRecentEvents(10)).thenReturn(List.of(record));

        mockMvc.perform(get("/api/v1/admin/audit/events").param("limit", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].eventType").value("wallet.audit"))
                .andExpect(jsonPath("$[0].recordHash").value("abc123"));
    }

    @Test
    @WithMockUser(roles = {"USER"})
    void readAuditEvents_rejectsNonAdminRole() throws Exception {
        mockMvc.perform(get("/api/v1/admin/audit/events"))
                .andExpect(status().isForbidden());
    }

    @Test
    void readAuditEvents_requiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/admin/audit/events"))
                .andExpect(status().isUnauthorized());
    }
}
