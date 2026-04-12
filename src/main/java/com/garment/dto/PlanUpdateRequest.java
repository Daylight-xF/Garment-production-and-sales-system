package com.garment.dto;

import lombok.Data;

import java.util.Date;

@Data
public class PlanUpdateRequest {

    private String planName;

    private String productDefinitionId;

    private String productName;

    private Integer quantity;

    private String unit;

    private Date startDate;

    private Date endDate;

    private String description;
}
