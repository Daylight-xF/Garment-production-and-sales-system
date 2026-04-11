package com.garment.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductionOverviewVO {

    private Long totalPlans;
    private Long completedPlans;
    private Long inProgressPlans;
    private Double planCompletionRate;
    private Long totalTasks;
    private Long completedTasks;
    private Double taskCompletionRate;
}
