package com.github.deployedreject.Aggregator_backend.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Set;

/**
 * Enforces origin security on open backend ports.
 * Loopback connections (localhost / TUI) bypass this filter automatically.
 * All external requests must present the valid 'X-Origin-Secret' header
 * (injected by the Cloudflare Worker). Direct access without this header is rejected with 403 Forbidden.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class OriginSecurityFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(OriginSecurityFilter.class);

    public static final String HEADER_ORIGIN_SECRET = "X-Origin-Secret";

    private static final Set<String> LOOPBACK_IPS = Set.of(
            "127.0.0.1",
            "::1",
            "0:0:0:0:0:0:0:1"
    );

    private final AppProperties appProperties;

    public OriginSecurityFilter(AppProperties appProperties) {
        this.appProperties = appProperties;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String remoteAddr = request.getRemoteAddr();

        // Local loopback calls (TUI, local admin scripts, internal jobs) are always permitted
        if (isLoopback(remoteAddr)) {
            filterChain.doFilter(request, response);
            return;
        }

        String configuredSecret = appProperties.getSecurity().getOriginSecret();

        // If a secret is configured, require it for all external traffic
        if (configuredSecret != null && !configuredSecret.isBlank()) {
            String incomingSecret = request.getHeader(HEADER_ORIGIN_SECRET);

            if (incomingSecret == null || !incomingSecret.trim().equals(configuredSecret.trim())) {
                log.warn("Rejected unauthorized direct external request from IP: {} on path: {}",
                        remoteAddr, request.getRequestURI());
                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                response.setContentType("application/json");
                response.getWriter().write("{\"error\":\"Forbidden\",\"message\":\"Direct origin access denied\"}");
                return;
            }
        }

        filterChain.doFilter(request, response);
    }

    private boolean isLoopback(String ip) {
        if (ip == null || ip.isBlank()) {
            return false;
        }
        if (LOOPBACK_IPS.contains(ip.trim())) {
            return true;
        }
        try {
            InetAddress addr = InetAddress.getByName(ip.trim());
            return addr.isLoopbackAddress();
        } catch (UnknownHostException e) {
            return false;
        }
    }
}
