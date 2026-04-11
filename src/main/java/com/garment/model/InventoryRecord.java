package com.garment.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Date;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "inventory_records")
public class InventoryRecord {

    @Id
    private String id;

    private String inventoryType;

    private String itemType;

    private String itemId;

    private String itemName;

    private Integer quantity;

    private String operator;

    private String operatorName;

    private String reason;

    @CreatedDate
    private Date createTime;
}
