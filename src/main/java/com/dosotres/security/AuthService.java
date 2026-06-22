package com.dosotres.security;

import com.dosotres.common.exception.ConflictException;
import com.dosotres.security.dto.AuthResponse;
import com.dosotres.security.dto.LoginRequest;
import com.dosotres.security.dto.RegisterRequest;
import com.dosotres.user.User;
import com.dosotres.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    public AuthResponse register(RegisterRequest req) {
        if (userRepository.findByEmail(req.email()).isPresent()) {
            // ConflictException → GlobalExceptionHandler → 409 con message
            // (fix 3.3: el 409 sin body dejaba el toast del frontend vacío).
            throw new ConflictException("Ya existe una cuenta con ese email");
        }

        User user = new User();
        user.setEmail(req.email());
        user.setDisplayName(req.displayName());
        user.setPasswordHash(passwordEncoder.encode(req.password()));
        user.setDateOfBirth(req.dateOfBirth());
        user = userRepository.save(user);

        String token = jwtService.generateToken(user.getId(), user.getEmail());
        log.info("User registered: id={}", user.getId());
        return new AuthResponse(user.getId(), user.getEmail(), user.getDisplayName(), token);
    }

    public AuthResponse login(LoginRequest req) {
        User user = userRepository.findByEmail(req.email())
                .orElseThrow(() -> new InvalidCredentialsException());

        if (!passwordEncoder.matches(req.password(), user.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }

        String token = jwtService.generateToken(user.getId(), user.getEmail());
        return new AuthResponse(user.getId(), user.getEmail(), user.getDisplayName(), token);
    }

    /** Manejada por GlobalExceptionHandler → 401 con message (fix 3.2). */
    public static class InvalidCredentialsException extends RuntimeException {
        public InvalidCredentialsException() {
            super("Email o contraseña incorrectos");
        }
    }
}
