package com.dosotres.config;

import com.dosotres.security.annotations.CurrentGroupId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.ServletWebRequest;

import static org.assertj.core.api.Assertions.assertThat;

class CurrentGroupIdResolverTest {

    private CurrentGroupIdResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new CurrentGroupIdResolver();
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
    void shouldResolveGroupIdFromRequestAttribute() {
        var request = new MockHttpServletRequest();
        request.setAttribute("groupId", 42L);
        var webRequest = new ServletWebRequest(request);

        Long result = resolver.resolveArgument(null, null, webRequest, null);

        assertThat(result).isEqualTo(42L);
    }

    @SuppressWarnings("unused")
    private void sampleMethod(@CurrentGroupId Long groupId) {}

    @SuppressWarnings("unused")
    private void plainMethod(Long groupId) {}
}
