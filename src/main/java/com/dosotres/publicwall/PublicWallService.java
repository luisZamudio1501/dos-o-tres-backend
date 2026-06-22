package com.dosotres.publicwall;

import com.dosotres.common.exception.ConflictException;
import com.dosotres.common.exception.ForbiddenException;
import com.dosotres.common.exception.ResourceNotFoundException;
import com.dosotres.common.exception.ValidationException;
import com.dosotres.messaging.MessagingService;
import com.dosotres.messaging.dto.ConversationSummaryResponse;
import com.dosotres.moderation.ModerationAccess;
import com.dosotres.publicwall.dto.CreatePublicRequestRequest;
import com.dosotres.publicwall.dto.PrayerEntryResponse;
import com.dosotres.publicwall.dto.PublicRequestResponse;
import com.dosotres.push.PushNotificationService;
import com.dosotres.user.User;
import com.dosotres.user.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class PublicWallService {

    private static final Logger log = LoggerFactory.getLogger(PublicWallService.class);

    /** Inactividad tras la cual un pedido activo se auto-archiva. */
    static final Duration STALE_THRESHOLD = Duration.ofDays(90);

    private final PublicPrayerRequestRepository requestRepository;
    private final PublicPrayerRepository prayerRepository;
    private final UserRepository userRepository;
    private final ModerationAccess moderationAccess;
    private final MessagingService messagingService;
    private final PushNotificationService pushService;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public PublicWallService(PublicPrayerRequestRepository requestRepository,
                             PublicPrayerRepository prayerRepository,
                             UserRepository userRepository,
                             ModerationAccess moderationAccess,
                             MessagingService messagingService,
                             PushNotificationService pushService,
                             ObjectMapper objectMapper,
                             Clock clock) {
        this.requestRepository = requestRepository;
        this.prayerRepository = prayerRepository;
        this.userRepository = userRepository;
        this.moderationAccess = moderationAccess;
        this.messagingService = messagingService;
        this.pushService = pushService;
        this.objectMapper = objectMapper;
        this.clock = clock;
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

    /** Feed activo (visible, activo, no archivado), con flag iPrayed por pedido sin N+1. */
    @Transactional(readOnly = true)
    public Page<PublicRequestResponse> feed(Long userId, Pageable pageable) {
        Page<PublicPrayerRequest> page = requestRepository
                .findByModerationStatusAndStatusAndArchivedAtIsNullOrderByCreatedAtDesc(
                        ModerationStatus.VISIBLE, PublicRequestStatus.ACTIVE, pageable);
        return mapWithPrayed(page, userId);
    }

    /** Testimonios públicos permanentes (respondidos con testimonio). */
    @Transactional(readOnly = true)
    public Page<PublicRequestResponse> testimonies(Long userId, Pageable pageable) {
        Page<PublicPrayerRequest> page = requestRepository
                .findTestimonies(ModerationStatus.VISIBLE, pageable);
        return mapWithPrayed(page, userId);
    }

    /**
     * "Oré por esto": idempotente, suma al contador y refresca la actividad una sola vez
     * por usuario. {@code visible} guarda si el orante se muestra con su nombre (default
     * anónimo); sólo aplica al registrar la primera oración.
     */
    public PublicRequestResponse pray(Long userId, Long requestId, boolean visible) {
        PublicPrayerRequest request = requestRepository.findById(requestId)
                .filter(r -> r.getModerationStatus() == ModerationStatus.VISIBLE)
                .orElseThrow(() -> new ResourceNotFoundException("PublicPrayerRequest", "id", requestId));

        if (!prayerRepository.existsByRequestIdAndUserId(requestId, userId)) {
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));

            PublicPrayer prayer = new PublicPrayer();
            prayer.setRequest(request);
            prayer.setUser(user);
            prayer.setVisible(visible);
            prayerRepository.save(prayer);

            request.setPrayCount(request.getPrayCount() + 1);
            request.setLastActivityAt(Instant.now(clock));
        }

        return toResponse(request, userId, true);
    }

    /**
     * Solicitud de vínculo del orante hacia el autor (Fase 5, Etapa 1). Exige haber orado
     * por el pedido y que no sea propio; delega en mensajería (gate 18 + anti-spam).
     */
    public ConversationSummaryResponse requestLink(Long userId, Long requestId) {
        PublicPrayerRequest request = requestRepository.findById(requestId)
                .filter(r -> r.getModerationStatus() == ModerationStatus.VISIBLE)
                .orElseThrow(() -> new ResourceNotFoundException("PublicPrayerRequest", "id", requestId));

        Long authorId = request.getAuthor().getId();
        if (authorId.equals(userId)) {
            throw new ValidationException("No podés solicitar un vínculo con tu propio pedido");
        }
        if (!prayerRepository.existsByRequestIdAndUserId(requestId, userId)) {
            throw new ForbiddenException("Primero orá por este pedido para poder conectar");
        }

        return messagingService.startLinkRequest(userId, authorId, request);
    }

    /**
     * El autor ve quiénes oraron por su pedido (Fase 6, Etapa 2): los visibles con su
     * nombre, los anónimos como "Anónimo" sin revelar su identidad.
     */
    @Transactional(readOnly = true)
    public List<PrayerEntryResponse> listPrayers(Long authorId, Long requestId) {
        PublicPrayerRequest request = requireOwnRequest(authorId, requestId);
        return prayerRepository.findByRequestIdOrderByPrayedAtDesc(request.getId()).stream()
                .map(p -> p.isVisible()
                        ? new PrayerEntryResponse(p.getId(), p.getUser().getId(),
                                p.getUser().getDisplayName(), p.getPrayedAt().toString())
                        : new PrayerEntryResponse(p.getId(), null, "Anónimo", p.getPrayedAt().toString()))
                .toList();
    }

    /**
     * Agradecimiento general del autor a todos los que oraron (Fase 6): un solo sentido,
     * no abre chat. Los anónimos lo reciben sin revelarse (el push no expone identidad
     * alguna a quien lo recibe, solo que el autor de ese pedido agradeció).
     */
    @Transactional(readOnly = true)
    public void sendThanks(Long authorId, Long requestId) {
        PublicPrayerRequest request = requireOwnRequest(authorId, requestId);
        List<Long> recipientIds = prayerRepository.findByRequestIdOrderByPrayedAtDesc(request.getId())
                .stream().map(p -> p.getUser().getId()).toList();
        if (recipientIds.isEmpty()) {
            return;
        }
        try {
            String payload = objectMapper.writeValueAsString(Map.of(
                    "title", "Gracias por orar",
                    "body", request.getAuthor().getDisplayName() + " agradeció tu oración por «"
                            + request.getTitle() + "»",
                    "url", "/muro"));
            pushService.sendToUsers(recipientIds, payload);
        } catch (Exception e) {
            log.warn("Thanks push failed requestId={}: {}", requestId, e.getMessage());
        }
    }

    /**
     * El autor inicia él mismo un vínculo con un orante (Fase 6, Etapa 2). Identifica al
     * orante por el id opaco de su oración (no por user_id), de modo que también puede
     * conectar con anónimos sin que el backend les revele la identidad: el orante recibe
     * la solicitud enmascarada y, si acepta, se revelan mutuamente.
     */
    public ConversationSummaryResponse requestLinkFromAuthor(Long authorId, Long requestId, Long prayerId) {
        PublicPrayerRequest request = requireOwnRequest(authorId, requestId);
        PublicPrayer prayer = prayerRepository.findById(prayerId)
                .filter(p -> p.getRequest().getId().equals(requestId))
                .orElseThrow(() -> new ResourceNotFoundException("PublicPrayer", "id", prayerId));
        return messagingService.startLinkRequest(authorId, prayer.getUser().getId(), request);
    }

    private PublicPrayerRequest requireOwnRequest(Long authorId, Long requestId) {
        PublicPrayerRequest request = requestRepository.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("PublicPrayerRequest", "id", requestId));
        if (!request.getAuthor().getId().equals(authorId)) {
            throw new ForbiddenException("Solo el autor puede ver u operar sobre quienes oraron");
        }
        return request;
    }

    /** El autor (aun anónimo) marca el pedido como respondido, con testimonio opcional. */
    public PublicRequestResponse markAnswered(Long userId, Long requestId, String testimony) {
        PublicPrayerRequest request = requestRepository.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("PublicPrayerRequest", "id", requestId));

        if (!request.getAuthor().getId().equals(userId)) {
            throw new ForbiddenException("Solo el autor puede marcar el pedido como respondido");
        }
        if (request.getStatus() == PublicRequestStatus.ANSWERED) {
            throw new ConflictException("El pedido ya está marcado como respondido");
        }

        String text = normalize(testimony);
        request.setStatus(PublicRequestStatus.ANSWERED);
        request.setAnsweredAt(Instant.now(clock));
        request.setTestimony(text);

        boolean iPrayed = prayerRepository.existsByRequestIdAndUserId(requestId, userId);
        return toResponse(request, userId, iPrayed);
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

    /** Archiva por inactividad los pedidos activos sin actividad en la ventana. Devuelve cuántos archivó. */
    public int archiveStale() {
        Instant now = Instant.now(clock);
        return requestRepository.archiveStaleActive(now, now.minus(STALE_THRESHOLD));
    }

    private Page<PublicRequestResponse> mapWithPrayed(Page<PublicPrayerRequest> page, Long userId) {
        List<Long> ids = page.getContent().stream().map(PublicPrayerRequest::getId).toList();
        Set<Long> prayedIds = ids.isEmpty()
                ? Set.of()
                : new HashSet<>(prayerRepository.findPrayedRequestIds(userId, ids));
        return page.map(r -> toResponse(r, userId, prayedIds.contains(r.getId())));
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
                r.getTestimony(),
                r.getStatus().name(),
                r.getPrayCount(),
                iPrayed,
                mine,
                r.getArchivedAt() != null,
                r.getCreatedAt() != null ? r.getCreatedAt().toString() : null,
                r.getAnsweredAt() != null ? r.getAnsweredAt().toString() : null
        );
    }
}
