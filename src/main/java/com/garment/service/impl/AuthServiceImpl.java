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
import org.springframework.dao.DuplicateKeyException;
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


        /**
         * 用户注册，创建新账号并分配默认角色（未激活）
         * <p>
         * 该方法用于处理新用户注册流程，主要功能包括：
         * 1. 验证用户名是否已存在
         * 2. 查找默认的"inactive"角色（未激活状态）
         * 3. 对用户密码进行加密处理
         * 4. 创建用户记录并分配默认角色
         * 5. 设置用户状态为启用（status=1）
         * </p>
         *
         * @param request 注册请求参数，包含用户名、密码、真实姓名、手机号、邮箱等信息
         * @throws BusinessException 如果用户名已存在或默认角色不存在时抛出业务异常
         */
        @Override
        public void register(RegisterRequest request) {
            // 检查用户名是否已存在
            if (userRepository.findByUsername(request.getUsername()).isPresent()) {
                throw new BusinessException("用户名已存在");
            }

            // 获取默认的未激活角色
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

            // 保存用户记录，处理重复键异常
            try {
                userRepository.save(user);
            } catch (DuplicateKeyException ex) {
                throw new BusinessException("用户名已存在");
            }
        }


        /**
         * 用户登录，验证用户名密码并生成JWT令牌
         * <p>
         * 该方法处理用户登录认证流程，主要功能包括：
         * 1. 根据用户名查找用户记录
         * 2. 验证用户状态（是否被禁用）
         * 3. 验证密码正确性（使用BCrypt加密比对）
         * 4. 加载用户角色和权限信息
         * 5. 检查用户是否已激活
         * 6. 生成JWT访问令牌
         * 7. 构建用户信息响应对象
         * </p>
         *
         * @param request 登录请求参数，包含用户名和密码
         * @return 登录响应对象，包含JWT令牌和用户详细信息（ID、用户名、真实姓名、角色列表、权限列表）
         * @throws BusinessException 如果账号未注册、被禁用、密码错误或未激活时抛出业务异常
         */
        @Override
        public LoginResponse login(LoginRequest request) {
            // 查找用户记录
            User user = userRepository.findByUsername(request.getUsername())
                    .orElseThrow(() -> new BusinessException("账号未注册，请先注册"));

            // 检查用户状态是否被禁用
            if (user.getStatus() == 0) {
                throw new BusinessException("账号已被禁用");
            }

            // 验证密码正确性
            if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
                throw new BusinessException("用户名或密码错误");
            }

            // 加载用户角色和权限信息
            List<String> roleCodes = new ArrayList<>();
            List<String> permissions = new ArrayList<>();
            if (user.getRoles() != null) {
                for (String roleId : user.getRoles()) {
                    roleRepository.findById(roleId).ifPresent(role -> {
                        roleCodes.add(role.getCode());
                        if (role.getPermissions() != null) {
                            // 去重添加权限
                            for (String perm : role.getPermissions()) {
                                if (!permissions.contains(perm)) {
                                    permissions.add(perm);
                                }
                            }
                        }
                    });
                }
            }

            // 检查用户是否已激活
            if (roleCodes.contains("inactive")) {
                throw new BusinessException("该用户还未激活，请联系管理员！");
            }

            // 生成JWT令牌
            String token = jwtUtil.generateToken(user.getId(), user.getUsername());

            // 构建用户信息对象
            LoginResponse.UserInfo userInfo = LoginResponse.UserInfo.builder()
                    .id(user.getId())
                    .username(user.getUsername())
                    .realName(user.getRealName())
                    .roles(roleCodes)
                    .permissions(permissions)
                    .build();

            // 构建登录响应对象
            return LoginResponse.builder()
                    .token(token)
                    .userInfo(userInfo)
                    .build();
        }


        /**
         * 用户登出（由于使用JWT无状态认证，此方法为空实现）
         * <p>
         * 该方法当前为空实现，因为系统采用JWT（JSON Web Token）进行无状态认证。
         * JWT令牌在客户端存储，服务端不维护会话状态，因此无需显式登出操作。
         * 客户端只需删除本地存储的令牌即可完成登出。
         * </p>
         */
        @Override
        public void logout() {
        }

}
