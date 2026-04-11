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
@Document(collection = "order_logs")
public class OrderLog {

    @Id
    private String id;

    private String orderId;

    private String operator;

    private String operatorName;

    private String action;

    private String remark;

    @CreatedDate
    private Date createTime;
}
