package com.vcart.service;

import com.vcart.model.CartItem;
import com.vcart.repository.CartRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Service
public class CartService {
    private final CartRepository repository;

    public CartService(CartRepository repository) {
        this.repository = repository;
    }

    public Map<String, Object> getCart(Long userId) {
        Long cartId = repository.getOrCreateCart(userId);
        List<CartItem> items = repository.findItems(cartId);
        BigDecimal total = items.stream()
            .map(CartItem::getLineTotal)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        return Map.of(
            "cartId", cartId,
            "items", items,
            "total", total,
            "itemCount", items.stream().mapToInt(CartItem::getQuantity).sum()
        );
    }

    @Transactional
    public void addItem(Long userId, Long productId, Integer quantity) {
        if (quantity == null || quantity < 1)
            throw new IllegalArgumentException("Quantity must be at least 1.");

        Long cartId = repository.getOrCreateCart(userId);
        repository.lockCart(cartId);
        Integer stock = repository.getStock(productId);

        if (stock == null || stock < quantity)
            throw new IllegalArgumentException("Product unavailable or insufficient stock.");

        if (repository.addItem(cartId, productId, quantity) != 1)
            throw new IllegalArgumentException("Requested quantity exceeds available stock.");
    }

    @Transactional
    public void updateQuantity(Long userId, Long itemId, Integer quantity) {
        if (quantity == null || quantity < 1)
            throw new IllegalArgumentException("Quantity must be at least 1.");

        Long cartId = repository.getOrCreateCart(userId);
        repository.lockCart(cartId);
        if (repository.updateQuantity(cartId, itemId, quantity) != 1)
            throw new IllegalArgumentException("Cart item not found or insufficient stock.");
    }

    @Transactional
    public void removeItem(Long userId, Long itemId) {
        Long cartId = repository.getOrCreateCart(userId);
        repository.lockCart(cartId);
        if (repository.removeItem(cartId, itemId) != 1)
            throw new IllegalArgumentException("Cart item not found.");
    }
}