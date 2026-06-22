package com.dosotres.admin;

import com.dosotres.admin.dto.AdminUserResponse;
import com.dosotres.user.User;
import com.dosotres.user.UserRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.Period;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Vista CRM de usuarios para el panel de administración (Fase 1). Gateada ADMIN:
 * expone PII (email), solo accesible al dueño.
 */
@Service
@Transactional(readOnly = true)
public class AdminUserService {

    private final UserRepository userRepository;
    private final AdminAccess adminAccess;
    private final Clock clock;

    public AdminUserService(UserRepository userRepository, AdminAccess adminAccess, Clock clock) {
        this.userRepository = userRepository;
        this.adminAccess = adminAccess;
        this.clock = clock;
    }

    public Page<AdminUserResponse> listUsers(Long adminId, String query, Pageable pageable) {
        adminAccess.requireAdmin(adminId);
        LocalDate today = LocalDate.now(clock);
        Page<User> page = (query == null || query.isBlank())
                ? userRepository.findAll(pageable)
                : userRepository.searchForAdmin(query.trim(), pageable);
        return page.map(u -> toResponse(u, today));
    }

    private AdminUserResponse toResponse(User u, LocalDate today) {
        Integer age = u.getDateOfBirth() == null
                ? null
                : Period.between(u.getDateOfBirth(), today).getYears();
        return new AdminUserResponse(
                u.getId(),
                u.getEmail(),
                u.getDisplayName(),
                u.getCountry(),
                u.getProvince(),
                age,
                u.getGlobalRole().name(),
                u.getCreatedAt(),
                u.getLastSeenAt()
        );
    }
}
