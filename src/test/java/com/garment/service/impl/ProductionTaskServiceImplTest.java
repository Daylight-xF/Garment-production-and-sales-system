package com.garment.service.impl;

import com.garment.exception.BusinessException;
import com.garment.model.ProductionPlan;
import com.garment.model.ProductionTask;
import com.garment.repository.ProductionPlanRepository;
import com.garment.repository.ProductionTaskRepository;
import com.garment.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.OptimisticLockingFailureException;

import java.util.Collections;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductionTaskServiceImplTest {

    @Mock
    private ProductionTaskRepository productionTaskRepository;

    @Mock
    private ProductionPlanRepository productionPlanRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private ProductionTaskServiceImpl productionTaskService;

    @Test
    void updateProgressShouldSurfacePlanSummaryOptimisticLockConflictAsBusinessException() {
        ProductionTask task = new ProductionTask();
        task.setId("task-1");
        task.setPlanId("plan-1");
        task.setPlanQuantity(10);
        task.setStatus("IN_PROGRESS");
        task.setCompletedQuantity(2);

        ProductionPlan plan = new ProductionPlan();
        plan.setId("plan-1");
        plan.setCompletedQuantity(2);

        when(productionTaskRepository.findById("task-1")).thenReturn(Optional.of(task));
        when(productionTaskRepository.save(any(ProductionTask.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(productionTaskRepository.findByPlanId("plan-1")).thenReturn(Collections.singletonList(task));
        when(productionPlanRepository.findById("plan-1")).thenReturn(Optional.of(plan));
        when(productionPlanRepository.save(any(ProductionPlan.class)))
                .thenThrow(new OptimisticLockingFailureException("plan summary conflict"));

        assertThatThrownBy(() -> productionTaskService.updateProgress("task-1", 50))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("生产计划汇总已更新，请刷新后重试");
    }
}
