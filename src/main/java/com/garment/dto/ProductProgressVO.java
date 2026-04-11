package com.garment.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductProgressVO {

    private String productName;
    private Integer plannedQuantity;
    private Integer completedQuantity;
    private Double progress;
}
