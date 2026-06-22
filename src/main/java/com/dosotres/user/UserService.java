package com.dosotres.user;

import com.dosotres.common.exception.ResourceNotFoundException;
import com.dosotres.user.dto.UpdateProfileRequest;
import com.dosotres.user.dto.UserProfileResponse;
import java.time.Clock;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class UserService {

    private final UserRepository userRepository;
    private final Clock clock;

    public UserService(UserRepository userRepository, Clock clock) {
        this.userRepository = userRepository;
        this.clock = clock;
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
        // La fecha de nacimiento no se limpia con null (perdería el gate de adultez);
        // solo se actualiza si viene un valor (cuentas viejas que la declaran).
        if (req.dateOfBirth() != null) {
            user.setDateOfBirth(req.dateOfBirth());
        }
        user.setPhone(normalize(req.phone()));
        if (req.phoneVisibility() != null) {
            user.setPhoneVisibility(req.phoneVisibility());
        }
        if (req.notifyOnRequestCreated() != null) {
            user.setNotifyOnRequestCreated(req.notifyOnRequestCreated());
        }
        if (req.notifyOnPrayed() != null) {
            user.setNotifyOnPrayed(req.notifyOnPrayed());
        }
        if (req.notifyOnAnswered() != null) {
            user.setNotifyOnAnswered(req.notifyOnAnswered());
        }
        if (req.allowStrangerMessages() != null) {
            user.setAllowStrangerMessages(req.allowStrangerMessages());
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
                user.getDateOfBirth() != null ? user.getDateOfBirth().toString() : null,
                AgePolicy.isAdult(user.getDateOfBirth(), clock),
                user.getPhone(),
                user.getPhoneVisibility().name(),
                user.getGlobalRole().name(),
                user.isNotifyOnRequestCreated(),
                user.isNotifyOnPrayed(),
                user.isNotifyOnAnswered(),
                user.isAllowStrangerMessages()
        );
    }
}
