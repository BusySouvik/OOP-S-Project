package com.vcart.controller;

import com.vcart.service.CartService;
import com.vcart.service.AuthService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
import org.springframework.security.core.Authentication;

@RestController
@RequestMapping("/api/cart")
public class CartController {
    private final CartService service;
    private final AuthService authService;

    public CartController(CartService service, AuthService authService) {
        this.service = service;
        this.authService = authService;
    }

    private void assertOwner(Long userId, Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new IllegalArgumentException("Please sign in.");
        }
        Long sessionUserId = ((Number) authService.currentUser(authentication.getName()).get("id")).longValue();
        if (!sessionUserId.equals(userId)) {
            throw new org.springframework.security.access.AccessDeniedException("You can only access your own cart.");
        }
    }

    @GetMapping("/{userId}")
    public ResponseEntity<?> getCart(@PathVariable Long userId, Authentication authentication) {
        assertOwner(userId, authentication);
        return ResponseEntity.ok(service.getCart(userId));
    }

    @PostMapping("/{userId}/items")
    public ResponseEntity<?> addItem(
            @PathVariable Long userId,
            @RequestBody Map<String, Integer> body, Authentication authentication) {
        assertOwner(userId, authentication);
        if (body == null || body.get("productId") == null || body.get("productId") < 1) {
            throw new IllegalArgumentException("A valid product ID is required.");
        }
        service.addItem(userId, body.get("productId").longValue(), body.get("quantity"));
        return ResponseEntity.ok(Map.of("message", "Item added to cart."));
    }

    @PatchMapping("/{userId}/items/{itemId}")
    public ResponseEntity<?> updateItem(
            @PathVariable Long userId,
            @PathVariable Long itemId,
            @RequestBody Map<String, Integer> body, Authentication authentication) {
        assertOwner(userId, authentication);
            if (body == null) throw new IllegalArgumentException("Quantity is required.");
        service.updateQuantity(userId, itemId, body.get("quantity"));
        return ResponseEntity.ok(Map.of("message", "Cart quantity updated."));
    }

    @DeleteMapping("/{userId}/items/{itemId}")
    public ResponseEntity<?> removeItem(
            @PathVariable Long userId, @PathVariable Long itemId, Authentication authentication) {
        assertOwner(userId, authentication);
        service.removeItem(userId, itemId);
        return ResponseEntity.ok(Map.of("message", "Item removed from cart."));
    }
}