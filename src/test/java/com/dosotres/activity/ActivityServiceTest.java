package com.dosotres.activity;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dosotres.common.exception.ForbiddenException;
import com.dosotres.common.exception.ResourceNotFoundException;
import com.dosotres.group.Group;
import com.dosotres.group.GroupMember;
import com.dosotres.group.GroupMemberRepository;
import com.dosotres.group.GroupRole;
import com.dosotres.user.User;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ActivityServiceTest {

    @Mock
    private ActivityEventRepository activityEventRepository;
    @Mock
    private GroupMemberRepository groupMemberRepository;

    private ActivityService service;

    @BeforeEach
    void setUp() {
        service = new ActivityService(activityEventRepository, groupMemberRepository, new ObjectMapper());
    }

    private User makeUser(Long id) {
        User u = new User();
        u.setId(id);
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

    private ActivityEvent makeEvent(Long id, Group group, User actor) {
        ActivityEvent e = new ActivityEvent();
        e.setId(id);
        e.setGroup(group);
        e.setActor(actor);
        e.setType(ActivityEventType.COMMITMENT_FULFILLED);
        return e;
    }

    @Test
    void delete_byAdminWhoIsNotAuthor_deletesEvent() {
        Group group = makeGroup(10L);
        User author = makeUser(1L);
        User admin = makeUser(3L);
        ActivityEvent event = makeEvent(100L, group, author);

        when(activityEventRepository.findById(100L)).thenReturn(Optional.of(event));
        when(groupMemberRepository.findByGroupIdAndUserId(10L, 3L))
                .thenReturn(Optional.of(makeMember(group, admin, GroupRole.ADMIN)));

        service.delete(100L, 10L, 3L);

        verify(activityEventRepository).delete(event);
    }

    @Test
    void delete_byAuthor_deletesEvent() {
        Group group = makeGroup(10L);
        User author = makeUser(1L);
        ActivityEvent event = makeEvent(100L, group, author);

        when(activityEventRepository.findById(100L)).thenReturn(Optional.of(event));

        service.delete(100L, 10L, 1L);

        verify(activityEventRepository).delete(event);
    }

    @Test
    void delete_byOtherMember_throwsForbidden() {
        Group group = makeGroup(10L);
        User author = makeUser(1L);
        User stranger = makeUser(2L);
        ActivityEvent event = makeEvent(100L, group, author);

        when(activityEventRepository.findById(100L)).thenReturn(Optional.of(event));
        when(groupMemberRepository.findByGroupIdAndUserId(10L, 2L))
                .thenReturn(Optional.of(makeMember(group, stranger, GroupRole.MEMBER)));

        assertThatThrownBy(() -> service.delete(100L, 10L, 2L))
                .isInstanceOf(ForbiddenException.class);

        verify(activityEventRepository, never()).delete(any(ActivityEvent.class));
    }

    @Test
    void delete_eventFromAnotherGroup_throwsNotFound() {
        Group group = makeGroup(10L);
        User author = makeUser(1L);
        ActivityEvent event = makeEvent(100L, group, author);

        when(activityEventRepository.findById(100L)).thenReturn(Optional.of(event));

        assertThatThrownBy(() -> service.delete(100L, 11L, 1L))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(activityEventRepository, never()).delete(any(ActivityEvent.class));
    }

    @Test
    void delete_eventNotFound_throwsNotFound() {
        when(activityEventRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(999L, 10L, 1L))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(activityEventRepository, never()).delete(any(ActivityEvent.class));
    }
}
