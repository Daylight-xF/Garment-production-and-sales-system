package com.garment.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;

@Data
public class FinishedProductCreateRequest {

    @NotBlank(message = "批次号不能为空")
    private String batchNo;

    @NotBlank(message = "名称不能为空")
    private String name;

    private String productCode;

    private String category;

    private String color;

    private String size;

    private String unit;

    private Integer quantity;

    private Integer alertThreshold;

    private String location;

    private Double price;

    private Double costPrice;

    private String description;
}
