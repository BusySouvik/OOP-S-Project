package com.vcart.controller;

import com.vcart.service.OwnerAnalyticsService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/{userId}/analytics")
public class OwnerAnalyticsController {
    private final OwnerAnalyticsService service;
    public OwnerAnalyticsController(OwnerAnalyticsService service) { this.service = service; }
    @GetMapping("/overview") public Object overview(@PathVariable Long userId) { return service.overview(userId); }
    @GetMapping("/sales") public Object sales(@PathVariable Long userId) { return service.dailySales(userId); }
    @GetMapping("/top-products") public Object products(@PathVariable Long userId) { return service.topProducts(userId); }
    @GetMapping("/recent-orders") public Object orders(@PathVariable Long userId) { return service.recentOrders(userId); }
}
