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
    private String planName;
    private String taskName;
    private String assignee;
    private String assigneeName;
    private Integer progress;
    private String status;
    private Date startDate;
    private Date endDate;
    private String description;
    private String createBy;
    private Date createTime;
    private Date updateTime;
}
