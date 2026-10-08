package com.racehorse.wallet;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import java.util.regex.Pattern;

@Component
public class OwnerOverdueFilter extends OncePerRequestFilter {
    private static final Pattern HORSE_PROFILE = Pattern.compile(
            "/api/horses/[0-9a-fA-F-]{36}(?:/(?:pedigree|race-results|training-evaluations|main-file))?");
    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final ZoneId clubZone;

    public OwnerOverdueFilter(JdbcTemplate jdbc, Clock clock,
                              @Value("${app.club-time-zone}") String clubZone) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.clubZone = ZoneId.of(clubZone);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof Jwt jwt
                && auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_HORSE_OWNER"))
                && auth.getAuthorities().stream().noneMatch(a -> a.getAuthority().equals("ROLE_CLUB_MANAGER"))) {
            UUID ownerId = UUID.fromString(jwt.getSubject());
            LocalDate due = jdbc.query("select service_next_due_date from users where id=?",
                    rs -> rs.next() && rs.getDate(1) != null ? rs.getDate(1).toLocalDate() : null, ownerId);
            long days = due == null ? 0 : ChronoUnit.DAYS.between(due, LocalDate.now(clock.withZone(clubZone)));
            if (days >= 2 && !allowed(request)) {
                response.sendError(HttpServletResponse.SC_FORBIDDEN,
                        "Gói dịch vụ quá hạn; chỉ có thể truy cập ví và hồ sơ ngựa");
                return;
            }
        }
        chain.doFilter(request, response);
    }

    private boolean allowed(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        if (path.startsWith("/api/wallet/") || path.equals("/api/wallet")
                || path.startsWith("/api/auth/") || path.startsWith("/api/notifications")) return true;
        if (!request.getMethod().equals("GET")) return false;
        return path.equals("/api/horses/mine") || HORSE_PROFILE.matcher(path).matches();
    }
}
