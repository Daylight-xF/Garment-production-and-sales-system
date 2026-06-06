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

/**
 * 角色管理控制器。
 *
 * <p>提供角色列表和角色详情查询接口，供用户管理与权限分配场景使用。</p>
 */
@RestController
@RequestMapping("/api/roles")
public class RoleController {
    // 角色业务服务，负责角色列表和角色详情查询。

    private final RoleService roleService;
    /**
     * 创建角色管理控制器。
     *
     * <p>通过构造器注入角色业务服务。</p>
     *
     * @param roleService 角色业务服务
     */

    public RoleController(RoleService roleService) {
        this.roleService = roleService;
    }


        /**
         * 获取角色列表
         * <p>
         * 接收接口请求参数，调用对应业务服务完成处理，并返回统一响应结果。
         * </p>
         *
         * @return 统一响应结果，包含所有角色列表
         */
        @GetMapping
        @PreAuthorize("hasAuthority('USER_READ')")
        public Result<List<Role>> getRoleList() {
            // 调用业务服务处理请求。
            List<Role> roles = roleService.getRoleList();
            // 返回统一成功响应。
            return Result.success(roles);
        }


        /**
         * 根据ID查询角色详情
         * <p>
         * 接收接口请求参数，调用对应业务服务完成处理，并返回统一响应结果。
         * </p>
         *
         * @param id 角色ID
         * @return 统一响应结果，包含角色详细信息
         */
        @GetMapping("/{id}")
        @PreAuthorize("hasAuthority('USER_READ')")
        public Result<Role> getRoleById(@PathVariable String id) {
            // 调用业务服务处理请求。
            Role role = roleService.getRoleById(id);
            // 返回统一成功响应。
            return Result.success(role);
        }

    
}
