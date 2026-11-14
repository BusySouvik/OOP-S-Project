package com.vcart.service;

import com.vcart.model.Product;
import com.vcart.repository.ProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;

@Service
public class ProductService {
    private static final BigDecimal MAX_PRODUCT_PRICE = new BigDecimal("9999999999.99");
    private static final int MAX_CATALOG_OFFSET = 100_000;

    private final ProductRepository productRepository;

    public ProductService(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    public List<Product> getAllProducts() {
        return productRepository.findAllActive();
    }

    public Product getProductById(Long id) {
        return productRepository.findById(id)
                .orElseThrow(() ->
                    new IllegalArgumentException("Product not found with ID: " + id));
    }

    public List<Product> searchProducts(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return getAllProducts();
        }

        return getCatalogProducts(keyword, null, null, null, null,
            "relevance", null, null);
    }

    public List<Product> getCatalogProducts(String keyword, Long categoryId,
                                            BigDecimal minimumPrice,
                                            BigDecimal maximumPrice,
                                            Boolean inStock, String sort,
                                            Integer limit, Integer offset) {
        String normalizedKeyword = keyword == null ? null : keyword.trim();
        if (normalizedKeyword != null && normalizedKeyword.length() > 120) {
            throw new IllegalArgumentException("Search cannot exceed 120 characters.");
        }
        if (categoryId != null && categoryId < 1) {
            throw new IllegalArgumentException("Category ID must be positive.");
        }
        if ((minimumPrice != null && minimumPrice.compareTo(BigDecimal.ZERO) < 0)
                || (maximumPrice != null && maximumPrice.compareTo(BigDecimal.ZERO) < 0)
                || (minimumPrice != null && minimumPrice.compareTo(MAX_PRODUCT_PRICE) > 0)
                || (maximumPrice != null && maximumPrice.compareTo(MAX_PRODUCT_PRICE) > 0)
                || (minimumPrice != null && maximumPrice != null
                    && minimumPrice.compareTo(maximumPrice) > 0)) {
            throw new IllegalArgumentException("Enter a valid minimum and maximum price.");
        }

        String normalizedSort = sort == null ? "relevance"
            : sort.trim().toLowerCase(Locale.ROOT);
        if (!List.of("relevance", "price_asc", "price_desc", "newest", "name_asc")
                .contains(normalizedSort)) {
            throw new IllegalArgumentException("Unsupported product sort order.");
        }
        if (limit != null && (limit < 1 || limit > 48)) {
            throw new IllegalArgumentException("Page size must be between 1 and 48.");
        }
        if ((limit != null && (offset == null || offset < 0))
                || (limit == null && offset != null)) {
            throw new IllegalArgumentException("A non-negative offset is required with a page size.");
        }
        if (offset != null && offset > MAX_CATALOG_OFFSET) {
            throw new IllegalArgumentException("Catalogue offset is too large.");
        }

        return productRepository.findCatalog(normalizedKeyword, categoryId,
            minimumPrice, maximumPrice, inStock, normalizedSort, limit, offset);
    }

    @Transactional
    public void addProduct(Product product) {
        if (product != null && product.getIsActive() == null) {
            product.setIsActive(true);
        }
        validateProduct(product);

        int rows = productRepository.save(product);

        if (rows != 1) {
            throw new IllegalStateException("Product could not be saved.");
        }
    }

    @Transactional
    public void updateProduct(Long id, Product product) {
        if (product == null || product.getIsActive() == null) {
            throw new IllegalArgumentException("Product status is required.");
        }
        validateProduct(product);
        product.setId(id);

        int rows = productRepository.update(product);

        if (rows != 1) {
            throw new IllegalArgumentException("Product not found or update failed.");
        }
    }

    @Transactional
    public void updateInventory(Long id, Integer quantity) {
        if (quantity == null || quantity < 0) {
            throw new IllegalArgumentException("Stock quantity cannot be negative.");
        }

        getProductById(id);

        int rows = productRepository.updateStock(id, quantity);

        if (rows != 1) {
            throw new IllegalStateException("Inventory update failed.");
        }
    }

    @Transactional
    public void deactivateProduct(Long id) {
        getProductById(id);

        int rows = productRepository.delete(id);

        if (rows != 1) {
            throw new IllegalStateException("Product deactivation failed.");
        }
    }

    private void validateProduct(Product product) {
        if (product == null) {
            throw new IllegalArgumentException("Product cannot be empty.");
        }

        if (product.getName() == null || product.getName().isBlank()) {
            throw new IllegalArgumentException("Product name is required.");
        }
        if (product.getName().trim().length() > 180) {
            throw new IllegalArgumentException("Product name cannot exceed 180 characters.");
        }

        if (product.getSlug() == null || product.getSlug().isBlank()) {
            throw new IllegalArgumentException("Product slug is required.");
        }
        if (product.getSlug().trim().length() > 200) {
            throw new IllegalArgumentException("Product slug cannot exceed 200 characters.");
        }

        if (product.getSku() == null || product.getSku().isBlank()) {
            throw new IllegalArgumentException("Product SKU is required.");
        }
        if (product.getSku().trim().length() > 80) {
            throw new IllegalArgumentException("Product SKU cannot exceed 80 characters.");
        }

        if (product.getPrice() == null ||
                product.getPrice().compareTo(BigDecimal.ZERO) < 0 ||
                product.getPrice().compareTo(new BigDecimal("9999999999.99")) > 0) {
            throw new IllegalArgumentException("Product price must be between 0 and 9999999999.99.");
        }

        if (product.getStockQuantity() == null ||
                product.getStockQuantity() < 0) {
            throw new IllegalArgumentException("Stock cannot be negative.");
        }

        if (product.getCategoryId() != null && product.getCategoryId() < 1) {
            throw new IllegalArgumentException("Category ID must be positive.");
        }
    }
}