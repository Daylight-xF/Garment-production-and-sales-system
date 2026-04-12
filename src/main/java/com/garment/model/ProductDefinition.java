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
@Document(collection = "product_definitions")
public class ProductDefinition {

    @Id
    private String id;

    private String productCode;

    private String productName;

    private String category;

    private String status;

    private String description;

    private List<ProductMaterial> materials;

    @CreatedDate
    private Date createTime;

    @LastModifiedDate
    private Date updateTime;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProductMaterial {
        private String materialId;
        private String materialName;
        private String materialCategory;
        private Double quantity;
        private String unit;
    }
}
