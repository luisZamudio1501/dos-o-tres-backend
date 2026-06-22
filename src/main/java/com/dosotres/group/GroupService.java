package com.dosotres.group;

import com.dosotres.activity.ActivityEventType;
import com.dosotres.activity.ActivityService;
import com.dosotres.common.exception.ForbiddenException;
import com.dosotres.common.exception.ResourceNotFoundException;
import com.dosotres.common.exception.ValidationException;
import com.dosotres.group.dto.CreateGroupRequest;
import com.dosotres.group.dto.DeleteGroupRequest;
import com.dosotres.group.dto.GroupMemberResponse;
import com.dosotres.group.dto.GroupResponse;
import com.dosotres.group.dto.UpdateGroupRequest;
import com.dosotres.prayer.PrayerCommitment;
import com.dosotres.prayer.PrayerCommitmentRepository;
import com.dosotres.user.PhoneVisibility;
import com.dosotres.user.User;
import com.dosotres.user.UserRepository;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class GroupService {

    private final GroupRepository groupRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final UserRepository userRepository;
    private final PrayerCommitmentRepository prayerCommitmentRepository;
    private final ActivityService activityService;

    public GroupService(GroupRepository groupRepository,
                        GroupMemberRepository groupMemberRepository,
                        UserRepository userRepository,
                        PrayerCommitmentRepository prayerCommitmentRepository,
                        ActivityService activityService) {
        this.groupRepository = groupRepository;
        this.groupMemberRepository = groupMemberRepository;
        this.userRepository = userRepository;
        this.prayerCommitmentRepository = prayerCommitmentRepository;
        this.activityService = activityService;
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

    /**
     * Unión por código: idempotente (regla D2 — fricción mínima). Si el usuario
     * ya es miembro, devuelve el grupo en lugar de fallar.
     */
    public GroupResponse joinByInviteCode(String inviteCode, Long userId) {
        Group group = groupRepository.findByInviteCode(inviteCode)
                .orElseThrow(() -> new ResourceNotFoundException("Group", "inviteCode", inviteCode));

        GroupRole role = groupMemberRepository.findByGroupIdAndUserId(group.getId(), userId)
                .map(GroupMember::getRole)
                .orElse(null);

        if (role == null) {
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));

            GroupMember member = new GroupMember();
            member.setGroup(group);
            member.setUser(user);
            member.setRole(GroupRole.MEMBER);
            groupMemberRepository.save(member);
            role = GroupRole.MEMBER;

            activityService.record(group, user, ActivityEventType.MEMBER_JOINED, false, Map.of());
        }

        int memberCount = groupMemberRepository.findByGroupId(group.getId()).size();
        return toGroupResponse(group, memberCount, role);
    }

    /**
     * Alta directa sin invite-code (Fase 7: crear grupo desde una conversación
     * ya aceptada). Idempotente igual que {@link #joinByInviteCode}.
     */
    public GroupResponse addMemberDirect(Long groupId, Long userId) {
        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> new ResourceNotFoundException("Group", "id", groupId));
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));

        GroupRole role = groupMemberRepository.findByGroupIdAndUserId(groupId, userId)
                .map(GroupMember::getRole)
                .orElse(null);

        if (role == null) {
            GroupMember member = new GroupMember();
            member.setGroup(group);
            member.setUser(user);
            member.setRole(GroupRole.MEMBER);
            groupMemberRepository.save(member);
            role = GroupRole.MEMBER;

            activityService.record(group, user, ActivityEventType.MEMBER_JOINED, false, Map.of());
        }

        int memberCount = groupMemberRepository.findByGroupId(groupId).size();
        return toGroupResponse(group, memberCount, role);
    }

    /** Regenera el token de invitación (regla D3 — blindar ante filtraciones). Solo admin. */
    public GroupResponse regenerateInviteCode(Long groupId, Long actingUserId) {
        GroupMember acting = requireAdmin(groupId, actingUserId);
        Group group = acting.getGroup();
        group.setInviteCode(UUID.randomUUID().toString());
        groupRepository.save(group);

        int memberCount = groupMemberRepository.findByGroupId(groupId).size();
        return toGroupResponse(group, memberCount, acting.getRole());
    }

    /**
     * Expulsión de miembro (regla D4): hard delete de sus compromisos no cumplidos.
     * Los pedidos del miembro quedan en el estado que tengan (la oración rige el
     * estado, no la expulsión); el historial de cumplimientos queda intacto.
     */
    public void removeMember(Long groupId, Long targetUserId, Long actingUserId) {
        requireAdmin(groupId, actingUserId);

        if (targetUserId.equals(actingUserId)) {
            throw new ValidationException("No podés quitarte a vos mismo del grupo");
        }

        GroupMember target = groupMemberRepository.findByGroupIdAndUserId(groupId, targetUserId)
                .orElseThrow(() -> new ResourceNotFoundException("GroupMember", "groupId+userId",
                        groupId + "+" + targetUserId));

        cleanupMemberData(groupId, targetUserId);
        groupMemberRepository.delete(target);
    }

    /**
     * Salir del grupo (autogestionado). Aplica la misma limpieza D4 que removeMember.
     * Un admin único con otros miembros no puede irse sin promover a otro antes
     * (evita grupos sin admin). Si es el último miembro, el grupo queda vacío
     * y solo puede eliminarse explícitamente con deleteGroup.
     */
    public void leave(Long groupId, Long userId) {
        GroupMember member = groupMemberRepository.findByGroupIdAndUserId(groupId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("GroupMember", "groupId+userId",
                        groupId + "+" + userId));

        List<GroupMember> allMembers = groupMemberRepository.findByGroupId(groupId);
        boolean otherAdmins = allMembers.stream()
                .anyMatch(m -> !m.getUser().getId().equals(userId) && m.getRole() == GroupRole.ADMIN);

        if (member.getRole() == GroupRole.ADMIN && !otherAdmins && allMembers.size() > 1) {
            throw new ValidationException("Promové a otro miembro como admin antes de salir del grupo");
        }

        cleanupMemberData(groupId, userId);
        groupMemberRepository.delete(member);
    }

    /** Limpieza D4 de los datos de un usuario dentro de un grupo. */
    private void cleanupMemberData(Long groupId, Long userId) {
        List<PrayerCommitment> pendingCommitments = prayerCommitmentRepository
                .findByUserIdAndPrayerRequestGroupIdAndFulfilledFalse(userId, groupId);
        prayerCommitmentRepository.deleteAll(pendingCommitments);
    }

    /** Personalización del grupo (nombre/color/emoji). Solo admin. */
    public GroupResponse update(Long groupId, UpdateGroupRequest req, Long actingUserId) {
        GroupMember acting = requireAdmin(groupId, actingUserId);
        Group group = acting.getGroup();

        if (req.name() != null && !req.name().isBlank()) {
            group.setName(req.name());
        }
        if (req.color() != null) {
            group.setColor(req.color());
        }
        if (req.iconEmoji() != null) {
            group.setIconEmoji(req.iconEmoji().isBlank() ? null : req.iconEmoji());
        }
        groupRepository.save(group);

        int memberCount = groupMemberRepository.findByGroupId(groupId).size();
        return toGroupResponse(group, memberCount, acting.getRole());
    }

    /**
     * Elimina el grupo y todos sus datos (cascada vía V9). Acción destructiva
     * irreversible: requiere que el admin tipee el nombre exacto del grupo.
     */
    public void deleteGroup(Long groupId, DeleteGroupRequest req, Long actingUserId) {
        GroupMember acting = requireAdmin(groupId, actingUserId);
        Group group = acting.getGroup();

        if (!group.getName().equals(req.name())) {
            throw new ValidationException("El nombre no coincide con el del grupo");
        }

        groupRepository.delete(group);
    }

    /** Nombra admin a un miembro existente. Solo admin. */
    public GroupMemberResponse promoteToAdmin(Long groupId, Long targetUserId, Long actingUserId) {
        requireAdmin(groupId, actingUserId);

        GroupMember target = groupMemberRepository.findByGroupIdAndUserId(groupId, targetUserId)
                .orElseThrow(() -> new ResourceNotFoundException("GroupMember", "groupId+userId",
                        groupId + "+" + targetUserId));

        target.setRole(GroupRole.ADMIN);
        groupMemberRepository.save(target);

        return toMemberResponse(target);
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
    public List<GroupMemberResponse> getMembers(Long groupId, Long userId) {
        groupMemberRepository.findByGroupIdAndUserId(groupId, userId)
                .orElseThrow(() -> new ForbiddenException("Only group members can see the member list"));

        List<GroupMember> members = groupMemberRepository.findByGroupId(groupId);
        return members.stream()
                .map(this::toMemberResponse)
                .toList();
    }

    /**
     * Ciudad/país del perfil (S5): visibles solo aquí, dentro de un grupo del
     * que el solicitante ya es miembro (regla de privacidad — dato sensible).
     * Teléfono (M.1): solo si el dueño eligió visibilidad GROUP.
     */
    private GroupMemberResponse toMemberResponse(GroupMember m) {
        User user = m.getUser();
        String phone = user.getPhoneVisibility() == PhoneVisibility.GROUP ? user.getPhone() : null;
        return new GroupMemberResponse(
                user.getId(),
                user.getDisplayName(),
                m.getRole().name(),
                m.getJoinedAt().toString(),
                user.getCity(),
                user.getCountry(),
                phone
        );
    }

    private GroupMember requireAdmin(Long groupId, Long userId) {
        GroupMember member = groupMemberRepository.findByGroupIdAndUserId(groupId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("GroupMember", "groupId+userId",
                        groupId + "+" + userId));
        if (member.getRole() != GroupRole.ADMIN) {
            throw new ForbiddenException("Only a group admin can perform this action");
        }
        return member;
    }

    private GroupResponse toGroupResponse(Group group, int memberCount, GroupRole role) {
        return new GroupResponse(
                group.getId(),
                group.getName(),
                group.getDescription(),
                group.getColor(),
                group.getIconEmoji(),
                group.getInviteCode(),
                memberCount,
                role.name(),
                group.getCreatedAt().toString()
        );
    }
}
