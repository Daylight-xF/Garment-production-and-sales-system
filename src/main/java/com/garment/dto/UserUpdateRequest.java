package com.garment.dto;

import lombok.Data;

import java.util.List;

@Data
public class UserUpdateRequest {

    private String realName;

    private String phone;

    private String email;

    private List<String> roles;

    private String password;
}
