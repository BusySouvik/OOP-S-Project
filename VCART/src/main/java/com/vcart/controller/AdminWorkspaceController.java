package com.vcart.controller;

import com.vcart.service.AdminWorkspaceService;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/{userId}/workspace")
public class AdminWorkspaceController {
    private final AdminWorkspaceService service;

    public AdminWorkspaceController(AdminWorkspaceService service) {
        this.service = service;
    }

    private Map<String, Object> requiredData(Map<String, Object> data) {
        if (data == null) {
            throw new IllegalArgumentException("Request data is required.");
        }
        return data;
    }

    private boolean requiredBoolean(Map<String, Object> data, String field) {
        Object value = requiredData(data).get(field);
        if (value instanceof Boolean bool) return bool;
        if (value instanceof String text && ("true".equalsIgnoreCase(text) || "false".equalsIgnoreCase(text))) {
            return Boolean.parseBoolean(text);
        }
        throw new IllegalArgumentException("A valid " + field + " value is required.");
    }

    @GetMapping("/categories")
    public Object categories(@PathVariable Long userId) {
        return service.categories(userId);
    }

    @PostMapping("/categories")
    public Object createCategory(@PathVariable Long userId, @RequestBody Map<String,Object> data) {
        return service.createCategory(userId,requiredData(data));
    }

    @PutMapping("/categories/{id}")
    public Object updateCategory(@PathVariable Long userId,@PathVariable Long id,@RequestBody Map<String,Object> data) {
        return service.updateCategory(userId,id,requiredData(data));
    }

    @DeleteMapping("/categories/{id}")
    public Object deactivateCategory(@PathVariable Long userId,@PathVariable Long id) {
        return service.deactivateCategory(userId,id);
    }

    @GetMapping("/customers")
    public Object customers(@PathVariable Long userId) {
        return service.customers(userId);
    }

    @PatchMapping("/customers/{id}")
    public Object customerActive(@PathVariable Long userId,@PathVariable Long id,@RequestBody Map<String,Object> data) {
        return service.customerActive(userId,id,requiredBoolean(data,"active"));
    }

    @GetMapping("/inventory/alerts")
    public Object inventory(@PathVariable Long userId) {
        return service.inventoryAlerts(userId);
    }

    @PatchMapping("/inventory/{id}")
    public Object stock(@PathVariable Long userId,@PathVariable Long id,@RequestBody Map<String,Integer> data) {
        if (data == null) throw new IllegalArgumentException("Request data is required.");
        return service.updateStock(userId,id,data.get("quantity"));
    }

    @GetMapping("/transactions")
    public Object transactions(@PathVariable Long userId) {
        return service.transactions(userId);
    }

    @GetMapping("/finance")
    public Object finance(@PathVariable Long userId) {
        return service.finance(userId);
    }

    @GetMapping("/refunds")
    public Object refunds(@PathVariable Long userId) {
        return service.refunds(userId);
    }

    @PostMapping("/refunds")
    public Object createRefund(@PathVariable Long userId,@RequestBody Map<String,Object> data) {
        return service.createRefund(userId,requiredData(data));
    }

    @PatchMapping("/refunds/{id}")
    public Object updateRefund(@PathVariable Long userId,@PathVariable Long id,@RequestBody Map<String,Object> data) {
        return service.updateRefund(userId,id,requiredData(data));
    }

    @PostMapping("/refunds/{id}/process")
    public Object processRefund(@PathVariable Long userId,@PathVariable Long id) {
        return service.processRefund(userId,id);
    }

    @GetMapping("/staff")
    public Object staff(@PathVariable Long userId) {
        return service.staff(userId);
    }

    @PostMapping("/staff")
    public Object createStaff(@PathVariable Long userId,@RequestBody Map<String,Object> data) {
        return service.createStaff(userId,requiredData(data));
    }

    @PatchMapping("/staff/{id}")
    public Object staffActive(@PathVariable Long userId,@PathVariable Long id,@RequestBody Map<String,Object> data) {
        return service.staffActive(userId,id,requiredBoolean(data,"active"));
    }

    @PutMapping("/staff/{id}")
    public Object updateStaff(@PathVariable Long userId,@PathVariable Long id,@RequestBody Map<String,Object> data) {
        return service.updateStaff(userId,id,requiredData(data));
    }

    @PostMapping("/staff/{id}/account")
    public Object enableStaffAccount(@PathVariable Long userId,@PathVariable Long id,@RequestBody Map<String,Object> data) {
        return service.enableStaffAccount(userId,id,requiredData(data));
    }

    @PostMapping("/staff/{id}/password")
    public Object resetStaffPassword(@PathVariable Long userId,@PathVariable Long id,@RequestBody Map<String,Object> data) {
        return service.resetStaffPassword(userId,id,requiredData(data));
    }

    @GetMapping("/support")
    public Object support(@PathVariable Long userId) {
        return service.support(userId);
    }

    @PostMapping("/support")
    public Object createSupport(@PathVariable Long userId,@RequestBody Map<String,Object> data) {
        return service.createSupport(userId,requiredData(data));
    }

    @PatchMapping("/support/{id}")
    public Object updateSupport(@PathVariable Long userId,@PathVariable Long id,@RequestBody Map<String,Object> data) {
        return service.updateSupport(userId,id,requiredData(data));
    }
}