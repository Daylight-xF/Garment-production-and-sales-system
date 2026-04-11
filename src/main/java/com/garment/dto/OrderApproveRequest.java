package com.garment.dto;

import lombok.Data;

import javax.validation.constraints.NotNull;

@Data
public class OrderApproveRequest {

    @NotNull(message = "审批结果不能为空")
    private Boolean approved;

    private String remark;
}
