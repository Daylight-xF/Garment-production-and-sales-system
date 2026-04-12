package com.garment.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.util.Date;

@Data
public class PlanCreateRequest {

    @NotBlank(message = "批次号不能为空")
    private String batchNo;

    @NotBlank(message = "请选择产品定义")
    private String productDefinitionId;

    @NotNull(message = "计划数量不能为空")
    private Integer quantity;

    private String unit;

    private Date startDate;

    private Date endDate;

    @NotBlank(message = "颜色不能为空")
    private String color;

    @NotBlank(message = "尺码不能为空")
    private String size;

    private String description;
}
