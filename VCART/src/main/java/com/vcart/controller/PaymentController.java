package com.vcart.controller;

import com.vcart.service.AuthService;
import com.vcart.service.payment.PaymentService;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/payments/{userId}/orders/{orderId}")
public class PaymentController {
    private final PaymentService payments;
    private final AuthService auth;

    public PaymentController(PaymentService payments, AuthService auth) {
        this.payments = payments;
        this.auth = auth;
    }

    @GetMapping
    public Map<String, Object> status(
        @PathVariable Long userId,
        @PathVariable Long orderId,
        Authentication authentication
    ) {
        assertOwner(userId, authentication);
        return payments.status(userId, orderId);
    }

    @PostMapping("/process")
    public Map<String, Object> process(
        @PathVariable Long userId,
        @PathVariable Long orderId,
        Authentication authentication
    ) {
        assertOwner(userId, authentication);
        return payments.process(userId, orderId);
    }

    private void assertOwner(Long userId, Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new AccessDeniedException("Customer sign-in is required.");
        }
        Object id = auth.currentUser(authentication.getName()).get("id");
        if (!(id instanceof Number number) || number.longValue() != userId) {
            throw new AccessDeniedException("You can only access payments for your own orders.");
        }
    }
}
