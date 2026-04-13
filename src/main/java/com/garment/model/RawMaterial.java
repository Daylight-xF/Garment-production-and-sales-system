package com.garment.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Date;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "raw_materials")
public class RawMaterial {

    @Id
    private String id;

    private String name;

    private String category;

    private String specification;

    private String unit;

    private Integer quantity;

    private Integer alertThreshold;

    private List<LocationInfo> locations;

    private String supplier;

    private Double price;

    private String description;

    @CreatedDate
    private Date createTime;

    @LastModifiedDate
    private Date updateTime;
}
