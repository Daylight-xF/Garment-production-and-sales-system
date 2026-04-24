package com.garment.service.impl;

import com.garment.dto.PlanCreateRequest;
import com.garment.dto.StockInOutRequest;
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
import static org.mockito.Mockito.doThrow;
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
        plan.setProductDefinitionId("def-cancel-conflict");
        ProductDefinition definition = definitionWithMaterial("def-cancel-conflict", "raw-conflict", 1.0);

        when(productionPlanRepository.findById("plan-cancel-conflict")).thenReturn(Optional.of(plan));
        when(productDefinitionRepository.findById("def-cancel-conflict")).thenReturn(Optional.of(definition));
        when(mongoAtomicOpsService.transitionPlanStatus(eq("plan-cancel-conflict"), eq("PENDING"), eq("CANCELLED"), any()))
                .thenReturn(false);

        assertThatThrownBy(() -> productionPlanService.approvePlan("plan-cancel-conflict", "CANCELLED"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("计划状态已变更，请刷新后再操作");
    }

    @Test
    void approvePlanShouldMarkMaterialsRestoreInProgressInAtomicCancellationPayload() {
        ProductionPlan before = approvedPlan("plan-cancel-success", "PENDING");
        before.setProductDefinitionId("def-cancel");
        ProductionPlan after = approvedPlan("plan-cancel-success", "CANCELLED");
        ProductDefinition definition = definitionWithMaterial("def-cancel", "raw-1", 2.0);

        when(productionPlanRepository.findById("plan-cancel-success"))
                .thenReturn(Optional.of(before))
                .thenReturn(Optional.of(after));
        when(mongoAtomicOpsService.transitionPlanStatus(eq("plan-cancel-success"), eq("PENDING"), eq("CANCELLED"), any()))
                .thenReturn(true);
        when(productDefinitionRepository.findById("def-cancel")).thenReturn(Optional.of(definition));
        when(mongoAtomicOpsService.completePlanMaterialsRestore("plan-cancel-success")).thenReturn(true);

        productionPlanService.approvePlan("plan-cancel-success", "CANCELLED");

        ArgumentCaptor<Document> payloadCaptor = ArgumentCaptor.forClass(Document.class);
        verify(mongoAtomicOpsService).transitionPlanStatus(eq("plan-cancel-success"), eq("PENDING"), eq("CANCELLED"), payloadCaptor.capture());
        assertThat(payloadCaptor.getValue().getBoolean("materialsRestoreInProgress")).isTrue();
        verify(mongoAtomicOpsService).completePlanMaterialsRestore("plan-cancel-success");
    }

    @Test
    void approvePlanShouldReleaseRestoreMarkerWhenCancellationRestoreFails() {
        ProductionPlan before = approvedPlan("plan-cancel-restore-fail", "PENDING");
        before.setProductDefinitionId("def-cancel-fail");
        ProductDefinition definition = definitionWithMaterial("def-cancel-fail", "raw-2", 1.5);

        when(productionPlanRepository.findById("plan-cancel-restore-fail")).thenReturn(Optional.of(before));
        when(productDefinitionRepository.findById("def-cancel-fail")).thenReturn(Optional.of(definition));
        when(mongoAtomicOpsService.transitionPlanStatus(eq("plan-cancel-restore-fail"), eq("PENDING"), eq("CANCELLED"), any()))
                .thenReturn(true);
        when(mongoAtomicOpsService.releasePlanMaterialsRestore("plan-cancel-restore-fail")).thenReturn(true);
        doThrow(new RuntimeException("restore failed"))
                .when(inventoryService).stockIn(any(StockInOutRequest.class), eq("system"));

        assertThatThrownBy(() -> productionPlanService.approvePlan("plan-cancel-restore-fail", "CANCELLED"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("restore failed");

        verify(mongoAtomicOpsService).releasePlanMaterialsRestore("plan-cancel-restore-fail");
    }

    @Test
    void approvePlanShouldKeepRestoreMarkerWhenCompensationRollbackFails() {
        ProductionPlan before = approvedPlan("plan-cancel-compensation-fail", "PENDING");
        before.setProductDefinitionId("def-cancel-compensation-fail");
        ProductDefinition definition = definitionWithMaterials(
                "def-cancel-compensation-fail",
                new ProductDefinition.ProductMaterial("raw-a", "Cotton", null, 1.0, "kg"),
                new ProductDefinition.ProductMaterial("raw-b", "Linen", null, 1.0, "kg")
        );

        when(productionPlanRepository.findById("plan-cancel-compensation-fail")).thenReturn(Optional.of(before));
        when(productDefinitionRepository.findById("def-cancel-compensation-fail")).thenReturn(Optional.of(definition));
        when(mongoAtomicOpsService.transitionPlanStatus(eq("plan-cancel-compensation-fail"), eq("PENDING"), eq("CANCELLED"), any()))
                .thenReturn(true);
        when(inventoryService.stockIn(any(StockInOutRequest.class), eq("system")))
                .thenReturn(null)
                .thenThrow(new RuntimeException("restore failed"));
        when(inventoryService.stockOut(any(StockInOutRequest.class), eq("system")))
                .thenThrow(new RuntimeException("rollback failed"));

        assertThatThrownBy(() -> productionPlanService.approvePlan("plan-cancel-compensation-fail", "CANCELLED"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("补偿回滚失败");

        verify(mongoAtomicOpsService, never()).releasePlanMaterialsRestore("plan-cancel-compensation-fail");
    }

    @Test
    void approvePlanShouldKeepRestoreMarkerWhenFinalizeStateUpdateFails() {
        ProductionPlan before = approvedPlan("plan-cancel-finalize-fail", "PENDING");
        before.setProductDefinitionId("def-cancel-finalize-fail");
        ProductDefinition definition = definitionWithMaterial("def-cancel-finalize-fail", "raw-finalize", 1.0);

        when(productionPlanRepository.findById("plan-cancel-finalize-fail")).thenReturn(Optional.of(before));
        when(productDefinitionRepository.findById("def-cancel-finalize-fail")).thenReturn(Optional.of(definition));
        when(mongoAtomicOpsService.transitionPlanStatus(eq("plan-cancel-finalize-fail"), eq("PENDING"), eq("CANCELLED"), any()))
                .thenReturn(true);
        when(mongoAtomicOpsService.completePlanMaterialsRestore("plan-cancel-finalize-fail")).thenReturn(false);

        assertThatThrownBy(() -> productionPlanService.approvePlan("plan-cancel-finalize-fail", "CANCELLED"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("返还标记未能完成更新");

        verify(mongoAtomicOpsService, never()).releasePlanMaterialsRestore("plan-cancel-finalize-fail");
    }

    @Test
    void deletePlanShouldRejectWhenMaterialsRestoreIsInProgress() {
        ProductionPlan cancelledPlan = approvedPlan("plan-delete-in-progress", "CANCELLED");
        cancelledPlan.setMaterialsRestoreInProgress(true);

        when(productionPlanRepository.findById("plan-delete-in-progress")).thenReturn(Optional.of(cancelledPlan));

        assertThatThrownBy(() -> productionPlanService.deletePlan("plan-delete-in-progress"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("返还处理中");
    }

    @Test
    void deletePlanShouldRejectWhenRestoreMetadataIsMissing() {
        ProductionPlan cancelledPlan = approvedPlan("plan-delete-missing-restore", "CANCELLED");
        cancelledPlan.setProductDefinitionId("def-delete-missing");

        when(productionPlanRepository.findById("plan-delete-missing-restore")).thenReturn(Optional.of(cancelledPlan));
        when(productDefinitionRepository.findById("def-delete-missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productionPlanService.deletePlan("plan-delete-missing-restore"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("返还信息缺失");
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
    void startProductionShouldSurfaceRollbackFailureWhenTaskSaveFails() {
        ProductionPlan before = approvedPlan("plan-start-rollback-fail", "APPROVED");

        when(productionPlanRepository.findById("plan-start-rollback-fail")).thenReturn(Optional.of(before));
        when(productionTaskRepository.findByPlanId("plan-start-rollback-fail")).thenReturn(Collections.emptyList());
        when(mongoAtomicOpsService.transitionPlanStatus("plan-start-rollback-fail", "APPROVED", "IN_PROGRESS", null))
                .thenReturn(true);
        when(mongoAtomicOpsService.transitionPlanStatus("plan-start-rollback-fail", "IN_PROGRESS", "APPROVED", null))
                .thenReturn(false);
        when(productionTaskRepository.save(any(ProductionTask.class))).thenThrow(new RuntimeException("task save failed"));

        assertThatThrownBy(() -> productionPlanService.startProduction("plan-start-rollback-fail", "starter"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("计划状态回滚未生效");
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

    private ProductDefinition definitionWithMaterial(String definitionId, String materialId, double unitQuantity) {
        ProductDefinition.ProductMaterial material = new ProductDefinition.ProductMaterial();
        material.setMaterialId(materialId);
        material.setMaterialName("Cotton");
        material.setQuantity(unitQuantity);
        material.setUnit("kg");

        ProductDefinition definition = new ProductDefinition();
        definition.setId(definitionId);
        definition.setMaterials(Collections.singletonList(material));
        return definition;
    }

    private ProductDefinition definitionWithMaterials(String definitionId, ProductDefinition.ProductMaterial... materials) {
        ProductDefinition definition = new ProductDefinition();
        definition.setId(definitionId);
        definition.setMaterials(java.util.Arrays.asList(materials));
        return definition;
    }
}
