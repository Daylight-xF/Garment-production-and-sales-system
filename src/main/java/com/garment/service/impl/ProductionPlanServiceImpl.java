package com.garment.service.impl;

import com.garment.dto.PlanCreateRequest;
import com.garment.dto.PlanUpdateRequest;
import com.garment.dto.PlanVO;
import com.garment.dto.StockInOutRequest;
import com.garment.dto.TaskVO;
import com.garment.exception.BusinessException;
import com.garment.model.ProductDefinition;
import com.garment.model.ProductionPlan;
import com.garment.model.ProductionTask;
import com.garment.model.RawMaterial;
import com.garment.model.User;
import com.garment.repository.ProductDefinitionRepository;
import com.garment.repository.ProductionPlanRepository;
import com.garment.repository.ProductionTaskRepository;
import com.garment.repository.RawMaterialRepository;
import com.garment.repository.UserRepository;
import com.garment.service.InventoryService;
import com.garment.service.ProductionPlanService;
import com.garment.service.support.MongoAtomicOpsService;
import org.bson.Document;
import org.springframework.dao.DuplicateKeyException;
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
    private final RawMaterialRepository rawMaterialRepository;
    private final InventoryService inventoryService;
    private final MongoAtomicOpsService mongoAtomicOpsService;

    public ProductionPlanServiceImpl(ProductionPlanRepository productionPlanRepository,
                                      UserRepository userRepository,
                                      ProductDefinitionRepository productDefinitionRepository,
                                      ProductionTaskRepository productionTaskRepository,
                                      RawMaterialRepository rawMaterialRepository,
                                      InventoryService inventoryService,
                                      MongoAtomicOpsService mongoAtomicOpsService) {
        this.productionPlanRepository = productionPlanRepository;
        this.userRepository = userRepository;
        this.productDefinitionRepository = productDefinitionRepository;
        this.productionTaskRepository = productionTaskRepository;
        this.rawMaterialRepository = rawMaterialRepository;
        this.inventoryService = inventoryService;
        this.mongoAtomicOpsService = mongoAtomicOpsService;
    }

    @Override
    public PlanVO createPlan(PlanCreateRequest request, String userId) {
        ProductDefinition productDef = productDefinitionRepository.findById(request.getProductDefinitionId())
                .orElseThrow(() -> new BusinessException("产品定义不存在"));

        checkAndDeductRawMaterials(productDef, request.getQuantity(), request.getBatchNo());

        ProductionPlan plan = new ProductionPlan();
        plan.setBatchNo(request.getBatchNo());
        plan.setProductDefinitionId(productDef.getId());
        plan.setProductCode(productDef.getProductCode());
        plan.setProductName(productDef.getProductName());
        plan.setCategory(productDef.getCategory());
        plan.setQuantity(request.getQuantity());
        plan.setCompletedQuantity(0);
        plan.setUnit(request.getUnit() != null ? request.getUnit() : "件");
        plan.setStartDate(request.getStartDate());
        plan.setEndDate(request.getEndDate());
        plan.setStatus("PENDING");
        plan.setDescription(request.getDescription());
        plan.setColor(request.getColor());
        plan.setSize(request.getSize());
        plan.setCreateBy(userId);
        plan.setMaterialsDeducted(true);

        ProductionPlan saved = productionPlanRepository.save(plan);
        return convertToVO(saved);
    }

    private void checkAndDeductRawMaterials(ProductDefinition productDef, Integer planQuantity, String batchNo) {
        if (productDef.getMaterials() == null || productDef.getMaterials().isEmpty()) {
            return;
        }

        List<String> insufficientMaterials = new ArrayList<>();

        for (ProductDefinition.ProductMaterial material : productDef.getMaterials()) {
            double neededQty = material.getQuantity() * planQuantity;

            RawMaterial rawMaterial = rawMaterialRepository.findById(material.getMaterialId())
                    .orElseThrow(() -> new BusinessException("原材料【" + material.getMaterialName() + "】不存在"));

            double currentStock = rawMaterial.getQuantity();

            if (currentStock < neededQty) {
                double shortage = neededQty - currentStock;
                insufficientMaterials.add("原材料【" + material.getMaterialName()
                        + "】库存不足：需求 " + formatDecimal(neededQty) + " " + (material.getUnit() != null ? material.getUnit() : "")
                        + "，当前库存 " + formatDecimal(currentStock)
                        + "，缺口 " + formatDecimal(shortage));
            }
        }

        if (!insufficientMaterials.isEmpty()) {
            throw new BusinessException("原材料库存不足，无法创建生产计划：" + String.join("；", insufficientMaterials));
        }

        for (ProductDefinition.ProductMaterial material : productDef.getMaterials()) {
            double neededQty = material.getQuantity() * planQuantity;
            int deductQty = (int) Math.round(neededQty);

            inventoryService.fifoDeductRawMaterial(material.getMaterialId(), deductQty,
                    "生产计划-" + batchNo + "-FIFO扣减");
        }
    }

    private void syncTaskPlanQuantities(String planId, int newPlanQuantity) {
        List<ProductionTask> tasks = productionTaskRepository.findByPlanId(planId);
        if (tasks.isEmpty()) {
            return;
        }
        for (ProductionTask task : tasks) {
            task.setPlanQuantity(newPlanQuantity);
            if (task.getPlanQuantity() != null && task.getPlanQuantity() > 0
                    && task.getCompletedQuantity() != null) {
                int newProgress = (int) Math.round(task.getCompletedQuantity() * 100.0 / task.getPlanQuantity());
                task.setProgress(Math.min(newProgress, 100));
            }
            productionTaskRepository.save(task);
        }

        int totalCompleted = tasks.stream()
                .mapToInt(t -> t.getCompletedQuantity() != null ? t.getCompletedQuantity() : 0)
                .sum();

        ProductionPlan plan = productionPlanRepository.findById(planId).orElse(null);
        if (plan != null) {
            plan.setCompletedQuantity(totalCompleted);
            productionPlanRepository.save(plan);
        }
    }

    private void restoreRawMaterials(String planId, String batchNo) {
        ProductionPlan plan = productionPlanRepository.findById(planId).orElse(null);
        if (plan == null || Boolean.FALSE.equals(plan.getMaterialsDeducted())) {
            return;
        }

        ProductDefinition productDef = productDefinitionRepository.findById(plan.getProductDefinitionId()).orElse(null);
        if (productDef == null || productDef.getMaterials() == null || productDef.getMaterials().isEmpty()) {
            return;
        }

        for (ProductDefinition.ProductMaterial material : productDef.getMaterials()) {
            double restoreQty = material.getQuantity() * plan.getQuantity();
            int restoreIntQty = (int) Math.round(restoreQty);

            StockInOutRequest stockInRequest = new StockInOutRequest();
            stockInRequest.setItemType("RAW_MATERIAL");
            stockInRequest.setItemId(material.getMaterialId());
            stockInRequest.setQuantity(restoreIntQty);
            stockInRequest.setReason("生产计划-" + batchNo + "-取消返还");
            inventoryService.stockIn(stockInRequest, "system");
        }

        plan.setMaterialsDeducted(false);
        productionPlanRepository.save(plan);
    }

    private String formatDecimal(double value) {
        if (value == Math.floor(value)) {
            return String.valueOf((int) value);
        }
        return String.valueOf(value);
    }

    @Override
    public Page<PlanVO> getPlanList(String keyword, String status, Pageable pageable) {
        List<ProductionPlan> allPlans = productionPlanRepository.findAll();

        List<ProductionPlan> filtered = allPlans.stream()
                .filter(plan -> {
                    boolean matchKeyword = true;
                    if (StringUtils.hasText(keyword)) {
                        matchKeyword = (plan.getBatchNo() != null && plan.getBatchNo().contains(keyword))
                                || (plan.getProductName() != null && plan.getProductName().contains(keyword));
                    }
                    boolean matchStatus = true;
                    if (StringUtils.hasText(status)) {
                        matchStatus = status.equals(plan.getStatus());
                    }
                    boolean hasRemainingStock = true;
                    if ("COMPLETED".equals(status)) {
                        int stockedIn = plan.getStockedInQuantity() != null ? plan.getStockedInQuantity() : 0;
                        int completed = plan.getCompletedQuantity() != null ? plan.getCompletedQuantity() : 0;
                        hasRemainingStock = stockedIn < completed;
                    }
                    return matchKeyword && matchStatus && hasRemainingStock;
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

        boolean isApproved = "APPROVED".equals(plan.getStatus());

        if (isApproved && request.getBatchNo() != null) {
            throw new BusinessException("已审批通过的计划不允许修改批次号");
        }
        if (!isApproved && request.getBatchNo() != null) {
            plan.setBatchNo(request.getBatchNo());
        }

        if (isApproved && request.getProductDefinitionId() != null) {
            throw new BusinessException("已审批通过的计划不允许修改产品定义");
        }
        if (!isApproved && request.getProductDefinitionId() != null) {
            ProductDefinition productDef = productDefinitionRepository.findById(request.getProductDefinitionId())
                    .orElseThrow(() -> new BusinessException("产品定义不存在"));
            plan.setProductDefinitionId(productDef.getId());
            plan.setProductCode(productDef.getProductCode());
            plan.setProductName(productDef.getProductName());
            plan.setCategory(productDef.getCategory());
        }
        if (!isApproved && request.getProductName() != null) {
            plan.setProductName(request.getProductName());
        }

        Integer oldQuantity = plan.getQuantity();
        if (request.getQuantity() != null) {
            int quantityDiff = request.getQuantity() - oldQuantity;
            if (quantityDiff != 0) {
                adjustInventoryForQuantityChange(plan, oldQuantity, request.getQuantity());
            }
            plan.setQuantity(request.getQuantity());

            syncTaskPlanQuantities(plan.getId(), request.getQuantity());
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
        if (request.getColor() != null) {
            plan.setColor(request.getColor());
        }
        if (request.getSize() != null) {
            plan.setSize(request.getSize());
        }

        ProductionPlan saved = productionPlanRepository.save(plan);
        return convertToVO(saved);
    }

    private void adjustInventoryForQuantityChange(ProductionPlan plan, int oldQty, int newQty) {
        ProductDefinition productDef = productDefinitionRepository.findById(plan.getProductDefinitionId()).orElse(null);
        if (productDef == null || productDef.getMaterials() == null || productDef.getMaterials().isEmpty()) {
            return;
        }

        int diff = newQty - oldQty;

        for (ProductDefinition.ProductMaterial material : productDef.getMaterials()) {
            double unitNeed = material.getQuantity();
            double totalAdjustment = unitNeed * Math.abs(diff);
            int adjustIntQty = (int) Math.round(totalAdjustment);

            if (diff > 0) {
                RawMaterial rawMaterial = rawMaterialRepository.findById(material.getMaterialId())
                        .orElseThrow(() -> new BusinessException("原材料【" + material.getMaterialName() + "】不存在"));

                double currentStock = rawMaterial.getQuantity();
                double neededForDiff = unitNeed * diff;
                if (currentStock < neededForDiff) {
                    double shortage = neededForDiff - currentStock;
                    throw new BusinessException(
                            "修改计划数量导致原材料库存不足：原材料【" + material.getMaterialName()
                                    + "】需额外扣减 " + formatDecimal(neededForDiff)
                                    + " " + (material.getUnit() != null ? material.getUnit() : "")
                                    + "，当前库存仅 " + formatDecimal(currentStock)
                                    + "，缺口 " + formatDecimal(shortage));
                }

                StockInOutRequest stockOutRequest = new StockInOutRequest();
                stockOutRequest.setItemType("RAW_MATERIAL");
                stockOutRequest.setItemId(material.getMaterialId());
                stockOutRequest.setQuantity(adjustIntQty);
                stockOutRequest.setReason("生产计划-" + plan.getBatchNo()
                        + "-数量调整(从" + oldQty + "增至" + newQty + ")-扣减");
                inventoryService.stockOut(stockOutRequest, "system");
            } else {
                StockInOutRequest stockInRequest = new StockInOutRequest();
                stockInRequest.setItemType("RAW_MATERIAL");
                stockInRequest.setItemId(material.getMaterialId());
                stockInRequest.setQuantity(adjustIntQty);
                stockInRequest.setReason("生产计划-" + plan.getBatchNo()
                        + "-数量调整(从" + oldQty + "减至" + newQty + ")-返还");
                inventoryService.stockIn(stockInRequest, "system");
            }
        }
    }

    @Override
    public void deletePlan(String id) {
        ProductionPlan plan = productionPlanRepository.findById(id)
                .orElseThrow(() -> new BusinessException("生产计划不存在"));

        if (!"CANCELLED".equals(plan.getStatus())) {
            String statusText = getStatusText(plan.getStatus());
            throw new BusinessException("只有【已取消】状态的生产计划才能删除，当前计划状态为：" + statusText);
        }

        restoreRawMaterials(id, plan.getBatchNo());

        productionPlanRepository.deleteById(id);
    }

    private String getStatusText(String status) {
        switch (status) {
            case "PENDING": return "待审批";
            case "APPROVED": return "已审批";
            case "IN_PROGRESS": return "进行中";
            case "COMPLETED": return "已完成";
            case "CANCELLED": return "已取消";
            default: return status;
        }
    }

    private void assertPlanStatusChanged(boolean changed) {
        if (!changed) {
            throw new BusinessException("计划状态已变更，请刷新后再操作");
        }
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

        assertPlanStatusChanged(mongoAtomicOpsService.transitionPlanStatus(id, "PENDING", status, null));

        if ("CANCELLED".equals(status)) {
            restoreRawMaterials(id, plan.getBatchNo());
        }

        return convertToVO(productionPlanRepository.findById(id)
                .orElseThrow(() -> new BusinessException("生产计划不存在")));
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
        task.setBatchNo(plan.getBatchNo());
        task.setProductName(plan.getProductName());
        task.setProductCode(plan.getProductCode());
        task.setColor(plan.getColor());
        task.setSize(plan.getSize());
        task.setTaskName(plan.getBatchNo() + "-生产任务");
        task.setProgress(0);
        task.setStatus("PENDING");
        task.setPlanQuantity(plan.getQuantity());
        task.setCompletedQuantity(0);
        task.setDescription("自动从生产计划【" + plan.getBatchNo() + "】生成");
        task.setCreateBy(userId);
        task.setAutoCreateKey("AUTO:" + plan.getId());

        assertPlanStatusChanged(mongoAtomicOpsService.transitionPlanStatus(planId, "APPROVED", "IN_PROGRESS", null));
        try {
            productionTaskRepository.save(task);
        } catch (DuplicateKeyException ignored) {
            // The auto-create key prevents duplicate task creation under races.
        }

        return convertToVO(productionPlanRepository.findById(planId)
                .orElseThrow(() -> new BusinessException("生产计划不存在")));
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
            throw new BusinessException("还有" + (tasks.size() - completedCount) + "个任务未完成，无法确认完成");
        }

        assertPlanStatusChanged(mongoAtomicOpsService.transitionPlanStatus(
                planId,
                "IN_PROGRESS",
                "COMPLETED",
                new Document("completedQuantity", plan.getQuantity())
        ));

        return convertToVO(productionPlanRepository.findById(planId)
                .orElseThrow(() -> new BusinessException("生产计划不存在")));
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
                .batchNo(task.getBatchNo())
                .productName(task.getProductName())
                .productCode(task.getProductCode())
                .color(task.getColor())
                .size(task.getSize())
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

        Date taskStartDate = null;
        Date taskEndDate = null;
        List<ProductionTask> tasks = productionTaskRepository.findByPlanId(plan.getId());
        if (tasks != null && !tasks.isEmpty()) {
            taskStartDate = tasks.stream()
                    .map(ProductionTask::getStartDate)
                    .filter(java.util.Objects::nonNull)
                    .min(Date::compareTo)
                    .orElse(null);
            taskEndDate = tasks.stream()
                    .map(ProductionTask::getEndDate)
                    .filter(java.util.Objects::nonNull)
                    .max(Date::compareTo)
                    .orElse(null);
        }

        return PlanVO.builder()
                .id(plan.getId())
                .batchNo(plan.getBatchNo())
                .productDefinitionId(plan.getProductDefinitionId())
                .productCode(plan.getProductCode())
                .productName(plan.getProductName())
                .quantity(plan.getQuantity())
                .completedQuantity(plan.getCompletedQuantity())
                .stockedInQuantity(plan.getStockedInQuantity() != null ? plan.getStockedInQuantity() : 0)
                .unit(plan.getUnit())
                .startDate(plan.getStartDate())
                .endDate(plan.getEndDate())
                .status(plan.getStatus())
                .description(plan.getDescription())
                .color(plan.getColor())
                .size(plan.getSize())
                .taskStartDate(taskStartDate)
                .taskEndDate(taskEndDate)
                .createBy(plan.getCreateBy())
                .createByName(createByName)
                .materialsDeducted(plan.getMaterialsDeducted())
                .createTime(plan.getCreateTime())
                .updateTime(plan.getUpdateTime())
                .build();
    }
}
