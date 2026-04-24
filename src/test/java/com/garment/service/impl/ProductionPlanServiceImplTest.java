package com.garment.service.impl;

import com.garment.dto.PlanCreateRequest;
import com.garment.exception.BusinessException;
import com.garment.model.ProductDefinition;
import com.garment.model.ProductionPlan;
import com.garment.model.ProductionTask;
import com.garment.repository.ProductDefinitionRepository;
import com.garment.repository.ProductionPlanRepository;
import com.garment.repository.ProductionTaskRepository;
import com.garment.repository.RawMaterialRepository;
import com.garment.repository.UserRepository;
import com.garment.service.InventoryService;
import com.garment.service.support.MongoAtomicOpsService;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.index.Indexed;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
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

    @Mock
    private MongoAtomicOpsService mongoAtomicOpsService;

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

    @Test
    void productionTaskAutoCreateKeyShouldUseSparseUniqueIndex() throws NoSuchFieldException {
        Field field = ProductionTask.class.getDeclaredField("autoCreateKey");
        Indexed indexed = field.getAnnotation(Indexed.class);

        assertThat(indexed).isNotNull();
        assertThat(indexed.unique()).isTrue();
        assertThat(indexed.sparse()).isTrue();
    }

    @Test
    void approvePlanShouldThrowBusinessConflictWhenAtomicApprovalFails() {
        ProductionPlan plan = approvedPlan("plan-approve-conflict", "PENDING");

        when(productionPlanRepository.findById("plan-approve-conflict")).thenReturn(Optional.of(plan));
        when(mongoAtomicOpsService.transitionPlanStatus("plan-approve-conflict", "PENDING", "APPROVED", null))
                .thenReturn(false);

        assertThatThrownBy(() -> productionPlanService.approvePlan("plan-approve-conflict", "APPROVED"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("计划状态已变更，请刷新后再操作");
    }

    @Test
    void approvePlanShouldThrowBusinessConflictWhenAtomicCancellationFails() {
        ProductionPlan plan = approvedPlan("plan-cancel-conflict", "PENDING");

        when(productionPlanRepository.findById("plan-cancel-conflict")).thenReturn(Optional.of(plan));
        when(mongoAtomicOpsService.transitionPlanStatus(eq("plan-cancel-conflict"), eq("PENDING"), eq("CANCELLED"), any()))
                .thenReturn(false);

        assertThatThrownBy(() -> productionPlanService.approvePlan("plan-cancel-conflict", "CANCELLED"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("计划状态已变更，请刷新后再操作");
    }

    @Test
    void approvePlanShouldClearMaterialsDeductedInAtomicCancellationPayload() {
        ProductionPlan before = approvedPlan("plan-cancel-success", "PENDING");
        before.setProductDefinitionId("def-cancel");
        ProductionPlan after = approvedPlan("plan-cancel-success", "CANCELLED");

        when(productionPlanRepository.findById("plan-cancel-success"))
                .thenReturn(Optional.of(before))
                .thenReturn(Optional.of(after));
        when(mongoAtomicOpsService.transitionPlanStatus(eq("plan-cancel-success"), eq("PENDING"), eq("CANCELLED"), any()))
                .thenReturn(true);
        when(productDefinitionRepository.findById("def-cancel")).thenReturn(Optional.empty());

        productionPlanService.approvePlan("plan-cancel-success", "CANCELLED");

        ArgumentCaptor<Document> payloadCaptor = ArgumentCaptor.forClass(Document.class);
        verify(mongoAtomicOpsService).transitionPlanStatus(eq("plan-cancel-success"), eq("PENDING"), eq("CANCELLED"), payloadCaptor.capture());
        assertThat(payloadCaptor.getValue().getBoolean("materialsDeducted")).isFalse();
    }

    @Test
    void startProductionShouldThrowBusinessConflictWhenAtomicTransitionFails() {
        ProductionPlan plan = approvedPlan("plan-start-conflict", "APPROVED");

        when(productionPlanRepository.findById("plan-start-conflict")).thenReturn(Optional.of(plan));
        when(productionTaskRepository.findByPlanId("plan-start-conflict")).thenReturn(Collections.emptyList());
        when(mongoAtomicOpsService.transitionPlanStatus("plan-start-conflict", "APPROVED", "IN_PROGRESS", null))
                .thenReturn(false);

        assertThatThrownBy(() -> productionPlanService.startProduction("plan-start-conflict", "starter"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("计划状态已变更，请刷新后再操作");

        verify(productionTaskRepository, never()).save(any(ProductionTask.class));
    }

    @Test
    void startProductionShouldPersistAutoCreateKeyForGeneratedTask() {
        ProductionPlan before = approvedPlan("plan-start-success", "APPROVED");
        ProductionPlan after = approvedPlan("plan-start-success", "IN_PROGRESS");

        when(productionPlanRepository.findById("plan-start-success"))
                .thenReturn(Optional.of(before))
                .thenReturn(Optional.of(after));
        when(productionTaskRepository.findByPlanId("plan-start-success")).thenReturn(Collections.emptyList());
        when(mongoAtomicOpsService.transitionPlanStatus("plan-start-success", "APPROVED", "IN_PROGRESS", null))
                .thenReturn(true);
        when(productionTaskRepository.save(any(ProductionTask.class))).thenAnswer(invocation -> invocation.getArgument(0));
        productionPlanService.startProduction("plan-start-success", "starter");

        ArgumentCaptor<ProductionTask> taskCaptor = ArgumentCaptor.forClass(ProductionTask.class);
        verify(productionTaskRepository).save(taskCaptor.capture());
        assertThat(taskCaptor.getValue().getAutoCreateKey()).isEqualTo("AUTO:plan-start-success");
        verify(mongoAtomicOpsService).transitionPlanStatus("plan-start-success", "APPROVED", "IN_PROGRESS", null);
    }

    @Test
    void startProductionShouldRollbackPlanStatusWhenTaskSaveFails() {
        ProductionPlan before = approvedPlan("plan-start-rollback", "APPROVED");

        when(productionPlanRepository.findById("plan-start-rollback")).thenReturn(Optional.of(before));
        when(productionTaskRepository.findByPlanId("plan-start-rollback")).thenReturn(Collections.emptyList());
        when(mongoAtomicOpsService.transitionPlanStatus("plan-start-rollback", "APPROVED", "IN_PROGRESS", null))
                .thenReturn(true);
        when(mongoAtomicOpsService.transitionPlanStatus("plan-start-rollback", "IN_PROGRESS", "APPROVED", null))
                .thenReturn(true);
        when(productionTaskRepository.save(any(ProductionTask.class))).thenThrow(new RuntimeException("task save failed"));

        assertThatThrownBy(() -> productionPlanService.startProduction("plan-start-rollback", "starter"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("task save failed");

        verify(mongoAtomicOpsService).transitionPlanStatus("plan-start-rollback", "IN_PROGRESS", "APPROVED", null);
    }

    @Test
    void completePlanShouldThrowBusinessConflictWhenAtomicTransitionFails() {
        ProductionPlan plan = approvedPlan("plan-complete-conflict", "IN_PROGRESS");
        ProductionTask task = new ProductionTask();
        task.setStatus("COMPLETED");

        when(productionPlanRepository.findById("plan-complete-conflict")).thenReturn(Optional.of(plan));
        when(productionTaskRepository.findByPlanId("plan-complete-conflict")).thenReturn(Collections.singletonList(task));
        when(mongoAtomicOpsService.transitionPlanStatus(eq("plan-complete-conflict"), eq("IN_PROGRESS"), eq("COMPLETED"), any()))
                .thenReturn(false);

        assertThatThrownBy(() -> productionPlanService.completePlan("plan-complete-conflict"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("计划状态已变更，请刷新后再操作");
    }

    private ProductionPlan approvedPlan(String id, String status) {
        ProductionPlan plan = new ProductionPlan();
        plan.setId(id);
        plan.setBatchNo("BATCH-" + id);
        plan.setProductCode("P001");
        plan.setProductName("Jacket");
        plan.setQuantity(12);
        plan.setStatus(status);
        plan.setMaterialsDeducted(true);
        return plan;
    }
}
