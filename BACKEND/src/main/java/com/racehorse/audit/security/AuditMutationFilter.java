package com.racehorse.audit.security;

import java.io.IOException;
import java.util.Locale;
import java.util.UUID;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.racehorse.audit.service.AuditService;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class AuditMutationFilter extends OncePerRequestFilter {
    private final AuditService auditService;
    public AuditMutationFilter(AuditService auditService) { this.auditService = auditService; }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        chain.doFilter(request, response);
        if (!isMutation(request.getMethod()) || response.getStatus() < 200 || response.getStatus() >= 300
                || request.getRequestURI().equals("/api/auth/login")) return;
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        UUID actor = authentication instanceof JwtAuthenticationToken token
                ? parseUuid(token.getToken().getSubject()) : null;
        String path = request.getRequestURI();
        auditService.record(actor, module(path), action(request.getMethod()), entityType(path), entityId(path),
                request.getMethod() + " " + path + " -> " + response.getStatus(),
                request.getRemoteAddr(), request.getHeader("User-Agent"));
    }

    private boolean isMutation(String method) { return !method.equals("GET") && !method.equals("HEAD") && !method.equals("OPTIONS"); }
    private String action(String method) { return switch (method) { case "POST" -> "CREATE"; case "DELETE" -> "DELETE"; default -> "UPDATE"; }; }
    private String module(String path) {
        if (path.contains("training") || path.contains("fitness") || path.contains("telemetry") || path.contains("racetrack")) return "TRAINING";
        if (path.contains("health") || path.contains("medical") || path.contains("weights")) return "MEDICAL";
        if (path.contains("stable") || path.contains("inventory") || path.contains("replenishment")) return "STABLE_CARE";
        if (path.contains("race")) return "RACING";
        if (path.contains("report")) return "REPORTING";
        if (path.contains("users") || path.contains("role-permissions") || path.contains("auth")) return "ACCESS";
        return "PROFILE";
    }
    private String entityType(String path) {
        String[] values = path.split("/");
        return values.length > 2 ? values[2].toUpperCase(Locale.ROOT) : "SYSTEM";
    }
    private UUID entityId(String path) {
        for (String value : path.split("/")) { UUID parsed = parseUuid(value); if (parsed != null) return parsed; }
        return null;
    }
    private UUID parseUuid(String value) { try { return UUID.fromString(value); } catch (Exception ignored) { return null; } }
}
