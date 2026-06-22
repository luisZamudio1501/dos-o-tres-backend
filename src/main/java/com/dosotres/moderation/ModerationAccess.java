package com.dosotres.moderation;

import com.dosotres.common.exception.ForbiddenException;
import com.dosotres.common.exception.ResourceNotFoundException;
import com.dosotres.user.User;
import com.dosotres.user.UserRepository;
import org.springframework.stereotype.Component;

/**
 * Punto único de autorización de moderador global (ADR-006). Reusado por
 * la moderación de reportes (M.0), el muro público (M.3) y la mensajería (M.5).
 */
@Component
public class ModerationAccess {

    private final UserRepository userRepository;

    public ModerationAccess(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * Exige capacidad de moderación (MODERATOR o ADMIN); devuelve el usuario.
     * Lanza Forbidden si no la tiene. ADMIN es superconjunto de MODERATOR.
     */
    public User requireModerator(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));
        if (!user.getGlobalRole().canModerate()) {
            throw new ForbiddenException("Requiere rol de moderador global");
        }
        return user;
    }

    public boolean isModerator(Long userId) {
        return userRepository.findById(userId)
                .map(u -> u.getGlobalRole().canModerate())
                .orElse(false);
    }
}
