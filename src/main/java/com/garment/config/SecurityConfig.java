package com.garment.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.garment.dto.Result;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import javax.servlet.http.HttpServletResponse;

/**
 * Spring Security 安全配置类。
 *
 * <p>配置系统的密码加密方式、无状态 JWT 认证、接口访问规则以及认证/授权失败时的 JSON 响应。</p>
 */
@EnableWebSecurity
@EnableGlobalMethodSecurity(prePostEnabled = true)
public class SecurityConfig extends WebSecurityConfigurerAdapter {

    // JWT 认证过滤器，用于在用户名密码认证过滤器之前解析并设置登录用户。
    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    // JSON 序列化工具，用于将统一响应对象写入认证/授权失败响应体。
    private final ObjectMapper objectMapper;

    /**
     * 构造安全配置对象。
     *
     * <p>通过构造器注入 JWT 认证过滤器和 JSON 序列化工具，供安全过滤链和异常处理器使用。</p>
     *
     * @param jwtAuthenticationFilter JWT 认证过滤器
     * @param objectMapper JSON 序列化工具
     */
    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter, ObjectMapper objectMapper) {
        // 保存 JWT 认证过滤器引用。
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        // 保存 JSON 序列化工具引用。
        this.objectMapper = objectMapper;
    }


        /**
         * 配置密码加密器，使用BCrypt算法
         * <p>
         * 该 Bean 提供用户密码加密与校验能力，注册、登录和默认管理员初始化都会复用同一套 BCrypt 编码器。
         * </p>
         *
         * @return BCryptPasswordEncoder实例
         */
        @Bean
        public PasswordEncoder passwordEncoder() {
            // 返回 BCrypt 密码编码器，避免明文密码直接存储或比较。
            return new BCryptPasswordEncoder();
        }
    

        /**
         * 配置认证管理器，使用内存认证（仅用于开发测试）
         * <p>
         * 该方法保留 Spring Security 认证管理器配置入口，当前项目主要依赖业务登录接口和 JWT 完成认证。
         * </p>
         *
         * @param auth AuthenticationManagerBuilder构建器
         * @throws Exception 配置异常
         */
        @Override
        protected void configure(AuthenticationManagerBuilder auth) throws Exception {
            // 启用空的内存认证配置，满足父类认证管理器构建流程。
            auth.inMemoryAuthentication();
        }


        /**
         * 配置HTTP安全策略，包括CSRF禁用、无状态会话、URL授权和异常处理
         * <p>
         * 该方法定义接口访问规则：登录和注册接口放行，其他接口需要认证；同时配置认证失败、授权失败响应和 JWT 过滤器位置。
         * </p>
         *
         * @param http HttpSecurity配置对象
         * @throws Exception 配置异常
         */
        @Override
        protected void configure(HttpSecurity http) throws Exception {
            // 使用链式配置定义 HTTP 安全策略。
            http
                    // 前后端分离接口使用 JWT，无需 CSRF Token。
                    .csrf().disable()
                    // 服务端不保存登录会话，所有请求都依赖 JWT 自带身份信息。
                    .sessionManagement().sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                    .and()
                    // 配置 URL 授权规则。
                    .authorizeRequests()
                        // 登录和注册 POST 接口允许匿名访问。
                        .antMatchers(HttpMethod.POST, "/api/auth/login", "/api/auth/register").permitAll()
                        // 登录和注册 GET 接口允许匿名访问，兼容前端或调试访问。
                        .antMatchers(HttpMethod.GET, "/api/auth/login", "/api/auth/register").permitAll()
                        // 其余接口必须通过认证。
                        .anyRequest().authenticated()
                    .and()
                    // 配置认证失败和授权失败时的统一 JSON 响应。
                    .exceptionHandling()
                        // 未登录或 token 失效时返回 401。
                        .authenticationEntryPoint((request, response, authException) -> {
                            // 设置响应类型为 JSON。
                            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                            // 设置响应字符集，避免中文提示乱码。
                            response.setCharacterEncoding("UTF-8");
                            // 设置 HTTP 状态码为 401 未认证。
                            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                            // 构造统一错误响应体。
                            Result<Void> result = Result.error(401, "未登录或登录已过期");
                            // 将错误响应序列化后写入响应体。
                            response.getWriter().write(objectMapper.writeValueAsString(result));
                        })
                        // 已登录但权限不足时返回 403。
                        .accessDeniedHandler((request, response, accessDeniedException) -> {
                            // 设置响应类型为 JSON。
                            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                            // 设置响应字符集，避免中文提示乱码。
                            response.setCharacterEncoding("UTF-8");
                            // 设置 HTTP 状态码为 403 无权限。
                            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                            // 构造统一错误响应体。
                            Result<Void> result = Result.error(403, "没有访问权限");
                            // 将错误响应序列化后写入响应体。
                            response.getWriter().write(objectMapper.writeValueAsString(result));
                        })
                    .and()
                    // 将 JWT 过滤器放到用户名密码过滤器之前，确保接口授权前已经解析 token。
                    .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        }

    
}
