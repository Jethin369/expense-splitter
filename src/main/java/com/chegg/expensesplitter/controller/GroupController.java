package com.chegg.expensesplitter.controller;

import com.chegg.expensesplitter.dto.CreateGroupRequest;
import com.chegg.expensesplitter.dto.GroupResponse;
import com.chegg.expensesplitter.model.Group;
import com.chegg.expensesplitter.service.GroupService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/groups")
public class GroupController {

    private final GroupService groupService;

    public GroupController(GroupService groupService) {
        this.groupService = groupService;
    }

    @PostMapping
    public ResponseEntity<GroupResponse> createGroup(@Valid @RequestBody CreateGroupRequest request) {
        Group group = groupService.createGroup(request.getName(), request.getMembers());
        return ResponseEntity.status(HttpStatus.CREATED).body(GroupResponse.fromEntity(group));
    }

    @GetMapping
    public ResponseEntity<List<GroupResponse>> listGroups() {
        List<GroupResponse> groups = groupService.getAllGroups().stream()
                .map(GroupResponse::fromEntity)
                .collect(Collectors.toList());
        return ResponseEntity.ok(groups);
    }

    @GetMapping("/{groupId}")
    public ResponseEntity<GroupResponse> getGroup(@PathVariable Long groupId) {
        Group group = groupService.getGroupOrThrow(groupId);
        return ResponseEntity.ok(GroupResponse.fromEntity(group));
    }
}
