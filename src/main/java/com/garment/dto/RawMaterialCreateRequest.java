package com.garment.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;

@Data
public class RawMaterialCreateRequest {

    @NotBlank(message = "名称不能为空")
    private String name;

    private String category;

    private String specification;

    private String unit;

    private Integer quantity;

    private Integer alertThreshold;

    private String location;

    private String supplier;

    private Double price;

    private String description;
}
