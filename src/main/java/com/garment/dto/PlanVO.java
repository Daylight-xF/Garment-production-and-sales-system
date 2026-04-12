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
public class PlanVO {

    private String id;
    private String batchNo;
    private String productDefinitionId;
    private String productCode;
    private String productName;
    private Integer quantity;
    private Integer completedQuantity;

    private Integer stockedInQuantity = 0;

    private String unit;
    private Date startDate;
    private Date endDate;
    private String status;
    private String description;
    private String createBy;
    private String createByName;

    private Boolean materialsDeducted;

    private Date createTime;
    private Date updateTime;
}
