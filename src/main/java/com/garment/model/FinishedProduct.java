package com.garment.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Date;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "finished_products")
@CompoundIndex(
        name = "finished_product_identity_unique",
        def = "{'productCode': 1, 'name': 1, 'color': 1, 'size': 1, 'batchNo': 1}",
        unique = true,
        sparse = true
)
public class FinishedProduct {

    @Id
    private String id;

    private String productCode;

    private String name;

    private String category;

    private String color;

    private String size;

    private String batchNo;

    private String unit;

    private Integer quantity;

    private Integer alertThreshold;

    private List<LocationInfo> locations;

    private Double price;

    private Double costPrice;

    private String description;

    @CreatedDate
    private Date createTime;

    @LastModifiedDate
    private Date updateTime;

    @Version
    private Long version;
}
