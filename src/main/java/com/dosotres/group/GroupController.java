package com.dosotres.group;

import com.dosotres.group.dto.CreateGroupRequest;
import com.dosotres.group.dto.GroupMemberResponse;
import com.dosotres.group.dto.GroupResponse;
import com.dosotres.group.dto.JoinGroupRequest;
import com.dosotres.security.annotations.AuthUser;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/groups")
public class GroupController {

    private final GroupService groupService;

    public GroupController(GroupService groupService) {
        this.groupService = groupService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public GroupResponse create(@Valid @RequestBody CreateGroupRequest req,
                                @AuthUser Long userId) {
        return groupService.create(req, userId);
    }

    @GetMapping
    public List<GroupResponse> listMyGroups(@AuthUser Long userId) {
        return groupService.listMyGroups(userId);
    }

    @PostMapping("/join")
    public GroupResponse join(@Valid @RequestBody JoinGroupRequest req,
                              @AuthUser Long userId) {
        return groupService.joinByInviteCode(req.inviteCode(), userId);
    }

    @GetMapping("/{id}")
    public GroupResponse getById(@PathVariable Long id,
                                 @AuthUser Long userId) {
        return groupService.getById(id, userId);
    }

    @GetMapping("/{id}/members")
    public List<GroupMemberResponse> getMembers(@PathVariable Long id,
                                                @AuthUser Long userId) {
        return groupService.getMembers(id, userId);
    }

    @PostMapping("/{id}/invite-code")
    public GroupResponse regenerateInviteCode(@PathVariable Long id,
                                              @AuthUser Long userId) {
        return groupService.regenerateInviteCode(id, userId);
    }

    @DeleteMapping("/{id}/members/{memberUserId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeMember(@PathVariable Long id,
                             @PathVariable Long memberUserId,
                             @AuthUser Long userId) {
        groupService.removeMember(id, memberUserId, userId);
    }

    @PatchMapping("/{id}/members/{memberUserId}/promote")
    public GroupMemberResponse promoteToAdmin(@PathVariable Long id,
                                              @PathVariable Long memberUserId,
                                              @AuthUser Long userId) {
        return groupService.promoteToAdmin(id, memberUserId, userId);
    }
}
