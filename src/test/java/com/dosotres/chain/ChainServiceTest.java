package com.dosotres.chain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dosotres.activity.ActivityEventType;
import com.dosotres.activity.ActivityService;
import com.dosotres.chain.dto.ChainDetailResponse;
import com.dosotres.chain.dto.ChainResponse;
import com.dosotres.chain.dto.CreateChainRequest;
import com.dosotres.common.exception.ForbiddenException;
import com.dosotres.common.exception.ValidationException;
import com.dosotres.group.Group;
import com.dosotres.group.GroupMember;
import com.dosotres.group.GroupMemberRepository;
import com.dosotres.group.GroupRole;
import com.dosotres.user.User;
import com.dosotres.user.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ChainServiceTest {

    private static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.parse("2026-06-11T12:00:00Z"), ZoneId.of("UTC"));

    @Mock
    private PrayerChainRepository chainRepository;
    @Mock
    private ChainCommitmentRepository commitmentRepository;
    @Mock
    private GroupMemberRepository groupMemberRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private ActivityService activityService;

    private ChainService service;

    @BeforeEach
    void setUp() {
        service = new ChainService(chainRepository, commitmentRepository,
                groupMemberRepository, userRepository, activityService, FIXED_CLOCK);
    }

    private User makeUser(Long id, String name) {
        User u = new User();
        u.setId(id);
        u.setDisplayName(name);
        return u;
    }

    private Group makeGroup(Long id) {
        Group g = new Group();
        g.setId(id);
        return g;
    }

    private GroupMember makeMember(Group group, User user, GroupRole role) {
        GroupMember m = new GroupMember();
        m.setGroup(group);
        m.setUser(user);
        m.setRole(role);
        return m;
    }

    private PrayerChain makeChain(Long id, Group group, User creator) {
        PrayerChain c = new PrayerChain();
        c.setId(id);
        c.setGroup(group);
        c.setName("Cadena 24h");
        c.setSlotMinutes(60);
        c.setDailyStartMinutes(0);
        c.setDurationMinutes(1440);
        c.setDateFrom(LocalDate.of(2026, 6, 10));
        c.setDateTo(LocalDate.of(2026, 6, 20));
        c.setCreatedBy(creator);
        return c;
    }

    @Test
    void create_byAdmin_savesChainAndRecordsEvent() {
        User admin = makeUser(1L, "Luis");
        Group group = makeGroup(10L);
        GroupMember adminMember = makeMember(group, admin, GroupRole.ADMIN);

        when(groupMemberRepository.findByGroupIdAndUserId(10L, 1L)).thenReturn(Optional.of(adminMember));
        when(chainRepository.save(any(PrayerChain.class))).thenAnswer(inv -> {
            PrayerChain c = inv.getArgument(0);
            c.setId(100L);
            return c;
        });

        ChainResponse response = service.create(
                new CreateChainRequest("Vigilia", null, 30, 0, 1440, "2026-06-15", "2026-06-22"),
                10L, 1L);

        assertThat(response.totalSlots()).isEqualTo(48);
        assertThat(response.status()).isEqualTo("UPCOMING");
        verify(activityService).record(eq(group), eq(admin), eq(ActivityEventType.CHAIN_CREATED), eq(false), anyMap());
    }

    @Test
    void create_byMember_throwsForbidden() {
        User member = makeUser(2L, "Ana");
        Group group = makeGroup(10L);
        when(groupMemberRepository.findByGroupIdAndUserId(10L, 2L))
                .thenReturn(Optional.of(makeMember(group, member, GroupRole.MEMBER)));

        assertThatThrownBy(() -> service.create(
                new CreateChainRequest("Vigilia", null, 30, 0, 1440, "2026-06-15", "2026-06-22"),
                10L, 2L))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void create_rejectsInvalidSlotMinutes() {
        User admin = makeUser(1L, "Luis");
        Group group = makeGroup(10L);
        when(groupMemberRepository.findByGroupIdAndUserId(10L, 1L))
                .thenReturn(Optional.of(makeMember(group, admin, GroupRole.ADMIN)));

        assertThatThrownBy(() -> service.create(
                new CreateChainRequest("Vigilia", null, 45, 0, 1440, "2026-06-15", "2026-06-22"),
                10L, 1L))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void create_rejectsDurationNotMultipleOfSlot() {
        User admin = makeUser(1L, "Luis");
        Group group = makeGroup(10L);
        when(groupMemberRepository.findByGroupIdAndUserId(10L, 1L))
                .thenReturn(Optional.of(makeMember(group, admin, GroupRole.ADMIN)));

        assertThatThrownBy(() -> service.create(
                new CreateChainRequest("Vigilia", null, 60, 0, 90, "2026-06-15", "2026-06-22"),
                10L, 1L))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void detail_buildsSlotsWithSubscribersAndCoverage() {
        User creator = makeUser(1L, "Luis");
        User ana = makeUser(2L, "Ana");
        Group group = makeGroup(10L);
        PrayerChain chain = makeChain(100L, group, creator);
        chain.setSlotMinutes(60);
        chain.setDurationMinutes(120);

        ChainCommitment c1 = new ChainCommitment();
        c1.setChain(chain);
        c1.setUser(ana);
        c1.setSlotIndex(0);

        when(chainRepository.findById(100L)).thenReturn(Optional.of(chain));
        when(commitmentRepository.findByChainId(100L)).thenReturn(List.of(c1));

        ChainDetailResponse detail = service.detail(100L, 10L);

        assertThat(detail.slots()).hasSize(2);
        assertThat(detail.slots().get(0).subscribers()).hasSize(1);
        assertThat(detail.slots().get(0).subscribers().get(0).displayName()).isEqualTo("Ana");
        assertThat(detail.slots().get(1).subscribers()).isEmpty();
        assertThat(detail.chain().coveredSlots()).isEqualTo(1);
        assertThat(detail.chain().status()).isEqualTo("ACTIVE");
    }

    @Test
    void subscribe_isIdempotent() {
        User creator = makeUser(1L, "Luis");
        User ana = makeUser(2L, "Ana");
        Group group = makeGroup(10L);
        PrayerChain chain = makeChain(100L, group, creator);

        ChainCommitment existing = new ChainCommitment();
        existing.setChain(chain);
        existing.setUser(ana);
        existing.setSlotIndex(5);

        when(chainRepository.findById(100L)).thenReturn(Optional.of(chain));
        when(commitmentRepository.findByChainIdAndUserIdAndSlotIndex(100L, 2L, 5))
                .thenReturn(Optional.of(existing));
        when(commitmentRepository.findByChainId(100L)).thenReturn(List.of(existing));

        service.subscribe(100L, 5, 10L, 2L);

        verify(commitmentRepository, never()).save(any(ChainCommitment.class));
        verify(activityService, never()).record(any(), any(), any(), anyBoolean(), anyMap());
    }

    @Test
    void subscribe_savesCommitmentAndRecordsEvent() {
        User creator = makeUser(1L, "Luis");
        User ana = makeUser(2L, "Ana");
        Group group = makeGroup(10L);
        PrayerChain chain = makeChain(100L, group, creator);

        when(chainRepository.findById(100L)).thenReturn(Optional.of(chain));
        when(commitmentRepository.findByChainIdAndUserIdAndSlotIndex(100L, 2L, 3))
                .thenReturn(Optional.empty());
        when(userRepository.findById(2L)).thenReturn(Optional.of(ana));
        when(commitmentRepository.save(any(ChainCommitment.class))).thenAnswer(inv -> inv.getArgument(0));
        when(commitmentRepository.findByChainId(100L)).thenReturn(List.of());

        service.subscribe(100L, 3, 10L, 2L);

        verify(commitmentRepository).save(any(ChainCommitment.class));
        verify(activityService).record(eq(group), eq(ana), eq(ActivityEventType.CHAIN_SLOT_TAKEN), eq(false), anyMap());
    }

    @Test
    void subscribe_rejectsFinishedChain() {
        User creator = makeUser(1L, "Luis");
        Group group = makeGroup(10L);
        PrayerChain chain = makeChain(100L, group, creator);
        chain.setDateFrom(LocalDate.of(2026, 5, 1));
        chain.setDateTo(LocalDate.of(2026, 5, 10));

        when(chainRepository.findById(100L)).thenReturn(Optional.of(chain));

        assertThatThrownBy(() -> service.subscribe(100L, 0, 10L, 2L))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("terminó");
    }

    @Test
    void subscribe_rejectsInvalidSlotIndex() {
        User creator = makeUser(1L, "Luis");
        Group group = makeGroup(10L);
        PrayerChain chain = makeChain(100L, group, creator);

        when(chainRepository.findById(100L)).thenReturn(Optional.of(chain));

        assertThatThrownBy(() -> service.subscribe(100L, 99, 10L, 2L))
                .isInstanceOf(ValidationException.class);
    }
}
