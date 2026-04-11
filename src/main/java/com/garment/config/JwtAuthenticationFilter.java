package com.garment.config;

import com.garment.model.Role;
import com.garment.model.User;
import com.garment.repository.RoleRepository;
import com.garment.repository.UserRepository;
import com.garment.util.JwtUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Slf4j
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;

    public JwtAuthenticationFilter(JwtUtil jwtUtil,
                                    UserRepository userRepository,
                                    RoleRepository roleRepository) {
        this.jwtUtil = jwtUtil;
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String token = getTokenFromRequest(request);

        if (StringUtils.hasText(token) && jwtUtil.validateToken(token)) {
            String userId = jwtUtil.getUserIdFromToken(token);
            String username = jwtUtil.getUsernameFromToken(token);

            List<SimpleGrantedAuthority> authorities = loadUserAuthorities(userId);

            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(
                            userId,
                            null,
                            authorities
                    );

            SecurityContextHolder.getContext().setAuthentication(authentication);
            log.debug("JWT认证成功, userId: {}, username: {}", userId, username);
        }

        filterChain.doFilter(request, response);
    }

    private List<SimpleGrantedAuthority> loadUserAuthorities(String userId) {
        List<SimpleGrantedAuthority> authorities = new ArrayList<>();
        Optional<User> userOpt = userRepository.findById(userId);
        if (!userOpt.isPresent()) {
            return authorities;
        }

        User user = userOpt.get();
        if (user.getRoles() != null) {
            for (String roleId : user.getRoles()) {
                Optional<Role> roleOpt = roleRepository.findById(roleId);
                if (roleOpt.isPresent()) {
                    Role role = roleOpt.get();
                    authorities.add(new SimpleGrantedAuthority("ROLE_" + role.getCode()));
                    if (role.getPermissions() != null) {
                        for (String perm : role.getPermissions()) {
                            authorities.add(new SimpleGrantedAuthority(perm));
                        }
                    }
                }
            }
        }

        return authorities;
    }

    private String getTokenFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }
}
