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
public class InventoryAlertVO {

    private String id;
    private String itemType;
    private String itemId;
    private String itemName;
    private Integer currentQuantity;
    private Integer threshold;
    private String status;
    private Date createTime;
    private Date handleTime;
    private String handleBy;
}
