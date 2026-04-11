package com.garment.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

@Data
public class StockInOutRequest {

    @NotBlank(message = "物品类型不能为空")
    private String itemType;

    @NotBlank(message = "物品ID不能为空")
    private String itemId;

    @NotNull(message = "数量不能为空")
    private Integer quantity;

    private String reason;
}
