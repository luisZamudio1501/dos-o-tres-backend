package com.dosotres.contact;

import com.dosotres.common.exception.ResourceNotFoundException;
import com.dosotres.email.EmailService;
import com.dosotres.user.User;
import com.dosotres.user.UserRepository;
import org.springframework.stereotype.Service;

@Service
public class ContactService {

    private final UserRepository userRepository;
    private final EmailService emailService;

    public ContactService(UserRepository userRepository, EmailService emailService) {
        this.userRepository = userRepository;
        this.emailService = emailService;
    }

    public void send(Long userId, String message) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));
        emailService.sendContact(user.getDisplayName(), user.getEmail(), message);
    }
}
