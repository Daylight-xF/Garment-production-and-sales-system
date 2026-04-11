package com.garment.dto;

import lombok.Data;

import javax.validation.constraints.NotEmpty;
import java.util.List;

@Data
public class RoleAssignRequest {

    @NotEmpty(message = "角色列表不能为空")
    private List<String> roleIds;
}
