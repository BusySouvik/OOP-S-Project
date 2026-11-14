package com.vcart.controller;

import com.vcart.service.DiscountService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.math.BigDecimal;
import java.util.Map;

@RestController
@RequestMapping("/api/discounts")
public class DiscountController {
    private final DiscountService service;

    public DiscountController(DiscountService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<?> all() {
        return ResponseEntity.ok(service.getAll());
    }

    @PostMapping("/validate")
    public ResponseEntity<?> validate(@RequestBody Map<String, String> data) {
        if (data == null || data.get("subtotal") == null) {
            throw new IllegalArgumentException("Valid code and subtotal are required.");
        }
        return ResponseEntity.ok(service.validate(
            data.get("code"), new BigDecimal(data.get("subtotal"))
        ));
    }

    @PostMapping
    public ResponseEntity<?> create(@RequestBody Map<String, Object> data) {
        return ResponseEntity.ok(service.create(data));
    }

    @PatchMapping("/{code}/deactivate")
    public ResponseEntity<?> deactivate(@PathVariable String code) {
        service.deactivate(code);
        return ResponseEntity.ok(Map.of("message", "Discount deactivated."));
    }
}