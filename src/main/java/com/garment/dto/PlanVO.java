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
    private String planName;
    private String productName;
    private Integer quantity;
    private Integer completedQuantity;
    private String unit;
    private Date startDate;
    private Date endDate;
    private String status;
    private String description;
    private String createBy;
    private String createByName;
    private Date createTime;
    private Date updateTime;
}
