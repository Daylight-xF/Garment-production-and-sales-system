package com.garment.dto;

import lombok.Data;

@Data
public class CustomerUpdateRequest {

    private String name;

    private String phone;

    private String email;

    private String address;

    private String level;

    private String remark;
}
