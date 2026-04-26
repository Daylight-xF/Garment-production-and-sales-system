package com.garment.dto;

import lombok.Data;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

@Data
public class RawMaterialCreateRequest {

    @NotBlank(message = "名称不能为空")
    private String name;

    private String category;

    private String specification;

    private String unit;

    @NotNull(message = "库存数量不能为空")
    @Min(value = 1, message = "库存数量必须大于0")
    private Integer quantity;

    private Integer alertThreshold;

    @NotBlank(message = "存放位置不能为空")
    private String location;

    private String supplier;

    private Double price;

    private String description;
}
