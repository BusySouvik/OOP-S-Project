package com.vcart.controller;

import com.vcart.service.AuthService;
import com.vcart.service.CartService;
import com.vcart.service.OrderService;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class CustomerOwnershipTests {

    @Test
    void customerCannotReadAnotherCustomersCart() {
        AuthService authService = mock(AuthService.class);
        CartService cartService = mock(CartService.class);
        when(authService.currentUser("customer@example.com")).thenReturn(Map.of("id", 1L));

        CartController controller = new CartController(cartService, authService);

        assertThrows(AccessDeniedException.class,
            () -> controller.getCart(2L, customerAuthentication()));
        verifyNoInteractions(cartService);
    }

    @Test
    void customerCannotReadAnotherCustomersOrder() {
        AuthService authService = mock(AuthService.class);
        OrderService orderService = mock(OrderService.class);
        when(authService.currentUser("customer@example.com")).thenReturn(Map.of("id", 1L));

        OrderController controller = new OrderController(orderService, authService);

        assertThrows(AccessDeniedException.class,
            () -> controller.getOrder(2L, 10L, customerAuthentication()));
        verifyNoInteractions(orderService);
    }

    private UsernamePasswordAuthenticationToken customerAuthentication() {
        return UsernamePasswordAuthenticationToken.authenticated(
            "customer@example.com", null, List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER")));
    }
}
