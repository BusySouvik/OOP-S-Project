package com.vcart.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class PageController {
    @GetMapping({"/login", "/customer/login"}) public String customerLogin() { return "customer-login"; }
    @GetMapping("/owner/login") public String ownerLogin() { return "owner-login"; }
    @GetMapping("/staff/login") public String staffLogin() { return "staff-login"; }
    @GetMapping("/staff/dashboard") public String staffDashboard() { return "staff-dashboard"; }
    @GetMapping({"/register", "/customer/register"}) public String register() { return "register"; }
    @GetMapping("/customer/tools") public String customerTools() { return "customer-tools"; }
    @GetMapping("/wishlist") public String wishlist() { return "wishlist"; }
    @GetMapping("/cart") public String cart() { return "cart"; }
    @GetMapping("/checkout") public String checkout() { return "checkout"; }
    @GetMapping("/orders") public String orders() { return "orders"; }
    @GetMapping("/about") public String about() { return "about"; }
    @GetMapping("/features") public String features() { return "features"; }
}
