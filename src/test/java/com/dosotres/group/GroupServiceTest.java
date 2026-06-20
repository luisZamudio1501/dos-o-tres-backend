package com.dosotres.group;

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
import com.dosotres.common.exception.ForbiddenException;
import com.dosotres.common.exception.ResourceNotFoundException;
import com.dosotres.common.exception.ValidationException;
import com.dosotres.group.dto.CreateGroupRequest;
import com.dosotres.group.dto.GroupMemberResponse;
import com.dosotres.group.dto.GroupResponse;
import com.dosotres.prayer.PrayerCommitment;
import com.dosotres.prayer.PrayerCommitmentRepository;
import com.dosotres.user.User;
import com.dosotres.user.UserRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GroupServiceTest {

    @Mock
    private GroupRepository groupRepository;
    @Mock
    private GroupMemberRepository groupMemberRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private PrayerCommitmentRepository prayerCommitmentRepository;
    @Mock
    private ActivityService activityService;

    private GroupService groupService;

    @BeforeEach
    void setUp() {
        groupService = new GroupService(groupRepository, groupMemberRepository, userRepository,
                prayerCommitmentRepository, activityService);
    }

    private User makeUser(Long id, String name) {
        User u = new User();
        u.setId(id);
        u.setDisplayName(name);
        return u;
    }

    private Group makeGroup(Long id, String name) {
        Group g = new Group();
        g.setId(id);
        g.setName(name);
        g.setInviteCode("inv-" + id);
        g.onCreate();
        return g;
    }

    private GroupMember makeMember(Group group, User user, GroupRole role) {
        GroupMember m = new GroupMember();
        m.setGroup(group);
        m.setUser(user);
        m.setRole(role);
        m.setJoinedAt(Instant.parse("2026-06-01T00:00:00Z"));
        return m;
    }

    @Test
    void create_assignsAdminRole() {
        User user = makeUser(1L, "Luis");

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(groupRepository.save(any(Group.class))).thenAnswer(inv -> {
            Group g = inv.getArgument(0);
            g.setId(10L);
            g.onCreate();
            return g;
        });
        when(groupMemberRepository.save(any(GroupMember.class))).thenAnswer(inv -> inv.getArgument(0));

        GroupResponse response = groupService.create(new CreateGroupRequest("Test Group", "Desc"), 1L);

        assertThat(response.role()).isEqualTo("ADMIN");
        assertThat(response.name()).isEqualTo("Test Group");
        assertThat(response.memberCount()).isEqualTo(1);

        ArgumentCaptor<GroupMember> captor = ArgumentCaptor.forClass(GroupMember.class);
        verify(groupMemberRepository).save(captor.capture());
        assertThat(captor.getValue().getRole()).isEqualTo(GroupRole.ADMIN);
    }

    @Test
    void join_isIdempotentWhenAlreadyMember() {
        User user = makeUser(1L, "Luis");
        Group group = makeGroup(10L, "Prayer Group");
        GroupMember existing = makeMember(group, user, GroupRole.ADMIN);

        when(groupRepository.findByInviteCode("inv-10")).thenReturn(Optional.of(group));
        when(groupMemberRepository.findByGroupIdAndUserId(10L, 1L)).thenReturn(Optional.of(existing));
        when(groupMemberRepository.findByGroupId(10L)).thenReturn(List.of(existing));

        GroupResponse response = groupService.joinByInviteCode("inv-10", 1L);

        assertThat(response.role()).isEqualTo("ADMIN");
        verify(groupMemberRepository, never()).save(any(GroupMember.class));
        verify(activityService, never()).record(any(), any(), any(), anyBoolean(), anyMap());
    }

    @Test
    void join_throwsNotFoundIfInvalidInviteCode() {
        when(groupRepository.findByInviteCode("invalid")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> groupService.joinByInviteCode("invalid", 1L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void join_success_assignsMemberRoleAndRecordsEvent() {
        User user = makeUser(2L, "Ana");
        Group group = makeGroup(10L, "Prayer Group");

        when(groupRepository.findByInviteCode("inv-10")).thenReturn(Optional.of(group));
        when(groupMemberRepository.findByGroupIdAndUserId(10L, 2L)).thenReturn(Optional.empty());
        when(userRepository.findById(2L)).thenReturn(Optional.of(user));
        when(groupMemberRepository.save(any(GroupMember.class))).thenAnswer(inv -> inv.getArgument(0));
        when(groupMemberRepository.findByGroupId(10L)).thenReturn(List.of(new GroupMember()));

        GroupResponse response = groupService.joinByInviteCode("inv-10", 2L);

        assertThat(response.role()).isEqualTo("MEMBER");
        assertThat(response.name()).isEqualTo("Prayer Group");

        ArgumentCaptor<GroupMember> captor = ArgumentCaptor.forClass(GroupMember.class);
        verify(groupMemberRepository).save(captor.capture());
        assertThat(captor.getValue().getRole()).isEqualTo(GroupRole.MEMBER);
        verify(activityService).record(eq(group), eq(user), eq(ActivityEventType.MEMBER_JOINED), eq(false), anyMap());
    }

    @Test
    void listMyGroups_returnsGroupsForUser() {
        User user = makeUser(1L, "Luis");
        Group group1 = makeGroup(10L, "Group A");
        Group group2 = makeGroup(20L, "Group B");
        GroupMember m1 = makeMember(group1, user, GroupRole.ADMIN);
        GroupMember m2 = makeMember(group2, user, GroupRole.MEMBER);

        when(groupMemberRepository.findByUserId(1L)).thenReturn(List.of(m1, m2));
        when(groupMemberRepository.findByGroupId(10L)).thenReturn(List.of(m1));
        when(groupMemberRepository.findByGroupId(20L)).thenReturn(List.of(m2));

        List<GroupResponse> groups = groupService.listMyGroups(1L);

        assertThat(groups).hasSize(2);
        assertThat(groups.get(0).name()).isEqualTo("Group A");
        assertThat(groups.get(0).role()).isEqualTo("ADMIN");
        assertThat(groups.get(1).name()).isEqualTo("Group B");
        assertThat(groups.get(1).role()).isEqualTo("MEMBER");
    }

    @Test
    void regenerateInviteCode_changesCodeWhenAdmin() {
        User admin = makeUser(1L, "Luis");
        Group group = makeGroup(10L, "Prayer Group");
        String oldCode = group.getInviteCode();
        GroupMember adminMember = makeMember(group, admin, GroupRole.ADMIN);

        when(groupMemberRepository.findByGroupIdAndUserId(10L, 1L)).thenReturn(Optional.of(adminMember));
        when(groupRepository.save(any(Group.class))).thenAnswer(inv -> inv.getArgument(0));
        when(groupMemberRepository.findByGroupId(10L)).thenReturn(List.of(adminMember));

        GroupResponse response = groupService.regenerateInviteCode(10L, 1L);

        assertThat(response.inviteCode()).isNotEqualTo(oldCode);
    }

    @Test
    void regenerateInviteCode_forbiddenForMember() {
        User member = makeUser(2L, "Ana");
        Group group = makeGroup(10L, "Prayer Group");
        GroupMember regular = makeMember(group, member, GroupRole.MEMBER);

        when(groupMemberRepository.findByGroupIdAndUserId(10L, 2L)).thenReturn(Optional.of(regular));

        assertThatThrownBy(() -> groupService.regenerateInviteCode(10L, 2L))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void removeMember_deletesPendingCommitments_keepsRequestStatus() {
        User admin = makeUser(1L, "Luis");
        User target = makeUser(2L, "Ana");
        Group group = makeGroup(10L, "Prayer Group");
        GroupMember adminMember = makeMember(group, admin, GroupRole.ADMIN);
        GroupMember targetMember = makeMember(group, target, GroupRole.MEMBER);

        PrayerCommitment pending = new PrayerCommitment();

        when(groupMemberRepository.findByGroupIdAndUserId(10L, 1L)).thenReturn(Optional.of(adminMember));
        when(groupMemberRepository.findByGroupIdAndUserId(10L, 2L)).thenReturn(Optional.of(targetMember));
        when(prayerCommitmentRepository.findByUserIdAndPrayerRequestGroupIdAndFulfilledFalse(2L, 10L))
                .thenReturn(List.of(pending));

        groupService.removeMember(10L, 2L, 1L);

        // D4 ya no fuerza el estado de los pedidos: solo borra compromisos pendientes.
        verify(prayerCommitmentRepository).deleteAll(List.of(pending));
        verify(groupMemberRepository).delete(targetMember);
    }

    @Test
    void removeMember_cannotRemoveSelf() {
        User admin = makeUser(1L, "Luis");
        Group group = makeGroup(10L, "Prayer Group");
        GroupMember adminMember = makeMember(group, admin, GroupRole.ADMIN);

        when(groupMemberRepository.findByGroupIdAndUserId(10L, 1L)).thenReturn(Optional.of(adminMember));

        assertThatThrownBy(() -> groupService.removeMember(10L, 1L, 1L))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void promoteToAdmin_changesRole() {
        User admin = makeUser(1L, "Luis");
        User target = makeUser(2L, "Ana");
        Group group = makeGroup(10L, "Prayer Group");
        GroupMember adminMember = makeMember(group, admin, GroupRole.ADMIN);
        GroupMember targetMember = makeMember(group, target, GroupRole.MEMBER);

        when(groupMemberRepository.findByGroupIdAndUserId(10L, 1L)).thenReturn(Optional.of(adminMember));
        when(groupMemberRepository.findByGroupIdAndUserId(10L, 2L)).thenReturn(Optional.of(targetMember));
        when(groupMemberRepository.save(any(GroupMember.class))).thenAnswer(inv -> inv.getArgument(0));

        GroupMemberResponse response = groupService.promoteToAdmin(10L, 2L, 1L);

        assertThat(response.role()).isEqualTo("ADMIN");
        assertThat(targetMember.getRole()).isEqualTo(GroupRole.ADMIN);
    }

    @Test
    void getMembers_forbiddenForNonMembers() {
        when(groupMemberRepository.findByGroupIdAndUserId(10L, 99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> groupService.getMembers(10L, 99L))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void getMembers_exposesPhoneOnlyWhenVisibilityIsGroup() {
        Group group = makeGroup(10L, "Prayer Group");
        User me = makeUser(1L, "Luis");
        User groupVisible = makeUser(2L, "Ana");
        groupVisible.setPhone("+54 341 5550000");
        groupVisible.setPhoneVisibility(com.dosotres.user.PhoneVisibility.GROUP);
        User privatePhone = makeUser(3L, "Marcos");
        privatePhone.setPhone("+54 341 5551111");

        GroupMember meMember = makeMember(group, me, GroupRole.MEMBER);
        GroupMember groupVisibleMember = makeMember(group, groupVisible, GroupRole.MEMBER);
        GroupMember privateMember = makeMember(group, privatePhone, GroupRole.MEMBER);

        when(groupMemberRepository.findByGroupIdAndUserId(10L, 1L)).thenReturn(Optional.of(meMember));
        when(groupMemberRepository.findByGroupId(10L))
                .thenReturn(List.of(meMember, groupVisibleMember, privateMember));

        List<GroupMemberResponse> members = groupService.getMembers(10L, 1L);

        assertThat(members).filteredOn(m -> m.userId().equals(2L))
                .extracting(GroupMemberResponse::phone)
                .containsExactly("+54 341 5550000");
        assertThat(members).filteredOn(m -> m.userId().equals(3L))
                .extracting(GroupMemberResponse::phone)
                .containsExactly((String) null);
    }
}
