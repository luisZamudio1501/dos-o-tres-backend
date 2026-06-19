package com.dosotres.user;

import com.dosotres.common.exception.ResourceNotFoundException;
import com.dosotres.user.dto.UpdateProfileRequest;
import com.dosotres.user.dto.UserProfileResponse;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public UserProfileResponse getProfile(Long userId) {
        return toResponse(findUser(userId));
    }

    /**
     * Actualiza el perfil. Campos de congregación: null o blanco limpian el
     * campo (perfil borrable — privacidad S5). displayName: null no modifica.
     */
    public UserProfileResponse updateProfile(Long userId, UpdateProfileRequest req) {
        User user = findUser(userId);

        if (req.displayName() != null && !req.displayName().isBlank()) {
            user.setDisplayName(req.displayName().trim());
        }
        user.setCountry(normalizeCountry(req.country()));
        user.setProvince(normalize(req.province()));
        user.setCity(normalize(req.city()));
        user.setChurchName(normalize(req.churchName()));
        if (req.notifyOnRequestCreated() != null) {
            user.setNotifyOnRequestCreated(req.notifyOnRequestCreated());
        }
        if (req.notifyOnPrayed() != null) {
            user.setNotifyOnPrayed(req.notifyOnPrayed());
        }
        if (req.notifyOnAnswered() != null) {
            user.setNotifyOnAnswered(req.notifyOnAnswered());
        }

        userRepository.save(user);
        return toResponse(user);
    }

    private User findUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));
    }

    private String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private String normalizeCountry(String value) {
        String normalized = normalize(value);
        return normalized == null ? null : normalized.toUpperCase(Locale.ROOT);
    }

    private UserProfileResponse toResponse(User user) {
        return new UserProfileResponse(
                user.getId(),
                user.getEmail(),
                user.getDisplayName(),
                user.getCountry(),
                user.getProvince(),
                user.getCity(),
                user.getChurchName(),
                user.isNotifyOnRequestCreated(),
                user.isNotifyOnPrayed(),
                user.isNotifyOnAnswered()
        );
    }
}
