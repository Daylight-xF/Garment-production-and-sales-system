package com.garment.service.impl;

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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
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
    public Page<UserVO> getUserList(String keyword, Pageable pageable) {
        Page<User> userPage;
        if (StringUtils.hasText(keyword)) {
            List<User> allUsers = userRepository.findAll();
            List<User> filtered = allUsers.stream()
                    .filter(u -> (u.getUsername() != null && u.getUsername().contains(keyword))
                            || (u.getRealName() != null && u.getRealName().contains(keyword))
                            || (u.getPhone() != null && u.getPhone().contains(keyword)))
                    .collect(Collectors.toList());
            int start = (int) pageable.getOffset();
            int end = Math.min(start + pageable.getPageSize(), filtered.size());
            List<User> pageContent = start < filtered.size() ? filtered.subList(start, end) : new ArrayList<>();
            userPage = new PageImpl<>(pageContent, pageable, filtered.size());
        } else {
            userPage = userRepository.findAll(pageable);
        }

        List<UserVO> voList = userPage.getContent().stream()
                .map(this::convertToVO)
                .collect(Collectors.toList());

        return new PageImpl<>(voList, pageable, userPage.getTotalElements());
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

        User user = new User();
        user.setUsername(request.getUsername());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setRealName(request.getRealName());
        user.setPhone(request.getPhone());
        user.setEmail(request.getEmail());
        user.setRoles(request.getRoles());
        user.setStatus(1);

        User saved = userRepository.save(user);
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

    private UserVO convertToVO(User user) {
        List<UserVO.RoleInfo> roleDetails = new ArrayList<>();
        List<String> roleCodes = new ArrayList<>();
        if (user.getRoles() != null) {
            for (String roleId : user.getRoles()) {
                roleRepository.findById(roleId).ifPresent(role -> {
                    roleDetails.add(UserVO.RoleInfo.builder()
                            .id(role.getId())
                            .name(role.getName())
                            .code(role.getCode())
                            .build());
                    roleCodes.add(role.getCode());
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
                .roleDetails(roleDetails)
                .createTime(createTime)
                .updateTime(updateTime)
                .build();
    }
}
