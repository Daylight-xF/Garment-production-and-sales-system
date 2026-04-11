package com.garment.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;

@Data
public class FinishedProductCreateRequest {

    @NotBlank(message = "名称不能为空")
    private String name;

    private String category;

    private String specification;

    private String unit;

    private Integer quantity;

    private Integer alertThreshold;

    private String location;

    private Double price;

    private Double costPrice;

    private String description;
}
