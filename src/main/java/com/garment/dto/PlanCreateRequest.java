package com.garment.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.util.Date;

@Data
public class PlanCreateRequest {

    @NotBlank(message = "计划名称不能为空")
    private String planName;

    @NotBlank(message = "产品名称不能为空")
    private String productName;

    @NotNull(message = "计划数量不能为空")
    private Integer quantity;

    private String unit;

    private Date startDate;

    private Date endDate;

    private String description;
}
