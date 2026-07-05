package com.elvo.wallet.client;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class DefaultMobileMoneyDisbursementClient implements MobileMoneyDisbursementClient {

    private static final Logger AUDIT_LOG = LoggerFactory.getLogger("audit.wallet.disbursement");

    private final RestClient restClient;
    private final boolean httpEnabled;

    public DefaultMobileMoneyDisbursementClient(
            @Value("${elvo.clients.mobile-money.base-url:}") String baseUrl) {
        if (baseUrl != null && !baseUrl.isBlank()) {
            this.restClient = RestClient.builder().baseUrl(baseUrl).build();
            this.httpEnabled = true;
        } else {
            this.restClient = null;
            this.httpEnabled = false;
        }
    }

    @Override
    public DisbursementResult disburse(UUID userId, String msisdn, BigDecimal amount, String reference) {
        if (!httpEnabled) {
            AUDIT_LOG.info("mobile_money_disbursement_stub userId={} msisdn={} amount={} reference={}",
                    userId, maskMsisdn(msisdn), amount, reference);
            return new DisbursementResult(true, "stub-" + reference, "Ledger-only disbursement (no MNO rail configured)");
        }

        DisbursementResponse response = restClient.post()
                .uri("/api/v1/disbursements")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(
                        "userId", userId.toString(),
                        "msisdn", msisdn,
                        "amount", amount,
                        "reference", reference))
                .retrieve()
                .body(DisbursementResponse.class);

        if (response == null || !response.success) {
            return new DisbursementResult(false, null, response == null ? "Empty disbursement response" : response.message);
        }
        return new DisbursementResult(true, response.externalReference, response.message);
    }

    private String maskMsisdn(String msisdn) {
        if (msisdn == null || msisdn.length() < 4) {
            return "***";
        }
        return "***" + msisdn.substring(msisdn.length() - 2);
    }

    private static final class DisbursementResponse {
        public boolean success;
        public String externalReference;
        public String message;
    }
}
