package com.dosotres.messaging;

import com.dosotres.messaging.dto.ConversationSummaryResponse;
import com.dosotres.messaging.dto.MessageResponse;
import com.dosotres.messaging.dto.SendMessageRequest;
import com.dosotres.messaging.dto.StartConversationRequest;
import com.dosotres.security.annotations.AuthUser;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/conversations")
public class MessagingController {

    private final MessagingService messagingService;

    public MessagingController(MessagingService messagingService) {
        this.messagingService = messagingService;
    }

    @PostMapping
    public ConversationSummaryResponse start(@AuthUser Long userId,
                                             @Valid @RequestBody StartConversationRequest req) {
        return messagingService.startConversation(userId, req.userId());
    }

    @GetMapping
    public List<ConversationSummaryResponse> list(@AuthUser Long userId) {
        return messagingService.listConversations(userId);
    }

    @GetMapping("/{id}/messages")
    public Page<MessageResponse> messages(@AuthUser Long userId,
                                          @PathVariable Long id,
                                          @PageableDefault(size = 30) Pageable pageable) {
        return messagingService.getMessages(userId, id, pageable);
    }

    @PostMapping("/{id}/messages")
    @ResponseStatus(HttpStatus.CREATED)
    public MessageResponse send(@AuthUser Long userId,
                                @PathVariable Long id,
                                @Valid @RequestBody SendMessageRequest req) {
        return messagingService.sendMessage(userId, id, req.body());
    }

    @PostMapping("/{id}/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void markRead(@AuthUser Long userId, @PathVariable Long id) {
        messagingService.markRead(userId, id);
    }
}
