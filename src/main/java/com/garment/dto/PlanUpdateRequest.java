package com.garment.dto;

import lombok.Data;

import java.util.Date;

@Data
public class PlanUpdateRequest {

    private String batchNo;

    private String productDefinitionId;

    private String productName;

    private Integer quantity;

    private String unit;

    private Date startDate;

    private Date endDate;

    private String color;

    private String size;

    private String description;
}
