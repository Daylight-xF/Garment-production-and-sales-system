package com.garment.service.impl;

import com.garment.dto.InventoryDeductionReceipt;
import com.garment.dto.PlanCreateRequest;
import com.garment.dto.PlanUpdateRequest;
import com.garment.dto.PlanVO;
import com.garment.dto.StockInOutRequest;
import com.garment.dto.TaskVO;
import com.garment.exception.BusinessException;
import com.garment.model.InventoryRecord;
import com.garment.model.ProductDefinition;
import com.garment.model.ProductionPlan;
import com.garment.model.ProductionTask;
import com.garment.model.RawMaterial;
import com.garment.model.User;
import com.garment.repository.InventoryRecordRepository;
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
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class ProductionPlanServiceImpl implements ProductionPlanService {

    private final ProductionPlanRepository productionPlanRepository;
    private final UserRepository userRepository;
    private final ProductDefinitionRepository productDefinitionRepository;
    private final ProductionTaskRepository productionTaskRepository;
    private final RawMaterialRepository rawMaterialRepository;
    private final InventoryRecordRepository inventoryRecordRepository;
    private final InventoryService inventoryService;
    private final MongoAtomicOpsService mongoAtomicOpsService;

    public ProductionPlanServiceImpl(ProductionPlanRepository productionPlanRepository,
                                      UserRepository userRepository,
                                      ProductDefinitionRepository productDefinitionRepository,
                                      ProductionTaskRepository productionTaskRepository,
                                      RawMaterialRepository rawMaterialRepository,
                                      InventoryRecordRepository inventoryRecordRepository,
                                      InventoryService inventoryService,
                                      MongoAtomicOpsService mongoAtomicOpsService) {
        this.productionPlanRepository = productionPlanRepository;
        this.userRepository = userRepository;
        this.productDefinitionRepository = productDefinitionRepository;
        this.productionTaskRepository = productionTaskRepository;
        this.rawMaterialRepository = rawMaterialRepository;
        this.inventoryRecordRepository = inventoryRecordRepository;
        this.inventoryService = inventoryService;
        this.mongoAtomicOpsService = mongoAtomicOpsService;
    }

    @Override
    public PlanVO createPlan(PlanCreateRequest request, String userId) {
        ProductDefinition productDef = productDefinitionRepository.findById(request.getProductDefinitionId())
                .orElseThrow(() -> new BusinessException("产品定义不存在"));

        List<InventoryDeductionReceipt> deductionReceipts =
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
        plan.setMaterialsDeducted(!deductionReceipts.isEmpty());
        plan.setMaterialDeductionReceipts(deductionReceipts);

        try {
            ProductionPlan saved = productionPlanRepository.save(plan);
            return convertToVO(saved);
        } catch (RuntimeException ex) {
            rollbackCreatedPlanMaterialDeductions(deductionReceipts, request.getBatchNo(), ex);
            throw ex;
        }
    }

    private void rollbackCreatedPlanMaterialDeductions(List<InventoryDeductionReceipt> deductionReceipts,
                                                       String batchNo,
                                                       RuntimeException originalEx) {
        for (int i = deductionReceipts.size() - 1; i >= 0; i--) {
            InventoryDeductionReceipt receipt = deductionReceipts.get(i);
            try {
                inventoryService.restoreInventoryDeduction(receipt, "生产计划-" + batchNo + "-创建回滚");
            } catch (RuntimeException rollbackEx) {
                IllegalStateException fatal = new IllegalStateException("生产计划创建失败，且原材料扣减回滚失败，请立即人工核对库存", rollbackEx);
                fatal.addSuppressed(originalEx);
                throw fatal;
            }
        }
    }

    private List<InventoryDeductionReceipt> checkAndDeductRawMaterials(ProductDefinition productDef, Integer planQuantity, String batchNo) {
        if (productDef.getMaterials() == null || productDef.getMaterials().isEmpty()) {
            return new ArrayList<>();
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

        List<InventoryDeductionReceipt> receipts = new ArrayList<>();
        for (ProductDefinition.ProductMaterial material : productDef.getMaterials()) {
            double neededQty = material.getQuantity() * planQuantity;
            int deductQty = (int) Math.round(neededQty);

            InventoryDeductionReceipt receipt = inventoryService.fifoDeductRawMaterialWithReceipt(
                    material.getMaterialId(), deductQty, "生产计划-" + batchNo + "-FIFO扣减");
            if (receipt != null) {
                receipts.add(receipt);
            }
        }
        return receipts;
    }

    private List<TaskQuantitySnapshot> captureTaskQuantitySnapshots(String planId) {
        List<ProductionTask> tasks = productionTaskRepository.findByPlanId(planId);
        if (tasks.isEmpty()) {
            return new ArrayList<>();
        }
        return tasks.stream()
                .map(TaskQuantitySnapshot::new)
                .collect(Collectors.toList());
    }

    private void syncTaskPlanQuantities(List<TaskQuantitySnapshot> taskSnapshots, String planId, int newPlanQuantity) {
        if (taskSnapshots.isEmpty()) {
            return;
        }
        for (TaskQuantitySnapshot snapshot : taskSnapshots) {
            ProductionTask task = snapshot.getTask();
            task.setPlanQuantity(newPlanQuantity);
            if (task.getPlanQuantity() != null && task.getPlanQuantity() > 0
                    && task.getCompletedQuantity() != null) {
                int newProgress = (int) Math.round(task.getCompletedQuantity() * 100.0 / task.getPlanQuantity());
                task.setProgress(Math.min(newProgress, 100));
            }
            productionTaskRepository.save(task);
        }

        int totalCompleted = taskSnapshots.stream()
                .map(TaskQuantitySnapshot::getTask)
                .mapToInt(t -> t.getCompletedQuantity() != null ? t.getCompletedQuantity() : 0)
                .sum();

        ProductionPlan plan = productionPlanRepository.findById(planId).orElse(null);
        if (plan != null) {
            plan.setCompletedQuantity(totalCompleted);
            productionPlanRepository.save(plan);
        }
    }

    private void rollbackTaskPlanQuantities(List<TaskQuantitySnapshot> taskSnapshots) {
        for (int i = taskSnapshots.size() - 1; i >= 0; i--) {
            TaskQuantitySnapshot snapshot = taskSnapshots.get(i);
            snapshot.restore();
            productionTaskRepository.save(snapshot.getTask());
        }
    }

    private List<StockInOutRequest> buildRawMaterialRestoreRequests(ProductionPlan planSnapshot, String batchNo,
                                                                   boolean strictValidation) {
        List<StockInOutRequest> restoreRequests = new ArrayList<>();
        if (planSnapshot == null || Boolean.FALSE.equals(planSnapshot.getMaterialsDeducted())) {
            return restoreRequests;
        }

        ProductDefinition productDef = productDefinitionRepository.findById(planSnapshot.getProductDefinitionId()).orElse(null);
        if (productDef == null) {
            if (strictValidation) {
                throw new BusinessException("产品定义不存在，无法返还原材料");
            }
            return restoreRequests;
        }
        if (productDef.getMaterials() == null || productDef.getMaterials().isEmpty()) {
            if (strictValidation) {
                throw new BusinessException("产品定义未配置原材料，无法返还原材料");
            }
            return restoreRequests;
        }

        for (ProductDefinition.ProductMaterial material : productDef.getMaterials()) {
            double restoreQty = material.getQuantity() * planSnapshot.getQuantity();
            int restoreIntQty = (int) Math.round(restoreQty);

            StockInOutRequest stockInRequest = new StockInOutRequest();
            stockInRequest.setItemType("RAW_MATERIAL");
            stockInRequest.setItemId(material.getMaterialId());
            stockInRequest.setQuantity(restoreIntQty);
            stockInRequest.setReason("生产计划-" + batchNo + "-取消返还");
            restoreRequests.add(stockInRequest);
        }

        return restoreRequests;
    }

    private List<InventoryDeductionReceipt> buildRawMaterialRestoreReceipts(ProductionPlan planSnapshot) {
        if (planSnapshot == null || Boolean.FALSE.equals(planSnapshot.getMaterialsDeducted())) {
            return new ArrayList<>();
        }
        List<InventoryDeductionReceipt> storedReceipts = planSnapshot.getMaterialDeductionReceipts() == null
                ? new ArrayList<>()
                : planSnapshot.getMaterialDeductionReceipts().stream()
                .filter(receipt -> receipt != null && "RAW_MATERIAL".equals(receipt.getItemType()))
                .collect(Collectors.toList());
        if (!storedReceipts.isEmpty()) {
            return storedReceipts;
        }
        return rebuildLegacyRawMaterialRestoreReceipts(planSnapshot.getBatchNo());
    }

    private List<InventoryDeductionReceipt> rebuildLegacyRawMaterialRestoreReceipts(String batchNo) {
        if (!StringUtils.hasText(batchNo)) {
            return new ArrayList<>();
        }

        String reasonPrefix = "生产计划-" + batchNo + "-FIFO扣减";
        return inventoryRecordRepository.findAll().stream()
                .filter(record -> "OUT".equals(record.getInventoryType()))
                .filter(record -> "RAW_MATERIAL".equals(record.getItemType()))
                .filter(record -> StringUtils.hasText(record.getReason()) && record.getReason().startsWith(reasonPrefix))
                .map(this::buildReceiptFromInventoryRecord)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    private InventoryDeductionReceipt buildReceiptFromInventoryRecord(InventoryRecord record) {
        Integer recordedQuantity = record.getQuantity();
        if (!StringUtils.hasText(record.getItemId()) || recordedQuantity == null || recordedQuantity == 0) {
            return null;
        }

        String fifoDetail = extractFifoDetail(record.getReason());
        if (!StringUtils.hasText(fifoDetail)) {
            return null;
        }

        int quantity = Math.abs(recordedQuantity);
        if (fifoDetail.startsWith("TOTAL(") && fifoDetail.endsWith(")")) {
            return new InventoryDeductionReceipt(
                    "RAW_MATERIAL",
                    record.getItemId(),
                    record.getItemName(),
                    quantity,
                    true,
                    new ArrayList<>());
        }

        List<InventoryDeductionReceipt.LocationDeduction> locationDeductions = parseLocationDeductions(fifoDetail);
        if (locationDeductions.isEmpty()) {
            return null;
        }

        return new InventoryDeductionReceipt(
                "RAW_MATERIAL",
                record.getItemId(),
                record.getItemName(),
                quantity,
                false,
                locationDeductions);
    }

    private String extractFifoDetail(String reason) {
        if (!StringUtils.hasText(reason)) {
            return null;
        }
        int start = reason.lastIndexOf("[FIFO:");
        int end = reason.lastIndexOf(']');
        if (start < 0 || end <= start + 6) {
            return null;
        }
        return reason.substring(start + 6, end).trim();
    }

    private List<InventoryDeductionReceipt.LocationDeduction> parseLocationDeductions(String fifoDetail) {
        List<InventoryDeductionReceipt.LocationDeduction> deductions = new ArrayList<>();
        Matcher matcher = Pattern.compile("([^()]+?)\\((\\d+)\\)").matcher(fifoDetail);
        int cursor = 0;
        while (matcher.find()) {
            if (matcher.start() != cursor) {
                return new ArrayList<>();
            }
            deductions.add(new InventoryDeductionReceipt.LocationDeduction(
                    matcher.group(1),
                    Integer.parseInt(matcher.group(2)),
                    null));
            cursor = matcher.end();
        }
        if (cursor != fifoDetail.length()) {
            return new ArrayList<>();
        }
        return deductions;
    }

    private void rollbackRestoredRawMaterials(List<StockInOutRequest> restoredRequests, String batchNo) {
        for (int i = restoredRequests.size() - 1; i >= 0; i--) {
            StockInOutRequest restoredRequest = restoredRequests.get(i);
            StockInOutRequest rollbackRequest = new StockInOutRequest();
            rollbackRequest.setItemType(restoredRequest.getItemType());
            rollbackRequest.setItemId(restoredRequest.getItemId());
            rollbackRequest.setQuantity(restoredRequest.getQuantity());
            rollbackRequest.setReason("生产计划-" + batchNo + "-取消返还回滚");
            inventoryService.stockOut(rollbackRequest, "system");
        }
    }

    private void rollbackRestoredRawMaterialReceipts(List<InventoryDeductionReceipt> restoredReceipts, String batchNo) {
        for (int i = restoredReceipts.size() - 1; i >= 0; i--) {
            InventoryDeductionReceipt restoredReceipt = restoredReceipts.get(i);
            StockInOutRequest rollbackRequest = new StockInOutRequest();
            rollbackRequest.setItemType(restoredReceipt.getItemType());
            rollbackRequest.setItemId(restoredReceipt.getItemId());
            rollbackRequest.setQuantity(restoredReceipt.getQuantity());
            rollbackRequest.setReason("生产计划-" + batchNo + "-取消返还回滚");
            inventoryService.stockOut(rollbackRequest, "system");
        }
    }

    private void executeRawMaterialRestore(List<StockInOutRequest> restoreRequests, String batchNo) {
        List<StockInOutRequest> restoredRequests = new ArrayList<>();
        try {
            for (StockInOutRequest restoreRequest : restoreRequests) {
                inventoryService.stockIn(restoreRequest, "system");
                restoredRequests.add(restoreRequest);
            }
        } catch (RuntimeException ex) {
            try {
                rollbackRestoredRawMaterials(restoredRequests, batchNo);
            } catch (RuntimeException rollbackEx) {
                IllegalStateException fatal = new IllegalStateException("原材料返还失败且补偿回滚失败，请立即人工核对库存", rollbackEx);
                fatal.addSuppressed(ex);
                throw fatal;
            }
            throw ex;
        }
    }

    private void completePlanMaterialRestore(String planId) {
        if (!mongoAtomicOpsService.completePlanMaterialsRestore(planId)) {
            throw new IllegalStateException("原材料返还成功，但计划返还标记未能完成更新，请立即人工核对生产计划");
        }
    }

    private void releasePlanMaterialRestore(String planId, RuntimeException ex) {
        if (!mongoAtomicOpsService.releasePlanMaterialsRestore(planId)) {
            throw new IllegalStateException("原材料返还失败，且计划返还标记未能回滚，请立即人工核对生产计划", ex);
        }
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
        MaterialAdjustmentResult adjustmentResult = MaterialAdjustmentResult.empty();
        List<TaskQuantitySnapshot> taskRollbackSnapshots = new ArrayList<>();

        if ("CANCELLED".equals(plan.getStatus())) {
            throw new BusinessException("已取消的生产计划不允许编辑");
        }

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

        try {
            Integer oldQuantity = plan.getQuantity();
            if (request.getQuantity() != null) {
                int quantityDiff = request.getQuantity() - oldQuantity;
                if (quantityDiff != 0) {
                    adjustmentResult = adjustInventoryForQuantityChange(plan, oldQuantity, request.getQuantity());
                    plan.setMaterialDeductionReceipts(adjustmentResult.getUpdatedReceipts());
                    taskRollbackSnapshots = captureTaskQuantitySnapshots(plan.getId());
                    syncTaskPlanQuantities(taskRollbackSnapshots, plan.getId(), request.getQuantity());
                }
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
            if (request.getColor() != null) {
                plan.setColor(request.getColor());
            }
            if (request.getSize() != null) {
                plan.setSize(request.getSize());
            }

            ProductionPlan saved = productionPlanRepository.save(plan);
            return convertToVO(saved);
        } catch (RuntimeException ex) {
            rollbackTaskPlanQuantities(taskRollbackSnapshots);
            rollbackPlanInventoryAdjustments(adjustmentResult);
            throw ex;
        }
    }

    private void executeRawMaterialReceiptRestore(List<InventoryDeductionReceipt> restoreReceipts, String batchNo) {
        List<InventoryDeductionReceipt> restoredReceipts = new ArrayList<>();
        try {
            for (InventoryDeductionReceipt restoreReceipt : restoreReceipts) {
                inventoryService.restoreInventoryDeduction(restoreReceipt, "生产计划-" + batchNo + "-取消返还");
                restoredReceipts.add(restoreReceipt);
            }
        } catch (RuntimeException ex) {
            try {
                rollbackRestoredRawMaterialReceipts(restoredReceipts, batchNo);
            } catch (RuntimeException rollbackEx) {
                IllegalStateException fatal = new IllegalStateException("原材料返还失败且补偿回滚失败，请立即人工核对库存", rollbackEx);
                fatal.addSuppressed(ex);
                throw fatal;
            }
            throw ex;
        }
    }

    private MaterialAdjustmentResult adjustInventoryForQuantityChange(ProductionPlan plan, int oldQty, int newQty) {
        ProductDefinition productDef = productDefinitionRepository.findById(plan.getProductDefinitionId()).orElse(null);
        if (productDef == null || productDef.getMaterials() == null || productDef.getMaterials().isEmpty()) {
            return MaterialAdjustmentResult.empty();
        }

        int diff = newQty - oldQty;
        List<InventoryDeductionReceipt> updatedReceipts = copyInventoryDeductionReceipts(plan.getMaterialDeductionReceipts());
        List<InventoryDeductionReceipt> deductedReceipts = new ArrayList<>();
        List<InventoryDeductionReceipt> restoredReceipts = new ArrayList<>();
        List<StockInOutRequest> rollbackRequests = new ArrayList<>();
        for (ProductDefinition.ProductMaterial material : productDef.getMaterials()) {
            double unitNeed = material.getQuantity();
            int adjustIntQty = (int) Math.round(unitNeed * Math.abs(diff));
            if (adjustIntQty <= 0) {
                continue;
            }

            if (shouldUseReceiptBasedQuantityAdjustment()) {
                if (diff > 0) {
                    InventoryDeductionReceipt receipt = inventoryService.fifoDeductRawMaterialWithReceipt(
                            material.getMaterialId(),
                            adjustIntQty,
                            "生产计划-" + plan.getBatchNo() + "-数量调整(从" + oldQty + "增至" + newQty + ")-扣减");
                    if (receipt != null) {
                        deductedReceipts.add(receipt);
                        updatedReceipts.add(receipt);
                    }
                } else {
                    List<InventoryDeductionReceipt> materialRestoreReceipts = extractRestoreReceipts(
                            updatedReceipts,
                            material.getMaterialId(),
                            material.getMaterialName(),
                            adjustIntQty);
                    for (InventoryDeductionReceipt restoreReceipt : materialRestoreReceipts) {
                        inventoryService.restoreInventoryDeduction(
                                restoreReceipt,
                                "生产计划-" + plan.getBatchNo() + "-数量调整(从" + oldQty + "减至" + newQty + ")-返还");
                    }
                    restoredReceipts.addAll(materialRestoreReceipts);
                }
                continue;
            }

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

                StockInOutRequest rollbackRequest = new StockInOutRequest();
                rollbackRequest.setItemType("RAW_MATERIAL");
                rollbackRequest.setItemId(material.getMaterialId());
                rollbackRequest.setQuantity(adjustIntQty);
                rollbackRequest.setReason("生产计划-" + plan.getBatchNo()
                        + "-数量调整(从" + oldQty + "增至" + newQty + ")-扣减回滚");
                rollbackRequests.add(rollbackRequest);
            } else {
                StockInOutRequest stockInRequest = new StockInOutRequest();
                stockInRequest.setItemType("RAW_MATERIAL");
                stockInRequest.setItemId(material.getMaterialId());
                stockInRequest.setQuantity(adjustIntQty);
                stockInRequest.setReason("生产计划-" + plan.getBatchNo()
                        + "-数量调整(从" + oldQty + "减至" + newQty + ")-返还");
                inventoryService.stockIn(stockInRequest, "system");

                StockInOutRequest rollbackRequest = new StockInOutRequest();
                rollbackRequest.setItemType("RAW_MATERIAL");
                rollbackRequest.setItemId(material.getMaterialId());
                rollbackRequest.setQuantity(adjustIntQty);
                rollbackRequest.setReason("生产计划-" + plan.getBatchNo()
                        + "-数量调整(从" + oldQty + "减至" + newQty + ")-返还回滚");
                rollbackRequests.add(rollbackRequest);
            }
        }
        return new MaterialAdjustmentResult(updatedReceipts, deductedReceipts, restoredReceipts);
    }

    private void rollbackPlanInventoryAdjustments(List<StockInOutRequest> rollbackRequests) {
        for (int i = rollbackRequests.size() - 1; i >= 0; i--) {
            StockInOutRequest rollbackRequest = rollbackRequests.get(i);
            if (rollbackRequest.getReason() != null && rollbackRequest.getReason().contains("扣减回滚")) {
                inventoryService.stockIn(rollbackRequest, "system");
            } else {
                inventoryService.stockOut(rollbackRequest, "system");
            }
        }
    }

    private void rollbackPlanInventoryAdjustments(MaterialAdjustmentResult adjustmentResult) {
        if (adjustmentResult == null) {
            return;
        }

        for (int i = adjustmentResult.getRestoredReceipts().size() - 1; i >= 0; i--) {
            InventoryDeductionReceipt restoredReceipt = adjustmentResult.getRestoredReceipts().get(i);
            inventoryService.fifoDeductRawMaterialWithReceipt(
                    restoredReceipt.getItemId(),
                    restoredReceipt.getQuantity(),
                    "生产计划-数量调整返还回滚");
        }

        for (int i = adjustmentResult.getDeductedReceipts().size() - 1; i >= 0; i--) {
            InventoryDeductionReceipt deductedReceipt = adjustmentResult.getDeductedReceipts().get(i);
            inventoryService.restoreInventoryDeduction(deductedReceipt, "生产计划-数量调整扣减回滚");
        }
    }

    private boolean shouldUseReceiptBasedQuantityAdjustment() {
        return true;
    }

    private List<InventoryDeductionReceipt> copyInventoryDeductionReceipts(List<InventoryDeductionReceipt> receipts) {
        List<InventoryDeductionReceipt> copies = new ArrayList<>();
        if (receipts == null) {
            return copies;
        }
        for (InventoryDeductionReceipt receipt : receipts) {
            if (receipt == null) {
                continue;
            }
            copies.add(copyInventoryDeductionReceipt(receipt));
        }
        return copies;
    }

    private InventoryDeductionReceipt copyInventoryDeductionReceipt(InventoryDeductionReceipt receipt) {
        return new InventoryDeductionReceipt(
                receipt.getItemType(),
                receipt.getItemId(),
                receipt.getItemName(),
                receipt.getQuantity(),
                receipt.isTotalOnly(),
                copyLocationDeductions(receipt.getLocationDeductions()));
    }

    private List<InventoryDeductionReceipt.LocationDeduction> copyLocationDeductions(
            List<InventoryDeductionReceipt.LocationDeduction> deductions) {
        List<InventoryDeductionReceipt.LocationDeduction> copies = new ArrayList<>();
        if (deductions == null) {
            return copies;
        }
        for (InventoryDeductionReceipt.LocationDeduction deduction : deductions) {
            if (deduction == null) {
                continue;
            }
            copies.add(new InventoryDeductionReceipt.LocationDeduction(
                    deduction.getLocation(),
                    deduction.getQuantity(),
                    deduction.getCreatedAt()));
        }
        return copies;
    }

    private List<InventoryDeductionReceipt> extractRestoreReceipts(List<InventoryDeductionReceipt> updatedReceipts,
                                                                   String itemId,
                                                                   String itemName,
                                                                   int quantityToRestore) {
        int remaining = quantityToRestore;
        List<InventoryDeductionReceipt> restoreReceipts = new ArrayList<>();

        for (int i = updatedReceipts.size() - 1; i >= 0 && remaining > 0; i--) {
            InventoryDeductionReceipt receipt = updatedReceipts.get(i);
            if (receipt == null || !Objects.equals(itemId, receipt.getItemId())
                    || receipt.getQuantity() == null || receipt.getQuantity() <= 0) {
                continue;
            }

            InventoryDeductionReceipt extractedReceipt = extractPartialReceipt(receipt, remaining, itemName);
            if (extractedReceipt == null || extractedReceipt.getQuantity() == null || extractedReceipt.getQuantity() <= 0) {
                continue;
            }

            remaining -= extractedReceipt.getQuantity();
            restoreReceipts.add(extractedReceipt);
            if (receipt.getQuantity() == null || receipt.getQuantity() <= 0) {
                updatedReceipts.remove(i);
            }
        }

        if (remaining > 0) {
            throw new BusinessException("计划原材料扣减记录不足，无法返还数量调整库存");
        }

        return restoreReceipts;
    }

    private InventoryDeductionReceipt extractPartialReceipt(InventoryDeductionReceipt receipt,
                                                            int maxQuantity,
                                                            String fallbackItemName) {
        int availableQuantity = receipt.getQuantity() != null ? receipt.getQuantity() : 0;
        if (availableQuantity <= 0 || maxQuantity <= 0) {
            return null;
        }

        int extractedQuantity = Math.min(availableQuantity, maxQuantity);
        String itemName = StringUtils.hasText(receipt.getItemName()) ? receipt.getItemName() : fallbackItemName;

        if (receipt.isTotalOnly() || receipt.getLocationDeductions() == null || receipt.getLocationDeductions().isEmpty()) {
            receipt.setQuantity(availableQuantity - extractedQuantity);
            return new InventoryDeductionReceipt(
                    receipt.getItemType(),
                    receipt.getItemId(),
                    itemName,
                    extractedQuantity,
                    true,
                    new ArrayList<>());
        }

        int remaining = extractedQuantity;
        List<InventoryDeductionReceipt.LocationDeduction> extractedDeductions = new ArrayList<>();
        List<InventoryDeductionReceipt.LocationDeduction> locationDeductions = receipt.getLocationDeductions();
        for (int i = locationDeductions.size() - 1; i >= 0 && remaining > 0; i--) {
            InventoryDeductionReceipt.LocationDeduction locationDeduction = locationDeductions.get(i);
            if (locationDeduction == null || locationDeduction.getQuantity() == null || locationDeduction.getQuantity() <= 0) {
                continue;
            }

            int deductionQuantity = Math.min(locationDeduction.getQuantity(), remaining);
            locationDeduction.setQuantity(locationDeduction.getQuantity() - deductionQuantity);
            extractedDeductions.add(0, new InventoryDeductionReceipt.LocationDeduction(
                    locationDeduction.getLocation(),
                    deductionQuantity,
                    locationDeduction.getCreatedAt()));
            remaining -= deductionQuantity;

            if (locationDeduction.getQuantity() <= 0) {
                locationDeductions.remove(i);
            }
        }

        receipt.setQuantity(availableQuantity - extractedQuantity);
        return new InventoryDeductionReceipt(
                receipt.getItemType(),
                receipt.getItemId(),
                itemName,
                extractedQuantity,
                false,
                extractedDeductions);
    }

    private static class MaterialAdjustmentResult {
        private final List<InventoryDeductionReceipt> updatedReceipts;
        private final List<InventoryDeductionReceipt> deductedReceipts;
        private final List<InventoryDeductionReceipt> restoredReceipts;

        private MaterialAdjustmentResult(List<InventoryDeductionReceipt> updatedReceipts,
                                         List<InventoryDeductionReceipt> deductedReceipts,
                                         List<InventoryDeductionReceipt> restoredReceipts) {
            this.updatedReceipts = updatedReceipts;
            this.deductedReceipts = deductedReceipts;
            this.restoredReceipts = restoredReceipts;
        }

        private static MaterialAdjustmentResult empty() {
            return new MaterialAdjustmentResult(new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
        }

        private List<InventoryDeductionReceipt> getUpdatedReceipts() {
            return updatedReceipts;
        }

        private List<InventoryDeductionReceipt> getDeductedReceipts() {
            return deductedReceipts;
        }

        private List<InventoryDeductionReceipt> getRestoredReceipts() {
            return restoredReceipts;
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

        if (Boolean.TRUE.equals(plan.getMaterialsRestoreInProgress())) {
            throw new BusinessException("计划原材料返还处理中，请稍后重试");
        }

        List<InventoryDeductionReceipt> restoreReceipts = buildRawMaterialRestoreReceipts(plan);
        List<StockInOutRequest> restoreRequests = restoreReceipts.isEmpty()
                ? buildRawMaterialRestoreRequests(plan, plan.getBatchNo(), false)
                : new ArrayList<>();
        if (Boolean.TRUE.equals(plan.getMaterialsDeducted()) && restoreReceipts.isEmpty() && restoreRequests.isEmpty()) {
            throw new BusinessException("原材料返还信息缺失，请先人工核对后再删除");
        }
        if (!restoreReceipts.isEmpty() || !restoreRequests.isEmpty()) {
            if (!mongoAtomicOpsService.markPlanMaterialsRestoreInProgress(id)) {
                throw new BusinessException("计划状态已变更，请刷新后再操作");
            }
            try {
                if (!restoreReceipts.isEmpty()) {
                    executeRawMaterialReceiptRestore(restoreReceipts, plan.getBatchNo());
                } else {
                    executeRawMaterialRestore(restoreRequests, plan.getBatchNo());
                }
                completePlanMaterialRestore(id);
            } catch (IllegalStateException fatalEx) {
                throw fatalEx;
            } catch (RuntimeException ex) {
                releasePlanMaterialRestore(id, ex);
                throw ex;
            }
        }

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
            throw new BusinessException("审批状态只能为【审批通过】或【已取消】");
        }

        List<InventoryDeductionReceipt> restoreReceipts = "CANCELLED".equals(status)
                ? buildRawMaterialRestoreReceipts(plan)
                : new ArrayList<>();
        List<StockInOutRequest> restoreRequests = "CANCELLED".equals(status) && restoreReceipts.isEmpty()
                ? buildRawMaterialRestoreRequests(plan, plan.getBatchNo(), true)
                : new ArrayList<>();
        Document transitionExtraFields = (!restoreReceipts.isEmpty() || !restoreRequests.isEmpty())
                ? new Document("materialsRestoreInProgress", true)
                : null;
        assertPlanStatusChanged(mongoAtomicOpsService.transitionPlanStatus(id, "PENDING", status, transitionExtraFields));

        if (!restoreReceipts.isEmpty() || !restoreRequests.isEmpty()) {
            try {
                if (!restoreReceipts.isEmpty()) {
                    executeRawMaterialReceiptRestore(restoreReceipts, plan.getBatchNo());
                } else {
                    executeRawMaterialRestore(restoreRequests, plan.getBatchNo());
                }
                completePlanMaterialRestore(id);
            } catch (IllegalStateException fatalEx) {
                throw fatalEx;
            } catch (RuntimeException ex) {
                releasePlanMaterialRestore(id, ex);
                throw ex;
            }
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
        } catch (RuntimeException ex) {
            boolean rolledBack;
            try {
                rolledBack = mongoAtomicOpsService.transitionPlanStatus(planId, "IN_PROGRESS", "APPROVED", null);
            } catch (RuntimeException rollbackEx) {
                IllegalStateException fatal = new IllegalStateException("任务创建失败，且计划状态回滚失败，请立即人工核对生产计划", rollbackEx);
                fatal.addSuppressed(ex);
                throw fatal;
            }
            if (!rolledBack) {
                IllegalStateException fatal = new IllegalStateException("任务创建失败，且计划状态回滚未生效，请立即人工核对生产计划");
                fatal.addSuppressed(ex);
                throw fatal;
            }
            throw ex;
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

    private static class TaskQuantitySnapshot {
        private final ProductionTask task;
        private final Integer planQuantity;
        private final Integer completedQuantity;
        private final Integer progress;

        private TaskQuantitySnapshot(ProductionTask task) {
            this.task = task;
            this.planQuantity = task.getPlanQuantity();
            this.completedQuantity = task.getCompletedQuantity();
            this.progress = task.getProgress();
        }

        private ProductionTask getTask() {
            return task;
        }

        private void restore() {
            task.setPlanQuantity(planQuantity);
            task.setCompletedQuantity(completedQuantity);
            task.setProgress(progress);
        }
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
