package com.garment.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Date;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "order_items")
public class OrderItem {

    @Id
    private String id;

    private String orderId;

    private String productId;

    private String productCode;

    private String productName;

    private String specification;

    private Integer quantity;

    private Double unitPrice;

    private Double amount;

    @CreatedDate
    private Date createTime;
}
