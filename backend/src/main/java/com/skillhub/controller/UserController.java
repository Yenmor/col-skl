package com.skillhub.controller;

import com.skillhub.config.BaseController;
import com.skillhub.dto.PatchUserRequest;
import com.skillhub.dto.UserDto;
import com.skillhub.model.User;
import com.skillhub.service.UserService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/users")
public class UserController extends BaseController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/me")
    public UserDto me(@RequestHeader("X-User-Id") String userId) {
        User u = userService.getOrCreate(userId);
        return toDto(u);
    }

    @PatchMapping("/me")
    public UserDto patchMe(@RequestHeader("X-User-Id") String userId,
                           @RequestBody PatchUserRequest body) {
        User u = userService.patch(userId, body.displayName(), body.avatarUrl());
        return toDto(u);
    }

    private UserDto toDto(User u) {
        return new UserDto(u.id(), u.displayName(), u.avatarUrl(), u.role(), u.createdAt());
    }
}