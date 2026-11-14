package com.vcart.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestHandler;
import org.springframework.security.web.csrf.XorCsrfTokenRequestAttributeHandler;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.util.StringUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.util.function.Supplier;

@Configuration
public class SecurityConfig {
    @Bean
    public SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public CsrfTokenRepository csrfTokenRepository() {
        return CookieCsrfTokenRepository.withHttpOnlyFalse();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   CsrfTokenRepository csrfTokenRepository) throws Exception {
        http.csrf(csrf -> csrf
                .csrfTokenRepository(csrfTokenRepository)
                .csrfTokenRequestHandler(new SpaCsrfTokenRequestHandler()))
            .securityContext(context -> context.securityContextRepository(securityContextRepository()))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/", "/about", "/features", "/login", "/register",
                    "/customer/login", "/customer/register", "/owner/login", "/staff/login",
                    "/css/**", "/js/**", "/images/**", "/api/auth/**").permitAll()

                // Owner-only screens and APIs
                .requestMatchers("/owner/dashboard", "/owner/analytics", "/admin", "/admin/**",
                    "/api/admin/**").hasRole("ADMIN")

                // Staff are limited to operational order and support routes.
                .requestMatchers("/staff/dashboard", "/api/staff/**").hasRole("STAFF")

                // Customer-only screens and APIs
                .requestMatchers("/customer/dashboard", "/customer/tools",
                    "/wishlist", "/cart", "/checkout", "/orders",
                    "/api/customer/**", "/api/cart/**", "/api/orders/**",
                    "/api/refunds/**", "/api/payments/**").hasRole("CUSTOMER")

                // Public catalogue; only owners may mutate products.
                .requestMatchers(HttpMethod.GET, "/api/products/**", "/api/categories/**",
                    "/api/discounts/**").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/discounts").hasRole("ADMIN")
                .requestMatchers(HttpMethod.PATCH, "/api/discounts/**").hasRole("ADMIN")
                .requestMatchers("/api/products/**").hasRole("ADMIN")
                .anyRequest().permitAll())
            .exceptionHandling(exceptions -> exceptions
                .authenticationEntryPoint(loginRedirectOrForbidden()))
            .formLogin(form -> form.disable())
            .httpBasic(basic -> basic.disable())
            .logout(logout -> logout.disable());
        return http.build();
    }

    private static AuthenticationEntryPoint loginRedirectOrForbidden() {
        return (request, response, exception) -> {
            String path = request.getServletPath();
            if (!StringUtils.hasText(path)) {
                String requestUri = request.getRequestURI();
                String contextPath = request.getContextPath();
                path = requestUri.substring(contextPath.length());
            }

            String loginPath = matchesRoot(path, "/owner") || matchesRoot(path, "/admin")
                ? "/owner/login"
                : matchesRoot(path, "/staff")
                    ? "/staff/login"
                    : matchesRoot(path, "/customer") || matchesRoot(path, "/wishlist")
                        || matchesRoot(path, "/cart")
                        || matchesRoot(path, "/checkout") || matchesRoot(path, "/orders")
                            ? "/customer/login"
                            : null;

            if (loginPath == null) {
                response.sendError(HttpServletResponse.SC_FORBIDDEN);
            } else {
                response.sendRedirect(loginPath);
            }
        };
    }

    private static boolean matchesRoot(String path, String root) {
        return path.equals(root) || path.startsWith(root + "/");
    }

    private static final class SpaCsrfTokenRequestHandler implements CsrfTokenRequestHandler {
        private final CsrfTokenRequestHandler plain = new CsrfTokenRequestAttributeHandler();
        private final CsrfTokenRequestHandler xor = new XorCsrfTokenRequestAttributeHandler();

        @Override
        public void handle(HttpServletRequest request, HttpServletResponse response,
                           Supplier<org.springframework.security.web.csrf.CsrfToken> csrfToken) {
            xor.handle(request, response, csrfToken);
            csrfToken.get();
        }

        @Override
        public String resolveCsrfTokenValue(HttpServletRequest request,
                                            org.springframework.security.web.csrf.CsrfToken csrfToken) {
            return StringUtils.hasText(request.getHeader(csrfToken.getHeaderName()))
                ? plain.resolveCsrfTokenValue(request, csrfToken)
                : xor.resolveCsrfTokenValue(request, csrfToken);
        }
    }
}
