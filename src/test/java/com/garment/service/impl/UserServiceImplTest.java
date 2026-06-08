package com.garment.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.garment.dto.RoleAssignRequest;
import com.garment.dto.UserCreateRequest;
import com.garment.dto.UserUpdateRequest;
import com.garment.exception.BusinessException;
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
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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

    @Test
    void createUserShouldTranslateDuplicateKeyException() {
        UserCreateRequest request = new UserCreateRequest();
        request.setUsername("duplicate-user");
        request.setPassword("password");
        request.setRealName("tester");

        when(userRepository.findByUsername("duplicate-user")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("password")).thenReturn("encoded-password");
        Role inactiveRole = new Role();
        inactiveRole.setId("role-inactive");
        inactiveRole.setCode("inactive");
        inactiveRole.setName("inactive");
        when(roleRepository.findByCode("inactive")).thenReturn(Optional.of(inactiveRole));
        when(userRepository.save(org.mockito.ArgumentMatchers.any(User.class)))
                .thenThrow(new DuplicateKeyException("duplicate username"));

        assertThatThrownBy(() -> userService.createUser(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("用户名已存在");
    }

    @Test
    void deleteUserShouldRejectBuiltInAdminAccount() {
        User admin = new User();
        admin.setId("admin-id");
        admin.setUsername("admin");

        when(userRepository.findById("admin-id")).thenReturn(Optional.of(admin));

        assertThatThrownBy(() -> userService.deleteUser("admin-id"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("admin账户为系统内置账户，无法删除");

        verify(userRepository, never()).deleteById("admin-id");
    }

    @Test
    void updateUserShouldRejectAdminTargetWhenOperatorIsAnotherAdminUser() {
        User admin = new User();
        admin.setId("admin-id");
        admin.setUsername("admin");

        User otherAdmin = new User();
        otherAdmin.setId("manager-id");
        otherAdmin.setUsername("guanliyuan");

        when(userRepository.findById("admin-id")).thenReturn(Optional.of(admin));
        when(userRepository.findById("manager-id")).thenReturn(Optional.of(otherAdmin));

        assertThatThrownBy(() -> userService.updateUser("admin-id", new UserUpdateRequest(), "manager-id"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("只有admin账号可以操作admin账户");

        verify(userRepository, never()).save(admin);
    }

    @Test
    void updateUserShouldAllowBuiltInAdminAccountToOperateItself() {
        User admin = new User();
        admin.setId("admin-id");
        admin.setUsername("admin");

        UserUpdateRequest request = new UserUpdateRequest();
        request.setRealName("系统管理员");

        when(userRepository.findById("admin-id")).thenReturn(Optional.of(admin));
        when(userRepository.save(admin)).thenReturn(admin);

        UserVO result = userService.updateUser("admin-id", request, "admin-id");

        assertThat(result.getRealName()).isEqualTo("系统管理员");
        verify(userRepository).save(admin);
    }

    @Test
    void assignRolesShouldRejectAdminTargetWhenOperatorIsAnotherAdminUser() {
        User admin = new User();
        admin.setId("admin-id");
        admin.setUsername("admin");

        RoleAssignRequest request = new RoleAssignRequest();
        request.setRoleIds(Collections.singletonList("role-admin"));

        when(userRepository.findById("admin-id")).thenReturn(Optional.of(admin));

        assertThatThrownBy(() -> userService.assignRoles("admin-id", request, "manager-id"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("admin账户为系统内置账户，无法分配角色或禁用");

        verify(userRepository, never()).save(admin);
    }

    @Test
    void assignRolesShouldRejectBuiltInAdminAccountEvenWhenOperatorIsAdmin() {
        User admin = new User();
        admin.setId("admin-id");
        admin.setUsername("admin");

        RoleAssignRequest request = new RoleAssignRequest();
        request.setRoleIds(Collections.singletonList("role-admin"));

        when(userRepository.findById("admin-id")).thenReturn(Optional.of(admin));

        assertThatThrownBy(() -> userService.assignRoles("admin-id", request, "admin-id"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("admin账户为系统内置账户，无法分配角色或禁用");

        verify(userRepository, never()).save(admin);
    }

    @Test
    void updateUserStatusShouldRejectAdminTargetWhenOperatorIsAnotherAdminUser() {
        User admin = new User();
        admin.setId("admin-id");
        admin.setUsername("admin");

        when(userRepository.findById("admin-id")).thenReturn(Optional.of(admin));

        assertThatThrownBy(() -> userService.updateUserStatus("admin-id", 0, "manager-id"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("admin账户为系统内置账户，无法分配角色或禁用");

        verify(userRepository, never()).save(admin);
    }

    @Test
    void updateUserStatusShouldRejectBuiltInAdminAccountEvenWhenOperatorIsAdmin() {
        User admin = new User();
        admin.setId("admin-id");
        admin.setUsername("admin");

        when(userRepository.findById("admin-id")).thenReturn(Optional.of(admin));

        assertThatThrownBy(() -> userService.updateUserStatus("admin-id", 0, "admin-id"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("admin账户为系统内置账户，无法分配角色或禁用");

        verify(userRepository, never()).save(admin);
    }
}
