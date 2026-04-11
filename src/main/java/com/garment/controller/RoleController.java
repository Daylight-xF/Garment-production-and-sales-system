package com.garment.controller;

import com.garment.dto.Result;
import com.garment.model.Role;
import com.garment.service.RoleService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/roles")
public class RoleController {

    private final RoleService roleService;

    public RoleController(RoleService roleService) {
        this.roleService = roleService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('USER_READ')")
    public Result<List<Role>> getRoleList() {
        List<Role> roles = roleService.getRoleList();
        return Result.success(roles);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('USER_READ')")
    public Result<Role> getRoleById(@PathVariable String id) {
        Role role = roleService.getRoleById(id);
        return Result.success(role);
    }
}
