package com.elvo.identity.client;

import java.util.HashMap;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.retry.backoff.ExponentialRandomBackOffPolicy;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Component
public class ProvisioningRetryExecutor {

    private final RetryTemplate retryTemplate;

    public ProvisioningRetryExecutor(ProvisioningClientProperties properties) {
        SimpleRetryPolicy retryPolicy = new SimpleRetryPolicy(
                Math.max(1, properties.getMaxAttempts()),
                retryableExceptions(),
                true
        );

        ExponentialRandomBackOffPolicy backOffPolicy = new ExponentialRandomBackOffPolicy();
        backOffPolicy.setInitialInterval(Math.max(1, properties.getRetryBackoffMs()));
        backOffPolicy.setMultiplier(Math.max(1.0, properties.getRetryBackoffMultiplier()));
        backOffPolicy.setMaxInterval(Math.max(
                Math.max(1, properties.getRetryBackoffMs()),
                properties.getRetryMaxBackoffMs()
        ));

        RetryTemplate template = new RetryTemplate();
        template.setRetryPolicy(retryPolicy);
        template.setBackOffPolicy(backOffPolicy);
        this.retryTemplate = template;
    }

    public void execute(String operation, Runnable action) {
        try {
            retryTemplate.execute(context -> {
                try {
                    action.run();
                    return null;
                } catch (RestClientResponseException ex) {
                    if (!isRetryableStatus(ex.getStatusCode())) {
                        throw new NonRetryableProvisioningException(operation, ex);
                    }
                    throw ex;
                }
            });
        } catch (NonRetryableProvisioningException ex) {
            throw new IllegalStateException("Provisioning call rejected for " + operation, ex.getCause());
        } catch (Exception ex) {
            throw new IllegalStateException("Provisioning call failed for " + operation, ex);
        }
    }

    private boolean isRetryableStatus(HttpStatusCode statusCode) {
        return statusCode.is5xxServerError() || statusCode.value() == HttpStatus.TOO_MANY_REQUESTS.value();
    }

    private Map<Class<? extends Throwable>, Boolean> retryableExceptions() {
        Map<Class<? extends Throwable>, Boolean> retryable = new HashMap<>();
        retryable.put(ResourceAccessException.class, true);
        retryable.put(RestClientResponseException.class, true);
        retryable.put(RestClientException.class, true);
        retryable.put(NonRetryableProvisioningException.class, false);
        return retryable;
    }

    private static final class NonRetryableProvisioningException extends RuntimeException {
        private NonRetryableProvisioningException(String operation, Throwable cause) {
            super("Non-retryable provisioning error for " + operation, cause);
        }
    }
}