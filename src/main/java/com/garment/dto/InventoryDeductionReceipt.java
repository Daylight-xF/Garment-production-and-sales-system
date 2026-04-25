package com.garment.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class InventoryDeductionReceipt {

    private String itemType;

    private String itemId;

    private String itemName;

    private Integer quantity;

    private boolean totalOnly;

    private List<LocationDeduction> locationDeductions;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LocationDeduction {
        private String location;
        private Integer quantity;
        private Date createdAt;
    }
}
