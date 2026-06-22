package com.dosotres.admin;

import com.dosotres.common.exception.ForbiddenException;
import com.dosotres.common.exception.ResourceNotFoundException;
import com.dosotres.user.User;
import com.dosotres.user.UserRepository;
import org.springframework.stereotype.Component;

/**
 * Punto único de autorización del panel de administración. Espeja el patrón de
 * {@link com.dosotres.moderation.ModerationAccess} pero exige el rol {@code ADMIN}
 * (superconjunto de MODERATOR). Gatea todo {@code /api/admin/**}.
 */
@Component
public class AdminAccess {

    private final UserRepository userRepository;

    public AdminAccess(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /** Exige rol ADMIN; devuelve el usuario. Lanza Forbidden si no lo es. */
    public User requireAdmin(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));
        if (!user.getGlobalRole().isAdmin()) {
            throw new ForbiddenException("Requiere rol de administrador");
        }
        return user;
    }

    public boolean isAdmin(Long userId) {
        return userRepository.findById(userId)
                .map(u -> u.getGlobalRole().isAdmin())
                .orElse(false);
    }
}
