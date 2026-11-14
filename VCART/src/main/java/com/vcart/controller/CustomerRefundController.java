package com.vcart.controller;

import com.vcart.service.AuthService;
import com.vcart.service.payment.RefundService;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/refunds/{userId}/orders/{orderId}")
public class CustomerRefundController {
    private final RefundService refunds;
    private final AuthService auth;

    public CustomerRefundController(RefundService refunds, AuthService auth) {
        this.refunds = refunds;
        this.auth = auth;
    }

    @GetMapping
    public Map<String, Object> status(
        @PathVariable Long userId,
        @PathVariable Long orderId,
        Authentication authentication
    ) {
        assertOwner(userId, authentication);
        return refunds.customerStatus(userId, orderId);
    }

    @PostMapping
    public Map<String, Object> request(
        @PathVariable Long userId,
        @PathVariable Long orderId,
        @RequestBody Map<String, String> data,
        Authentication authentication
    ) {
        assertOwner(userId, authentication);
        if (data == null) {
            throw new IllegalArgumentException("Refund reason is required.");
        }
        return refunds.request(userId, orderId, data.get("reason"));
    }

    private void assertOwner(Long userId, Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new AccessDeniedException("Customer sign-in is required.");
        }
        Map<String, Object> user = auth.currentUser(authentication.getName());
        Object id = user.get("id");
        if (!"CUSTOMER".equalsIgnoreCase(String.valueOf(user.get("role"))) ||
                !(id instanceof Number number) || number.longValue() != userId) {
            throw new AccessDeniedException("You can only manage refunds for your own orders.");
        }
    }
}
