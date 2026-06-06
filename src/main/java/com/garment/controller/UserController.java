package com.garment.controller;

import com.garment.dto.ChangePasswordRequest;
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
import java.util.List;
import java.util.Map;

/**
 * 用户管理控制器。
 *
 * <p>提供用户列表查询、用户详情、创建、更新、删除、角色分配、状态更新和密码修改等接口。</p>
 */
@RestController
@RequestMapping("/api/users")
public class UserController {
    // 用户业务服务，负责用户资料、角色、状态和密码等业务处理。

    private final UserService userService;
    /**
     * 创建用户管理控制器。
     *
     * <p>通过构造器注入用户业务服务。</p>
     *
     * @param userService 用户业务服务
     */

    public UserController(UserService userService) {
        this.userService = userService;
    }


        /**
         * 查询用户列表，支持分页和多条件筛选
         * <p>
         * 接收接口请求参数，调用对应业务服务完成处理，并返回统一响应结果。
         * </p>
         *
         * @param keyword 搜索关键词，支持用户名或真实姓名模糊查询
         * @param role 角色代码
         * @param status 用户状态
         * @param page 页码，从1开始
         * @param size 每页大小
         * @return 统一响应结果，包含用户列表、总数、页码和每页大小
         */
        @GetMapping
        @PreAuthorize("hasAuthority('USER_READ')")
        public Result<Map<String, Object>> getUserList(
                @RequestParam(defaultValue = "") String keyword,
                @RequestParam(required = false) String role,
                @RequestParam(required = false) Integer status,
                @RequestParam(defaultValue = "1") int page,
                @RequestParam(defaultValue = "10") int size) {
            // 构建分页和排序参数。
            Pageable pageable = PageRequest.of(page - 1, size, Sort.by(Sort.Direction.DESC, "createTime"));
            // 查询分页数据。
            Page<UserVO> userPage = userService.getUserList(keyword, role, status, pageable);

            // 组装分页返回结构。
            Map<String, Object> result = new HashMap<>();
            result.put("list", userPage.getContent());
            result.put("total", userPage.getTotalElements());
            result.put("page", page);
            result.put("size", size);

            // 返回分页查询结果。
            return Result.success(result);
        }


        /**
         * 获取可分配的用户列表
         * <p>
         * 接收接口请求参数，调用对应业务服务完成处理，并返回统一响应结果。
         * </p>
         *
         * @return 统一响应结果，包含可分配的用户列表
         */
        @GetMapping("/assignable")
        public Result<List<UserVO>> getAssignableUsers() {
            // 调用业务服务处理请求。
            List<UserVO> users = userService.getAssignableUsers();
            // 返回统一成功响应。
            return Result.success(users);
        }


        /**
         * 获取当前登录用户信息
         * <p>
         * 接收接口请求参数，调用对应业务服务完成处理，并返回统一响应结果。
         * </p>
         *
         * @param authentication 认证信息，用于获取用户ID
         * @return 统一响应结果，包含当前用户详细信息
         */
        @GetMapping("/info")
        public Result<UserVO> getCurrentUser(Authentication authentication) {
            // 从认证上下文中获取当前用户 ID。
            String userId = (String) authentication.getPrincipal();
            // 调用业务服务处理请求。
            UserVO userVO = userService.getCurrentUser(userId);
            // 返回统一成功响应。
            return Result.success(userVO);
        }


        /**
         * 根据ID查询用户详情
         * <p>
         * 接收接口请求参数，调用对应业务服务完成处理，并返回统一响应结果。
         * </p>
         *
         * @param id 用户ID
         * @return 统一响应结果，包含用户详细信息
         */
        @GetMapping("/{id}")
        @PreAuthorize("hasAuthority('USER_READ')")
        public Result<UserVO> getUserById(@PathVariable String id) {
            // 调用业务服务处理请求。
            UserVO userVO = userService.getUserById(id);
            // 返回统一成功响应。
            return Result.success(userVO);
        }


        /**
         * 创建新用户
         * <p>
         * 接收接口请求参数，调用对应业务服务完成处理，并返回统一响应结果。
         * </p>
         *
         * @param request 用户创建请求参数
         * @return 统一响应结果，包含创建后的用户信息
         */
        @PostMapping
        @PreAuthorize("hasAuthority('USER_CREATE')")
        public Result<UserVO> createUser(@Valid @RequestBody UserCreateRequest request) {
            // 调用业务服务处理请求。
            UserVO userVO = userService.createUser(request);
            // 返回统一成功响应。
            return Result.success(userVO);
        }


        /**
         * 更新用户信息
         * <p>
         * 接收接口请求参数，调用对应业务服务完成处理，并返回统一响应结果。
         * </p>
         *
         * @param id 用户ID
         * @param request 用户更新请求参数
         * @return 统一响应结果，包含更新后的用户信息
         */
        @PutMapping("/{id}")
        @PreAuthorize("hasAuthority('USER_UPDATE')")
        public Result<UserVO> updateUser(@PathVariable String id, @RequestBody UserUpdateRequest request) {
            // 调用业务服务处理请求。
            UserVO userVO = userService.updateUser(id, request);
            // 返回统一成功响应。
            return Result.success(userVO);
        }


        /**
         * 删除用户
         * <p>
         * 接收接口请求参数，调用对应业务服务完成处理，并返回统一响应结果。
         * </p>
         *
         * @param id 用户ID
         * @return 统一响应结果
         */
        @DeleteMapping("/{id}")
        @PreAuthorize("hasAuthority('USER_DELETE')")
        public Result<Void> deleteUser(@PathVariable String id) {
            // 调用业务服务删除记录。
            userService.deleteUser(id);
            // 返回统一成功响应。
            return Result.success();
        }


        /**
         * 为用户分配角色
         * <p>
         * 接收接口请求参数，调用对应业务服务完成处理，并返回统一响应结果。
         * </p>
         *
         * @param id 用户ID
         * @param request 角色分配请求参数，包含角色ID列表
         * @return 统一响应结果，包含分配角色后的用户信息
         */
        @PutMapping("/{id}/roles")
        @PreAuthorize("hasAuthority('ROLE_ASSIGN')")
        public Result<UserVO> assignRoles(@PathVariable String id, @Valid @RequestBody RoleAssignRequest request) {
            // 调用业务服务处理请求。
            UserVO userVO = userService.assignRoles(id, request);
            // 返回统一成功响应。
            return Result.success(userVO);
        }


        /**
         * 更新用户状态
         * <p>
         * 接收接口请求参数，调用对应业务服务完成处理，并返回统一响应结果。
         * </p>
         *
         * @param id 用户ID
         * @param body 请求体，包含用户状态值
         * @return 统一响应结果，包含更新状态后的用户信息
         */
        @PutMapping("/{id}/status")
        @PreAuthorize("hasAuthority('USER_UPDATE')")
        public Result<UserVO> updateUserStatus(@PathVariable String id, @RequestBody Map<String, Integer> body) {
            // 从请求体中读取业务参数。
            Integer status = body.get("status");
            // 调用业务服务处理请求。
            UserVO userVO = userService.updateUserStatus(id, status);
            // 返回统一成功响应。
            return Result.success(userVO);
        }


        /**
         * 修改用户密码
         * <p>
         * 接收接口请求参数，调用对应业务服务完成处理，并返回统一响应结果。
         * </p>
         *
         * @param authentication 认证信息，用于获取当前用户ID
         * @param request 密码修改请求参数，包含旧密码和新密码
         * @return 统一响应结果
         */
        @PutMapping("/password")
        public Result<Void> changePassword(Authentication authentication, @Valid @RequestBody ChangePasswordRequest request) {
            // 从认证上下文中获取当前用户 ID。
            String userId = (String) authentication.getPrincipal();
            userService.changePassword(userId, request);
            // 返回统一成功响应。
            return Result.success();
        }

    
}
