package com.garment.dto;

import lombok.Data;

import javax.validation.constraints.NotNull;

@Data
public class ThresholdRequest {

    @NotNull(message = "预警阈值不能为空")
    private Integer alertThreshold;
}
