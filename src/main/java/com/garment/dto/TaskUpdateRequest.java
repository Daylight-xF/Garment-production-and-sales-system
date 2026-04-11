package com.garment.dto;

import lombok.Data;

import java.util.Date;

@Data
public class TaskUpdateRequest {

    private String taskName;

    private String assignee;

    private Integer progress;

    private String status;

    private Date startDate;

    private Date endDate;

    private String description;
}
