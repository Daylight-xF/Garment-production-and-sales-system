package com.garment.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Date;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "production_plans")
public class ProductionPlan {

    @Id
    private String id;

    private String batchNo;

    private String productDefinitionId;

    private String productCode;

    private String productName;

    private String category;

    private Integer quantity;

    private Integer completedQuantity = 0;

    private Integer stockedInQuantity = 0;

    private String unit;

    private Date startDate;

    private Date endDate;

    private String status;

    private String color;

    private String size;

    private String description;

    private String createBy;

    private Boolean materialsDeducted = false;

    private Boolean materialsRestoreInProgress = false;

    @CreatedDate
    private Date createTime;

    @LastModifiedDate
    private Date updateTime;

    @Version
    private Long version;
}
