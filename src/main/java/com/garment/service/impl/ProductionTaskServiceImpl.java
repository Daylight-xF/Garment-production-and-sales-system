package com.garment.service.impl;

import com.garment.dto.TaskCreateRequest;
import com.garment.dto.TaskUpdateRequest;
import com.garment.dto.TaskVO;
import com.garment.exception.BusinessException;
import com.garment.model.ProductionPlan;
import com.garment.model.ProductionTask;
import com.garment.model.User;
import com.garment.repository.ProductionPlanRepository;
import com.garment.repository.ProductionTaskRepository;
import com.garment.repository.UserRepository;
import com.garment.service.ProductionTaskService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class ProductionTaskServiceImpl implements ProductionTaskService {

    private final ProductionTaskRepository productionTaskRepository;
    private final ProductionPlanRepository productionPlanRepository;
    private final UserRepository userRepository;

    public ProductionTaskServiceImpl(ProductionTaskRepository productionTaskRepository,
                                      ProductionPlanRepository productionPlanRepository,
                                      UserRepository userRepository) {
        this.productionTaskRepository = productionTaskRepository;
        this.productionPlanRepository = productionPlanRepository;
        this.userRepository = userRepository;
    }

    @Override
    public TaskVO createTask(TaskCreateRequest request, String userId) {
        ProductionPlan plan = productionPlanRepository.findById(request.getPlanId())
                .orElseThrow(() -> new BusinessException("关联的生产计划不存在"));

        ProductionTask task = new ProductionTask();
        task.setPlanId(request.getPlanId());
        task.setBatchNo(plan.getBatchNo());
        task.setTaskName(request.getTaskName());
        task.setProgress(0);
        task.setStatus("PENDING");
        task.setStartDate(request.getStartDate());
        task.setEndDate(request.getEndDate());
        task.setDescription(request.getDescription());
        task.setCreateBy(userId);

        if (StringUtils.hasText(request.getAssignee())) {
            User assigneeUser = userRepository.findById(request.getAssignee())
                    .orElseThrow(() -> new BusinessException("分配人不存在"));
            task.setAssignee(request.getAssignee());
            task.setAssigneeName(assigneeUser.getRealName());
        }

        ProductionTask saved = saveTaskWithConflictTranslation(task);
        return convertToVO(saved);
    }

    @Override
    public Page<TaskVO> getTaskList(String planId, String assignee, String status, Pageable pageable) {
        List<ProductionTask> allTasks = productionTaskRepository.findAll();

        List<ProductionTask> filtered = allTasks.stream()
                .filter(task -> {
                    boolean matchPlanId = true;
                    if (StringUtils.hasText(planId)) {
                        matchPlanId = planId.equals(task.getPlanId());
                    }
                    boolean matchAssignee = true;
                    if (StringUtils.hasText(assignee)) {
                        matchAssignee = assignee.equals(task.getAssignee());
                    }
                    boolean matchStatus = true;
                    if (StringUtils.hasText(status)) {
                        matchStatus = status.equals(task.getStatus());
                    }
                    return matchPlanId && matchAssignee && matchStatus;
                })
                .collect(Collectors.toList());

        filtered.sort((a, b) -> {
            if (a.getCreateTime() == null && b.getCreateTime() == null) return 0;
            if (a.getCreateTime() == null) return 1;
            if (b.getCreateTime() == null) return -1;
            return b.getCreateTime().compareTo(a.getCreateTime());
        });

        int start = (int) pageable.getOffset();
        int end = Math.min(start + pageable.getPageSize(), filtered.size());
        List<ProductionTask> pageContent = start < filtered.size() ? filtered.subList(start, end) : new ArrayList<>();

        List<TaskVO> voList = pageContent.stream()
                .map(this::convertToVO)
                .collect(Collectors.toList());

        return new PageImpl<>(voList, pageable, filtered.size());
    }

    @Override
    public TaskVO getTaskById(String id) {
        ProductionTask task = productionTaskRepository.findById(id)
                .orElseThrow(() -> new BusinessException("生产任务不存在"));
        return convertToVO(task);
    }

    @Override
    public TaskVO updateTask(String id, TaskUpdateRequest request) {
        ProductionTask task = productionTaskRepository.findById(id)
                .orElseThrow(() -> new BusinessException("生产任务不存在"));

        if (request.getTaskName() != null) {
            task.setTaskName(request.getTaskName());
        }
        if (request.getStartDate() != null) {
            task.setStartDate(request.getStartDate());
        }
        if (request.getEndDate() != null) {
            task.setEndDate(request.getEndDate());
        }
        if (request.getDescription() != null) {
            task.setDescription(request.getDescription());
        }
        if (request.getStatus() != null) {
            task.setStatus(request.getStatus());
        }

        ProductionTask saved = saveTaskWithConflictTranslation(task);
        return convertToVO(saved);
    }

    @Override
    public TaskVO assignTask(String id, String assignee) {
        ProductionTask task = productionTaskRepository.findById(id)
                .orElseThrow(() -> new BusinessException("生产任务不存在"));

        User assigneeUser = userRepository.findById(assignee)
                .orElseThrow(() -> new BusinessException("分配人不存在"));

        task.setAssignee(assignee);
        task.setAssigneeName(assigneeUser.getRealName());

        if (task.getStartDate() == null) {
            task.setStartDate(new Date());
        }

        if ("PENDING".equals(task.getStatus())) {
            task.setStatus("IN_PROGRESS");
        }

        ProductionTask saved = saveTaskWithConflictTranslation(task);
        return convertToVO(saved);
    }

    @Override
    public TaskVO updateProgress(String id, Integer progress) {
        ProductionTask task = productionTaskRepository.findById(id)
                .orElseThrow(() -> new BusinessException("生产任务不存在"));

        if (progress < 0 || progress > 100) {
            throw new BusinessException("进度必须在0-100之间");
        }

        TaskProgressSnapshot snapshot = new TaskProgressSnapshot(task);
        task.setProgress(progress);

        if (task.getPlanQuantity() != null && task.getPlanQuantity() > 0) {
            int completed = (int) Math.round(task.getPlanQuantity() * progress / 100.0);
            task.setCompletedQuantity(completed);
        }

        if (progress == 100) {
            task.setStatus("COMPLETED");
            if (task.getEndDate() == null) {
                task.setEndDate(new Date());
            }
        } else if ("PENDING".equals(task.getStatus()) || "COMPLETED".equals(task.getStatus())) {
            task.setStatus("IN_PROGRESS");
        }

        ProductionTask saved = saveTaskWithConflictTranslation(task);
        try {
            updatePlanCompletedQuantityEnhanced(task.getPlanId());
        } catch (RuntimeException ex) {
            rollbackTaskProgress(task, snapshot, ex);
            throw ex;
        }

        return convertToVO(saved);
    }

    private void updatePlanCompletedQuantityEnhanced(String planId) {
        if (!StringUtils.hasText(planId)) return;

        List<ProductionTask> tasks = productionTaskRepository.findByPlanId(planId);

        int totalCompleted = tasks.stream()
                .mapToInt(t -> t.getCompletedQuantity() != null ? t.getCompletedQuantity() : 0)
                .sum();

        ProductionPlan plan = productionPlanRepository.findById(planId).orElse(null);
        if (plan != null) {
            plan.setCompletedQuantity(totalCompleted);
            try {
                productionPlanRepository.save(plan);
            } catch (OptimisticLockingFailureException ex) {
                throw new BusinessException("生产计划汇总已更新，请刷新后重试");
            }
        }
    }

    private ProductionTask saveTaskWithConflictTranslation(ProductionTask task) {
        try {
            return productionTaskRepository.save(task);
        } catch (OptimisticLockingFailureException ex) {
            throw new BusinessException("生产任务已发生变更，请刷新后重试");
        }
    }

    private void rollbackTaskProgress(ProductionTask task, TaskProgressSnapshot snapshot, RuntimeException originalEx) {
        snapshot.restore(task);
        try {
            productionTaskRepository.save(task);
        } catch (OptimisticLockingFailureException rollbackEx) {
            IllegalStateException fatal = new IllegalStateException("任务进度更新失败，且任务状态回滚失败，请立即人工核对");
            fatal.addSuppressed(originalEx);
            fatal.addSuppressed(rollbackEx);
            throw fatal;
        }
    }

    @Override
    public int migrateProductInfoForAllTasks() {
        List<ProductionTask> allTasks = productionTaskRepository.findAll();
        int migratedCount = 0;

        for (ProductionTask task : allTasks) {
            if (StringUtils.hasText(task.getPlanId())) {
                ProductionPlan plan = productionPlanRepository.findById(task.getPlanId()).orElse(null);
                if (plan != null) {
                    boolean needUpdate = false;

                    if (!StringUtils.hasText(task.getProductName()) && StringUtils.hasText(plan.getProductName())) {
                        task.setProductName(plan.getProductName());
                        needUpdate = true;
                    }

                    if (!StringUtils.hasText(task.getProductCode()) && StringUtils.hasText(plan.getProductCode())) {
                        task.setProductCode(plan.getProductCode());
                        needUpdate = true;
                    }

                    if (needUpdate) {
                        productionTaskRepository.save(task);
                        migratedCount++;
                    }
                }
            }
        }

        return migratedCount;
    }

    private static class TaskProgressSnapshot {
        private final Integer progress;
        private final Integer completedQuantity;
        private final String status;
        private final Date endDate;

        private TaskProgressSnapshot(ProductionTask task) {
            this.progress = task.getProgress();
            this.completedQuantity = task.getCompletedQuantity();
            this.status = task.getStatus();
            this.endDate = task.getEndDate();
        }

        private void restore(ProductionTask task) {
            task.setProgress(progress);
            task.setCompletedQuantity(completedQuantity);
            task.setStatus(status);
            task.setEndDate(endDate);
        }
    }

    private TaskVO convertToVO(ProductionTask task) {
        String productName = "";
        String productCode = "";
        String color = "";
        String size = "";
        if (StringUtils.hasText(task.getPlanId())) {
            ProductionPlan plan = productionPlanRepository.findById(task.getPlanId()).orElse(null);
            if (plan != null) {
                productName = plan.getProductName();
                productCode = plan.getProductCode();
                color = plan.getColor();
                size = plan.getSize();
            }
        }

        return TaskVO.builder()
                .id(task.getId())
                .planId(task.getPlanId())
                .batchNo(task.getBatchNo())
                .productName(productName)
                .productCode(productCode)
                .color(color)
                .size(size)
                .taskName(task.getTaskName())
                .assignee(task.getAssignee())
                .assigneeName(task.getAssigneeName())
                .progress(task.getProgress())
                .planQuantity(task.getPlanQuantity())
                .completedQuantity(task.getCompletedQuantity())
                .status(task.getStatus())
                .startDate(task.getStartDate())
                .endDate(task.getEndDate())
                .description(task.getDescription())
                .createBy(task.getCreateBy())
                .createTime(task.getCreateTime())
                .updateTime(task.getUpdateTime())
                .build();
    }
}
