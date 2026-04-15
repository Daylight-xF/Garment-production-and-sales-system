package com.garment.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductDefinitionVO {

    private String id;
    private String productCode;
    private String productName;
    private String category;
    private String status;
    private String description;
    private List<MaterialVO> materials;
    private Double unitCost;
    private Date createTime;
    private Date updateTime;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MaterialVO {
        private String materialId;
        private String materialName;
        private String materialCategory;
        private Double quantity;
        private String unit;
        private Double materialPrice;
        private Double materialCost;
    }
}
