package com.elvo.identity.client;

import java.util.Map;
import java.util.UUID;

import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class DefaultAccountLifecycleClient implements AccountLifecycleClient {

    private final RestClient restClient;
    private final ProvisioningClientProperties properties;
    private final ProvisioningRetryExecutor retryExecutor;
    private final InternalServiceJwtTokenGenerator tokenGenerator;

    public DefaultAccountLifecycleClient(ProvisioningClientProperties properties,
                                         ProvisioningRetryExecutor retryExecutor,
                                         InternalServiceJwtTokenGenerator tokenGenerator) {
        this.properties = properties;
        this.retryExecutor = retryExecutor;
        this.tokenGenerator = tokenGenerator;
        this.restClient = createRestClient(properties.getAccountLifecycleBaseUrl());
    }

    @Override
    public void syncPostVerification(UUID userId, boolean emailVerified, boolean mobileVerified, String idempotencyKey) {
        String kycStatus = resolveKycStatus(emailVerified, mobileVerified);
        retryExecutor.execute("account verification sync", () -> {
            restClient.post()
                    .uri("/api/v1/internal/accounts/sync-verification")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("X-Source-Service", properties.getSourceServiceName())
                    .header("Authorization", "Bearer " + tokenGenerator.generateToken(properties.getSourceServiceName()))
                    .body(Map.of(
                            "userId", userId,
                            "kycStatus", kycStatus,
                            "reason", "Identity post-verification sync",
                            "requestId", idempotencyKey,
                            "correlationId", idempotencyKey,
                            "sourceService", properties.getSourceServiceName()))
                    .retrieve()
                    .toBodilessEntity();
        });
    }

    private static String resolveKycStatus(boolean emailVerified, boolean mobileVerified) {
        if (emailVerified && mobileVerified) {
            return "VERIFIED";
        }
        if (emailVerified || mobileVerified) {
            return "PARTIAL";
        }
        return "UNVERIFIED";
    }

    private RestClient createRestClient(String baseUrl) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Math.max(1, properties.getConnectTimeoutMs()));
        requestFactory.setReadTimeout(Math.max(1, properties.getReadTimeoutMs()));
        return RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .build();
    }
}
