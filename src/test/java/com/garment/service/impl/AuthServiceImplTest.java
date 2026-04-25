package com.garment.service.impl;

import com.garment.dto.RegisterRequest;
import com.garment.exception.BusinessException;
import com.garment.model.Role;
import com.garment.model.User;
import com.garment.repository.RoleRepository;
import com.garment.repository.UserRepository;
import com.garment.util.JwtUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtUtil jwtUtil;

    @InjectMocks
    private AuthServiceImpl authService;

    @Test
    void registerShouldTranslateDuplicateKeyException() {
        RegisterRequest request = new RegisterRequest();
        request.setUsername("duplicate-user");
        request.setPassword("password");
        request.setRealName("tester");

        Role inactiveRole = new Role();
        inactiveRole.setId("role-inactive");
        inactiveRole.setCode("inactive");
        inactiveRole.setName("inactive");

        when(userRepository.findByUsername("duplicate-user")).thenReturn(Optional.empty());
        when(roleRepository.findByCode("inactive")).thenReturn(Optional.of(inactiveRole));
        when(passwordEncoder.encode("password")).thenReturn("encoded-password");
        when(userRepository.save(any(User.class))).thenThrow(new DuplicateKeyException("duplicate username"));

        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("用户名已存在");
    }
}
