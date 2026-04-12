package com.garment.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import java.util.List;

@Data
public class ProductDefinitionCreateRequest {

    @NotBlank(message = "产品编号不能为空")
    private String productCode;

    @NotBlank(message = "产品名称不能为空")
    private String productName;

    @NotBlank(message = "产品分类不能为空")
    private String category;

    private String status = "启用";

    private String description;

    private List<MaterialItem> materials;

    @Data
    public static class MaterialItem {
        private String materialId;
        private Double quantity;
    }
}
