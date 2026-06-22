package com.dosotres.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.dosotres.common.exception.ForbiddenException;
import com.dosotres.common.exception.ResourceNotFoundException;
import com.dosotres.user.GlobalRole;
import com.dosotres.user.User;
import com.dosotres.user.UserRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AdminAccessTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private AdminAccess adminAccess;

    private User userWithRole(GlobalRole role) {
        User u = new User();
        u.setGlobalRole(role);
        return u;
    }

    @Test
    void requireAdmin_allowsAdmin() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(userWithRole(GlobalRole.ADMIN)));
        assertThat(adminAccess.requireAdmin(1L).getGlobalRole()).isEqualTo(GlobalRole.ADMIN);
        assertThat(adminAccess.isAdmin(1L)).isTrue();
    }

    @Test
    void requireAdmin_rejectsModerator() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(userWithRole(GlobalRole.MODERATOR)));
        assertThatThrownBy(() -> adminAccess.requireAdmin(1L)).isInstanceOf(ForbiddenException.class);
        assertThat(adminAccess.isAdmin(1L)).isFalse();
    }

    @Test
    void requireAdmin_rejectsPlainUser() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(userWithRole(GlobalRole.USER)));
        assertThatThrownBy(() -> adminAccess.requireAdmin(1L)).isInstanceOf(ForbiddenException.class);
    }

    @Test
    void requireAdmin_throwsWhenMissing() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> adminAccess.requireAdmin(99L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void globalRoleHelpers_adminIsSupersetOfModerator() {
        assertThat(GlobalRole.ADMIN.canModerate()).isTrue();
        assertThat(GlobalRole.MODERATOR.canModerate()).isTrue();
        assertThat(GlobalRole.USER.canModerate()).isFalse();
        assertThat(GlobalRole.ADMIN.isAdmin()).isTrue();
        assertThat(GlobalRole.MODERATOR.isAdmin()).isFalse();
    }
}
