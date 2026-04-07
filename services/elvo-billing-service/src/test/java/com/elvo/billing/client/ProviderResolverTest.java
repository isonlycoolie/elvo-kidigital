package com.elvo.billing.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.elvo.billing.dto.request.UtilityPaymentRequestDto;
import com.elvo.billing.dto.response.LookupResponseDto;
import com.elvo.billing.dto.response.PaymentResponseDto;
import org.junit.jupiter.api.Test;

class ProviderResolverTest {

    @Test
    void shouldResolveRegisteredAdapterByNormalizedServiceCode() {
        ProviderResolver resolver = new ProviderResolver();
        BillingAdapter adapter = new BillingAdapter() {
            @Override
            public LookupResponseDto lookup(UtilityPaymentRequestDto paymentRequest) {
                return new LookupResponseDto();
            }

            @Override
            public PaymentResponseDto pay(UtilityPaymentRequestDto paymentRequest) {
                return new PaymentResponseDto();
            }
        };

        resolver.register("selcom", adapter);

        assertThat(resolver.supports(" SELCOM ")).isTrue();
        assertThat(resolver.resolve(" selcom ")).isSameAs(adapter);
    }

    @Test
    void shouldRejectUnknownServiceCode() {
        ProviderResolver resolver = new ProviderResolver();

        assertThatThrownBy(() -> resolver.resolve("unknown"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("No billing adapter registered for serviceCode");
    }

    @Test
    void shouldRejectBlankServiceCode() {
        ProviderResolver resolver = new ProviderResolver();

        assertThatThrownBy(() -> resolver.resolve("   "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("serviceCode is required");
    }
}