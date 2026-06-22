package com.dosotres.security;

import com.dosotres.user.LastSeenTracker;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthFilter.class);

    private final JwtService jwtService;
    private final LastSeenTracker lastSeenTracker;

    public JwtAuthFilter(JwtService jwtService, LastSeenTracker lastSeenTracker) {
        this.jwtService = jwtService;
        this.lastSeenTracker = lastSeenTracker;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return request.getServletPath().startsWith("/api/auth/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");

        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7);
            if (jwtService.isValid(token)) {
                Long userId = jwtService.extractUserId(token);
                UsernamePasswordAuthenticationToken auth =
                        new UsernamePasswordAuthenticationToken(userId, null, List.of());
                auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(auth);

                // Actividad "abrió la app" (throttle 1h en SQL). Nunca debe
                // tumbar el request: un fallo de DB se traga y se loguea.
                try {
                    lastSeenTracker.touch(userId);
                } catch (RuntimeException e) {
                    log.warn("No se pudo actualizar last_seen_at del usuario {}: {}",
                            userId, e.getMessage());
                }
            }
        }

        filterChain.doFilter(request, response);
    }
}
