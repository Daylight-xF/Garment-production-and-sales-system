package com.garment.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Date;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "sales_records")
public class SalesRecord {

    @Id
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

    private List<SalesRecordItem> items;

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

    @CreatedDate
    private Date createTime;

    @LastModifiedDate
    private Date updateTime;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SalesRecordItem {
        private String productId;
        private String productCode;
        private String productName;
        private String specification;
        private Integer quantity;
        private Double unitPrice;
        private Double amount;
    }
}
