package com.vcart.controller;

import com.vcart.model.Product;
import com.vcart.service.ProductService;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.math.BigDecimal;

@RestController
@RequestMapping("/api/products")
public class ProductController {

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    @GetMapping
    public ResponseEntity<List<Product>> getAllProducts(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(required = false) Boolean inStock,
            @RequestParam(defaultValue = "relevance") String sort,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) Integer offset) {
        return ResponseEntity.ok(productService.getCatalogProducts(
            search, categoryId, minPrice, maxPrice, inStock, sort, limit, offset));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Product> getProductById(@PathVariable Long id) {
        return ResponseEntity.ok(productService.getProductById(id));
    }

    @GetMapping("/search")
    public ResponseEntity<List<Product>> searchProducts(
            @RequestParam String keyword) {
        return ResponseEntity.ok(productService.searchProducts(keyword));
    }

    @PostMapping
    public ResponseEntity<Map<String, String>> addProduct(
            @RequestBody Product product) {
        productService.addProduct(product);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Map.of("message", "Product created successfully."));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Map<String, String>> updateProduct(
            @PathVariable Long id,
            @RequestBody Product product) {
        productService.updateProduct(id, product);

        return ResponseEntity.ok(
                Map.of("message", "Product updated successfully."));
    }

    @PatchMapping("/{id}/stock")
    public ResponseEntity<Map<String, String>> updateStock(
            @PathVariable Long id,
            @RequestBody Map<String, Integer> request) {

        Integer quantity = request.get("quantity");
        productService.updateInventory(id, quantity);

        return ResponseEntity.ok(
                Map.of("message", "Inventory updated successfully."));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, String>> deactivateProduct(
            @PathVariable Long id) {
        productService.deactivateProduct(id);

        return ResponseEntity.ok(
                Map.of("message", "Product deactivated successfully."));
    }
}