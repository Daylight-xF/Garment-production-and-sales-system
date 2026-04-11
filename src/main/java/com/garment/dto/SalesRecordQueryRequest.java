package com.garment.dto;

import lombok.Data;

import java.util.Date;

@Data
public class SalesRecordQueryRequest {

    private String customerId;

    private Date startDate;

    private Date endDate;

    private String keyword;

    private int page = 1;

    private int size = 10;
}
