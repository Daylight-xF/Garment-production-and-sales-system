package com.garment.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SalesRecordVO {

    private String id;
    private String orderId;
    private String orderNo;
    private String customerId;
    private String customerName;
    private Integer productCount;
    private Integer totalQuantity;
    private Double totalAmount;
    private Date orderDate;
    private Date shipDate;
    private Date completeDate;
    private List<SalesRecordItemVO> items;
    private String productId;
    private String productCode;
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

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class SalesRecordItemVO {
        private String productId;
        private String productCode;
        private String productName;
        private String specification;
        private Integer quantity;
        private Double unitPrice;
        private Double amount;
    }
}
