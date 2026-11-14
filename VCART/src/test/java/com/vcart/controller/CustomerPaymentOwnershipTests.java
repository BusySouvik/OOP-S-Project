package com.vcart.controller;

import com.vcart.service.AuthService;
import com.vcart.service.payment.PaymentService;
import com.vcart.service.payment.RefundService;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class CustomerPaymentOwnershipTests {
    @Test
    void customerCannotReadAnotherUsersPayment() {
        AuthService auth = mock(AuthService.class);
        PaymentService payments = mock(PaymentService.class);
        when(auth.currentUser("customer@example.com")).thenReturn(
            Map.of("id", 1L, "role", "CUSTOMER"));
        PaymentController controller = new PaymentController(payments, auth);

        assertThrows(AccessDeniedException.class, () -> controller.status(
            2L, 9L, new UsernamePasswordAuthenticationToken(
                "customer@example.com", "n/a")));
        verifyNoInteractions(payments);
    }

    @Test
    void customerCannotRequestRefundForAnotherUsersOrder() {
        AuthService auth = mock(AuthService.class);
        RefundService refunds = mock(RefundService.class);
        when(auth.currentUser("customer@example.com")).thenReturn(
            Map.of("id", 1L, "role", "CUSTOMER"));
        CustomerRefundController controller = new CustomerRefundController(refunds, auth);

        assertThrows(AccessDeniedException.class, () -> controller.request(
            2L, 9L, Map.of("reason", "not my order"),
            new UsernamePasswordAuthenticationToken("customer@example.com", "n/a")));
        verifyNoInteractions(refunds);
    }
}
