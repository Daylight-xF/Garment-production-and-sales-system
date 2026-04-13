package com.garment.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserVO {

    private String id;
    private String username;
    private String realName;
    private String phone;
    private String email;
    private Integer status;
    private List<String> roles;
    private List<String> permissions;
    private List<RoleInfo> roleDetails;
    private Date createTime;
    private Date updateTime;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class RoleInfo {
        private String id;
        private String name;
        private String code;
    }
}
