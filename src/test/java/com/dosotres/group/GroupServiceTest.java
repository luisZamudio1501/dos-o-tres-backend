package com.dosotres.group;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dosotres.common.exception.ConflictException;
import com.dosotres.common.exception.ResourceNotFoundException;
import com.dosotres.group.dto.CreateGroupRequest;
import com.dosotres.group.dto.GroupResponse;
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

    private GroupService groupService;

    @BeforeEach
    void setUp() {
        groupService = new GroupService(groupRepository, groupMemberRepository, userRepository);
    }

    @Test
    void create_assignsAdminRole() {
        User user = new User();
        user.setId(1L);
        user.setDisplayName("Luis");

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
    void join_throwsConflictIfAlreadyMember() {
        Group group = new Group();
        group.setId(10L);

        when(groupRepository.findByInviteCode("abc-123")).thenReturn(Optional.of(group));
        when(groupMemberRepository.existsByGroupIdAndUserId(10L, 1L)).thenReturn(true);

        assertThatThrownBy(() -> groupService.joinByInviteCode("abc-123", 1L))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("already a member");
    }

    @Test
    void join_throwsNotFoundIfInvalidInviteCode() {
        when(groupRepository.findByInviteCode("invalid")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> groupService.joinByInviteCode("invalid", 1L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void join_success_assignsMemberRole() {
        User user = new User();
        user.setId(2L);
        user.setDisplayName("Ana");

        Group group = new Group();
        group.setId(10L);
        group.setName("Prayer Group");
        group.setInviteCode("abc-123");
        group.onCreate();

        when(groupRepository.findByInviteCode("abc-123")).thenReturn(Optional.of(group));
        when(groupMemberRepository.existsByGroupIdAndUserId(10L, 2L)).thenReturn(false);
        when(userRepository.findById(2L)).thenReturn(Optional.of(user));
        when(groupMemberRepository.save(any(GroupMember.class))).thenAnswer(inv -> inv.getArgument(0));
        when(groupMemberRepository.findByGroupId(10L)).thenReturn(List.of(new GroupMember()));

        GroupResponse response = groupService.joinByInviteCode("abc-123", 2L);

        assertThat(response.role()).isEqualTo("MEMBER");
        assertThat(response.name()).isEqualTo("Prayer Group");

        ArgumentCaptor<GroupMember> captor = ArgumentCaptor.forClass(GroupMember.class);
        verify(groupMemberRepository).save(captor.capture());
        assertThat(captor.getValue().getRole()).isEqualTo(GroupRole.MEMBER);
    }

    @Test
    void listMyGroups_returnsGroupsForUser() {
        User user = new User();
        user.setId(1L);
        user.setDisplayName("Luis");

        Group group1 = new Group();
        group1.setId(10L);
        group1.setName("Group A");
        group1.setInviteCode("inv-1");
        group1.onCreate();

        Group group2 = new Group();
        group2.setId(20L);
        group2.setName("Group B");
        group2.setInviteCode("inv-2");
        group2.onCreate();

        GroupMember m1 = new GroupMember();
        m1.setGroup(group1);
        m1.setUser(user);
        m1.setRole(GroupRole.ADMIN);

        GroupMember m2 = new GroupMember();
        m2.setGroup(group2);
        m2.setUser(user);
        m2.setRole(GroupRole.MEMBER);

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
}
