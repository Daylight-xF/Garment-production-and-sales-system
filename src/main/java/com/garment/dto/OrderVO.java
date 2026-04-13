package com.garment.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderVO {

    private String id;
    private String orderNo;
    private String customerId;
    private String customerName;
    private Double totalAmount;
    private String status;
    private String createBy;
    private String createByName;
    private String approveBy;
    private String approveByName;
    private Date approveTime;
    private Date shipTime;
    private Date completeTime;
    private String approveRemark;
    private String remark;
    private Date createTime;
    private Date updateTime;
    private List<OrderItemDTO> items;
    private List<OrderLogVO> logs;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class OrderLogVO {
        private String id;
        private String operator;
        private String operatorName;
        private String action;
        private String remark;
        private Date createTime;
    }
}
