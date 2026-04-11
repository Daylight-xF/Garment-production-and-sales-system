package com.garment.dto;

import lombok.Data;

import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotEmpty;
import java.util.List;

@Data
public class OrderCreateRequest {

    @NotBlank(message = "客户ID不能为空")
    private String customerId;

    private String customerName;

    @NotEmpty(message = "订单明细不能为空")
    @Valid
    private List<OrderItemDTO> items;

    private String remark;
}
