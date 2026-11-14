package com.vcart.service;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class StaffOperationsServiceTests {
    @Test
    void inactiveStaffCannotLoadOperationalData() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject(anyString(), eq(Integer.class), eq("inactive@example.com")))
            .thenReturn(0);

        StaffOperationsService service = new StaffOperationsService(jdbc);

        assertThrows(AccessDeniedException.class,
            () -> service.orders("inactive@example.com"));
        verify(jdbc, never()).queryForList(anyString(), any(Object[].class));
    }

    @Test
    void simulatedPaidOrderCannotBeFulfilledByStaff() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject(anyString(), eq(Integer.class), eq("staff@example.com")))
            .thenReturn(1);
        when(jdbc.queryForList(contains("SELECT o.order_status"), eq(9L))).thenReturn(List.of(
            Map.of("order_status", "PLACED", "payment_status", "PAID",
                "verified_payment", false)
        ));

        StaffOperationsService service = new StaffOperationsService(jdbc);

        assertThrows(IllegalArgumentException.class,
            () -> service.updateOrderStatus("staff@example.com", 9L, "PROCESSING"));
        verify(jdbc, never()).update(anyString(), any(Object[].class));
    }
}
