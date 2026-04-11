package com.garment.dto;

import lombok.Data;

@Data
public class RawMaterialUpdateRequest {

    private String name;

    private String category;

    private String specification;

    private String unit;

    private String location;

    private String supplier;

    private Double price;

    private String description;
}
