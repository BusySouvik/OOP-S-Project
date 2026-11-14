package com.vcart.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AdminServiceTests {

    @Test
    void allowsForwardFulfillmentTransitionsAndEarlyCancellation() {
        assertDoesNotThrow(() -> AdminService.validateStatusTransition("PLACED", "PROCESSING"));
        assertDoesNotThrow(() -> AdminService.validateStatusTransition("PROCESSING", "SHIPPED"));
        assertDoesNotThrow(() -> AdminService.validateStatusTransition("SHIPPED", "DELIVERED"));
        assertDoesNotThrow(() -> AdminService.validateStatusTransition("PROCESSING", "CANCELLED"));
    }

    @Test
    void rejectsReopeningOrCancellingFulfilledOrders() {
        assertThrows(IllegalArgumentException.class,
            () -> AdminService.validateStatusTransition("CANCELLED", "PROCESSING"));
        assertThrows(IllegalArgumentException.class,
            () -> AdminService.validateStatusTransition("SHIPPED", "CANCELLED"));
        assertThrows(IllegalArgumentException.class,
            () -> AdminService.validateStatusTransition("DELIVERED", "CANCELLED"));
    }
}
