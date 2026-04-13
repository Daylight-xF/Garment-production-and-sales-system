package com.garment.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.garment.dto.UserVO;
import com.garment.model.Role;
import com.garment.model.User;
import com.garment.repository.RoleRepository;
import com.garment.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UserServiceImpl userService;

    @Test
    void getCurrentUserShouldIncludeRoleDerivedPermissions() {
        User user = new User();
        user.setId("user-1");
        user.setUsername("admin");
        user.setRealName("管理员");
        user.setRoles(Collections.singletonList("role-admin"));
        user.setStatus(1);

        Role adminRole = new Role();
        adminRole.setId("role-admin");
        adminRole.setCode("admin");
        adminRole.setName("系统管理员");
        adminRole.setPermissions(Arrays.asList("ORDER_READ", "ORDER_APPROVE", "ORDER_CANCEL"));

        when(userRepository.findById("user-1")).thenReturn(Optional.of(user));
        when(roleRepository.findById("role-admin")).thenReturn(Optional.of(adminRole));

        UserVO userVO = userService.getCurrentUser("user-1");

        Map<String, Object> payload = new ObjectMapper().convertValue(userVO, new TypeReference<Map<String, Object>>() {});

        assertThat(payload).containsKey("permissions");
        assertThat(payload.get("permissions")).asList().contains("ORDER_APPROVE", "ORDER_CANCEL");
    }
}
