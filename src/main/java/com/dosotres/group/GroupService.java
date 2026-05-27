package com.dosotres.group;

import com.dosotres.common.exception.ConflictException;
import com.dosotres.common.exception.ResourceNotFoundException;
import com.dosotres.group.dto.CreateGroupRequest;
import com.dosotres.group.dto.GroupMemberResponse;
import com.dosotres.group.dto.GroupResponse;
import com.dosotres.user.User;
import com.dosotres.user.UserRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class GroupService {

    private final GroupRepository groupRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final UserRepository userRepository;

    public GroupService(GroupRepository groupRepository,
                        GroupMemberRepository groupMemberRepository,
                        UserRepository userRepository) {
        this.groupRepository = groupRepository;
        this.groupMemberRepository = groupMemberRepository;
        this.userRepository = userRepository;
    }

    public GroupResponse create(CreateGroupRequest req, Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));

        Group group = new Group();
        group.setName(req.name());
        group.setDescription(req.description());
        group.setInviteCode(UUID.randomUUID().toString());
        group.setCreatedBy(user);
        groupRepository.save(group);

        GroupMember member = new GroupMember();
        member.setGroup(group);
        member.setUser(user);
        member.setRole(GroupRole.ADMIN);
        groupMemberRepository.save(member);

        return toGroupResponse(group, 1, GroupRole.ADMIN);
    }

    @Transactional(readOnly = true)
    public List<GroupResponse> listMyGroups(Long userId) {
        List<GroupMember> memberships = groupMemberRepository.findByUserId(userId);
        return memberships.stream()
                .map(m -> {
                    int memberCount = groupMemberRepository.findByGroupId(m.getGroup().getId()).size();
                    return toGroupResponse(m.getGroup(), memberCount, m.getRole());
                })
                .toList();
    }

    public GroupResponse joinByInviteCode(String inviteCode, Long userId) {
        Group group = groupRepository.findByInviteCode(inviteCode)
                .orElseThrow(() -> new ResourceNotFoundException("Group", "inviteCode", inviteCode));

        if (groupMemberRepository.existsByGroupIdAndUserId(group.getId(), userId)) {
            throw new ConflictException("User is already a member of this group");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));

        GroupMember member = new GroupMember();
        member.setGroup(group);
        member.setUser(user);
        member.setRole(GroupRole.MEMBER);
        groupMemberRepository.save(member);

        int memberCount = groupMemberRepository.findByGroupId(group.getId()).size();
        return toGroupResponse(group, memberCount, GroupRole.MEMBER);
    }

    @Transactional(readOnly = true)
    public GroupResponse getById(Long groupId, Long userId) {
        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> new ResourceNotFoundException("Group", "id", groupId));

        GroupMember membership = groupMemberRepository.findByGroupIdAndUserId(groupId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("GroupMember", "groupId+userId", groupId + "+" + userId));

        int memberCount = groupMemberRepository.findByGroupId(groupId).size();
        return toGroupResponse(group, memberCount, membership.getRole());
    }

    @Transactional(readOnly = true)
    public List<GroupMemberResponse> getMembers(Long groupId) {
        List<GroupMember> members = groupMemberRepository.findByGroupId(groupId);
        return members.stream()
                .map(m -> new GroupMemberResponse(
                        m.getUser().getId(),
                        m.getUser().getDisplayName(),
                        m.getRole().name(),
                        m.getJoinedAt().toString()
                ))
                .toList();
    }

    private GroupResponse toGroupResponse(Group group, int memberCount, GroupRole role) {
        return new GroupResponse(
                group.getId(),
                group.getName(),
                group.getDescription(),
                group.getInviteCode(),
                memberCount,
                role.name(),
                group.getCreatedAt().toString()
        );
    }
}
