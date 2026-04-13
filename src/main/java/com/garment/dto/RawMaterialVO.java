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
public class RawMaterialVO {

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
    private Date createTime;
    private Date updateTime;
}
