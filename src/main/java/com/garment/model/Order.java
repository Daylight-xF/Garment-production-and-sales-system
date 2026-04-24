package com.garment.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Date;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "orders")
public class Order {

    @Id
    private String id;

    @Indexed(unique = true)
    private String orderNo;

    private String customerId;

    private String customerName;

    private Double totalAmount;

    private String status;

    private String createBy;

    private String createByName;

    private String approveBy;

    private String approveByName;

    private Date approveTime;

    private Date shipTime;

    private Date completeTime;

    private String approveRemark;

    private String remark;

    @CreatedDate
    private Date createTime;

    @LastModifiedDate
    private Date updateTime;

    @Version
    private Long version;
}
