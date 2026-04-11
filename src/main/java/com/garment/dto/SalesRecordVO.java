package com.garment.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SalesRecordVO {

    private String id;
    private String customerId;
    private String customerName;
    private String productId;
    private String productName;
    private Integer quantity;
    private Double unitPrice;
    private Double amount;
    private Date saleDate;
    private String createBy;
    private String createByName;
    private String remark;
    private Date createTime;
    private Date updateTime;
}
