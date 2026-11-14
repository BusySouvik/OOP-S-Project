package com.vcart.controller;

import com.vcart.service.StaffOperationsService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/staff")
public class StaffOperationsController {
    private final StaffOperationsService service;

    public StaffOperationsController(StaffOperationsService service) {
        this.service = service;
    }

    @GetMapping("/orders")
    public List<Map<String, Object>> orders(Authentication authentication) {
        return service.orders(authentication.getName());
    }

    @PatchMapping("/orders/{orderId}/status")
    public Map<String, Object> updateOrderStatus(
        Authentication authentication,
        @PathVariable Long orderId,
        @RequestBody Map<String, String> data
    ) {
        if (data == null) {
            throw new IllegalArgumentException("Order status is required.");
        }
        return service.updateOrderStatus(authentication.getName(), orderId, data.get("status"));
    }

    @GetMapping("/support")
    public List<Map<String, Object>> support(Authentication authentication) {
        return service.support(authentication.getName());
    }

    @PatchMapping("/support/{supportId}/status")
    public Map<String, Object> updateSupportStatus(
        Authentication authentication,
        @PathVariable Long supportId,
        @RequestBody Map<String, String> data
    ) {
        if (data == null) {
            throw new IllegalArgumentException("Support status is required.");
        }
        return service.updateSupportStatus(authentication.getName(), supportId, data.get("status"));
    }
}
