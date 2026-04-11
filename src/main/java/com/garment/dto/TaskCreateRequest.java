package com.garment.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import java.util.Date;

@Data
public class TaskCreateRequest {

    @NotBlank(message = "关联计划ID不能为空")
    private String planId;

    @NotBlank(message = "任务名称不能为空")
    private String taskName;

    private String assignee;

    private Date startDate;

    private Date endDate;

    private String description;
}
