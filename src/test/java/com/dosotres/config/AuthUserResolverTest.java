package com.dosotres.config;

import com.dosotres.security.annotations.AuthUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AuthUserResolverTest {

    private AuthUserResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new AuthUserResolver();
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldSupportAnnotatedParameter() throws Exception {
        var parameter = new MethodParameter(
                getClass().getDeclaredMethod("sampleMethod", Long.class), 0);

        assertThat(resolver.supportsParameter(parameter)).isTrue();
    }

    @Test
    void shouldNotSupportUnannotatedParameter() throws Exception {
        var parameter = new MethodParameter(
                getClass().getDeclaredMethod("plainMethod", Long.class), 0);

        assertThat(resolver.supportsParameter(parameter)).isFalse();
    }

    @Test
    void shouldResolveUserIdFromSecurityContext() {
        var auth = new UsernamePasswordAuthenticationToken(7L, null, List.of());
        SecurityContextHolder.getContext().setAuthentication(auth);

        Long result = resolver.resolveArgument(null, null, null, null);

        assertThat(result).isEqualTo(7L);
    }

    @SuppressWarnings("unused")
    private void sampleMethod(@AuthUser Long userId) {}

    @SuppressWarnings("unused")
    private void plainMethod(Long userId) {}
}
