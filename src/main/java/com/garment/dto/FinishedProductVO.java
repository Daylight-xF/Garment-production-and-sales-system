package com.garment.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import com.garment.model.LocationInfo;

import java.util.Date;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FinishedProductVO {

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
    private Date createTime;
    private Date updateTime;
}
