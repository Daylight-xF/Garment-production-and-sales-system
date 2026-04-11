package com.garment.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SalesOverviewVO {

    private Double totalAmount;
    private Double monthlyAmount;
    private Long totalOrders;
    private Long monthlyOrders;
    private Double avgOrderAmount;
    private Long customerCount;
}
