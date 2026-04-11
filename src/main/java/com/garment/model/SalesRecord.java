package com.garment.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Date;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "sales_records")
public class SalesRecord {

    @Id
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

    @CreatedDate
    private Date createTime;

    @LastModifiedDate
    private Date updateTime;
}
