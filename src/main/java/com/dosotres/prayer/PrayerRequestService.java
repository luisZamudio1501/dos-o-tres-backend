package com.dosotres.prayer;

import com.dosotres.activity.ActivityEventType;
import com.dosotres.activity.ActivityService;
import com.dosotres.common.exception.ConflictException;
import com.dosotres.common.exception.ForbiddenException;
import com.dosotres.common.exception.ResourceNotFoundException;
import com.dosotres.common.exception.ValidationException;
import com.dosotres.group.Group;
import com.dosotres.group.GroupMemberRepository;
import com.dosotres.group.GroupRepository;
import com.dosotres.group.GroupRole;
import com.dosotres.prayer.dto.CreatePrayerRequest;
import com.dosotres.prayer.dto.PrayerLogResponse;
import com.dosotres.prayer.dto.PrayerRequestResponse;
import com.dosotres.user.User;
import com.dosotres.user.UserRepository;
import java.time.Clock;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class PrayerRequestService {

    private static final Logger log = LoggerFactory.getLogger(PrayerRequestService.class);

    private final PrayerRequestRepository prayerRequestRepository;
    private final GroupRepository groupRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final UserRepository userRepository;
    private final PrayerCommitmentRepository commitmentRepository;
    private final SessionPrayerRequestRepository sessionPrayerRequestRepository;
    private final ActivityService activityService;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    public PrayerRequestService(PrayerRequestRepository prayerRequestRepository,
                                 GroupRepository groupRepository,
                                 GroupMemberRepository groupMemberRepository,
                                 UserRepository userRepository,
                                 PrayerCommitmentRepository commitmentRepository,
                                 SessionPrayerRequestRepository sessionPrayerRequestRepository,
                                 ActivityService activityService,
                                 ApplicationEventPublisher eventPublisher,
                                 Clock clock) {
        this.prayerRequestRepository = prayerRequestRepository;
        this.groupRepository = groupRepository;
        this.groupMemberRepository = groupMemberRepository;
        this.userRepository = userRepository;
        this.commitmentRepository = commitmentRepository;
        this.sessionPrayerRequestRepository = sessionPrayerRequestRepository;
        this.activityService = activityService;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    /** Agenda de oración del usuario: todo pedido por el que oró, más reciente primero. */
    @Transactional(readOnly = true)
    public Page<PrayerRequestResponse> prayerHistory(Long userId, Pageable pageable) {
        Page<PrayerHistoryRow> rows = commitmentRepository.findPrayerHistoryByUserId(userId, pageable);

        List<Long> ids = rows.getContent().stream().map(r -> r.getRequest().getId()).toList();
        Map<Long, Long> counts = new HashMap<>();
        Map<Long, Long> prayedCounts = new HashMap<>();
        if (!ids.isEmpty()) {
            for (Object[] row : commitmentRepository.countGroupedByPrayerRequestIds(ids)) {
                counts.put((Long) row[0], (Long) row[1]);
            }
            for (Object[] row : commitmentRepository.countDistinctUsersGroupedByPrayerRequestIds(ids)) {
                prayedCounts.put((Long) row[0], (Long) row[1]);
            }
        }
        return rows.map(row -> toResponse(row.getRequest(),
                counts.getOrDefault(row.getRequest().getId(), 0L).intValue(),
                prayedCounts.getOrDefault(row.getRequest().getId(), 0L).intValue(),
                row.getCnt().intValue(),
                row.getLastPrayedAt() != null ? row.getLastPrayedAt().toString() : null));
    }

    public PrayerRequestResponse create(CreatePrayerRequest req, Long groupId, Long userId) {
        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> new ResourceNotFoundException("Group", "id", groupId));
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));

        PrayerRequest pr = new PrayerRequest();
        pr.setGroup(group);
        pr.setAuthor(user);
        pr.setTitle(req.title());
        pr.setDescription(req.description());
        pr.setStatus(PrayerRequestStatus.ACTIVE);
        prayerRequestRepository.save(pr);

        activityService.record(group, user, ActivityEventType.REQUEST_CREATED, false,
                Map.of("prayerRequestId", pr.getId(), "prayerTitle", pr.getTitle()));

        return toResponse(pr, 0, 0); // recién creado: nadie oró todavía
    }

    /**
     * Crea un pedido en el espacio personal del usuario: PRIVATE, sin grupo y
     * sin evento de muro (todavía no es visible para nadie más que el autor).
     */
    public PrayerRequestResponse createPersonal(CreatePrayerRequest req, Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));

        PrayerRequest pr = new PrayerRequest();
        pr.setAuthor(user);
        pr.setTitle(req.title());
        pr.setDescription(req.description());
        pr.setStatus(PrayerRequestStatus.ACTIVE);
        pr.setVisibility(PrayerVisibility.PRIVATE);
        prayerRequestRepository.save(pr);

        return toResponse(pr, 0, 0);
    }

    /** Pedidos privados del usuario (su espacio personal). */
    @Transactional(readOnly = true)
    public Page<PrayerRequestResponse> listMine(Long userId, Pageable pageable) {
        Page<PrayerRequest> page = prayerRequestRepository
                .findByAuthorIdAndVisibility(userId, PrayerVisibility.PRIVATE, pageable);

        List<Long> ids = page.getContent().stream().map(PrayerRequest::getId).toList();
        Map<Long, Long> counts = new HashMap<>();
        Map<Long, Long> prayedCounts = new HashMap<>();
        if (!ids.isEmpty()) {
            for (Object[] row : commitmentRepository.countGroupedByPrayerRequestIds(ids)) {
                counts.put((Long) row[0], (Long) row[1]);
            }
            for (Object[] row : commitmentRepository.countDistinctUsersGroupedByPrayerRequestIds(ids)) {
                prayedCounts.put((Long) row[0], (Long) row[1]);
            }
        }
        Map<Long, Object[]> myStats = myStatsFor(userId, ids);
        return page.map(pr -> toResponse(pr,
                counts.getOrDefault(pr.getId(), 0L).intValue(),
                prayedCounts.getOrDefault(pr.getId(), 0L).intValue(),
                myStats, pr.getId()));
    }

    /**
     * Comparte un pedido privado con un grupo (one-way). Solo el autor, sobre un
     * pedido aún PRIVATE, y solo a un grupo del que sea miembro. Recién al
     * compartir se registra el evento de creación en el muro de ese grupo.
     */
    public PrayerRequestResponse share(Long id, Long userId, Long groupId) {
        PrayerRequest pr = prayerRequestRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("PrayerRequest", "id", id));

        if (!pr.getAuthor().getId().equals(userId)) {
            throw new ForbiddenException("Only the owner can share this prayer request");
        }
        if (pr.getVisibility() != PrayerVisibility.PRIVATE) {
            throw new ValidationException("El pedido ya está compartido con un grupo");
        }
        if (!groupMemberRepository.existsByGroupIdAndUserId(groupId, userId)) {
            throw new ForbiddenException("No sos miembro de ese grupo");
        }

        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> new ResourceNotFoundException("Group", "id", groupId));
        pr.setGroup(group);
        pr.setVisibility(PrayerVisibility.GROUP);
        prayerRequestRepository.save(pr);

        activityService.record(group, pr.getAuthor(), ActivityEventType.REQUEST_CREATED, false,
                Map.of("prayerRequestId", pr.getId(), "prayerTitle", pr.getTitle()));

        return buildResponse(pr, userId);
    }

    /**
     * Pedidos orables del usuario para una sesión unificada: siempre sus pedidos
     * privados; si includeGroups, además los pedidos orables (ACTIVE/ON_HOLD) de
     * todos sus grupos. Permite juntar varios grupos + lo personal en un momento.
     */
    @Transactional(readOnly = true)
    public List<PrayerRequestResponse> prayable(Long userId, boolean includeGroups) {
        List<PrayerRequest> result = new ArrayList<>(
                prayerRequestRepository.findByAuthorIdAndVisibilityAndStatusNot(
                        userId, PrayerVisibility.PRIVATE, PrayerRequestStatus.ANSWERED));

        if (includeGroups) {
            List<Long> groupIds = groupMemberRepository.findByUserId(userId).stream()
                    .map(m -> m.getGroup().getId())
                    .toList();
            if (!groupIds.isEmpty()) {
                result.addAll(prayerRequestRepository.findByGroupIdInAndStatusIn(
                        groupIds,
                        List.of(PrayerRequestStatus.ACTIVE, PrayerRequestStatus.ON_HOLD)));
            }
        }

        List<Long> ids = result.stream().map(PrayerRequest::getId).toList();
        Map<Long, Long> counts = new HashMap<>();
        Map<Long, Long> prayedCounts = new HashMap<>();
        if (!ids.isEmpty()) {
            for (Object[] row : commitmentRepository.countGroupedByPrayerRequestIds(ids)) {
                counts.put((Long) row[0], (Long) row[1]);
            }
            for (Object[] row : commitmentRepository.countDistinctUsersGroupedByPrayerRequestIds(ids)) {
                prayedCounts.put((Long) row[0], (Long) row[1]);
            }
        }
        Map<Long, Object[]> myStats = myStatsFor(userId, ids);
        return result.stream()
                .map(pr -> toResponse(pr,
                        counts.getOrDefault(pr.getId(), 0L).intValue(),
                        prayedCounts.getOrDefault(pr.getId(), 0L).intValue(),
                        myStats, pr.getId()))
                .toList();
    }

    @Transactional(readOnly = true)
    public Page<PrayerRequestResponse> listByGroup(Long groupId, PrayerRequestStatus status, Pageable pageable,
                                                    Long userId) {
        Page<PrayerRequest> page;
        if (status != null) {
            page = prayerRequestRepository.findByGroupIdAndStatus(groupId, status, pageable);
        } else {
            page = prayerRequestRepository.findByGroupId(groupId, pageable);
        }

        // Conteos en queries agregadas para toda la página — evita N+1 (fix 3.4).
        List<Long> ids = page.getContent().stream().map(PrayerRequest::getId).toList();
        Map<Long, Long> counts = new HashMap<>();
        Map<Long, Long> prayedCounts = new HashMap<>();
        if (!ids.isEmpty()) {
            for (Object[] row : commitmentRepository.countGroupedByPrayerRequestIds(ids)) {
                counts.put((Long) row[0], (Long) row[1]);
            }
            for (Object[] row : commitmentRepository.countDistinctUsersGroupedByPrayerRequestIds(ids)) {
                prayedCounts.put((Long) row[0], (Long) row[1]);
            }
        }
        Map<Long, Object[]> myStats = myStatsFor(userId, ids);
        return page.map(pr -> toResponse(pr,
                counts.getOrDefault(pr.getId(), 0L).intValue(),
                prayedCounts.getOrDefault(pr.getId(), 0L).intValue(),
                myStats, pr.getId()));
    }

    @Transactional(readOnly = true)
    public PrayerRequestResponse getById(Long id, Long groupId, Long userId) {
        PrayerRequest pr = findInGroup(id, groupId);
        return buildResponse(pr, userId);
    }

    /** Historial "quién oró" por un pedido. Enmascara como "Alguien" las oraciones privadas. */
    @Transactional(readOnly = true)
    public List<PrayerLogResponse> listPrayers(Long id, Long groupId) {
        PrayerRequest pr = findInGroup(id, groupId);
        return commitmentRepository
                .findByPrayerRequestIdAndFulfilledTrueOrderByFulfilledAtDesc(pr.getId())
                .stream()
                .map(c -> new PrayerLogResponse(
                        c.isPrivate() ? "Alguien" : c.getUser().getDisplayName(),
                        c.getFulfilledAt() != null ? c.getFulfilledAt().toString()
                                : c.getCommittedDate().toString(),
                        c.isPrivate()))
                .toList();
    }

    /**
     * "Oré por esto" (botón directo): registra el cumplimiento del día y, si el
     * pedido estaba ACTIVE (nadie había orado), lo pasa a ON_HOLD (en espera).
     * Idempotente por (pedido, usuario, día en la zona del usuario): si ya oró
     * hoy, no duplica el cumplimiento ni el evento del muro.
     */
    public PrayerRequestResponse pray(Long id, Long groupId, Long userId, boolean isPrivate) {
        PrayerRequest pr = findInGroup(id, groupId);
        if (pr.getStatus() == PrayerRequestStatus.ANSWERED) {
            throw new ValidationException("No se puede orar por un pedido ya respondido");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));

        Instant now = Instant.now(clock);
        LocalDate today = LocalDate.ofInstant(now, userZone(user));

        PrayerCommitment commitment = commitmentRepository
                .findByPrayerRequestIdAndUserIdAndCommittedDate(id, userId, today)
                .orElseGet(() -> {
                    PrayerCommitment c = new PrayerCommitment();
                    c.setPrayerRequest(pr);
                    c.setUser(user);
                    c.setCommittedDate(today);
                    return c;
                });

        if (!commitment.isFulfilled()) {
            commitment.setFulfilled(true);
            commitment.setFulfilledAt(now);
            commitment.setPrivate(isPrivate);
            commitmentRepository.save(commitment);

            activityService.record(pr.getGroup(), user,
                    ActivityEventType.COMMITMENT_FULFILLED, isPrivate,
                    Map.of("prayerRequestId", pr.getId(), "prayerTitle", pr.getTitle()));
        }

        if (pr.getStatus() == PrayerRequestStatus.ACTIVE) {
            pr.setStatus(PrayerRequestStatus.ON_HOLD);
            prayerRequestRepository.save(pr);
        }

        return buildResponse(pr, userId);
    }

    public PrayerRequestResponse markAsAnswered(Long id, Long groupId, Long userId) {
        return changeStatus(id, groupId, userId, PrayerRequestStatus.ANSWERED, null);
    }

    /**
     * Cambio de estado MANUAL. Con la semántica V3, ACTIVE/ON_HOLD se manejan
     * automáticamente al orar; lo único manual es marcar como respondido y, sobre
     * un respondido, reactivarlo (recalculando el estado según las oraciones reales).
     */
    public PrayerRequestResponse changeStatus(Long id, Long groupId, Long userId,
                                              PrayerRequestStatus newStatus, String testimony) {
        PrayerRequest pr = findInGroup(id, groupId);

        boolean isAuthor = pr.getAuthor().getId().equals(userId);
        boolean isAdmin = groupMemberRepository.findByGroupIdAndUserId(groupId, userId)
                .map(m -> m.getRole() == GroupRole.ADMIN)
                .orElse(false);
        if (!isAuthor && !isAdmin) {
            throw new ForbiddenException("Only the author or a group admin can change the status");
        }

        boolean hasTestimony = testimony != null && !testimony.isBlank();

        if (newStatus == PrayerRequestStatus.ANSWERED) {
            if (pr.getStatus() == PrayerRequestStatus.ANSWERED) {
                throw new ConflictException("Prayer request is already answered");
            }
            if (hasTestimony && !isAuthor) {
                throw new ForbiddenException("Only the author can write a testimony");
            }
            pr.setStatus(PrayerRequestStatus.ANSWERED);
            pr.setAnsweredAt(Instant.now(clock));
            pr.setTestimony(hasTestimony ? testimony.trim() : null);
            prayerRequestRepository.save(pr);

            User actor = userRepository.findById(userId)
                    .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));
            activityService.record(pr.getGroup(), actor, ActivityEventType.REQUEST_ANSWERED, false,
                    Map.of("prayerRequestId", pr.getId(),
                            "prayerTitle", pr.getTitle(),
                            "hasTestimony", hasTestimony));

            // Push asíncrono (tras commit) a quienes oraron por el pedido.
            eventPublisher.publishEvent(new PrayerAnsweredEvent(
                    pr.getId(), pr.getTitle(), pr.getAuthor().getId(),
                    pr.getGroup() != null ? pr.getGroup().getId() : null));
            return buildResponse(pr, userId);
        }

        // Cualquier otro estado solo es válido como "reactivar" un pedido respondido.
        if (pr.getStatus() != PrayerRequestStatus.ANSWERED) {
            throw new ValidationException(
                    "El estado se actualiza automáticamente al orar; no se puede cambiar manualmente");
        }
        if (hasTestimony) {
            throw new ValidationException(
                    "El testimonio solo se puede escribir al marcar el pedido como respondido");
        }

        // Reactivar: el estado depende de si ya hay oraciones reales.
        PrayerRequestStatus recalculated =
                commitmentRepository.countDistinctUsersByPrayerRequestId(pr.getId()) > 0
                        ? PrayerRequestStatus.ON_HOLD
                        : PrayerRequestStatus.ACTIVE;
        pr.setStatus(recalculated);
        pr.setAnsweredAt(null);
        pr.setTestimony(null);
        prayerRequestRepository.save(pr);

        User actor = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));
        activityService.record(pr.getGroup(), actor, ActivityEventType.REQUEST_REACTIVATED, false,
                Map.of("prayerRequestId", pr.getId(), "prayerTitle", pr.getTitle()));
        return buildResponse(pr, userId);
    }

    /**
     * Elimina un pedido de oración. Regla de negocio: solo el administrador del
     * grupo puede borrar (cualquier pedido del grupo). Un miembro no admin no
     * puede borrar pedidos, ni siquiera los propios — solo puede salir del grupo.
     */
    public void delete(Long id, Long groupId, Long userId) {
        PrayerRequest pr = findInGroup(id, groupId);

        boolean isAdmin = groupMemberRepository.findByGroupIdAndUserId(groupId, userId)
                .map(m -> m.getRole() == GroupRole.ADMIN)
                .orElse(false);
        if (!isAdmin) {
            throw new ForbiddenException("Only a group admin can delete prayer requests");
        }

        // FKs con RESTRICT: eliminar hijos antes que el pedido.
        sessionPrayerRequestRepository.deleteByPrayerRequestId(id);
        commitmentRepository.deleteByPrayerRequestId(id);
        prayerRequestRepository.delete(pr);
    }

    private PrayerRequest findInGroup(Long id, Long groupId) {
        PrayerRequest pr = prayerRequestRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("PrayerRequest", "id", id));
        if (!pr.getGroup().getId().equals(groupId)) {
            throw new ResourceNotFoundException("PrayerRequest", "id+groupId", id + "+" + groupId);
        }
        return pr;
    }

    private ZoneId userZone(User user) {
        try {
            return user.getTimezone() != null ? ZoneId.of(user.getTimezone()) : ZoneOffset.UTC;
        } catch (DateTimeException e) {
            log.warn("Invalid timezone '{}' for userId={}, falling back to UTC",
                    user.getTimezone(), user.getId());
            return ZoneOffset.UTC;
        }
    }

    private PrayerRequestResponse buildResponse(PrayerRequest pr, Long userId) {
        Map<Long, Object[]> myStats = myStatsFor(userId, List.of(pr.getId()));
        return toResponse(pr,
                (int) commitmentRepository.countByPrayerRequestId(pr.getId()),
                (int) commitmentRepository.countDistinctUsersByPrayerRequestId(pr.getId()),
                myStats, pr.getId());
    }

    /** Cuántas veces oró el usuario actual por cada pedido + última vez (F.2), sin N+1. */
    private Map<Long, Object[]> myStatsFor(Long userId, List<Long> requestIds) {
        Map<Long, Object[]> myStats = new HashMap<>();
        if (!requestIds.isEmpty()) {
            for (Object[] row : commitmentRepository
                    .findMyPrayerStatsGroupedByPrayerRequestIds(userId, requestIds)) {
                myStats.put((Long) row[0], new Object[]{row[1], row[2]});
            }
        }
        return myStats;
    }

    private PrayerRequestResponse toResponse(PrayerRequest pr, int commitmentCount, int prayedByCount) {
        return toResponse(pr, commitmentCount, prayedByCount, Map.of(), pr.getId());
    }

    private PrayerRequestResponse toResponse(PrayerRequest pr, int commitmentCount, int prayedByCount,
                                              Map<Long, Object[]> myStats, Long requestId) {
        Object[] stat = myStats.get(requestId);
        int myPrayerCount = stat != null ? ((Long) stat[0]).intValue() : 0;
        String myLastPrayedAt = stat != null && stat[1] != null ? ((Instant) stat[1]).toString() : null;
        return toResponse(pr, commitmentCount, prayedByCount, myPrayerCount, myLastPrayedAt);
    }

    private PrayerRequestResponse toResponse(PrayerRequest pr, int commitmentCount, int prayedByCount,
                                              int myPrayerCount, String myLastPrayedAt) {
        return new PrayerRequestResponse(
                pr.getId(),
                pr.getAuthor().getId(),
                pr.getAuthor().getDisplayName(),
                pr.getTitle(),
                pr.getDescription(),
                pr.getStatus().name(),
                pr.getAnsweredAt() != null ? pr.getAnsweredAt().toString() : null,
                pr.getTestimony(),
                pr.getCreatedAt() != null ? pr.getCreatedAt().toString() : null,
                commitmentCount,
                prayedByCount,
                pr.getVisibility().name(),
                pr.getGroup() != null ? pr.getGroup().getId() : null,
                pr.getGroup() != null ? pr.getGroup().getName() : null,
                myPrayerCount,
                myLastPrayedAt
        );
    }
}
