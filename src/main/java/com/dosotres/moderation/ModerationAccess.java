package com.dosotres.moderation;

import com.dosotres.common.exception.ForbiddenException;
import com.dosotres.common.exception.ResourceNotFoundException;
import com.dosotres.user.GlobalRole;
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

    /** Exige rol MODERATOR; devuelve el usuario. Lanza Forbidden si no lo es. */
    public User requireModerator(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));
        if (user.getGlobalRole() != GlobalRole.MODERATOR) {
            throw new ForbiddenException("Requiere rol de moderador global");
        }
        return user;
    }

    public boolean isModerator(Long userId) {
        return userRepository.findById(userId)
                .map(u -> u.getGlobalRole() == GlobalRole.MODERATOR)
                .orElse(false);
    }
}
