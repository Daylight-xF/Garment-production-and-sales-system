package com.garment.service;

import com.garment.dto.ProductDefinitionCreateRequest;
import com.garment.dto.ProductDefinitionUpdateRequest;
import com.garment.dto.ProductDefinitionVO;
import org.springframework.data.domain.Page;

public interface ProductDefinitionService {

    Page<ProductDefinitionVO> getProductDefinitionList(String name, String category, org.springframework.data.domain.Pageable pageable);

    ProductDefinitionVO getProductDefinitionById(String id);

    ProductDefinitionVO createProductDefinition(ProductDefinitionCreateRequest request);

    ProductDefinitionVO updateProductDefinition(String id, ProductDefinitionUpdateRequest request);

    void deleteProductDefinition(String id);
}
