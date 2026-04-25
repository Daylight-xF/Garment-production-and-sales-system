package com.garment.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Date;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "inventory_alerts")
public class InventoryAlert {

    @Id
    private String id;

    private String itemType;

    private String itemId;

    private String itemName;

    private Integer currentQuantity;

    private Integer threshold;

    private String status;

    @Indexed(unique = true, sparse = true)
    private String openAlertKey;

    @CreatedDate
    private Date createTime;

    private Date handleTime;

    private String handleBy;

    @Version
    private Long version;
}
