package com.vcart.controller;

import com.vcart.service.CustomerFeatureService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/customer")
public class CustomerFeatureController {
    private final CustomerFeatureService service;
    public CustomerFeatureController(CustomerFeatureService service) { this.service = service; }
    private String email(Authentication auth) {
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) throw new IllegalArgumentException("Please sign in as a customer.");
        return auth.getName();
    }
    @GetMapping("/profile") public Object profile(Authentication a) { return service.profile(email(a)); }
    @PatchMapping("/profile") public Object updateProfile(Authentication a, @RequestBody Map<String,String> b) { return service.updateProfile(email(a), b); }
    @GetMapping("/wishlist") public Object wishlist(Authentication a) { return service.wishlist(email(a)); }
    @PostMapping("/wishlist/{productId}") public Object addWishlist(Authentication a, @PathVariable Long productId) { return service.addWishlist(email(a), productId); }
    @DeleteMapping("/wishlist/{productId}") public Object removeWishlist(Authentication a, @PathVariable Long productId) { return service.removeWishlist(email(a), productId); }
    @GetMapping("/support") public Object support(Authentication a) { return service.supportMessages(email(a)); }
    @PostMapping("/support") public Object createSupport(Authentication a, @RequestBody Map<String,String> b) { return service.createSupportMessage(email(a), b); }
}
