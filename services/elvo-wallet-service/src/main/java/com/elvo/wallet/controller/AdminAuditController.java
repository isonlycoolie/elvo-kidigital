package com.elvo.wallet.controller;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.elvo.wallet.audit.AuditEventRecord;
import com.elvo.wallet.audit.ImmutableAuditStorageService;
import com.elvo.wallet.dto.response.AuditEventResponseDto;

@RestController
@RequestMapping("/api/v1/admin/audit")
@Validated
@PreAuthorize("hasRole('AUDIT_ADMIN')")
public class AdminAuditController {

    private static final Logger AUDIT_LOG = LoggerFactory.getLogger("audit.wallet.admin.controller");

    private final ImmutableAuditStorageService immutableAuditStorageService;

    public AdminAuditController(ImmutableAuditStorageService immutableAuditStorageService) {
        this.immutableAuditStorageService = immutableAuditStorageService;
    }

    @GetMapping("/events")
    public List<AuditEventResponseDto> readAuditEvents(@RequestParam(name = "limit", defaultValue = "50") int limit) {
        int boundedLimit = Math.max(1, Math.min(limit, 200));
        AUDIT_LOG.info("admin_audit_log_read_requested limit={}", boundedLimit);
        return immutableAuditStorageService.readRecentEvents(boundedLimit).stream()
                .map(this::toResponse)
                .toList();
    }

    private AuditEventResponseDto toResponse(AuditEventRecord record) {
        return new AuditEventResponseDto(
                record.getEventType(),
                record.getRequestId(),
                record.getCorrelationId(),
                record.getOccurredAt(),
                record.getPayload(),
                record.getPreviousHash(),
                record.getRecordHash(),
                record.getCreatedAt());
    }
}