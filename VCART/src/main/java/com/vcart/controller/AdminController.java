package com.vcart.controller;

import com.vcart.service.AdminService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
public class AdminController {
    private final AdminService service;

    public AdminController(AdminService service) {
        this.service = service;
    }

    @GetMapping("/{userId}/dashboard")
    public ResponseEntity<?> dashboard(@PathVariable Long userId) {
        return ResponseEntity.ok(service.dashboard(userId));
    }

    @GetMapping("/{userId}/orders")
    public ResponseEntity<?> orders(@PathVariable Long userId) {
        return ResponseEntity.ok(service.allOrders(userId));
    }

    @PatchMapping("/{userId}/orders/{orderId}/status")
    public ResponseEntity<?> updateStatus(
            @PathVariable Long userId,
            @PathVariable Long orderId,
            @RequestBody Map<String, String> body) {
        service.updateOrderStatus(userId, orderId, body.get("status"));
        return ResponseEntity.ok(Map.of("message", "Order status updated."));
    }
}