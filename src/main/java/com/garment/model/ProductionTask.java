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
@Document(collection = "production_tasks")
public class ProductionTask {

    @Id
    private String id;

    private String planId;

    private String batchNo;

    private String productName;

    private String productCode;

    private String color;

    private String size;

    private String taskName;

    private String assignee;

    private String assigneeName;

    private Integer progress = 0;

    private String status;

    private Date startDate;

    private Date endDate;

    private String description;

    private Integer planQuantity = 0;

    private Integer completedQuantity = 0;

    private String createBy;

    @CreatedDate
    private Date createTime;

    @LastModifiedDate
    private Date updateTime;
}
