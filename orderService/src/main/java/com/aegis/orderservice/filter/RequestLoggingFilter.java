package com.aegis.orderservice.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.util.regex.Pattern;

/**
 * Logs each request with correlationId, orderId (when in path), endpoint, latency, status code.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class RequestLoggingFilter extends OncePerRequestFilter {

    private static final Logger logger = LoggerFactory.getLogger(RequestLoggingFilter.class);
    private static final Pattern ORDER_ID_PATH = Pattern.compile("^/api/v1/orders/([0-9a-fA-F-]{36})$");

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {
        long start = System.currentTimeMillis();
        String method = request.getMethod();
        String uri = request.getRequestURI();
        String endpoint = method + " " + uri;

        String orderId = extractOrderId(uri);
        if (orderId != null) {
            MDC.put("orderId", orderId);
        }
        try {
            filterChain.doFilter(request, response);
        } finally {
            if (orderId != null) {
                MDC.remove("orderId");
            }
            long latencyMs = System.currentTimeMillis() - start;
            int status = response.getStatus();
            logger.info("endpoint={} latencyMs={} status={} correlationId={}",
                    endpoint, latencyMs, status, MDC.get(CorrelationIdFilter.MDC_KEY));
        }
    }

    private static String extractOrderId(String uri) {
        var m = ORDER_ID_PATH.matcher(uri);
        return m.matches() ? m.group(1) : null;
    }
}
