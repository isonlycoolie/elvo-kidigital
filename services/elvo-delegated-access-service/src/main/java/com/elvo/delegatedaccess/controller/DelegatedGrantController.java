package com.elvo.delegatedaccess.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.elvo.delegatedaccess.dto.request.DelegatedGrantRequest;
import com.elvo.delegatedaccess.dto.response.DelegatedGrantResponse;
import com.elvo.delegatedaccess.exception.ApiResponse;
import com.elvo.delegatedaccess.service.DelegatedGrantService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/delegated-access/grants")
@Validated
public class DelegatedGrantController {

    private final DelegatedGrantService delegatedGrantService;

    public DelegatedGrantController(DelegatedGrantService delegatedGrantService) {
        this.delegatedGrantService = delegatedGrantService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<DelegatedGrantResponse>> createGrant(@Valid @RequestBody DelegatedGrantRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Grant created", delegatedGrantService.createGrant(request)));
    }

    @GetMapping("/owner/{ownerUserId}")
    public ResponseEntity<ApiResponse<List<DelegatedGrantResponse>>> listGrants(@PathVariable UUID ownerUserId) {
        return ResponseEntity.ok(ApiResponse.ok("Grants loaded", delegatedGrantService.listGrantsForOwner(ownerUserId)));
    }

    @DeleteMapping("/{grantId}")
    public ResponseEntity<ApiResponse<DelegatedGrantResponse>> revokeGrant(@PathVariable UUID grantId) {
        DelegatedGrantResponse revoked = delegatedGrantService.revokeGrant(grantId);
        if (revoked == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(ApiResponse.ok("Grant revoked", revoked));
    }
}
