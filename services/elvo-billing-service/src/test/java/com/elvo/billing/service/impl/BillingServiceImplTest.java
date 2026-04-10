package com.elvo.billing.service.impl;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import org.mockito.Mock;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import com.elvo.billing.audit.PaymentAuditLogger;
import com.elvo.billing.dto.request.ProviderCallbackDto;
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
import com.elvo.billing.service.settlement.BillingWalletSettlementService;

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
    private BillingWalletSettlementService walletSettlementService;

    @Mock
    private PaymentAuditLogger paymentAuditLogger;

    @Mock
    private BillingMetricsRecorder billingMetricsRecorder;

    @Mock
    private SentryBreadcrumbLogger sentryBreadcrumbLogger;

    @Mock
    private BillingRoleBasedAccessControl roleBasedAccessControl;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

        private BillingServiceImpl newService() {
                return new BillingServiceImpl(
                                paymentFlow,
                                lookupFlow,
                                billPaymentRepository,
                                billingEventPublisher,
                                paymentAuditLogger,
                                billingMetricsRecorder,
                                sentryBreadcrumbLogger,
                                roleBasedAccessControl);
        }

    @Test
    void shouldReversePaymentUsingLockedReferenceAndPublishCompensationEvent() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "ops-admin",
                "N/A",
                java.util.List.of(new SimpleGrantedAuthority("ROLE_OPERATIONS_ADMIN"))));

                BillingServiceImpl service = newService();

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
        }

    @Test
    void executePaymentShouldReserveAndConfirmWalletWhenSuccessful() {
                BillingServiceImpl service = newService();
        ReflectionTestUtils.setField(service, "walletSettlementService", walletSettlementService);
        ReflectionTestUtils.setField(service, "walletSettlementEnabled", true);

        UUID userId = UUID.randomUUID();
        UUID walletId = UUID.randomUUID();
        UUID reservationId = UUID.randomUUID();

        UtilityPaymentRequestDto request = new UtilityPaymentRequestDto();
        request.setReferenceNumber("REF-EXEC-1");
        request.setAmount(new BigDecimal("1200.00"));
        request.setMetadata("{\"userId\":\"" + userId + "\"}");

        when(walletSettlementService.reserve(eq(userId), eq(new BigDecimal("1200.00")), eq("bill:REF-EXEC-1:reserve")))
                .thenReturn(new BillingWalletSettlementService.WalletReservation(walletId, reservationId));

        PaymentResponseDto response = new PaymentResponseDto();
        response.setStatus(PaymentStatus.SUCCESS);
        when(paymentFlow.execute(any(), any(), any(), any(), any(), any(), eq(userId), eq(walletId))).thenReturn(response);

        PaymentResponseDto result = service.executePayment(request);

        assertThat(result.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
        verify(walletSettlementService).reserve(eq(userId), eq(new BigDecimal("1200.00")), eq("bill:REF-EXEC-1:reserve"));
        verify(walletSettlementService).confirm(eq(userId), eq(reservationId), eq("bill:REF-EXEC-1:confirm"));
        verify(walletSettlementService, never()).release(eq(userId), eq(reservationId), any());
    }

    @Test
    void executePaymentShouldReleaseReservationWhenPaymentFlowFails() {
        BillingServiceImpl service = newService();
        ReflectionTestUtils.setField(service, "walletSettlementService", walletSettlementService);
        ReflectionTestUtils.setField(service, "walletSettlementEnabled", true);

        UUID userId = UUID.randomUUID();
        UUID walletId = UUID.randomUUID();
        UUID reservationId = UUID.randomUUID();

        UtilityPaymentRequestDto request = new UtilityPaymentRequestDto();
        request.setReferenceNumber("REF-EXEC-2");
        request.setAmount(new BigDecimal("640.00"));
        request.setMetadata("{\"userId\":\"" + userId + "\"}");

        when(walletSettlementService.reserve(eq(userId), eq(new BigDecimal("640.00")), eq("bill:REF-EXEC-2:reserve")))
                .thenReturn(new BillingWalletSettlementService.WalletReservation(walletId, reservationId));
        when(paymentFlow.execute(any(), any(), any(), any(), any(), any(), eq(userId), eq(walletId)))
                .thenThrow(new IllegalStateException("provider-down"));

        assertThatThrownBy(() -> service.executePayment(request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("provider-down");

        verify(walletSettlementService).release(eq(userId), eq(reservationId), eq("bill:REF-EXEC-2:release"));
    }

    @Test
    void handleProviderCallbackShouldReleaseWalletReservationWhenFailed() {
        BillingServiceImpl service = newService();
        ReflectionTestUtils.setField(service, "walletSettlementService", walletSettlementService);
        ReflectionTestUtils.setField(service, "walletSettlementEnabled", true);

        UUID paymentId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID reservationId = UUID.randomUUID();

        BillPayment payment = new BillPayment();
        payment.setPaymentId(paymentId);
        payment.setReferenceNumber("REF-CB-FAILED-1");
        payment.setRequestId("REQ-CB-1");
        payment.setStatus(PaymentStatus.PENDING);
        payment.setAmount(new BigDecimal("300.00"));
        payment.setCurrency("TZS");
        payment.setMetadata("{\"userId\":\"" + userId + "\",\"walletReservationId\":\"" + reservationId + "\"}");

        ProviderCallbackDto callback = new ProviderCallbackDto();
        callback.setReferenceNumber("REF-CB-FAILED-1");
        callback.setStatus("FAILED");
        callback.setExternalReference("EXT-REF-CB-1");

        when(billPaymentRepository.getPaymentByReferenceWithLock("REF-CB-FAILED-1")).thenReturn(Optional.of(payment));
        when(billPaymentRepository.countByStatus(PaymentStatus.PENDING)).thenReturn(2L);

        PaymentResponseDto result = service.handleProviderCallback(callback);

        assertThat(result.getStatus()).isEqualTo(PaymentStatus.FAILED);
        verify(billPaymentRepository).updatePaymentStatus(paymentId, PaymentStatus.FAILED);
        verify(walletSettlementService).release(eq(userId), eq(reservationId), eq("bill:REF-CB-FAILED-1:release"));
    }

    @Test
    void shouldRejectReverseWhenPaymentIsNotSuccessful() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "ops-admin",
                "N/A",
                java.util.List.of(new SimpleGrantedAuthority("ROLE_OPERATIONS_ADMIN"))));

        BillingServiceImpl service = newService();

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
                any(),
                eq("v1"));
        verify(roleBasedAccessControl).authorize(com.elvo.billing.security.BillingSensitivePermission.PAYMENT_REVERSE);
    }

    @Test
    void shouldRejectReverseWhenRbacDeniesPermission() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "billing-user",
                "N/A",
                java.util.List.of(new SimpleGrantedAuthority("ROLE_BILLING_USER"))));

        BillingServiceImpl service = newService();

        UtilityPaymentRequestDto request = new UtilityPaymentRequestDto();
        request.setReferenceNumber("REF-RBAC-1");

        doThrow(new AccessDeniedException("RBAC denied"))
                .when(roleBasedAccessControl)
                .authorize(com.elvo.billing.security.BillingSensitivePermission.PAYMENT_REVERSE);

        assertThatThrownBy(() -> service.reversePayment(request))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("RBAC denied");

        verify(billPaymentRepository, never()).getPaymentByReferenceWithLock("REF-RBAC-1");
    }
}
