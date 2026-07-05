package com.elvo.delegatedaccess.service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Service;

import com.elvo.delegatedaccess.dto.request.DelegatedGrantRequest;
import com.elvo.delegatedaccess.dto.response.DelegatedGrantResponse;

@Service
public class DelegatedGrantService {

    private final ConcurrentHashMap<UUID, DelegatedGrantResponse> grants = new ConcurrentHashMap<>();

    public DelegatedGrantResponse createGrant(DelegatedGrantRequest request) {
        DelegatedGrantResponse grant = new DelegatedGrantResponse(
                UUID.randomUUID(),
                request.getOwnerUserId(),
                request.getDelegateUserId(),
                request.getScope(),
                "ACTIVE",
                request.getExpiresAt());
        grants.put(grant.getGrantId(), grant);
        return grant;
    }

    public List<DelegatedGrantResponse> listGrantsForOwner(UUID ownerUserId) {
        return grants.values().stream()
                .filter(grant -> ownerUserId.equals(grant.getOwnerUserId()))
                .filter(grant -> !"REVOKED".equals(grant.getStatus()))
                .toList();
    }

    public DelegatedGrantResponse revokeGrant(UUID grantId) {
        DelegatedGrantResponse grant = grants.get(grantId);
        if (grant == null) {
            return null;
        }
        grant.setStatus("REVOKED");
        return grant;
    }
}
