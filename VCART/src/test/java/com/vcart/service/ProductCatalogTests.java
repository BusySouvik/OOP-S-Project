package com.vcart.service;

import com.vcart.model.Product;
import com.vcart.repository.ProductRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProductCatalogTests {
    private final ProductRepository repository = mock(ProductRepository.class);
    private final ProductService service = new ProductService(repository);

    @Test
    void forwardsCombinedCatalogueFiltersAndPagination() {
        List<Product> products = List.of(new Product());
        when(repository.findCatalog("lamp", 4L, new BigDecimal("10"),
            new BigDecimal("50"), true, "price_asc", 12, 0)).thenReturn(products);

        assertEquals(products, service.getCatalogProducts(" lamp ", 4L,
            new BigDecimal("10"), new BigDecimal("50"), true,
            "price_asc", 12, 0));
        verify(repository).findCatalog("lamp", 4L, new BigDecimal("10"),
            new BigDecimal("50"), true, "price_asc", 12, 0);
    }

    @Test
    void rejectsInvalidPriceRangeAndSortInsteadOfSilentlyIgnoringFilters() {
        assertThrows(IllegalArgumentException.class, () -> service.getCatalogProducts(
            null, null, new BigDecimal("20"), new BigDecimal("10"),
            null, "newest", null, null));
        assertThrows(IllegalArgumentException.class, () -> service.getCatalogProducts(
            null, null, null, null, null, "price; drop table products", null, null));
    }

    @Test
    void validatesPageSizeAndOffsetTogether() {
        assertThrows(IllegalArgumentException.class, () -> service.getCatalogProducts(
            null, null, null, null, null, "newest", 100, 0));
        assertThrows(IllegalArgumentException.class, () -> service.getCatalogProducts(
            null, null, null, null, null, "newest", 12, null));
        assertThrows(IllegalArgumentException.class, () -> service.getCatalogProducts(
            null, null, null, null, null, "newest", 12, 100_001));
    }
}
