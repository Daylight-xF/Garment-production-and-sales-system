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
public class TaskVO {

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
    private Integer progress;

    private Integer planQuantity;

    private Integer completedQuantity;

    private String status;
    private Date startDate;
    private Date endDate;
    private String description;
    private String createBy;
    private Date createTime;
    private Date updateTime;
}
