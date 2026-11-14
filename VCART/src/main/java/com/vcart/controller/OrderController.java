package com.vcart.controller;

import com.vcart.service.OrderService;
import com.vcart.service.AuthService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import org.springframework.security.core.Authentication;

@RestController
@RequestMapping("/api/orders")
public class OrderController {
    private final OrderService service;
    private final AuthService authService;

    public OrderController(OrderService service, AuthService authService) {
        this.service = service;
        this.authService = authService;
    }

    private void assertOwner(Long userId, Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new IllegalArgumentException("Please sign in.");
        }
        Long sessionUserId = ((Number) authService.currentUser(authentication.getName()).get("id")).longValue();
        if (!sessionUserId.equals(userId)) {
            throw new org.springframework.security.access.AccessDeniedException("You can only access your own orders.");
        }
    }

    @PostMapping("/checkout/{userId}")
    public ResponseEntity<?> checkout(
            @PathVariable Long userId,
            @RequestBody Map<String, String> data, Authentication authentication) {
        assertOwner(userId, authentication);
        return ResponseEntity.ok(service.checkout(userId, data));
    }

    @GetMapping("/{userId}")
    public ResponseEntity<?> getOrders(@PathVariable Long userId, Authentication authentication) {
        assertOwner(userId, authentication);
        return ResponseEntity.ok(service.getOrders(userId));
    }

    @GetMapping("/{userId}/{orderId}")
    public ResponseEntity<?> getOrder(
            @PathVariable Long userId,
            @PathVariable Long orderId, Authentication authentication) {
        assertOwner(userId, authentication);
        return ResponseEntity.ok(service.getOrder(userId, orderId));
    }

    @PatchMapping("/{userId}/{orderId}/cancel")
    public ResponseEntity<?> cancelOrder(
            @PathVariable Long userId,
            @PathVariable Long orderId, Authentication authentication) {
        assertOwner(userId, authentication);
        service.cancelOrder(userId, orderId);
        return ResponseEntity.ok(
            Map.of("message", "Order cancelled and inventory restored.")
        );
    }
}