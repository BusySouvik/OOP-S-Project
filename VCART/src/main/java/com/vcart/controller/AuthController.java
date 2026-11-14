package com.vcart.controller;

import com.vcart.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService service;
    private final SecurityContextRepository contextRepository;

    public AuthController(AuthService service,
                          SecurityContextRepository contextRepository) {
        this.service = service;
        this.contextRepository = contextRepository;
    }

    @GetMapping("/csrf")
    public ResponseEntity<Void> csrf(CsrfToken token) {
        token.getToken();
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody Map<String, String> data) {
        return ResponseEntity.ok(service.register(data));
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(
            @RequestBody Map<String, String> data,
            HttpServletRequest request,
            HttpServletResponse response) {

        Map<String, Object> user = service.login(data, "CUSTOMER");
        request.getSession(true);
        request.changeSessionId();

        String email = user.get("email").toString();
        String role = user.get("role").toString().toUpperCase();

        var authentication = UsernamePasswordAuthenticationToken.authenticated(
            email, null,
            List.of(new SimpleGrantedAuthority("ROLE_" + role))
        );

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        contextRepository.saveContext(context, request, response);

        return ResponseEntity.ok(user);
    }

    @PostMapping("/login/customer")
    public ResponseEntity<?> customerLogin(@RequestBody Map<String, String> data, HttpServletRequest request, HttpServletResponse response) {
        return establishSession(service.login(data, "CUSTOMER"), request, response);
    }

    @PostMapping("/login/owner")
    public ResponseEntity<?> ownerLogin(@RequestBody Map<String, String> data, HttpServletRequest request, HttpServletResponse response) {
        return establishSession(service.login(data, "ADMIN"), request, response);
    }

    @PostMapping("/login/staff")
    public ResponseEntity<?> staffLogin(@RequestBody Map<String, String> data, HttpServletRequest request, HttpServletResponse response) {
        return establishSession(service.login(data, "STAFF"), request, response);
    }

    private ResponseEntity<?> establishSession(Map<String, Object> user, HttpServletRequest request, HttpServletResponse response) {
        request.getSession(true);
        request.changeSessionId();
        String email = user.get("email").toString();
        String role = user.get("role").toString().toUpperCase();
        var authentication = UsernamePasswordAuthenticationToken.authenticated(email, null,
            List.of(new SimpleGrantedAuthority("ROLE_" + role)));
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        contextRepository.saveContext(context, request, response);
        return ResponseEntity.ok(user);
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(HttpServletRequest request,
                                    HttpServletResponse response) {
        new SecurityContextLogoutHandler().logout(
            request, response,
            SecurityContextHolder.getContext().getAuthentication()
        );

        return ResponseEntity.ok(Map.of("message", "Logged out successfully."));
    }

    @GetMapping("/me")
    public ResponseEntity<?> currentUser(
            org.springframework.security.core.Authentication authentication) {
        if (authentication == null ||
            !authentication.isAuthenticated() ||
            "anonymousUser".equals(authentication.getPrincipal())) {
            return ResponseEntity.status(401)
                .body(Map.of("message", "Not logged in."));
        }

        return ResponseEntity.ok(service.currentUser(authentication.getName()));
    }
}