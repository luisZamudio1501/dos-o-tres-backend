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
}
