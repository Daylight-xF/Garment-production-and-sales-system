package com.garment.service.impl;

import com.garment.dto.PlanCreateRequest;
import com.garment.dto.PlanUpdateRequest;
import com.garment.dto.PlanVO;
import com.garment.dto.TaskVO;
import com.garment.exception.BusinessException;
import com.garment.model.ProductDefinition;
import com.garment.model.ProductionPlan;
import com.garment.model.ProductionTask;
import com.garment.model.User;
import com.garment.repository.ProductDefinitionRepository;
import com.garment.repository.ProductionPlanRepository;
import com.garment.repository.ProductionTaskRepository;
import com.garment.repository.UserRepository;
import com.garment.service.ProductionPlanService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class ProductionPlanServiceImpl implements ProductionPlanService {

    private final ProductionPlanRepository productionPlanRepository;
    private final UserRepository userRepository;
    private final ProductDefinitionRepository productDefinitionRepository;
    private final ProductionTaskRepository productionTaskRepository;

    public ProductionPlanServiceImpl(ProductionPlanRepository productionPlanRepository,
                                      UserRepository userRepository,
                                      ProductDefinitionRepository productDefinitionRepository,
                                      ProductionTaskRepository productionTaskRepository) {
        this.productionPlanRepository = productionPlanRepository;
        this.userRepository = userRepository;
        this.productDefinitionRepository = productDefinitionRepository;
        this.productionTaskRepository = productionTaskRepository;
    }

    @Override
    public PlanVO createPlan(PlanCreateRequest request, String userId) {
        ProductDefinition productDef = productDefinitionRepository.findById(request.getProductDefinitionId())
                .orElseThrow(() -> new BusinessException("产品定义不存在"));

        ProductionPlan plan = new ProductionPlan();
        plan.setPlanName(request.getPlanName());
        plan.setProductDefinitionId(productDef.getId());
        plan.setProductName(productDef.getProductName());
        plan.setQuantity(request.getQuantity());
        plan.setCompletedQuantity(0);
        plan.setUnit(request.getUnit() != null ? request.getUnit() : "件");
        plan.setStartDate(request.getStartDate());
        plan.setEndDate(request.getEndDate());
        plan.setStatus("PENDING");
        plan.setDescription(request.getDescription());
        plan.setCreateBy(userId);

        ProductionPlan saved = productionPlanRepository.save(plan);
        return convertToVO(saved);
    }

    @Override
    public Page<PlanVO> getPlanList(String keyword, String status, Pageable pageable) {
        List<ProductionPlan> allPlans = productionPlanRepository.findAll();

        List<ProductionPlan> filtered = allPlans.stream()
                .filter(plan -> {
                    boolean matchKeyword = true;
                    if (StringUtils.hasText(keyword)) {
                        matchKeyword = (plan.getPlanName() != null && plan.getPlanName().contains(keyword))
                                || (plan.getProductName() != null && plan.getProductName().contains(keyword));
                    }
                    boolean matchStatus = true;
                    if (StringUtils.hasText(status)) {
                        matchStatus = status.equals(plan.getStatus());
                    }
                    return matchKeyword && matchStatus;
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
        List<ProductionPlan> pageContent = start < filtered.size() ? filtered.subList(start, end) : new ArrayList<>();

        List<PlanVO> voList = pageContent.stream()
                .map(this::convertToVO)
                .collect(Collectors.toList());

        return new PageImpl<>(voList, pageable, filtered.size());
    }

    @Override
    public PlanVO getPlanById(String id) {
        ProductionPlan plan = productionPlanRepository.findById(id)
                .orElseThrow(() -> new BusinessException("生产计划不存在"));
        return convertToVO(plan);
    }

    @Override
    public PlanVO updatePlan(String id, PlanUpdateRequest request) {
        ProductionPlan plan = productionPlanRepository.findById(id)
                .orElseThrow(() -> new BusinessException("生产计划不存在"));

        if (request.getPlanName() != null) {
            plan.setPlanName(request.getPlanName());
        }
        if (request.getProductDefinitionId() != null) {
            ProductDefinition productDef = productDefinitionRepository.findById(request.getProductDefinitionId())
                    .orElseThrow(() -> new BusinessException("产品定义不存在"));
            plan.setProductDefinitionId(productDef.getId());
            plan.setProductName(productDef.getProductName());
        }
        if (request.getProductName() != null) {
            plan.setProductName(request.getProductName());
        }
        if (request.getQuantity() != null) {
            plan.setQuantity(request.getQuantity());
        }
        if (request.getUnit() != null) {
            plan.setUnit(request.getUnit());
        }
        if (request.getStartDate() != null) {
            plan.setStartDate(request.getStartDate());
        }
        if (request.getEndDate() != null) {
            plan.setEndDate(request.getEndDate());
        }
        if (request.getDescription() != null) {
            plan.setDescription(request.getDescription());
        }

        ProductionPlan saved = productionPlanRepository.save(plan);
        return convertToVO(saved);
    }

    @Override
    public void deletePlan(String id) {
        if (!productionPlanRepository.existsById(id)) {
            throw new BusinessException("生产计划不存在");
        }
        productionPlanRepository.deleteById(id);
    }

    @Override
    public PlanVO approvePlan(String id, String status) {
        ProductionPlan plan = productionPlanRepository.findById(id)
                .orElseThrow(() -> new BusinessException("生产计划不存在"));

        if (!"PENDING".equals(plan.getStatus())) {
            throw new BusinessException("只有待审批状态的计划才能审批");
        }

        if (!"APPROVED".equals(status) && !"CANCELLED".equals(status)) {
            throw new BusinessException("审批状态只能为APPROVED或CANCELLED");
        }

        plan.setStatus(status);
        ProductionPlan saved = productionPlanRepository.save(plan);
        return convertToVO(saved);
    }

    @Override
    public PlanVO startProduction(String planId, String userId) {
        ProductionPlan plan = productionPlanRepository.findById(planId)
                .orElseThrow(() -> new BusinessException("生产计划不存在"));

        if (!"APPROVED".equals(plan.getStatus())) {
            throw new BusinessException("只有已审批状态的计划才能开始生产");
        }

        List<ProductionTask> existingTasks = productionTaskRepository.findByPlanId(planId);
        if (!existingTasks.isEmpty()) {
            throw new BusinessException("该计划已生成任务，请勿重复操作");
        }

        ProductionTask task = new ProductionTask();
        task.setPlanId(plan.getId());
        task.setPlanName(plan.getPlanName());
        task.setTaskName(plan.getPlanName() + "-生产任务");
        task.setProgress(0);
        task.setStatus("PENDING");
        task.setPlanQuantity(plan.getQuantity());
        task.setCompletedQuantity(0);
        task.setStartDate(new Date());
        task.setEndDate(plan.getEndDate());
        task.setDescription("自动从生产计划【" + plan.getPlanName() + "】生成");
        task.setCreateBy(userId);

        productionTaskRepository.save(task);

        plan.setStatus("IN_PROGRESS");
        ProductionPlan savedPlan = productionPlanRepository.save(plan);

        return convertToVO(savedPlan);
    }

    @Override
    public PlanVO completePlan(String planId) {
        ProductionPlan plan = productionPlanRepository.findById(planId)
                .orElseThrow(() -> new BusinessException("生产计划不存在"));

        if (!"IN_PROGRESS".equals(plan.getStatus())) {
            throw new BusinessException("只有进行中状态的计划才能完成");
        }

        List<ProductionTask> tasks = productionTaskRepository.findByPlanId(planId);
        if (tasks.isEmpty()) {
            throw new BusinessException("该计划没有关联的生产任务");
        }

        boolean allCompleted = tasks.stream()
                .allMatch(task -> "COMPLETED".equals(task.getStatus()));

        if (!allCompleted) {
            long completedCount = tasks.stream()
                    .filter(t -> "COMPLETED".equals(t.getStatus()))
                    .count();
            throw new BusinessException(
                    "还有" + (tasks.size() - completedCount) + "个任务未完成，无法确认完成"
            );
        }

        plan.setStatus("COMPLETED");
        plan.setCompletedQuantity(plan.getQuantity());
        ProductionPlan savedPlan = productionPlanRepository.save(plan);

        return convertToVO(savedPlan);
    }

    @Override
    public List<TaskVO> getTasksByPlanId(String planId) {
        if (!productionPlanRepository.existsById(planId)) {
            throw new BusinessException("生产计划不存在");
        }

        List<ProductionTask> tasks = productionTaskRepository.findByPlanId(planId);

        return tasks.stream()
                .map(this::convertTaskToVO)
                .collect(Collectors.toList());
    }

    private TaskVO convertTaskToVO(ProductionTask task) {
        return TaskVO.builder()
                .id(task.getId())
                .planId(task.getPlanId())
                .planName(task.getPlanName())
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

    private PlanVO convertToVO(ProductionPlan plan) {
        String createByName = null;
        if (StringUtils.hasText(plan.getCreateBy())) {
            createByName = userRepository.findById(plan.getCreateBy())
                    .map(User::getRealName)
                    .orElse(null);
        }

        return PlanVO.builder()
                .id(plan.getId())
                .planName(plan.getPlanName())
                .productDefinitionId(plan.getProductDefinitionId())
                .productName(plan.getProductName())
                .quantity(plan.getQuantity())
                .completedQuantity(plan.getCompletedQuantity())
                .unit(plan.getUnit())
                .startDate(plan.getStartDate())
                .endDate(plan.getEndDate())
                .status(plan.getStatus())
                .description(plan.getDescription())
                .createBy(plan.getCreateBy())
                .createByName(createByName)
                .createTime(plan.getCreateTime())
                .updateTime(plan.getUpdateTime())
                .build();
    }
}
