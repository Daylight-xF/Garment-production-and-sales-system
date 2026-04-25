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

    @Override
    public Page<UserVO> getUserList(String keyword, String role, Integer status, Pageable pageable) {
        List<User> allUsers = userRepository.findAll();
        List<User> filtered = allUsers.stream().filter(user -> {
            if (StringUtils.hasText(keyword)) {
                boolean matchKeyword = (user.getUsername() != null && user.getUsername().contains(keyword))
                        || (user.getRealName() != null && user.getRealName().contains(keyword))
                        || (user.getPhone() != null && user.getPhone().contains(keyword));
                if (!matchKeyword) return false;
            }
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
            if (status != null) {
                if (!status.equals(user.getStatus())) return false;
            }
            return true;
        }).collect(Collectors.toList());

        int start = (int) pageable.getOffset();
        int end = Math.min(start + pageable.getPageSize(), filtered.size());
        List<User> pageContent = start < filtered.size() ? filtered.subList(start, end) : new ArrayList<>();

        List<UserVO> voList = pageContent.stream()
                .map(this::convertToVO)
                .collect(Collectors.toList());

        return new PageImpl<>(voList, pageable, filtered.size());
    }

    @Override
    public List<UserVO> getAssignableUsers() {
        List<User> allUsers = userRepository.findAll();
        return allUsers.stream()
                .filter(user -> user.getStatus() != null && user.getStatus() == 1)
                .filter(user -> hasProductionRole(user))
                .map(this::convertToVO)
                .collect(Collectors.toList());
    }

    private boolean hasProductionRole(User user) {
        if (user.getRoles() == null || user.getRoles().isEmpty()) {
            return false;
        }
        for (String roleId : user.getRoles()) {
            Role role = roleRepository.findById(roleId).orElse(null);
            if (role != null && "production_manager".equals(role.getCode())) {
                return true;
            }
        }
        return false;
    }

    @Override
    public UserVO getUserById(String id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new BusinessException("用户不存在"));
        return convertToVO(user);
    }

    @Override
    public UserVO createUser(UserCreateRequest request) {
        if (userRepository.findByUsername(request.getUsername()).isPresent()) {
            throw new BusinessException("用户名已存在");
        }

        List<String> roleIds = request.getRoles();
        if (roleIds == null || roleIds.isEmpty()) {
            Role inactiveRole = roleRepository.findByCode("inactive")
                    .orElseThrow(() -> new BusinessException("默认角色不存在，请联系管理员"));
            roleIds = Collections.singletonList(inactiveRole.getId());
        }

        User user = new User();
        user.setUsername(request.getUsername());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setRealName(request.getRealName());
        user.setPhone(request.getPhone());
        user.setEmail(request.getEmail());
        user.setRoles(roleIds);
        user.setStatus(1);

        User saved;
        try {
            saved = userRepository.save(user);
        } catch (DuplicateKeyException ex) {
            throw new BusinessException("用户名已存在");
        }
        return convertToVO(saved);
    }

    @Override
    public UserVO updateUser(String id, UserUpdateRequest request) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new BusinessException("用户不存在"));

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
        if (StringUtils.hasText(request.getPassword())) {
            user.setPassword(passwordEncoder.encode(request.getPassword()));
        }

        User saved = userRepository.save(user);
        return convertToVO(saved);
    }

    @Override
    public void deleteUser(String id) {
        if (!userRepository.existsById(id)) {
            throw new BusinessException("用户不存在");
        }
        userRepository.deleteById(id);
    }

    @Override
    public UserVO assignRoles(String id, RoleAssignRequest request) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new BusinessException("用户不存在"));

        for (String roleId : request.getRoleIds()) {
            if (!roleRepository.existsById(roleId)) {
                throw new BusinessException("角色不存在: " + roleId);
            }
        }

        user.setRoles(request.getRoleIds());
        User saved = userRepository.save(user);
        return convertToVO(saved);
    }

    @Override
    public UserVO updateUserStatus(String id, Integer status) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new BusinessException("用户不存在"));

        user.setStatus(status);
        User saved = userRepository.save(user);
        return convertToVO(saved);
    }

    @Override
    public UserVO getCurrentUser(String userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException("用户不存在"));
        return convertToVO(user);
    }

    @Override
    public void changePassword(String userId, ChangePasswordRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException("用户不存在"));

        // 验证旧密码是否正确
        if (!passwordEncoder.matches(request.getOldPassword(), user.getPassword())) {
            throw new BusinessException("旧密码错误");
        }

        // 更新为新密码
        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);
    }

    private UserVO convertToVO(User user) {
        List<UserVO.RoleInfo> roleDetails = new ArrayList<>();
        List<String> roleCodes = new ArrayList<>();
        LinkedHashSet<String> permissions = new LinkedHashSet<>();
        if (user.getRoles() != null) {
            for (String roleId : user.getRoles()) {
                roleRepository.findById(roleId).ifPresent(role -> {
                    roleDetails.add(UserVO.RoleInfo.builder()
                            .id(role.getId())
                            .name(role.getName())
                            .code(role.getCode())
                            .build());
                    roleCodes.add(role.getCode());
                    if (role.getPermissions() != null) {
                        permissions.addAll(role.getPermissions());
                    }
                });
            }
        }

        java.util.Date createTime = user.getCreateTime();
        java.util.Date updateTime = user.getUpdateTime();
        if (createTime == null) {
            createTime = new java.util.Date();
        }
        if (updateTime == null) {
            updateTime = createTime;
        }

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
}
