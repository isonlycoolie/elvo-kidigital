package com.elvo.billing.service.impl;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.eq;
import org.mockito.Mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import com.elvo.billing.audit.PaymentAuditLogger;
import com.elvo.billing.dto.request.UtilityPaymentRequestDto;
import com.elvo.billing.dto.response.PaymentResponseDto;
import com.elvo.billing.entity.BillPayment;
import com.elvo.billing.entity.enums.PaymentStatus;
import com.elvo.billing.exception.DuplicatePaymentException;
import com.elvo.billing.monitoring.BillingMetricsRecorder;
import com.elvo.billing.monitoring.SentryBreadcrumbLogger;
import com.elvo.billing.repository.BillPaymentRepository;
import com.elvo.billing.security.BillingRoleBasedAccessControl;
import com.elvo.billing.service.event.BillingEventPublisher;
import com.elvo.billing.service.orchestration.LookupFlow;
import com.elvo.billing.service.orchestration.PaymentFlow;

@ExtendWith(MockitoExtension.class)
class BillingServiceImplTest {

    @Mock
    private PaymentFlow paymentFlow;

    @Mock
    private LookupFlow lookupFlow;

    @Mock
    private BillPaymentRepository billPaymentRepository;

    @Mock
    private BillingEventPublisher billingEventPublisher;

    @Mock
    private PaymentAuditLogger paymentAuditLogger;

    @Mock
    private BillingMetricsRecorder billingMetricsRecorder;

    @Mock
    private SentryBreadcrumbLogger sentryBreadcrumbLogger;

    @Mock
    private BillingRoleBasedAccessControl roleBasedAccessControl;

    @Test
    void shouldReversePaymentUsingLockedReferenceAndPublishCompensationEvent() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "ops-admin",
                "N/A",
                java.util.List.of(new SimpleGrantedAuthority("ROLE_OPERATIONS_ADMIN"))));

        BillingServiceImpl service = new BillingServiceImpl(
            paymentFlow,
            lookupFlow,
            billPaymentRepository,
            billingEventPublisher,
                paymentAuditLogger,
                billingMetricsRecorder,
                sentryBreadcrumbLogger,
                roleBasedAccessControl);

        UtilityPaymentRequestDto request = new UtilityPaymentRequestDto();
        request.setReferenceNumber("REF-REV-1");

        BillPayment payment = new BillPayment();
        payment.setPaymentId(UUID.randomUUID());
        payment.setRequestId("REQ-REV-1");
        payment.setStatus(PaymentStatus.SUCCESS);
        payment.setAmount(BigDecimal.valueOf(1700));
        payment.setCurrency("TZS");

        when(billPaymentRepository.getPaymentByReferenceWithLock("REF-REV-1")).thenReturn(Optional.of(payment));
        when(billPaymentRepository.countByStatus(PaymentStatus.PENDING)).thenReturn(3L);

        PaymentResponseDto response = service.reversePayment(request);

        assertThat(response.getStatus()).isEqualTo(PaymentStatus.REVERSED);
        assertThat(response.getMetadata()).contains("compensationTriggered");
        verify(billPaymentRepository).updatePaymentStatus(payment.getPaymentId(), PaymentStatus.REVERSED);
        verify(paymentAuditLogger).logReverse(payment);
        verify(billingEventPublisher).publish(eq("billing.payment.reversed"), eq("REQ-REV-1"), eq(response.getMetadata()), eq("v1"));
        verify(billingMetricsRecorder).recordPendingPayments(3L);
        verify(roleBasedAccessControl).authorize(com.elvo.billing.security.BillingSensitivePermission.PAYMENT_REVERSE);
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldRejectReverseWhenPaymentIsNotSuccessful() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
            "ops-admin",
            "N/A",
            java.util.List.of(new SimpleGrantedAuthority("ROLE_OPERATIONS_ADMIN"))));

        BillingServiceImpl service = new BillingServiceImpl(
                paymentFlow,
                lookupFlow,
                billPaymentRepository,
                billingEventPublisher,
                paymentAuditLogger,
                billingMetricsRecorder,
            sentryBreadcrumbLogger,
            roleBasedAccessControl);

        UtilityPaymentRequestDto request = new UtilityPaymentRequestDto();
        request.setReferenceNumber("REF-REV-2");

        BillPayment payment = new BillPayment();
        payment.setPaymentId(UUID.randomUUID());
        payment.setRequestId("REQ-REV-2");
        payment.setStatus(PaymentStatus.PENDING);
        payment.setAmount(BigDecimal.valueOf(900));
        payment.setCurrency("TZS");

        when(billPaymentRepository.getPaymentByReferenceWithLock("REF-REV-2")).thenReturn(Optional.of(payment));

        assertThatThrownBy(() -> service.reversePayment(request))
                .isInstanceOf(DuplicatePaymentException.class)
                .hasMessageContaining("not reversible");

        verify(billPaymentRepository, never()).updatePaymentStatus(payment.getPaymentId(), PaymentStatus.REVERSED);
        verify(billingEventPublisher, never()).publish(
                eq("billing.payment.reversed"),
                eq("REQ-REV-2"),
                org.mockito.ArgumentMatchers.anyString(),
                eq("v1"));
        verify(roleBasedAccessControl).authorize(com.elvo.billing.security.BillingSensitivePermission.PAYMENT_REVERSE);
        SecurityContextHolder.clearContext();
        }

        @Test
        void shouldRejectReverseWhenRbacDeniesPermission() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
            "billing-user",
            "N/A",
            java.util.List.of(new SimpleGrantedAuthority("ROLE_BILLING_USER"))));

        BillingServiceImpl service = new BillingServiceImpl(
            paymentFlow,
            lookupFlow,
            billPaymentRepository,
            billingEventPublisher,
            paymentAuditLogger,
            billingMetricsRecorder,
            sentryBreadcrumbLogger,
            roleBasedAccessControl);

        UtilityPaymentRequestDto request = new UtilityPaymentRequestDto();
        request.setReferenceNumber("REF-RBAC-1");

        org.mockito.Mockito.doThrow(new AccessDeniedException("RBAC denied"))
            .when(roleBasedAccessControl)
            .authorize(com.elvo.billing.security.BillingSensitivePermission.PAYMENT_REVERSE);

        assertThatThrownBy(() -> service.reversePayment(request))
            .isInstanceOf(AccessDeniedException.class)
            .hasMessageContaining("RBAC denied");

        verify(billPaymentRepository, never()).getPaymentByReferenceWithLock("REF-RBAC-1");
        SecurityContextHolder.clearContext();
    }
}
