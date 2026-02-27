package com.aegis.orderservice.filter;

import com.aegis.orderservice.config.RateLimitProperties;
import com.aegis.orderservice.ratelimit.RateLimiter;
import com.aegis.orderservice.ratelimit.TokenBucketResult;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;

/**
 * Token-bucket rate limit for POST /api/v1/orders and GET /api/v1/orders/{id}.
 * Returns 429 Too Many Requests with Retry-After when exceeded.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class RateLimitFilter extends OncePerRequestFilter {

    private static final String POST_ORDERS_KEY = "ratelimit:post:orders";
    private static final String GET_ORDER_KEY = "ratelimit:get:order";
    private static final String API_ORDERS_PREFIX = "/api/v1/orders";

    private final Optional<RateLimiter> rateLimiter;
    private final Optional<RateLimitProperties> rateLimitProperties;

    public RateLimitFilter(Optional<RateLimiter> rateLimiter,
                           RateLimitProperties rateLimitProperties) {
        this.rateLimiter = rateLimiter;
        this.rateLimitProperties = Optional.of(rateLimitProperties);
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {
        if (rateLimiter.isEmpty() || rateLimitProperties.isEmpty()) {
            filterChain.doFilter(request, response);
            return;
        }

        String path = request.getRequestURI();
        String method = request.getMethod();

        TokenBucketResult result;
        if ("POST".equalsIgnoreCase(method) && path != null && path.equals(API_ORDERS_PREFIX)) {
            RateLimitProperties props = rateLimitProperties.get();
            result = rateLimiter.get().tryConsume(
                    POST_ORDERS_KEY,
                    props.getPostOrdersCapacity(),
                    props.getPostOrdersRefillPerSecond());
        } else if ("GET".equalsIgnoreCase(method) && path != null && path.startsWith(API_ORDERS_PREFIX + "/")) {
            RateLimitProperties props = rateLimitProperties.get();
            result = rateLimiter.get().tryConsume(
                    GET_ORDER_KEY,
                    props.getGetOrderCapacity(),
                    props.getGetOrderRefillPerSecond());
        } else {
            filterChain.doFilter(request, response);
            return;
        }

        if (!result.isAllowed()) {
            response.setStatus(429);
            response.setHeader("Retry-After", String.valueOf(result.getRetryAfterSeconds()));
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"Too Many Requests\",\"retryAfterSeconds\":" + result.getRetryAfterSeconds() + "}");
            return;
        }

        filterChain.doFilter(request, response);
    }
}
