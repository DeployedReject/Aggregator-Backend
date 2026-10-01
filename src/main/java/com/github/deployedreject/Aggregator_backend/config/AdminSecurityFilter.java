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
 * Strictly limits /api/v1/admin/** management endpoints to localhost/loopback connections only.
 * Any request originating from the external internet receives an immediate 404 Not Found,
 * completely hiding the existence of the management API from external port scanners and attackers.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class AdminSecurityFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(AdminSecurityFilter.class);

    private static final Set<String> LOOPBACK_IPS = Set.of(
            "127.0.0.1",
            "::1",
            "0:0:0:0:0:0:0:1"
    );

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String path = request.getRequestURI();
        if (path != null && path.startsWith("/api/v1/admin")) {
            String remoteAddr = request.getRemoteAddr();

            if (!isLoopback(remoteAddr)) {
                log.warn("Blocked unauthorized external attempt to access admin management API from IP: {} on path: {}",
                        remoteAddr, path);
                response.sendError(HttpServletResponse.SC_NOT_FOUND, "Not Found");
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
