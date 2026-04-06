package com.elvo.wallet.security;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

import org.springframework.stereotype.Service;

@Service
public class AmlCaseWorkflowService {

    public enum CaseStatus {
        OPEN,
        UNDER_REVIEW,
        RESOLVED
    }

    public record AmlCase(String caseId,
                          String category,
                          UUID userId,
                          UUID walletId,
                          String operation,
                          String reason,
                          Map<String, Object> evidence,
                          CaseStatus status,
                          boolean suspiciousActivityConfirmed,
                          String resolutionNotes,
                          String resolvedBy,
                          Instant createdAt,
                          Instant updatedAt) {
    }

    private final ConcurrentHashMap<String, AmlCase> cases = new ConcurrentHashMap<>();
    private final ConcurrentLinkedDeque<String> caseOrder = new ConcurrentLinkedDeque<>();

    public AmlCase createCase(String category,
                              UUID userId,
                              UUID walletId,
                              String operation,
                              String reason,
                              Map<String, Object> evidence) {
        Instant now = Instant.now();
        String caseId = "aml-" + UUID.randomUUID();
        AmlCase amlCase = new AmlCase(
                caseId,
                category,
                userId,
                walletId,
                operation,
                reason,
                evidence == null ? Map.of() : Map.copyOf(evidence),
                CaseStatus.OPEN,
                false,
                null,
                null,
                now,
                now);
        cases.put(caseId, amlCase);
        caseOrder.addFirst(caseId);
        return amlCase;
    }

    public List<AmlCase> listCases(CaseStatus status, int limit) {
        int boundedLimit = Math.max(1, Math.min(limit, 200));
        List<AmlCase> results = new ArrayList<>();
        for (String caseId : caseOrder) {
            AmlCase amlCase = cases.get(caseId);
            if (amlCase == null) {
                continue;
            }
            if (status != null && amlCase.status() != status) {
                continue;
            }
            results.add(amlCase);
            if (results.size() >= boundedLimit) {
                break;
            }
        }
        results.sort(Comparator.comparing(AmlCase::createdAt).reversed());
        return results;
    }

    public AmlCase getCase(String caseId) {
        if (caseId == null || caseId.isBlank()) {
            return null;
        }
        return cases.get(caseId);
    }

    public AmlCase setUnderReview(String caseId) {
        AmlCase existing = getCase(caseId);
        if (existing == null) {
            return null;
        }
        AmlCase updated = new AmlCase(
                existing.caseId(),
                existing.category(),
                existing.userId(),
                existing.walletId(),
                existing.operation(),
                existing.reason(),
                existing.evidence(),
                CaseStatus.UNDER_REVIEW,
                existing.suspiciousActivityConfirmed(),
                existing.resolutionNotes(),
                existing.resolvedBy(),
                existing.createdAt(),
                Instant.now());
        cases.put(caseId, updated);
        return updated;
    }

    public AmlCase resolveCase(String caseId,
                               boolean suspiciousActivityConfirmed,
                               String resolutionNotes,
                               String resolvedBy) {
        AmlCase existing = getCase(caseId);
        if (existing == null) {
            return null;
        }
        AmlCase updated = new AmlCase(
                existing.caseId(),
                existing.category(),
                existing.userId(),
                existing.walletId(),
                existing.operation(),
                existing.reason(),
                existing.evidence(),
                CaseStatus.RESOLVED,
                suspiciousActivityConfirmed,
                resolutionNotes,
                resolvedBy,
                existing.createdAt(),
                Instant.now());
        cases.put(caseId, updated);
        return updated;
    }
}
