package com.garment.service.impl;

import com.garment.dto.LoginRequest;
import com.garment.dto.LoginResponse;
import com.garment.dto.RegisterRequest;
import com.garment.exception.BusinessException;
import com.garment.model.Role;
import com.garment.model.User;
import com.garment.repository.RoleRepository;
import com.garment.repository.UserRepository;
import com.garment.service.AuthService;
import com.garment.util.JwtUtil;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    public AuthServiceImpl(UserRepository userRepository,
                           RoleRepository roleRepository,
                           PasswordEncoder passwordEncoder,
                           JwtUtil jwtUtil) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
    }

    @Override
    public void register(RegisterRequest request) {
        if (userRepository.findByUsername(request.getUsername()).isPresent()) {
            throw new BusinessException("用户名已存在");
        }

        Role inactiveRole = roleRepository.findByCode("inactive")
                .orElseThrow(() -> new BusinessException("默认角色不存在，请联系管理员"));

        User user = new User();
        user.setUsername(request.getUsername());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setRealName(request.getRealName());
        user.setPhone(request.getPhone());
        user.setEmail(request.getEmail());
        user.setRoles(Collections.singletonList(inactiveRole.getId()));
        user.setStatus(1);

        userRepository.save(user);
    }

    @Override
    public LoginResponse login(LoginRequest request) {
        User user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new BusinessException("用户名或密码错误"));

        if (user.getStatus() == 0) {
            throw new BusinessException("账号已被禁用");
        }

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new BusinessException("用户名或密码错误");
        }

        List<String> roleCodes = new ArrayList<>();
        List<String> permissions = new ArrayList<>();
        if (user.getRoles() != null) {
            for (String roleId : user.getRoles()) {
                roleRepository.findById(roleId).ifPresent(role -> {
                    roleCodes.add(role.getCode());
                    if (role.getPermissions() != null) {
                        for (String perm : role.getPermissions()) {
                            if (!permissions.contains(perm)) {
                                permissions.add(perm);
                            }
                        }
                    }
                });
            }
        }

        if (roleCodes.contains("inactive")) {
            throw new BusinessException("该用户还未激活，请联系管理员！");
        }

        String token = jwtUtil.generateToken(user.getId(), user.getUsername());

        LoginResponse.UserInfo userInfo = LoginResponse.UserInfo.builder()
                .id(user.getId())
                .username(user.getUsername())
                .realName(user.getRealName())
                .roles(roleCodes)
                .permissions(permissions)
                .build();

        return LoginResponse.builder()
                .token(token)
                .userInfo(userInfo)
                .build();
    }

    @Override
    public void logout() {
    }
}
