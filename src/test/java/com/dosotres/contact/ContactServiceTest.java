package com.dosotres.contact;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dosotres.common.exception.ResourceNotFoundException;
import com.dosotres.email.EmailService;
import com.dosotres.user.User;
import com.dosotres.user.UserRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ContactServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private EmailService emailService;

    private ContactService service;

    @BeforeEach
    void setUp() {
        service = new ContactService(userRepository, emailService);
    }

    @Test
    void send_forwardsToEmailService() {
        User u = new User();
        u.setId(1L);
        u.setEmail("user@example.com");
        u.setDisplayName("Luis");
        when(userRepository.findById(1L)).thenReturn(Optional.of(u));

        service.send(1L, "Hola, tengo una pregunta sobre la app.");

        verify(emailService).sendContact("Luis", "user@example.com", "Hola, tengo una pregunta sobre la app.");
    }

    @Test
    void send_throwsWhenUserNotFound() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.send(99L, "mensaje"))
                .isInstanceOf(ResourceNotFoundException.class);

    }
}
