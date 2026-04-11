package com.garment.service;

import com.garment.dto.LoginRequest;
import com.garment.dto.LoginResponse;
import com.garment.dto.RegisterRequest;

public interface AuthService {

    void register(RegisterRequest request);

    LoginResponse login(LoginRequest request);

    void logout();
}
