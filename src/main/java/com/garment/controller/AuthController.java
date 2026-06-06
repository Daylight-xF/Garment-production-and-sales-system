package com.garment.controller;

import com.garment.dto.LoginRequest;
import com.garment.dto.LoginResponse;
import com.garment.dto.RegisterRequest;
import com.garment.dto.Result;
import com.garment.service.AuthService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;

/**
 * 认证控制器。
 *
 * <p>提供用户注册、登录和登出相关接口，统一委托 AuthService 完成认证业务处理。</p>
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    // 认证业务服务，负责注册、登录和登出等核心逻辑。
    private final AuthService authService;

    /**
     * 创建认证控制器。
     *
     * <p>通过构造器注入认证业务服务。</p>
     *
     * @param authService 认证业务服务
     */
    public AuthController(AuthService authService) {
        this.authService = authService;
    }


        /**
         * 用户注册接口
         * <p>
         * 接收注册请求参数，校验通过后调用认证服务创建新用户。
         * </p>
         *
         * @param request 注册请求参数，包含用户名、密码等信息
         * @return 统一响应结果
         */
        @PostMapping("/register")
        public Result<Void> register(@Valid @RequestBody RegisterRequest request) {
            // 调用认证服务完成用户注册。
            authService.register(request);

            // 返回注册成功响应。
            return Result.success();
        }


        /**
         * 用户登录接口
         * <p>
         * 接收登录请求参数，校验通过后调用认证服务生成登录响应信息。
         * </p>
         *
         * @param request 登录请求参数，包含用户名和密码
         * @return 统一响应结果，包含登录响应信息
         */
        @PostMapping("/login")
        public Result<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
            // 调用认证服务完成登录校验并生成 token 等响应数据。
            LoginResponse response = authService.login(request);

            // 返回登录成功响应。
            return Result.success(response);
        }


        /**
         * 用户登出接口
         * <p>
         * 调用认证服务执行登出逻辑，并返回统一成功响应。
         * </p>
         *
         * @return 统一响应结果
         */
        @PostMapping("/logout")
        public Result<Void> logout() {
            // 调用认证服务执行登出处理。
            authService.logout();

            // 返回登出成功响应。
            return Result.success("登出成功", null);
        }

}
