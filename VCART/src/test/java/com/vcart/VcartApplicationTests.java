package com.vcart;

import com.vcart.service.ProductService;
import com.vcart.service.CategoryService;
import com.vcart.model.Product;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import jakarta.servlet.http.Cookie;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.annotation.DirtiesContext;

import java.util.List;
import java.util.Map;
import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

@SpringBootTest
@AutoConfigureMockMvc
class VcartApplicationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @MockitoBean
    private ProductService productService;

    @MockitoBean
    private CategoryService categoryService;

    @Test
    void contextLoads() {
    }

    @Test
    void publicHomepageIsAvailableWithoutAuthentication() throws Exception {
        when(productService.getCatalogProducts(null, null, null, null, null,
            "newest", 12, 0)).thenReturn(List.of());
        when(categoryService.getActiveCategories()).thenReturn(List.of());

        mockMvc.perform(get("/"))
            .andExpect(status().isOk())
            .andExpect(view().name("home"));
    }

    @Test
    void customerDashboardUsesTheStorefrontTemplate() throws Exception {
        when(productService.getCatalogProducts(null, null, null, null, null,
            "newest", 12, 0)).thenReturn(List.of());
        when(categoryService.getActiveCategories()).thenReturn(List.of());

        mockMvc.perform(get("/customer/dashboard").with(user("customer").roles("CUSTOMER")))
            .andExpect(status().isOk())
            .andExpect(view().name("home"))
            .andExpect(content().string(containsString("Explore the <em>catalogue.</em>")));
    }

    @Test
    void storefrontRendersDatabaseBackedCategoriesAndProducts() throws Exception {
        Product product = new Product(4L, 2L, "Desk lamp", "desk-lamp", "LAMP-4",
            "Adjustable reading light", new BigDecimal("45.00"), 3, null, true);
        product.setCategoryName("Lighting");
        when(productService.getCatalogProducts(null, null, null, null, null,
            "newest", 12, 0)).thenReturn(List.of(product));
        when(categoryService.getActiveCategories()).thenReturn(List.of(
            Map.of("id", 2L, "name", "Lighting", "description", "Reading and task lights")));

        mockMvc.perform(get("/"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("Lighting")))
            .andExpect(content().string(containsString("Desk lamp")))
            .andExpect(content().string(containsString("3 available")));
    }

    @Test
    void publicCatalogueApiBindsCombinedFiltersAndPagination() throws Exception {
        when(productService.getCatalogProducts("lamp", 2L, new BigDecimal("10"),
            new BigDecimal("50"), true, "price_asc", 12, 0)).thenReturn(List.of());

        mockMvc.perform(get("/api/products")
                .param("search", "lamp")
                .param("categoryId", "2")
                .param("minPrice", "10")
                .param("maxPrice", "50")
                .param("inStock", "true")
                .param("sort", "price_asc")
                .param("limit", "12")
                .param("offset", "0"))
            .andExpect(status().isOk())
            .andExpect(content().string("[]"));

        verify(productService).getCatalogProducts("lamp", 2L, new BigDecimal("10"),
            new BigDecimal("50"), true, "price_asc", 12, 0);
    }

    @Test
    void anonymousWishlistIsRedirectedToCustomerLogin() throws Exception {
        mockMvc.perform(get("/wishlist"))
            .andExpect(status().isFound())
            .andExpect(redirectedUrl("/customer/login"));
    }

    @Test
    void ownerDashboardRedirectsToAnalytics() throws Exception {
        mockMvc.perform(get("/owner/dashboard").with(user("owner").roles("ADMIN")))
            .andExpect(status().isFound())
            .andExpect(redirectedUrl("/owner/analytics"));
    }

    @Test
    void anonymousOwnerIsRedirectedToOwnerLogin() throws Exception {
        mockMvc.perform(get("/owner/analytics"))
            .andExpect(status().isFound())
            .andExpect(redirectedUrl("/owner/login"));
    }

    @Test
    void anonymousCustomerIsRedirectedToCustomerLogin() throws Exception {
        mockMvc.perform(get("/customer/dashboard"))
            .andExpect(status().isFound())
            .andExpect(redirectedUrl("/customer/login"));
    }

    @Test
    void anonymousStaffIsRedirectedToStaffLogin() throws Exception {
        mockMvc.perform(get("/staff/dashboard"))
            .andExpect(status().isFound())
            .andExpect(redirectedUrl("/staff/login"));
    }

    @Test
    void customerCannotOpenOwnerAnalytics() throws Exception {
        mockMvc.perform(get("/owner/analytics").with(user("customer").roles("CUSTOMER")))
            .andExpect(status().isForbidden());
    }

    @Test
    void ownerAnalyticsPageUsesProfileAndManageControls() throws Exception {
        mockMvc.perform(get("/owner/analytics").with(user("owner").roles("ADMIN")))
            .andExpect(status().isOk())
            .andExpect(view().name("owner-analytics"))
            .andExpect(content().string(containsString("id=\"profile-name\"")))
            .andExpect(content().string(containsString("href=\"/admin/manage\"")));
    }

    @Test
    void customerCannotOpenOwnerWorkspace() throws Exception {
        mockMvc.perform(get("/admin/manage").with(user("customer").roles("CUSTOMER")))
            .andExpect(status().isForbidden());
    }

    @Test
    void customerCannotCallOwnerApi() throws Exception {
        mockMvc.perform(get("/api/admin/1/orders").with(user("customer").roles("CUSTOMER")))
            .andExpect(status().isForbidden());
    }

    @Test
    void unauthenticatedOwnerApiDoesNotRedirectToHtmlLogin() throws Exception {
        mockMvc.perform(get("/api/admin/1/analytics/overview"))
            .andExpect(status().isForbidden());
    }

    @Test
    void ownerCannotCallCustomerCartApi() throws Exception {
        mockMvc.perform(get("/api/cart/1").with(user("owner").roles("ADMIN")))
            .andExpect(status().isForbidden());
    }

    @Test
    void customerCannotCallStaffOperations() throws Exception {
        mockMvc.perform(get("/api/staff/orders").with(user("customer").roles("CUSTOMER")))
            .andExpect(status().isForbidden());
    }

    @Test
    void staffCannotCallOwnerOrCustomerApis() throws Exception {
        mockMvc.perform(get("/api/admin/1/workspace/finance").with(user("staff").roles("STAFF")))
            .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/cart/1").with(user("staff").roles("STAFF")))
            .andExpect(status().isForbidden());
    }

    @Test
    void ownerCannotCallStaffOperations() throws Exception {
        mockMvc.perform(get("/api/staff/orders").with(user("owner").roles("ADMIN")))
            .andExpect(status().isForbidden());
    }

    @Test
    void configuredPasswordEncoderUsesBcrypt() {
        String encoded = passwordEncoder.encode("test-password");
        assertTrue(encoded.startsWith("$2"));
        assertTrue(passwordEncoder.matches("test-password", encoded));
        assertFalse(passwordEncoder.matches("wrong-password", encoded));
    }

    @Test
    void ownerCanOpenOwnerWorkspace() throws Exception {
        mockMvc.perform(get("/admin/manage").with(user("owner").roles("ADMIN")))
            .andExpect(status().isOk());
    }

    @Test
    void unsafeApiRequestsRequireCsrfToken() throws Exception {
        mockMvc.perform(post("/api/auth/logout")
                .with(user("owner").roles("ADMIN"))
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/auth/logout")
                .with(user("owner").roles("ADMIN"))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk());
    }

    @Test
    void successfulLogoutInvalidatesExistingSession() throws Exception {
        MockHttpSession session = new MockHttpSession();

        mockMvc.perform(post("/api/auth/logout")
                .session(session)
                .with(user("owner").roles("ADMIN"))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk());

        assertTrue(session.isInvalid());
    }

    @Test
    void csrfEndpointIssuesReadableCookieForBrowserClient() throws Exception {
        mockMvc.perform(get("/api/auth/csrf"))
            .andExpect(status().isNoContent())
            .andExpect(header().string("Set-Cookie", containsString("XSRF-TOKEN=")));
    }

    @Test
    @DirtiesContext(methodMode = DirtiesContext.MethodMode.BEFORE_METHOD)
    void browserCsrfCookieHeaderAllowsAuthenticatedMutation() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/auth/csrf")).andReturn();
        Cookie cookie = result.getResponse().getCookie("XSRF-TOKEN");
        assertNotNull(cookie);

        mockMvc.perform(post("/api/auth/logout")
                .with(user("owner").roles("ADMIN"))
                .cookie(cookie)
                .header("X-XSRF-TOKEN", cookie.getValue())
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk());
    }
}
