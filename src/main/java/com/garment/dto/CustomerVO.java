package com.garment.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CustomerVO {

    private String id;
    private String name;
    private String phone;
    private String email;
    private String address;
    private String level;
    private String remark;
    private String createBy;
    private Date createTime;
    private Date updateTime;
}
