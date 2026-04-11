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
public class RawMaterialVO {

    private String id;
    private String name;
    private String category;
    private String specification;
    private String unit;
    private Integer quantity;
    private Integer alertThreshold;
    private String location;
    private String supplier;
    private Double price;
    private String description;
    private Date createTime;
    private Date updateTime;
}
