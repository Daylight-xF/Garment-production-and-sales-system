package com.garment.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderItemDTO {

    @NotBlank(message = "产品ID不能为空")
    private String productId;

    private String productCode;

    private String productName;

    private String color;

    private String size;

    @Min(value = 1, message = "数量不能小于1")
    private Integer quantity;

    private Double unitPrice;

    private Double amount;
}
