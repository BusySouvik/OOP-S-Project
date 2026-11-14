package com.vcart.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class OwnerDashboardController {
    @GetMapping("/owner/dashboard")
    public String dashboard() {
        return "redirect:/owner/analytics";
    }
}
