package com.garment.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InventoryOverviewVO {

    private Long rawMaterialCount;
    private Long finishedProductCount;
    private Long rawMaterialTotalQuantity;
    private Long finishedProductTotalQuantity;
    private Long alertCount;
}
