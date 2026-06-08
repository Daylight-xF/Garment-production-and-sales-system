package com.garment.service.impl;

import com.garment.dto.ChangePasswordRequest;
import com.garment.dto.RoleAssignRequest;
import com.garment.dto.UserCreateRequest;
import com.garment.dto.UserUpdateRequest;
import com.garment.dto.UserVO;
import com.garment.exception.BusinessException;
import com.garment.model.Role;
import com.garment.model.User;
import com.garment.repository.RoleRepository;
import com.garment.repository.UserRepository;
import com.garment.service.UserService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    public UserServiceImpl(UserRepository userRepository,
                            RoleRepository roleRepository,
                            PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * 分页查询用户列表
     * <p>
     * 该方法支持根据关键词、角色和状态等多个条件筛选用户，并返回分页结果。
     * 筛选逻辑：
     * - 关键词：匹配用户名、真实姓名或手机号
     * - 角色：匹配用户的角色代码
     * - 状态：精确匹配用户状态（0-禁用，1-启用）
     * </p>
     *
     * @param keyword 搜索关键词，用于匹配用户名、真实姓名或手机号
     * @param role 角色代码，用于筛选特定角色的用户
     * @param status 用户状态，0表示禁用，1表示启用，null表示不限制
     * @param pageable 分页参数，包含页码、每页大小和排序信息
     * @return 分页的用户视图对象列表
     */
    @Override
    public Page<UserVO> getUserList(String keyword, String role, Integer status, Pageable pageable) {
        List<User> allUsers = userRepository.findAll();
        
        // 根据关键词、角色和状态过滤用户
        List<User> filtered = allUsers.stream().filter(user -> {
            // 关键词匹配：用户名、真实姓名或手机号
            if (StringUtils.hasText(keyword)) {
                boolean matchKeyword = (user.getUsername() != null && user.getUsername().contains(keyword))
                        || (user.getRealName() != null && user.getRealName().contains(keyword))
                        || (user.getPhone() != null && user.getPhone().contains(keyword));
                if (!matchKeyword) return false;
            }
            
            // 角色匹配：检查用户是否具有指定角色
            if (StringUtils.hasText(role)) {
                boolean[] hasRole = {false};
                if (user.getRoles() != null) {
                    for (String roleId : user.getRoles()) {
                        roleRepository.findById(roleId).ifPresent(r -> {
                            if (r.getCode().equals(role)) hasRole[0] = true;
                        });
                    }
                }
                if (!hasRole[0]) return false;
            }
            
            // 状态匹配：精确匹配用户状态
            if (status != null) {
                if (!status.equals(user.getStatus())) return false;
            }
            return true;
        }).collect(Collectors.toList());

        // 计算分页范围并提取当前页数据
        int start = (int) pageable.getOffset();
        int end = Math.min(start + pageable.getPageSize(), filtered.size());
        List<User> pageContent = start < filtered.size() ? filtered.subList(start, end) : new ArrayList<>();

        // 转换为视图对象并返回分页结果
        List<UserVO> voList = pageContent.stream()
                .map(this::convertToVO)
                .collect(Collectors.toList());

        return new PageImpl<>(voList, pageable, filtered.size());
    }

    /**
     * 获取可分配任务的用户列表
     * <p>
     * 该方法用于获取所有具有生产经理角色且状态为启用的用户，通常用于任务分配时的人员选择。
     * 筛选条件：
     * 1. 用户状态必须为启用（status = 1）
     * 2. 用户必须具有production_manager角色
     * </p>
     *
     * @return 可分配的用户视图对象列表
     */
    @Override
    public List<UserVO> getAssignableUsers() {
        List<User> allUsers = userRepository.findAll();
        
        // 筛选出启用状态且具有生产经理角色的用户
        return allUsers.stream()
                .filter(user -> user.getStatus() != null && user.getStatus() == 1)
                .filter(user -> hasProductionRole(user))
                .map(this::convertToVO)
                .collect(Collectors.toList());
    }

    /**
     * 判断用户是否具有生产经理角色
     * <p>
     * 该方法检查用户的角色列表中是否包含production_manager角色。
     * </p>
     *
     * @param user 需要检查的用户对象
     * @return true表示用户具有生产经理角色，false表示不具有
     */
    private boolean hasProductionRole(User user) {
        if (user.getRoles() == null || user.getRoles().isEmpty()) {
            return false;
        }
        
        // 遍历用户的所有角色，检查是否存在production_manager角色
        for (String roleId : user.getRoles()) {
            Role role = roleRepository.findById(roleId).orElse(null);
            if (role != null && "production_manager".equals(role.getCode())) {
                return true;
            }
        }
        return false;
    }

    /**
     * 根据ID查询用户详情
     * <p>
     * 该方法从数据库中获取指定ID的用户信息，并将其转换为视图对象返回。
     * 如果用户不存在，则抛出业务异常。
     * </p>
     *
     * @param id 用户的唯一标识符
     * @return 用户视图对象，包含用户的详细信息
     */
    @Override
    public UserVO getUserById(String id) {
        // 从数据库查询用户，不存在时抛出异常
        User user = userRepository.findById(id)
                .orElseThrow(() -> new BusinessException("用户不存在"));
        
        // 转换为视图对象并返回
        return convertToVO(user);
    }

    /**
     * 创建新用户
     * <p>
     * 该方法用于创建新的用户账户，主要功能包括：
     * 1. 校验用户名是否已存在
     * 2. 对密码进行加密处理
     * 3. 如果没有指定角色，则分配默认的inactive角色
     * 4. 设置用户状态为启用（status = 1）
     * 5. 保存用户到数据库
     * </p>
     *
     * @param request 用户创建请求对象，包含用户名、密码、真实姓名、联系方式等信息
     * @return 创建后的用户视图对象
     */
    @Override
    public UserVO createUser(UserCreateRequest request) {
        // 校验用户名是否已存在
        if (userRepository.findByUsername(request.getUsername()).isPresent()) {
            throw new BusinessException("用户名已存在");
        }

        // 处理用户角色，未指定时分配默认的inactive角色
        List<String> roleIds = request.getRoles();
        if (roleIds == null || roleIds.isEmpty()) {
            Role inactiveRole = roleRepository.findByCode("inactive")
                    .orElseThrow(() -> new BusinessException("默认角色不存在，请联系管理员"));
            roleIds = Collections.singletonList(inactiveRole.getId());
        }

        // 创建用户对象并初始化基本信息
        User user = new User();
        user.setUsername(request.getUsername());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setRealName(request.getRealName());
        user.setPhone(request.getPhone());
        user.setEmail(request.getEmail());
        user.setRoles(roleIds);
        user.setStatus(1);

        // 保存用户到数据库，处理可能的重复键异常
        User saved;
        try {
            saved = userRepository.save(user);
        } catch (DuplicateKeyException ex) {
            throw new BusinessException("用户名已存在");
        }
        
        // 转换为视图对象并返回
        return convertToVO(saved);
    }

    /**
     * 更新用户信息
     * <p>
     * 该方法支持更新用户的多个字段，包括真实姓名、电话、邮箱、角色和密码等。
     * 只有请求中提供的非空字段才会被更新，其他字段保持不变。
     * 如果提供了新密码，会自动进行加密处理。
     * </p>
     *
     * @param id 用户的唯一标识符
     * @param request 更新请求对象，包含需要更新的字段信息
     * @return 更新后的用户视图对象
     */
    @Override
    public UserVO updateUser(String id, UserUpdateRequest request) {
        return updateUser(id, request, null);
    }

    @Override
    public UserVO updateUser(String id, UserUpdateRequest request, String operatorUserId) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new BusinessException("用户不存在"));

        // 检查是否为内置管理员，内置管理员不允许被操作
        assertCanOperateBuiltInAdmin(user, operatorUserId);

        // 选择性更新用户字段，只更新请求中提供的非空字段
        if (request.getRealName() != null) {
            user.setRealName(request.getRealName());
        }
        if (request.getPhone() != null) {
            user.setPhone(request.getPhone());
        }
        if (request.getEmail() != null) {
            user.setEmail(request.getEmail());
        }
        if (request.getRoles() != null) {
            user.setRoles(request.getRoles());
        }
        
        // 如果提供了新密码，则进行加密后更新
        if (StringUtils.hasText(request.getPassword())) {
            user.setPassword(passwordEncoder.encode(request.getPassword()));
        }

        // 保存更新后的用户并转换为视图对象返回
        User saved = userRepository.save(user);
        return convertToVO(saved);
    }

    /**
     * 删除用户
     * <p>
     * 该方法用于删除指定ID的用户。删除前会校验用户是否存在，如果不存在则抛出业务异常。
     * </p>
     *
     * @param id 用户的唯一标识符
     */
    @Override
    public void deleteUser(String id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new BusinessException("用户不存在"));

        if ("admin".equals(user.getUsername())) {
            throw new BusinessException("admin账户为系统内置账户，无法删除");
        }
        
        // 从数据库中删除用户
        userRepository.deleteById(id);
    }

    /**
     * 为用户分配角色
     * <p>
     * 该方法用于更新用户的角色列表，实现角色的批量分配。
     * 主要功能包括：
     * 1. 校验用户是否存在
     * 2. 校验所有待分配的角色是否存在
     * 3. 更新用户的角色列表
     * </p>
     *
     * @param id 用户的唯一标识符
     * @param request 角色分配请求对象，包含待分配的角色ID列表
     * @return 更新后的用户视图对象
     */
    @Override
    public UserVO assignRoles(String id, RoleAssignRequest request) {
        return assignRoles(id, request, null);
    }

    /**
     * 为用户分配角色（含操作者权限校验）
     * <p>
     * 该重载方法在角色分配的基础上增加了操作者身份校验，
     * 确保非admin用户无法操作内置管理员账户。
     * 主要功能包括：
     * 1. 校验目标用户是否存在
     * 2. 校验操作者对内置管理员账户的操作权限
     * 3. 校验所有待分配的角色是否存在
     * 4. 更新用户的角色列表并持久化
     * </p>
     *
     * @param id 目标用户的唯一标识符
     * @param request 角色分配请求对象，包含待分配的角色ID列表
     * @param operatorUserId 操作者的用户ID，用于内置管理员操作权限校验
     * @return 更新后的用户视图对象，包含最新的角色信息
     */
    @Override
    public UserVO assignRoles(String id, RoleAssignRequest request, String operatorUserId) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new BusinessException("用户不存在"));

        assertCanChangeBuiltInAdminRoleOrStatus(user);

        // 校验所有待分配的角色是否存在
        for (String roleId : request.getRoleIds()) {
            if (!roleRepository.existsById(roleId)) {
                throw new BusinessException("角色不存在: " + roleId);
            }
        }

        // 更新用户的角色列表并保存
        user.setRoles(request.getRoleIds());
        User saved = userRepository.save(user);
        return convertToVO(saved);
    }

    /**
     * 更新用户状态
     * <p>
     * 该方法用于启用或禁用用户账户。
     * 状态值说明：0-禁用，1-启用
     * </p>
     *
     * @param id 用户的唯一标识符
     * @param status 新的用户状态，0表示禁用，1表示启用
     * @return 更新后的用户视图对象
     */
    @Override
    public UserVO updateUserStatus(String id, Integer status) {
        return updateUserStatus(id, status, null);
    }

    @Override
    public UserVO updateUserStatus(String id, Integer status, String operatorUserId) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new BusinessException("用户不存在"));
        assertCanChangeBuiltInAdminRoleOrStatus(user);

        // 更新用户状态并保存
        user.setStatus(status);
        User saved = userRepository.save(user);
        return convertToVO(saved);
    }

    /**
     * 获取当前登录用户信息
     * <p>
     * 该方法用于获取指定ID的用户详细信息，通常用于用户登录后获取个人信息。
     * </p>
     *
     * @param userId 用户的唯一标识符
     * @return 用户视图对象，包含用户的详细信息
     */
    @Override
    public UserVO getCurrentUser(String userId) {
        // 查询用户信息，不存在时抛出异常
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException("用户不存在"));
        
        // 转换为视图对象并返回
        return convertToVO(user);
    }

    /**
     * 修改用户密码
     * <p>
     * 该方法用于修改用户的登录密码，主要功能包括：
     * 1. 校验用户是否存在
     * 2. 验证旧密码是否正确
     * 3. 对新密码进行加密处理
     * 4. 更新用户密码到数据库
     * </p>
     *
     * @param userId 用户的唯一标识符
     * @param request 密码修改请求对象，包含旧密码和新密码
     */
    @Override
    public void changePassword(String userId, ChangePasswordRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException("用户不存在"));

        // 验证旧密码是否正确
        if (!passwordEncoder.matches(request.getOldPassword(), user.getPassword())) {
            throw new BusinessException("旧密码错误");
        }

        // 对新密码进行加密后更新到数据库
        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);
    }

    /**
     * 将用户实体转换为视图对象
     * <p>
     * 该方法将数据库中的用户实体对象转换为前端展示的视图对象，包含用户的完整信息。
     * 同时会关联查询用户的角色信息和权限列表：
     * - 角色详情：包含角色ID、名称和代码
     * - 角色代码列表：用于快速判断用户角色
     * - 权限列表：合并所有角色的权限，去重后返回
     * 
     * 如果创建时间或更新时间为空，会自动设置默认值。
     * </p>
     *
     * @param user 用户实体对象
     * @return 用户视图对象，包含扩展的角色和权限信息
     */
    private UserVO convertToVO(User user) {
        // 初始化角色详情、角色代码和权限列表
        List<UserVO.RoleInfo> roleDetails = new ArrayList<>();
        List<String> roleCodes = new ArrayList<>();
        LinkedHashSet<String> permissions = new LinkedHashSet<>();
        
        // 遍历用户的角色ID列表，查询每个角色的详细信息
        if (user.getRoles() != null) {
            for (String roleId : user.getRoles()) {
                roleRepository.findById(roleId).ifPresent(role -> {
                    roleDetails.add(UserVO.RoleInfo.builder()
                            .id(role.getId())
                            .name(role.getName())
                            .code(role.getCode())
                            .build());
                    roleCodes.add(role.getCode());
                    
                    // 累加角色的权限
                    if (role.getPermissions() != null) {
                        permissions.addAll(role.getPermissions());
                    }
                });
            }
        }

        // 处理创建时间和更新时间，确保不为null
        java.util.Date createTime = user.getCreateTime();
        java.util.Date updateTime = user.getUpdateTime();
        if (createTime == null) {
            createTime = new java.util.Date();
        }
        if (updateTime == null) {
            updateTime = createTime;
        }

        // 构建并返回用户视图对象
        return UserVO.builder()
                .id(user.getId())
                .username(user.getUsername())
                .realName(user.getRealName())
                .phone(user.getPhone())
                .email(user.getEmail())
                .status(user.getStatus())
                .roles(roleCodes)
                .permissions(new ArrayList<>(permissions))
                .roleDetails(roleDetails)
                .createTime(createTime)
                .updateTime(updateTime)
                .build();
    }

    /**
     * 校验操作者对内置管理员账户的操作权限
     * <p>
     * 仅允许admin账户操作admin账户，防止普通用户越权修改内置管理员。
     * 校验规则：
     * 1. 目标用户非admin → 直接放行，无需额外校验
     * 2. 目标用户为admin且操作者为其本人 → 放行（admin自操作）
     * 3. 目标用户为admin且操作者为其他admin → 放行
     * 4. 其他情况 → 抛出业务异常
     * </p>
     *
     * @param targetUser 操作的目标用户实体
     * @param operatorUserId 操作者的用户ID，可能为{@code null}
     */
    private void assertCanOperateBuiltInAdmin(User targetUser, String operatorUserId) {
        // 目标用户非admin，无需校验
        if (!"admin".equals(targetUser.getUsername())) {
            return;
        }

        // admin账户自操作，允许
        if (targetUser.getId() != null && targetUser.getId().equals(operatorUserId)) {
            return;
        }

        // 查询操作者身份，非admin则拒绝操作
        User operator = StringUtils.hasText(operatorUserId)
                ? userRepository.findById(operatorUserId).orElse(null)
                : null;
        if (operator == null || !"admin".equals(operator.getUsername())) {
            throw new BusinessException("只有admin账号可以操作admin账户");
        }
    }

    private void assertCanChangeBuiltInAdminRoleOrStatus(User targetUser) {
        if ("admin".equals(targetUser.getUsername())) {
            throw new BusinessException("admin账户为系统内置账户，无法分配角色或禁用");
        }
    }

}
