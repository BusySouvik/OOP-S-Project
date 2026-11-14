package com.vcart.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class AdminWorkspacePageController {
    @GetMapping("/admin")
    public String dashboardAlias() {
        return "redirect:/owner/dashboard";
    }

    @GetMapping("/admin/manage")
    public String workspace() {
        return "admin-workspace";
    }
}