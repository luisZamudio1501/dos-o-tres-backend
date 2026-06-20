package com.dosotres.security;

import com.dosotres.group.GroupMemberRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@Component
public class GroupContextFilter extends OncePerRequestFilter {

    private static final String GROUP_HEADER = "X-Group-Id";

    private final GroupMemberRepository groupMemberRepository;
    private final ObjectMapper objectMapper;
    private final List<RequestMatcher> excludedMatchers;

    public GroupContextFilter(GroupMemberRepository groupMemberRepository, ObjectMapper objectMapper) {
        this.groupMemberRepository = groupMemberRepository;
        this.objectMapper = objectMapper;
        this.excludedMatchers = List.of(
                new AntPathRequestMatcher("/api/auth/**"),
                // Endpoints de grupos: la autorización va por path (membresía/rol
                // verificados en GroupService), no por contexto X-Group-Id.
                new AntPathRequestMatcher("/api/groups/**"),
                // Perfil propio (S5): no depende de ningún grupo — un usuario
                // recién registrado sin grupos también puede editarlo.
                new AntPathRequestMatcher("/api/users/me"),
                // Espacio personal: opera sobre el usuario, sin contexto de grupo.
                new AntPathRequestMatcher("/api/me/**"),
                // Suscripciones push: son del usuario, no dependen de ningún grupo.
                new AntPathRequestMatcher("/api/push/**"),
                // Cronómetro: la sesión es del usuario; el acceso a cada pedido
                // se valida por-pedido en attach (sesión unificada cross-group).
                new AntPathRequestMatcher("/api/timer/**"),
                // Formulario de contacto: es del usuario, sin contexto de grupo.
                new AntPathRequestMatcher("/api/contact/**"),
                // Moderación (reportes/bloqueos/rol global): comunidad-amplia, sin grupo.
                new AntPathRequestMatcher("/api/moderation/**"),
                // Muro público: pedidos visibles para toda la comunidad, sin grupo.
                new AntPathRequestMatcher("/api/public/**"),
                // Mensajería: conversaciones entre usuarios, no dependen de un grupo.
                new AntPathRequestMatcher("/api/conversations/**"),
                new AntPathRequestMatcher("/actuator/**"),
                new AntPathRequestMatcher("/swagger-ui/**"),
                new AntPathRequestMatcher("/v3/api-docs/**")
        );
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return excludedMatchers.stream().anyMatch(matcher -> matcher.matches(request));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            filterChain.doFilter(request, response);
            return;
        }

        String headerValue = request.getHeader(GROUP_HEADER);
        if (headerValue == null || headerValue.isBlank()) {
            respondForbidden(response);
            return;
        }

        Long groupId;
        try {
            groupId = Long.parseLong(headerValue);
        } catch (NumberFormatException e) {
            respondForbidden(response);
            return;
        }

        Long userId = (Long) authentication.getPrincipal();

        if (!groupMemberRepository.existsByGroupIdAndUserId(groupId, userId)) {
            respondForbidden(response);
            return;
        }

        request.setAttribute("groupId", groupId);
        filterChain.doFilter(request, response);
    }

    private void respondForbidden(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getOutputStream(),
                Map.of("status", 403, "message", "No eres miembro de este grupo"));
    }
}
