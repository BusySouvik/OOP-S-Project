package com.vcart.controller;

import com.vcart.service.ProductService;
import com.vcart.service.CategoryService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class HomeController {

    private final ProductService productService;
    private final CategoryService categoryService;

    public HomeController(ProductService productService, CategoryService categoryService) {
        this.productService = productService;
        this.categoryService = categoryService;
    }

    @GetMapping("/")
    public String home(Model model) {
        populateStorefront(model, false);
        return "home";
    }

    @GetMapping("/customer/dashboard")
    public String customerHome(Model model) {
        populateStorefront(model, true);
        return "home";
    }

    private void populateStorefront(Model model, boolean customerHome) {
        model.addAttribute("products", productService.getCatalogProducts(
            null, null, null, null, null, "newest", 12, 0));
        model.addAttribute("categories", categoryService.getActiveCategories());
        model.addAttribute("customerHome", customerHome);
    }
}