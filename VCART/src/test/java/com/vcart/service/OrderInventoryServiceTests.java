package com.vcart.service;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class OrderInventoryServiceTests {
    @Test
    void restoresAllOrderItemsAndCouponUsage() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForList(contains("FROM order_items"), eq(11L))).thenReturn(List.of(
            Map.of("product_id", 21L, "quantity", 2),
            Map.of("product_id", 22L, "quantity", 1)
        ));
        when(jdbc.update(anyString(), eq(2), eq(21L))).thenReturn(1);
        when(jdbc.update(anyString(), eq(1), eq(22L))).thenReturn(1);

        new OrderInventoryService(jdbc).restoreOrder(11L, 5L);

        verify(jdbc).update(contains("UPDATE products"), eq(2), eq(21L));
        verify(jdbc).update(contains("UPDATE products"), eq(1), eq(22L));
        verify(jdbc).update(contains("UPDATE discount_codes"), eq(5L));
    }
}
