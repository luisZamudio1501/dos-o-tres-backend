package com.dosotres.user;

import com.dosotres.security.annotations.AuthUser;
import com.dosotres.user.dto.UpdateProfileRequest;
import com.dosotres.user.dto.UserProfileResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/me")
    public UserProfileResponse me(@AuthUser Long userId) {
        return userService.getProfile(userId);
    }

    @PatchMapping("/me")
    public UserProfileResponse updateMe(@Valid @RequestBody UpdateProfileRequest req,
                                        @AuthUser Long userId) {
        return userService.updateProfile(userId, req);
    }
}
