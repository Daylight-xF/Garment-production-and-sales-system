package com.garment.service.impl;

import com.garment.dto.PlanCreateRequest;
import com.garment.model.ProductDefinition;
import com.garment.model.ProductionPlan;
import com.garment.repository.ProductDefinitionRepository;
import com.garment.repository.ProductionPlanRepository;
import com.garment.repository.ProductionTaskRepository;
import com.garment.repository.RawMaterialRepository;
import com.garment.repository.UserRepository;
import com.garment.service.InventoryService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductionPlanServiceImplTest {

    @Mock
    private ProductionPlanRepository productionPlanRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ProductDefinitionRepository productDefinitionRepository;

    @Mock
    private ProductionTaskRepository productionTaskRepository;

    @Mock
    private RawMaterialRepository rawMaterialRepository;

    @Mock
    private InventoryService inventoryService;

    @InjectMocks
    private ProductionPlanServiceImpl productionPlanService;

    @Test
    void createPlanShouldPersistProductDefinitionCategory() {
        ProductDefinition definition = new ProductDefinition();
        definition.setId("def-1");
        definition.setProductCode("P001");
        definition.setProductName("Casual Pants");
        definition.setCategory("BOTTOM");

        PlanCreateRequest request = new PlanCreateRequest();
        request.setBatchNo("BATCH-100");
        request.setProductDefinitionId("def-1");
        request.setQuantity(20);
        request.setColor("Black");
        request.setSize("L");
        request.setUnit("piece");

        when(productDefinitionRepository.findById("def-1")).thenReturn(Optional.of(definition));
        when(productionPlanRepository.save(any(ProductionPlan.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(userRepository.findById("admin-1")).thenReturn(Optional.empty());
        when(productionTaskRepository.findByPlanId(any())).thenReturn(Collections.emptyList());

        productionPlanService.createPlan(request, "admin-1");

        ArgumentCaptor<ProductionPlan> planCaptor = ArgumentCaptor.forClass(ProductionPlan.class);
        verify(productionPlanRepository).save(planCaptor.capture());
        assertThat(planCaptor.getValue().getCategory()).isEqualTo("BOTTOM");
    }
}
