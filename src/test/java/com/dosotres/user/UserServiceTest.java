package com.dosotres.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.dosotres.common.exception.ResourceNotFoundException;
import com.dosotres.user.dto.UpdateProfileRequest;
import com.dosotres.user.dto.UserProfileResponse;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    private UserService userService;

    @BeforeEach
    void setUp() {
        userService = new UserService(userRepository);
    }

    private User makeUser() {
        User u = new User();
        u.setId(1L);
        u.setEmail("luis@test.com");
        u.setDisplayName("Luis");
        return u;
    }

    @Test
    void getProfile_returnsProfileWithNullCongregationFields() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(makeUser()));

        UserProfileResponse res = userService.getProfile(1L);

        assertThat(res.id()).isEqualTo(1L);
        assertThat(res.email()).isEqualTo("luis@test.com");
        assertThat(res.displayName()).isEqualTo("Luis");
        assertThat(res.country()).isNull();
        assertThat(res.province()).isNull();
        assertThat(res.city()).isNull();
        assertThat(res.churchName()).isNull();
    }

    @Test
    void getProfile_unknownUser_throwsNotFound() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getProfile(99L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void updateProfile_setsAllFieldsAndUppercasesCountry() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(makeUser()));

        UserProfileResponse res = userService.updateProfile(1L, new UpdateProfileRequest(
                "Luis Z.", "ar", "Santa Fe", "Rosario", "Iglesia Bautista Centro"));

        assertThat(res.displayName()).isEqualTo("Luis Z.");
        assertThat(res.country()).isEqualTo("AR");
        assertThat(res.province()).isEqualTo("Santa Fe");
        assertThat(res.city()).isEqualTo("Rosario");
        assertThat(res.churchName()).isEqualTo("Iglesia Bautista Centro");
    }

    @Test
    void updateProfile_blankOrNullClearsCongregationFields() {
        User user = makeUser();
        user.setCountry("AR");
        user.setProvince("Santa Fe");
        user.setCity("Rosario");
        user.setChurchName("Iglesia Bautista Centro");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        UserProfileResponse res = userService.updateProfile(1L, new UpdateProfileRequest(
                null, null, "  ", "", null));

        assertThat(res.country()).isNull();
        assertThat(res.province()).isNull();
        assertThat(res.city()).isNull();
        assertThat(res.churchName()).isNull();
    }

    @Test
    void updateProfile_nullDisplayNameKeepsExistingName() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(makeUser()));

        UserProfileResponse res = userService.updateProfile(1L, new UpdateProfileRequest(
                null, "AR", null, "Rosario", null));

        assertThat(res.displayName()).isEqualTo("Luis");
        assertThat(res.country()).isEqualTo("AR");
        assertThat(res.city()).isEqualTo("Rosario");
    }

    @Test
    void updateProfile_trimsWhitespace() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(makeUser()));

        UserProfileResponse res = userService.updateProfile(1L, new UpdateProfileRequest(
                "  Luis Z.  ", null, null, "  Rosario  ", null));

        assertThat(res.displayName()).isEqualTo("Luis Z.");
        assertThat(res.city()).isEqualTo("Rosario");
    }
}
