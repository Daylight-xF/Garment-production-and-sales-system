package com.garment.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;

@Data
public class CustomerCreateRequest {

    @NotBlank(message = "客户名称不能为空")
    private String name;

    private String phone;

    private String email;

    private String address;

    private String level;

    private String remark;
}
