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
public class InventoryRecordVO {

    private String id;
    private String inventoryType;
    private String itemType;
    private String itemId;
    private String itemName;
    private Integer quantity;
    private String operator;
    private String operatorName;
    private String reason;
    private Date createTime;
}
