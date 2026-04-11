package com.garment.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.util.Date;

@Data
public class SalesRecordCreateRequest {

    @NotBlank(message = "客户ID不能为空")
    private String customerId;

    private String productId;

    private String productName;

    @NotNull(message = "数量不能为空")
    private Integer quantity;

    @NotNull(message = "单价不能为空")
    private Double unitPrice;

    private Date saleDate;

    private String remark;
}
