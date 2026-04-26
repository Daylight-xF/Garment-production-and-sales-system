package com.garment.service.impl;

import com.garment.exception.BusinessException;
import com.garment.model.ProductionPlan;
import com.garment.model.ProductionTask;
import com.garment.model.User;
import com.garment.repository.ProductionPlanRepository;
import com.garment.repository.ProductionTaskRepository;
import com.garment.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
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
    void getTaskListShouldFilterAssigneeByVisibleName() {
        ProductionTask matched = new ProductionTask();
        matched.setId("task-1");
        matched.setAssignee("user-1");
        matched.setAssigneeName("生产者08");

        ProductionTask unmatched = new ProductionTask();
        unmatched.setId("task-2");
        unmatched.setAssignee("user-2");
        unmatched.setAssigneeName("生产者05");

        when(productionTaskRepository.findAll()).thenReturn(Arrays.asList(matched, unmatched));

        Page<com.garment.dto.TaskVO> result = productionTaskService.getTaskList(
                "", "生产者08", "", PageRequest.of(0, 10));

        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent().get(0).getId()).isEqualTo("task-1");
    }

    @Test
    void getTaskListShouldStillFilterAssigneeById() {
        ProductionTask matched = new ProductionTask();
        matched.setId("task-id-1");
        matched.setAssignee("user-1");
        matched.setAssigneeName("生产者08");

        ProductionTask unmatched = new ProductionTask();
        unmatched.setId("task-id-2");
        unmatched.setAssignee("user-2");
        unmatched.setAssigneeName("生产者05");

        when(productionTaskRepository.findAll()).thenReturn(Arrays.asList(matched, unmatched));

        Page<com.garment.dto.TaskVO> result = productionTaskService.getTaskList(
                "", "user-1", "", PageRequest.of(0, 10));

        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent().get(0).getId()).isEqualTo("task-id-1");
    }

    @Test
    void updateTaskShouldTranslateOptimisticLockConflictWhenSaveFails() {
        ProductionTask task = new ProductionTask();
        task.setId("task-update-conflict");
        task.setStatus("PENDING");

        com.garment.dto.TaskUpdateRequest request = new com.garment.dto.TaskUpdateRequest();
        request.setTaskName("new name");

        when(productionTaskRepository.findById("task-update-conflict")).thenReturn(Optional.of(task));
        when(productionTaskRepository.save(any(ProductionTask.class)))
                .thenThrow(new OptimisticLockingFailureException("task conflict"));

        assertThatThrownBy(() -> productionTaskService.updateTask("task-update-conflict", request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("生产任务");
    }

    @Test
    void assignTaskShouldTranslateOptimisticLockConflictWhenSaveFails() {
        ProductionTask task = new ProductionTask();
        task.setId("task-assign-conflict");
        task.setStatus("PENDING");

        User assignee = new User();
        assignee.setId("user-1");
        assignee.setRealName("tester");

        when(productionTaskRepository.findById("task-assign-conflict")).thenReturn(Optional.of(task));
        when(userRepository.findById("user-1")).thenReturn(Optional.of(assignee));
        when(productionTaskRepository.save(any(ProductionTask.class)))
                .thenThrow(new OptimisticLockingFailureException("task conflict"));

        assertThatThrownBy(() -> productionTaskService.assignTask("task-assign-conflict", "user-1"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("生产任务");
    }

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
    @Test
    void updateProgressShouldRollbackTaskWhenPlanSummarySaveFails() {
        ProductionTask task = new ProductionTask();
        task.setId("task-progress-rollback");
        task.setPlanId("plan-progress-rollback");
        task.setPlanQuantity(10);
        task.setStatus("IN_PROGRESS");
        task.setCompletedQuantity(2);
        task.setProgress(20);

        ProductionPlan plan = new ProductionPlan();
        plan.setId("plan-progress-rollback");
        plan.setCompletedQuantity(2);

        when(productionTaskRepository.findById("task-progress-rollback")).thenReturn(Optional.of(task));
        java.util.List<Integer> savedProgresses = new java.util.ArrayList<>();
        java.util.List<Integer> savedCompletedQuantities = new java.util.ArrayList<>();
        when(productionTaskRepository.save(any(ProductionTask.class))).thenAnswer(invocation -> {
            ProductionTask savedTask = invocation.getArgument(0);
            savedProgresses.add(savedTask.getProgress());
            savedCompletedQuantities.add(savedTask.getCompletedQuantity());
            return savedTask;
        });
        when(productionTaskRepository.findByPlanId("plan-progress-rollback")).thenReturn(Collections.singletonList(task));
        when(productionPlanRepository.findById("plan-progress-rollback")).thenReturn(Optional.of(plan));
        when(productionPlanRepository.save(any(ProductionPlan.class)))
                .thenThrow(new OptimisticLockingFailureException("plan summary conflict"));

        assertThatThrownBy(() -> productionTaskService.updateProgress("task-progress-rollback", 50))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("生产计划汇总");

        verify(productionTaskRepository, times(2)).save(any(ProductionTask.class));
        assertThat(savedProgresses).containsExactly(50, 20);
        assertThat(savedCompletedQuantities).containsExactly(5, 2);
    }
}
