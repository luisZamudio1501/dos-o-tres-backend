package com.dosotres.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.dosotres.security.dto.AuthResponse;
import com.dosotres.security.dto.LoginRequest;
import com.dosotres.security.dto.RegisterRequest;
import com.dosotres.user.User;
import com.dosotres.user.UserRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private JwtService jwtService;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(userRepository, passwordEncoder, jwtService);
    }

    @Test
    void register_success() {
        when(userRepository.findByEmail("luis@test.com")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("password123")).thenReturn("hashed");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(1L);
            return u;
        });
        when(jwtService.generateToken(anyLong(), anyString())).thenReturn("jwt-token");

        AuthResponse response = authService.register(
                new RegisterRequest("luis@test.com", "Luis", "password123"));

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.email()).isEqualTo("luis@test.com");
        assertThat(response.displayName()).isEqualTo("Luis");
        assertThat(response.token()).isEqualTo("jwt-token");
    }

    @Test
    void register_duplicateEmail_throwsEmailAlreadyExists() {
        User existing = new User();
        existing.setEmail("luis@test.com");
        when(userRepository.findByEmail("luis@test.com")).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> authService.register(
                new RegisterRequest("luis@test.com", "Luis", "password123")))
                .isInstanceOf(AuthService.EmailAlreadyExistsException.class)
                .hasMessageContaining("luis@test.com");
    }

    @Test
    void login_success() {
        User user = new User();
        user.setId(1L);
        user.setEmail("luis@test.com");
        user.setDisplayName("Luis");
        user.setPasswordHash("hashed");

        when(userRepository.findByEmail("luis@test.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("password123", "hashed")).thenReturn(true);
        when(jwtService.generateToken(1L, "luis@test.com")).thenReturn("jwt-token");

        AuthResponse response = authService.login(new LoginRequest("luis@test.com", "password123"));

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.token()).isEqualTo("jwt-token");
    }

    @Test
    void login_wrongPassword_throwsInvalidCredentials() {
        User user = new User();
        user.setId(1L);
        user.setEmail("luis@test.com");
        user.setPasswordHash("hashed");

        when(userRepository.findByEmail("luis@test.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong", "hashed")).thenReturn(false);

        assertThatThrownBy(() -> authService.login(new LoginRequest("luis@test.com", "wrong")))
                .isInstanceOf(AuthService.InvalidCredentialsException.class);
    }

    @Test
    void login_nonExistentEmail_throwsInvalidCredentials() {
        when(userRepository.findByEmail("nobody@test.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(new LoginRequest("nobody@test.com", "password")))
                .isInstanceOf(AuthService.InvalidCredentialsException.class);
    }
}
