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

/**
 * JWT 认证过滤器。
 *
 * <p>每次请求进入业务接口前，从 Authorization 请求头中解析 JWT，校验通过后加载用户权限并写入 Spring Security 上下文。</p>
 */
@Slf4j
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    // JWT 工具类，用于校验 token 并解析用户信息。
    private final JwtUtil jwtUtil;
    // 用户仓库，用于根据 token 中的用户 ID 查询用户角色。
    private final UserRepository userRepository;
    // 角色仓库，用于根据用户角色 ID 查询角色编码和权限列表。
    private final RoleRepository roleRepository;

    /**
     * 构造 JWT 认证过滤器。
     *
     * <p>通过构造器注入 JWT 工具类、用户仓库和角色仓库，供请求过滤时使用。</p>
     *
     * @param jwtUtil JWT 工具类
     * @param userRepository 用户数据访问接口
     * @param roleRepository 角色数据访问接口
     */
    public JwtAuthenticationFilter(JwtUtil jwtUtil,
                                    UserRepository userRepository,
                                    RoleRepository roleRepository) {
        // 保存 JWT 工具类引用。
        this.jwtUtil = jwtUtil;
        // 保存用户仓库引用。
        this.userRepository = userRepository;
        // 保存角色仓库引用。
        this.roleRepository = roleRepository;
    }


        /**
         * 执行JWT认证过滤逻辑，验证token并设置用户认证信息
         * <p>
         * 该方法从请求头中提取 Bearer token，校验 token 有效后解析用户信息、加载权限，并将认证对象放入安全上下文。
         * </p>
         *
         * @param request HTTP请求对象
         * @param response HTTP响应对象
         * @param filterChain 过滤器链
         * @throws ServletException Servlet异常
         * @throws IOException IO异常
         */
        @Override
        protected void doFilterInternal(HttpServletRequest request,
                                        HttpServletResponse response,
                                        FilterChain filterChain) throws ServletException, IOException {
            // 从当前请求的 Authorization 头中提取 JWT。
            String token = getTokenFromRequest(request);

            // 只有 token 存在且校验通过时，才构建认证信息。
            if (StringUtils.hasText(token) && jwtUtil.validateToken(token)) {
                // 从 token 中解析用户 ID，作为认证主体。
                String userId = jwtUtil.getUserIdFromToken(token);
                // 从 token 中解析用户名，用于调试日志。
                String username = jwtUtil.getUsernameFromToken(token);

                // 根据用户角色加载 Spring Security 权限列表。
                List<SimpleGrantedAuthority> authorities = loadUserAuthorities(userId);

                // 构建认证对象：主体使用 userId，凭证置空，权限使用数据库加载结果。
                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(
                                userId,
                                null,
                                authorities
                        );

                // 将认证对象写入安全上下文，后续授权流程即可读取当前用户身份。
                SecurityContextHolder.getContext().setAuthentication(authentication);
                // 输出认证成功调试日志。
                log.debug("JWT认证成功, userId: {}, username: {}", userId, username);
            }

            // 无论是否认证成功，都继续执行后续过滤器链。
            filterChain.doFilter(request, response);
        }


        /**
         * 加载用户权限信息，包括角色和权限
         * <p>
         * 该方法根据用户 ID 查询用户角色，再逐个查询角色信息，组装角色授权标识和具体权限标识。
         * </p>
         *
         * @param userId 用户ID
         * @return 权限列表，包含角色标识（ROLE_前缀）和具体权限
         */
        private List<SimpleGrantedAuthority> loadUserAuthorities(String userId) {
            // 初始化权限列表，用户不存在或没有角色时返回空列表。
            List<SimpleGrantedAuthority> authorities = new ArrayList<>();
            // 根据用户 ID 查询用户。
            Optional<User> userOpt = userRepository.findById(userId);
            // 用户不存在时无法加载权限，直接返回空列表。
            if (!userOpt.isPresent()) {
                return authorities;
            }

            // 读取用户对象。
            User user = userOpt.get();
            // 用户角色列表不为空时，逐个角色加载权限。
            if (user.getRoles() != null) {
                // 遍历用户拥有的角色 ID。
                for (String roleId : user.getRoles()) {
                    // 查询角色信息。
                    Optional<Role> roleOpt = roleRepository.findById(roleId);
                    // 角色存在时加入角色授权和权限授权。
                    if (roleOpt.isPresent()) {
                        // 读取角色对象。
                        Role role = roleOpt.get();
                        // 添加 Spring Security 角色标识，统一使用 ROLE_ 前缀。
                        authorities.add(new SimpleGrantedAuthority("ROLE_" + role.getCode()));
                        // 角色包含权限列表时，继续追加具体权限标识。
                        if (role.getPermissions() != null) {
                            // 遍历角色下的每个权限编码。
                            for (String perm : role.getPermissions()) {
                                // 将权限编码转换为 Spring Security 授权对象。
                                authorities.add(new SimpleGrantedAuthority(perm));
                            }
                        }
                    }
                }
            }

            // 返回加载完成的角色和权限列表。
            return authorities;
        }


        /**
         * 从HTTP请求头中提取JWT token
         * <p>
         * 该方法读取 Authorization 请求头，仅当其符合 Bearer token 格式时返回真实 token 内容。
         * </p>
         *
         * @param request HTTP请求对象
         * @return JWT token字符串，如果不存在则返回null
         */
        private String getTokenFromRequest(HttpServletRequest request) {
            // 读取 Authorization 请求头。
            String bearerToken = request.getHeader("Authorization");
            // 只处理 Bearer 开头的认证头，并去掉前缀返回 token。
            if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
                return bearerToken.substring(7);
            }
            // 请求头为空或格式不正确时返回 null。
            return null;
        }

    
}
