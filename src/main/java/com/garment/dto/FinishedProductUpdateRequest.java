package com.garment.dto;

import lombok.Data;

@Data
public class FinishedProductUpdateRequest {

    private String name;

    private String category;

    private String color;

    private String size;

    private String unit;

    private String location;

    private Double price;

    private Double costPrice;

    private String description;
}
