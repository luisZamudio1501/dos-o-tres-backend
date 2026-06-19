package com.dosotres.publicwall;

import com.dosotres.common.exception.ForbiddenException;
import com.dosotres.common.exception.ResourceNotFoundException;
import com.dosotres.moderation.ModerationAccess;
import com.dosotres.publicwall.dto.CreatePublicRequestRequest;
import com.dosotres.publicwall.dto.PublicRequestResponse;
import com.dosotres.user.User;
import com.dosotres.user.UserRepository;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class PublicWallService {

    private final PublicPrayerRequestRepository requestRepository;
    private final PublicPrayerRepository prayerRepository;
    private final UserRepository userRepository;
    private final ModerationAccess moderationAccess;

    public PublicWallService(PublicPrayerRequestRepository requestRepository,
                             PublicPrayerRepository prayerRepository,
                             UserRepository userRepository,
                             ModerationAccess moderationAccess) {
        this.requestRepository = requestRepository;
        this.prayerRepository = prayerRepository;
        this.userRepository = userRepository;
        this.moderationAccess = moderationAccess;
    }

    /** Publica un pedido en el muro público. */
    public PublicRequestResponse create(Long userId, CreatePublicRequestRequest req) {
        User author = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));

        PublicPrayerRequest request = new PublicPrayerRequest();
        request.setAuthor(author);
        request.setTitle(req.title().trim());
        request.setBody(normalize(req.body()));
        request.setAnonymous(req.anonymous());
        request.setStatus(PublicRequestStatus.ACTIVE);
        request.setModerationStatus(ModerationStatus.VISIBLE);
        request.setPrayCount(0);
        requestRepository.save(request);

        return toResponse(request, userId, false);
    }

    /** Feed público (solo visible), con flag iPrayed por pedido sin N+1. */
    @Transactional(readOnly = true)
    public Page<PublicRequestResponse> feed(Long userId, Pageable pageable) {
        Page<PublicPrayerRequest> page = requestRepository
                .findByModerationStatusOrderByCreatedAtDesc(ModerationStatus.VISIBLE, pageable);

        List<Long> ids = page.getContent().stream().map(PublicPrayerRequest::getId).toList();
        Set<Long> prayedIds = ids.isEmpty()
                ? Set.of()
                : new HashSet<>(prayerRepository.findPrayedRequestIds(userId, ids));

        return page.map(r -> toResponse(r, userId, prayedIds.contains(r.getId())));
    }

    /** "Oré por esto": idempotente, suma al contador una sola vez por usuario. */
    public PublicRequestResponse pray(Long userId, Long requestId) {
        PublicPrayerRequest request = requestRepository.findById(requestId)
                .filter(r -> r.getModerationStatus() == ModerationStatus.VISIBLE)
                .orElseThrow(() -> new ResourceNotFoundException("PublicPrayerRequest", "id", requestId));

        if (!prayerRepository.existsByRequestIdAndUserId(requestId, userId)) {
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));

            PublicPrayer prayer = new PublicPrayer();
            prayer.setRequest(request);
            prayer.setUser(user);
            prayerRepository.save(prayer);

            request.setPrayCount(request.getPrayCount() + 1);
        }

        return toResponse(request, userId, true);
    }

    /** Moderador global: oculta (HIDDEN) o restaura (VISIBLE) un pedido del muro. */
    public PublicRequestResponse setVisibility(Long moderatorId, Long requestId, ModerationStatus status) {
        moderationAccess.requireModerator(moderatorId);
        PublicPrayerRequest request = requestRepository.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("PublicPrayerRequest", "id", requestId));
        request.setModerationStatus(status);
        boolean iPrayed = prayerRepository.existsByRequestIdAndUserId(requestId, moderatorId);
        return toResponse(request, moderatorId, iPrayed);
    }

    /** Borra un pedido público: lo puede borrar su autor o un moderador global. */
    public void delete(Long userId, Long requestId) {
        PublicPrayerRequest request = requestRepository.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("PublicPrayerRequest", "id", requestId));
        boolean isAuthor = request.getAuthor().getId().equals(userId);
        if (!isAuthor && !moderationAccess.isModerator(userId)) {
            throw new ForbiddenException("Solo el autor o un moderador puede borrar este pedido público");
        }
        prayerRepository.deleteByRequestId(requestId);
        requestRepository.delete(request);
    }

    private String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private PublicRequestResponse toResponse(PublicPrayerRequest r, Long currentUserId, boolean iPrayed) {
        boolean anonymous = r.isAnonymous();
        boolean mine = r.getAuthor().getId().equals(currentUserId);
        return new PublicRequestResponse(
                r.getId(),
                anonymous ? null : r.getAuthor().getId(),
                anonymous ? null : r.getAuthor().getDisplayName(),
                anonymous ? null : r.getAuthor().getCountry(),
                anonymous,
                r.getTitle(),
                r.getBody(),
                r.getStatus().name(),
                r.getPrayCount(),
                iPrayed,
                mine,
                r.getCreatedAt() != null ? r.getCreatedAt().toString() : null
        );
    }
}
