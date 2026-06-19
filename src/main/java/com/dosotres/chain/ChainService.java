package com.dosotres.chain;

import com.dosotres.activity.ActivityEventType;
import com.dosotres.activity.ActivityService;
import com.dosotres.chain.dto.ChainDetailResponse;
import com.dosotres.chain.dto.ChainResponse;
import com.dosotres.chain.dto.ChainSlotResponse;
import com.dosotres.chain.dto.CreateChainRequest;
import com.dosotres.common.exception.ForbiddenException;
import com.dosotres.common.exception.ResourceNotFoundException;
import com.dosotres.common.exception.ValidationException;
import com.dosotres.group.Group;
import com.dosotres.group.GroupMember;
import com.dosotres.group.GroupMemberRepository;
import com.dosotres.group.GroupRole;
import com.dosotres.user.User;
import com.dosotres.user.UserRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class ChainService {

    private static final Set<Integer> ALLOWED_SLOT_MINUTES = Set.of(15, 30, 60);

    private final PrayerChainRepository chainRepository;
    private final ChainCommitmentRepository commitmentRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final UserRepository userRepository;
    private final ActivityService activityService;
    private final Clock clock;

    public ChainService(PrayerChainRepository chainRepository,
                        ChainCommitmentRepository commitmentRepository,
                        GroupMemberRepository groupMemberRepository,
                        UserRepository userRepository,
                        ActivityService activityService,
                        Clock clock) {
        this.chainRepository = chainRepository;
        this.commitmentRepository = commitmentRepository;
        this.groupMemberRepository = groupMemberRepository;
        this.userRepository = userRepository;
        this.activityService = activityService;
        this.clock = clock;
    }

    public ChainResponse create(CreateChainRequest req, Long groupId, Long userId) {
        GroupMember creator = groupMemberRepository.findByGroupIdAndUserId(groupId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("GroupMember", "groupId+userId",
                        groupId + "+" + userId));
        if (creator.getRole() != GroupRole.ADMIN) {
            throw new ForbiddenException("Only a group admin can create a prayer chain");
        }

        if (!ALLOWED_SLOT_MINUTES.contains(req.slotMinutes())) {
            throw new ValidationException("La fracción debe ser de 15, 30 o 60 minutos");
        }
        if (req.durationMinutes() % req.slotMinutes() != 0) {
            throw new ValidationException("La duración diaria debe ser múltiplo de la fracción elegida");
        }

        LocalDate dateFrom = parseDate(req.dateFrom());
        LocalDate dateTo = parseDate(req.dateTo());
        if (dateTo.isBefore(dateFrom)) {
            throw new ValidationException("La fecha de fin no puede ser anterior a la de inicio");
        }
        if (dateTo.isBefore(LocalDate.now(clock))) {
            throw new ValidationException("La cadena no puede terminar en el pasado");
        }

        PrayerChain chain = new PrayerChain();
        chain.setGroup(creator.getGroup());
        chain.setName(req.name());
        chain.setDescription(req.description());
        chain.setSlotMinutes(req.slotMinutes());
        chain.setDailyStartMinutes(req.dailyStartMinutes());
        chain.setDurationMinutes(req.durationMinutes());
        chain.setDateFrom(dateFrom);
        chain.setDateTo(dateTo);
        chain.setCreatedBy(creator.getUser());
        chainRepository.save(chain);

        activityService.record(creator.getGroup(), creator.getUser(),
                ActivityEventType.CHAIN_CREATED, false,
                Map.of("chainId", chain.getId(), "chainName", chain.getName()));

        return toResponse(chain, 0);
    }

    @Transactional(readOnly = true)
    public List<ChainResponse> listByGroup(Long groupId) {
        return chainRepository.findByGroupIdOrderByDateFromDesc(groupId).stream()
                .map(c -> toResponse(c, (int) commitmentRepository.countCoveredSlots(c.getId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public ChainDetailResponse detail(Long chainId, Long groupId) {
        PrayerChain chain = findInGroup(chainId, groupId);
        List<ChainCommitment> commitments = commitmentRepository.findByChainId(chainId);

        List<ChainSlotResponse> slots = new ArrayList<>();
        for (int i = 0; i < chain.totalSlots(); i++) {
            final int index = i;
            List<ChainSlotResponse.SlotSubscriber> subscribers = commitments.stream()
                    .filter(c -> c.getSlotIndex() == index)
                    .map(c -> new ChainSlotResponse.SlotSubscriber(
                            c.getUser().getId(), c.getUser().getDisplayName()))
                    .toList();
            int startMinutes = (chain.getDailyStartMinutes() + i * chain.getSlotMinutes()) % 1440;
            slots.add(new ChainSlotResponse(index, startMinutes, subscribers));
        }

        long covered = slots.stream().filter(s -> !s.subscribers().isEmpty()).count();
        return new ChainDetailResponse(toResponse(chain, (int) covered), slots);
    }

    public ChainDetailResponse subscribe(Long chainId, int slotIndex, Long groupId, Long userId) {
        PrayerChain chain = findInGroup(chainId, groupId);
        validateSlotIndex(chain, slotIndex);
        if (status(chain).equals("FINISHED")) {
            throw new ValidationException("Esta cadena ya terminó");
        }

        boolean alreadySubscribed = commitmentRepository
                .findByChainIdAndUserIdAndSlotIndex(chainId, userId, slotIndex)
                .isPresent();

        if (!alreadySubscribed) {
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));

            ChainCommitment commitment = new ChainCommitment();
            commitment.setChain(chain);
            commitment.setUser(user);
            commitment.setSlotIndex(slotIndex);
            commitmentRepository.save(commitment);

            activityService.record(chain.getGroup(), user,
                    ActivityEventType.CHAIN_SLOT_TAKEN, false,
                    Map.of("chainId", chain.getId(),
                            "chainName", chain.getName(),
                            "slotStartMinutes", (chain.getDailyStartMinutes()
                                    + slotIndex * chain.getSlotMinutes()) % 1440));
        }

        return detail(chainId, groupId);
    }

    public ChainDetailResponse unsubscribe(Long chainId, int slotIndex, Long groupId, Long userId) {
        PrayerChain chain = findInGroup(chainId, groupId);
        validateSlotIndex(chain, slotIndex);

        commitmentRepository.findByChainIdAndUserIdAndSlotIndex(chainId, userId, slotIndex)
                .ifPresent(commitmentRepository::delete);

        return detail(chainId, groupId);
    }

    public void delete(Long chainId, Long groupId, Long userId) {
        PrayerChain chain = findInGroup(chainId, groupId);
        boolean isCreator = chain.getCreatedBy().getId().equals(userId);
        boolean isAdmin = groupMemberRepository.findByGroupIdAndUserId(groupId, userId)
                .map(m -> m.getRole() == GroupRole.ADMIN)
                .orElse(false);
        if (!isCreator && !isAdmin) {
            throw new ForbiddenException("Only the chain creator or a group admin can delete this chain");
        }
        commitmentRepository.deleteByChainId(chainId);
        chainRepository.delete(chain);
    }

    private PrayerChain findInGroup(Long chainId, Long groupId) {
        PrayerChain chain = chainRepository.findById(chainId)
                .orElseThrow(() -> new ResourceNotFoundException("PrayerChain", "id", chainId));
        if (!chain.getGroup().getId().equals(groupId)) {
            throw new ResourceNotFoundException("PrayerChain", "id+groupId", chainId + "+" + groupId);
        }
        return chain;
    }

    private void validateSlotIndex(PrayerChain chain, int slotIndex) {
        if (slotIndex < 0 || slotIndex >= chain.totalSlots()) {
            throw new ValidationException("Franja inexistente en esta cadena");
        }
    }

    private String status(PrayerChain chain) {
        LocalDate today = LocalDate.now(clock);
        if (today.isBefore(chain.getDateFrom())) return "UPCOMING";
        if (today.isAfter(chain.getDateTo())) return "FINISHED";
        return "ACTIVE";
    }

    private LocalDate parseDate(String value) {
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException e) {
            throw new ValidationException("Fecha inválida: " + value);
        }
    }

    private ChainResponse toResponse(PrayerChain chain, int coveredSlots) {
        return new ChainResponse(
                chain.getId(),
                chain.getName(),
                chain.getDescription(),
                chain.getSlotMinutes(),
                chain.getDailyStartMinutes(),
                chain.getDurationMinutes(),
                chain.getDateFrom().toString(),
                chain.getDateTo().toString(),
                status(chain),
                chain.totalSlots(),
                coveredSlots,
                chain.getCreatedBy().getId(),
                chain.getCreatedBy().getDisplayName()
        );
    }
}
