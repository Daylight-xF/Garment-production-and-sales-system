package com.garment.service.impl;

import com.garment.dto.ProductDefinitionCreateRequest;
import com.garment.exception.BusinessException;
import com.garment.dto.ProductDefinitionVO;
import com.garment.model.ProductDefinition;
import com.garment.model.RawMaterial;
import com.garment.repository.ProductDefinitionRepository;
import com.garment.repository.RawMaterialRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductDefinitionServiceImplTest {

    @Mock
    private ProductDefinitionRepository productDefinitionRepository;

    @Mock
    private RawMaterialRepository rawMaterialRepository;

    @Mock
    private MongoTemplate mongoTemplate;

    @InjectMocks
    private ProductDefinitionServiceImpl productDefinitionService;

    @Test
    void getProductDefinitionListShouldCalculateMaterialAndUnitCostFromCurrentRawMaterialPrices() {
        ProductDefinition.ProductMaterial fabric = new ProductDefinition.ProductMaterial("material-1", "棉布", "面料", 2.5, "米");
        ProductDefinition.ProductMaterial button = new ProductDefinition.ProductMaterial("material-2", "纽扣", "辅料", 4.0, "颗");

        ProductDefinition definition = new ProductDefinition();
        definition.setId("product-1");
        definition.setProductCode("P001");
        definition.setProductName("衬衫");
        definition.setCategory("上装");
        definition.setStatus("启用");
        definition.setMaterials(Arrays.asList(fabric, button));

        RawMaterial fabricMaterial = new RawMaterial();
        fabricMaterial.setId("material-1");
        fabricMaterial.setPrice(8.0);

        RawMaterial buttonMaterial = new RawMaterial();
        buttonMaterial.setId("material-2");
        buttonMaterial.setPrice(0.5);

        when(mongoTemplate.count(any(Query.class), eq(ProductDefinition.class))).thenReturn(1L);
        when(mongoTemplate.find(any(Query.class), eq(ProductDefinition.class))).thenReturn(Collections.singletonList(definition));
        when(rawMaterialRepository.findById("material-1")).thenReturn(Optional.of(fabricMaterial));
        when(rawMaterialRepository.findById("material-2")).thenReturn(Optional.of(buttonMaterial));

        Page<ProductDefinitionVO> result = productDefinitionService.getProductDefinitionList("", "", "", PageRequest.of(0, 10));

        assertThat(result.getContent()).hasSize(1);
        ProductDefinitionVO vo = result.getContent().get(0);
        assertThat(vo.getUnitCost()).isEqualTo(22.0);
        assertThat(vo.getMaterials()).hasSize(2);
        assertThat(vo.getMaterials().get(0).getMaterialPrice()).isEqualTo(8.0);
        assertThat(vo.getMaterials().get(0).getMaterialCost()).isEqualTo(20.0);
        assertThat(vo.getMaterials().get(1).getMaterialPrice()).isEqualTo(0.5);
        assertThat(vo.getMaterials().get(1).getMaterialCost()).isEqualTo(2.0);
    }

    @Test
    void createProductDefinitionShouldTranslateDuplicateKeyException() {
        ProductDefinitionCreateRequest request = new ProductDefinitionCreateRequest();
        request.setProductCode("P001");
        request.setProductName("衬衫");
        request.setCategory("上装");
        request.setStatus("启用");

        when(productDefinitionRepository.existsByProductCode("P001")).thenReturn(false);
        when(productDefinitionRepository.save(any(ProductDefinition.class)))
                .thenThrow(new DuplicateKeyException("duplicate product code"));

        assertThatThrownBy(() -> productDefinitionService.createProductDefinition(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("产品编号已存在");
    }
}
