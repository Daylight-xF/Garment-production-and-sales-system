package com.garment.dto;

import lombok.Data;

@Data
public class MoveLocationRequest {

    private String sourceLocation;

    private String targetLocation;

    private Integer quantity;
}