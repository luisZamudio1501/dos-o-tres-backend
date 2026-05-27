package com.dosotres.security;

import com.dosotres.group.GroupMemberRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GroupContextFilterTest {

    @Mock
    private GroupMemberRepository groupMemberRepository;

    @Mock
    private FilterChain filterChain;

    private GroupContextFilter filter;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        filter = new GroupContextFilter(groupMemberRepository, objectMapper);
        SecurityContextHolder.clearContext();
    }

    private void authenticateUser(Long userId) {
        var auth = new UsernamePasswordAuthenticationToken(userId, null, List.of());
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    private MockHttpServletRequest createRequest(String method, String path) {
        var request = new MockHttpServletRequest(method, path);
        request.setServletPath(path);
        return request;
    }

    @Test
    void shouldBypassAuthRoutes() throws Exception {
        var request = createRequest("POST", "/api/auth/login");

        assertThat(filter.shouldNotFilter(request)).isTrue();
    }

    @Test
    void shouldBypassGetGroups() throws Exception {
        var request = createRequest("GET", "/api/groups");

        assertThat(filter.shouldNotFilter(request)).isTrue();
    }

    @Test
    void shouldBypassPostGroups() throws Exception {
        var request = createRequest("POST", "/api/groups");

        assertThat(filter.shouldNotFilter(request)).isTrue();
    }

    @Test
    void shouldBypassPostGroupsJoin() throws Exception {
        var request = createRequest("POST", "/api/groups/join");

        assertThat(filter.shouldNotFilter(request)).isTrue();
    }

    @Test
    void shouldNotBypassGroupScopedRoute() throws Exception {
        var request = createRequest("GET", "/api/prayer-requests");

        assertThat(filter.shouldNotFilter(request)).isFalse();
    }

    @Test
    void shouldReturn403WhenHeaderMissing() throws Exception {
        authenticateUser(1L);
        var request = new MockHttpServletRequest("GET", "/api/prayer-requests");
        var response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString()).contains("No eres miembro de este grupo");
        verifyNoInteractions(filterChain);
    }

    @Test
    void shouldReturn403WhenHeaderNotParseable() throws Exception {
        authenticateUser(1L);
        var request = new MockHttpServletRequest("GET", "/api/prayer-requests");
        request.addHeader("X-Group-Id", "abc");
        var response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString()).contains("No eres miembro de este grupo");
        verifyNoInteractions(filterChain);
    }

    @Test
    void shouldReturn403WhenUserNotMember() throws Exception {
        authenticateUser(1L);
        var request = new MockHttpServletRequest("GET", "/api/prayer-requests");
        request.addHeader("X-Group-Id", "10");
        var response = new MockHttpServletResponse();

        when(groupMemberRepository.existsByGroupIdAndUserId(10L, 1L)).thenReturn(false);

        filter.doFilterInternal(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString()).contains("No eres miembro de este grupo");
        verifyNoInteractions(filterChain);
    }

    @Test
    void shouldSetAttributeWhenValid() throws Exception {
        authenticateUser(1L);
        var request = new MockHttpServletRequest("GET", "/api/prayer-requests");
        request.addHeader("X-Group-Id", "10");
        var response = new MockHttpServletResponse();

        when(groupMemberRepository.existsByGroupIdAndUserId(10L, 1L)).thenReturn(true);

        filter.doFilterInternal(request, response, filterChain);

        assertThat(request.getAttribute("groupId")).isEqualTo(10L);
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void shouldPassThroughWhenNotAuthenticated() throws Exception {
        var request = new MockHttpServletRequest("GET", "/api/prayer-requests");
        var response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        verifyNoInteractions(groupMemberRepository);
    }
}
