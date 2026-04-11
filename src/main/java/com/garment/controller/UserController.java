package com.garment.controller;

import com.garment.dto.RoleAssignRequest;
import com.garment.dto.Result;
import com.garment.dto.UserCreateRequest;
import com.garment.dto.UserUpdateRequest;
import com.garment.dto.UserVO;
import com.garment.service.UserService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('USER_READ')")
    public Result<Map<String, Object>> getUserList(
            @RequestParam(defaultValue = "") String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        Pageable pageable = PageRequest.of(page - 1, size, Sort.by(Sort.Direction.DESC, "createTime"));
        Page<UserVO> userPage = userService.getUserList(keyword, pageable);

        Map<String, Object> result = new HashMap<>();
        result.put("list", userPage.getContent());
        result.put("total", userPage.getTotalElements());
        result.put("page", page);
        result.put("size", size);

        return Result.success(result);
    }

    @GetMapping("/info")
    public Result<UserVO> getCurrentUser(Authentication authentication) {
        String userId = (String) authentication.getPrincipal();
        UserVO userVO = userService.getCurrentUser(userId);
        return Result.success(userVO);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('USER_READ')")
    public Result<UserVO> getUserById(@PathVariable String id) {
        UserVO userVO = userService.getUserById(id);
        return Result.success(userVO);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('USER_CREATE')")
    public Result<UserVO> createUser(@Valid @RequestBody UserCreateRequest request) {
        UserVO userVO = userService.createUser(request);
        return Result.success(userVO);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('USER_UPDATE')")
    public Result<UserVO> updateUser(@PathVariable String id, @RequestBody UserUpdateRequest request) {
        UserVO userVO = userService.updateUser(id, request);
        return Result.success(userVO);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('USER_DELETE')")
    public Result<Void> deleteUser(@PathVariable String id) {
        userService.deleteUser(id);
        return Result.success();
    }

    @PutMapping("/{id}/roles")
    @PreAuthorize("hasAuthority('ROLE_ASSIGN')")
    public Result<UserVO> assignRoles(@PathVariable String id, @Valid @RequestBody RoleAssignRequest request) {
        UserVO userVO = userService.assignRoles(id, request);
        return Result.success(userVO);
    }

    @PutMapping("/{id}/status")
    @PreAuthorize("hasAuthority('USER_UPDATE')")
    public Result<UserVO> updateUserStatus(@PathVariable String id, @RequestBody Map<String, Integer> body) {
        Integer status = body.get("status");
        UserVO userVO = userService.updateUserStatus(id, status);
        return Result.success(userVO);
    }
}
