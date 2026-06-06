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

        /**
         * 创建生产计划。
         *
         * <p>该方法会先根据请求中的产品定义ID查询产品基础信息，再根据生产数量校验并扣减所需原材料库存。
         * 原材料扣减成功后，系统会组装生产计划实体，将计划初始化为待生产状态，并保存到数据库。
         * 如果生产计划保存失败，则会根据扣减凭证回滚已经扣减的原材料库存，避免库存数据与生产计划数据不一致。</p>
         *
         * @param request 创建生产计划的请求参数，包含产品定义ID、批次号、计划数量、生产周期、规格信息等
         * @param userId 当前创建生产计划的用户ID
         * @return 创建成功后的生产计划视图对象
         * @throws BusinessException 当产品定义不存在、原材料库存不足或扣减失败时抛出
         */
        @Override
        public PlanVO createPlan(PlanCreateRequest request, String userId) {
            // 根据产品定义ID查询产品基础信息，生产计划中的产品编码、名称、分类等字段会从这里带出。
            ProductDefinition productDef = productDefinitionRepository.findById(request.getProductDefinitionId())
                    .orElseThrow(() -> new BusinessException("产品定义不存在"));

            // 创建生产计划前先校验并扣减所需原材料，返回的扣减凭证用于保存失败时回滚库存。
            List<InventoryDeductionReceipt> deductionReceipts =
                    checkAndDeductRawMaterials(productDef, request.getQuantity(), request.getBatchNo());

            // 组装生产计划实体，初始化批次、数量、周期、状态以及创建人等核心信息。
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
            // 记录原材料是否已扣减及对应凭证，便于后续追踪和异常回滚。
            plan.setMaterialsDeducted(!deductionReceipts.isEmpty());
            plan.setMaterialDeductionReceipts(deductionReceipts);

            try {
                // 保存生产计划并转换为前端需要的VO对象。
                ProductionPlan saved = productionPlanRepository.save(plan);
                return convertToVO(saved);
            } catch (RuntimeException ex) {
                // 如果保存计划失败，恢复前面已经扣减的原材料，避免库存与生产计划不一致。
                rollbackCreatedPlanMaterialDeductions(deductionReceipts, request.getBatchNo(), ex);
                throw ex;
            }
        }
    

        /**
         * 回滚生产计划创建时扣减的原材料库存。
         *
         * <p>当生产计划创建失败时，该方法会按照扣减凭证的逆序依次恢复已扣减的原材料库存。
         * 如果在回滚过程中发生异常，则抛出致命错误并保留原始异常信息，提示需要人工核对库存。</p>
         *
         * @param deductionReceipts 原材料扣减凭证列表，用于指导库存回滚操作
         * @param batchNo 生产计划批次号，用于生成回滚操作的说明信息
         * @param originalEx 原始异常信息，在回滚失败时会作为被抑制的异常添加到新异常中
         */
        private void rollbackCreatedPlanMaterialDeductions(List<InventoryDeductionReceipt> deductionReceipts,
                                                           String batchNo,
                                                           RuntimeException originalEx) {
            // 按逆序回滚扣减凭证，确保先扣减的后恢复，避免库存状态不一致。
            for (int i = deductionReceipts.size() - 1; i >= 0; i--) {
                InventoryDeductionReceipt receipt = deductionReceipts.get(i);
                try {
                    inventoryService.restoreInventoryDeduction(receipt, "生产计划-" + batchNo + "-创建回滚");
                } catch (RuntimeException rollbackEx) {
                    // 回滚失败时抛出致命错误，同时保留原始异常和回滚异常信息。
                    IllegalStateException fatal = new IllegalStateException("生产计划创建失败，且原材料扣减回滚失败，请立即人工核对库存", rollbackEx);
                    fatal.addSuppressed(originalEx);
                    throw fatal;
                }
            }
        }
    

        /**
         * 校验并扣减生产计划所需的原材料库存。
         *
         * <p>该方法首先检查产品定义中配置的所有原材料是否都有足够的库存，如果任何原材料库存不足，
         * 则抛出业务异常并列出所有缺口的详细信息。校验通过后，按照FIFO（先进先出）策略依次扣减
         * 各原材料的库存，并返回扣减凭证列表用于后续可能的回滚操作。</p>
         *
         * @param productDef 产品定义信息，包含该产品所需的所有原材料清单及单位用量
         * @param planQuantity 生产计划的计划数量，用于计算总需求
         * @param batchNo 生产计划批次号，用于标识扣减操作的来源
         * @return 原材料扣减凭证列表，如果产品未配置原材料则返回空列表
         * @throws BusinessException 当原材料不存在或库存不足时抛出，包含详细的缺口信息
         */
        private List<InventoryDeductionReceipt> checkAndDeductRawMaterials(ProductDefinition productDef, Integer planQuantity, String batchNo) {
            // 如果产品未配置原材料，直接返回空列表，无需扣减。
            if (productDef.getMaterials() == null || productDef.getMaterials().isEmpty()) {
                return new ArrayList<>();
            }

            List<String> insufficientMaterials = new ArrayList<>();

            // 第一轮遍历：校验所有原材料的库存是否充足，收集库存不足的信息。
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

            // 如果存在库存不足的原材料，汇总所有缺口信息并抛出异常，阻止生产计划创建。
            if (!insufficientMaterials.isEmpty()) {
                throw new BusinessException("原材料库存不足，无法创建生产计划：" + String.join("；", insufficientMaterials));
            }

            // 第二轮遍历：库存校验通过后，执行FIFO扣减并收集扣减凭证。
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
    

        /**
         * 捕获生产计划关联任务的当前数量快照。
         *
         * <p>该方法查询指定生产计划下的所有生产任务，并将每个任务的当前数量信息封装为快照对象。
         * 这些快照用于后续操作失败时的回滚，确保任务的计划数量、完成数量等状态可以恢复到修改前。</p>
         *
         * @param planId 生产计划的ID，用于查询关联的生产任务
         * @return 任务数量快照列表，如果计划没有关联任务则返回空列表
         */
        private List<TaskQuantitySnapshot> captureTaskQuantitySnapshots(String planId) {
            List<ProductionTask> tasks = productionTaskRepository.findByPlanId(planId);
            if (tasks == null || tasks.isEmpty()) {
                return new ArrayList<>();
            }
            // 将每个生产任务转换为数量快照，保存当前的计划数量、完成数量、进度和状态等信息。
            return tasks.stream()
                    .map(TaskQuantitySnapshot::new)
                    .collect(Collectors.toList());
        }
    

    /**
     * 同步任务计划数量并更新相关进度和状态
     *
     * <p>该方法会遍历所有任务快照，将每个生产任务的计划数量更新为新值，
     * 并根据已完成数量重新计算进度百分比，最后同步任务状态。</p>
     *
     * @param taskSnapshots 任务数量快照列表，包含需要更新的生产任务信息
     * @param newPlanQuantity 新的计划数量值，用于更新所有任务的计划数量
     * @return 所有任务的已完成数量总和，如果任务已完成数量为空则按0计算
     */
    private int syncTaskPlanQuantities(List<TaskQuantitySnapshot> taskSnapshots, int newPlanQuantity) {
        if (taskSnapshots.isEmpty()) {
            return 0;
        }

        // 遍历所有任务快照，更新计划数量、进度和状态
        for (TaskQuantitySnapshot snapshot : taskSnapshots) {
            ProductionTask task = snapshot.getTask();
            task.setPlanQuantity(newPlanQuantity);

            // 当计划数量和已完成数量都有效时，重新计算进度并同步任务状态
            if (task.getPlanQuantity() != null && task.getPlanQuantity() > 0
                    && task.getCompletedQuantity() != null) {
                int newProgress = (int) Math.round(task.getCompletedQuantity() * 100.0 / task.getPlanQuantity());
                task.setProgress(Math.min(newProgress, 100));
                syncTaskStatusAfterQuantityChange(task);
            }
            productionTaskRepository.save(task);
        }

        // 计算并返回所有任务的已完成数量总和
        return taskSnapshots.stream()
                .map(TaskQuantitySnapshot::getTask)
                .mapToInt(t -> t.getCompletedQuantity() != null ? t.getCompletedQuantity() : 0)
                .sum();
    }


    /**
     * 解析并确定最终的已完成数量
     *
     * <p>该方法比较生产计划层面的已完成数量和所有任务层面的已完成数量总和，
     * 取两者中的较大值作为最终的已完成数量，以确保数据的准确性和一致性。</p>
     *
     * @param plan 生产计划对象，从中获取计划层面的已完成数量
     * @param taskSnapshots 任务数量快照列表，用于计算所有任务的已完成数量总和
     * @return 最终的已完成数量，取计划层面和任务层面已完成数量的最大值
     */
    private int resolveCompletedQuantity(ProductionPlan plan, List<TaskQuantitySnapshot> taskSnapshots) {
        int planCompleted = plan.getCompletedQuantity() != null ? plan.getCompletedQuantity() : 0;

        // 计算所有任务的已完成数量总和
        int taskCompleted = taskSnapshots.stream()
                .map(TaskQuantitySnapshot::getTask)
                .mapToInt(t -> t.getCompletedQuantity() != null ? t.getCompletedQuantity() : 0)
                .sum();

        // 返回计划层面和任务层面已完成数量的最大值，确保数据一致性
        return Math.max(planCompleted, taskCompleted);
    }


    /**
     * 在数量变更后同步任务状态
     *
     * <p>该方法根据任务的计划数量和已完成数量的关系，自动更新任务的状态和进度：
     * 如果已完成数量达到或超过计划数量，则标记为已完成并设置结束时间；
     * 如果任务之前是完成状态或已有完成数量但未达到计划数量，则标记为进行中。</p>
     *
     * @param task 需要更新状态的生产任务对象
     */
    private void syncTaskStatusAfterQuantityChange(ProductionTask task) {
        int planQuantity = task.getPlanQuantity() != null ? task.getPlanQuantity() : 0;
        int completedQuantity = task.getCompletedQuantity() != null ? task.getCompletedQuantity() : 0;

        // 计划数量无效时直接返回，避免除零错误
        if (planQuantity <= 0) {
            return;
        }

        // 已完成数量达到或超过计划数量时，标记任务为已完成状态
        if (completedQuantity >= planQuantity) {
            task.setProgress(100);
            task.setStatus("COMPLETED");
            if (task.getEndDate() == null) {
                task.setEndDate(new Date());
            }
            return;
        }

        // 任务之前是完成状态或已有部分完成但未达到计划数量时，恢复为进行中状态
        if ("COMPLETED".equals(task.getStatus()) || completedQuantity > 0) {
            task.setStatus("IN_PROGRESS");
            task.setEndDate(null);
        }
    }


    /**
     * 回滚任务计划数量到之前的状态
     * <p>
     * 该方法按照从后往前的顺序恢复任务数量快照，确保在事务失败或其他异常情况下
     * 能够将任务计划数量恢复到操作前的状态，保证数据一致性。
     * </p>
     *
     * @param taskSnapshots 任务数量快照列表，包含需要回滚的任务及其对应的数量信息
     */
    private void rollbackTaskPlanQuantities(List<TaskQuantitySnapshot> taskSnapshots) {
        // 逆序遍历快照列表，确保回滚顺序与执行顺序相反
        for (int i = taskSnapshots.size() - 1; i >= 0; i--) {
            TaskQuantitySnapshot snapshot = taskSnapshots.get(i);
            // 恢复任务到快照记录的状态
            snapshot.restore();
            // 持久化恢复后的任务数据到数据库
            productionTaskRepository.save(snapshot.getTask());
        }
    }


    /**
     * 构建原材料库存返还请求列表
     * <p>
     * 该方法根据生产计划快照和产品信息，计算需要返还的原材料数量并生成对应的库存入库请求。
     * 主要用于生产计划取消时，将已扣除的原材料重新返还到库存中。
     * </p>
     *
     * @param planSnapshot 生产计划快照，包含产品定义ID、生产数量等信息
     * @param batchNo 批次号，用于生成返还原因说明
     * @param strictValidation 是否启用严格验证模式，true表示遇到异常时抛出异常，false表示静默返回空列表
     * @return 原材料库存返还请求列表，每个请求对应一种原材料的返还操作
     */
    private List<StockInOutRequest> buildRawMaterialRestoreRequests(ProductionPlan planSnapshot, String batchNo,
                                                                   boolean strictValidation) {
        List<StockInOutRequest> restoreRequests = new ArrayList<>();

        // 校验生产计划状态，只有已扣除材料的计划才需要返还
        if (planSnapshot == null || Boolean.FALSE.equals(planSnapshot.getMaterialsDeducted())) {
            return restoreRequests;
        }

        // 获取产品定义信息
        ProductDefinition productDef = productDefinitionRepository.findById(planSnapshot.getProductDefinitionId()).orElse(null);
        if (productDef == null) {
            if (strictValidation) {
                throw new BusinessException("产品定义不存在，无法返还原材料");
            }
            return restoreRequests;
        }

        // 校验产品是否配置了原材料
        if (productDef.getMaterials() == null || productDef.getMaterials().isEmpty()) {
            if (strictValidation) {
                throw new BusinessException("产品定义未配置原材料，无法返还原材料");
            }
            return restoreRequests;
        }

        // 遍历所有原材料，计算返还数量并生成库存请求
        for (ProductDefinition.ProductMaterial material : productDef.getMaterials()) {
            // 计算该材料的总返还数量：单件用量 × 生产数量
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


    /**
     * 构建原材料返还的库存扣减凭证列表
     * <p>
     * 该方法从生产计划快照中获取之前扣除原材料时生成的凭证信息，用于后续的库存返还操作。
     * 优先使用快照中存储的凭证数据，如果不存在则通过批次号重新构建历史凭证。
     * </p>
     *
     * @param planSnapshot 生产计划快照，包含物料扣减凭证信息
     * @return 原材料库存扣减凭证列表，仅包含RAW_MATERIAL类型的凭证
     */
    private List<InventoryDeductionReceipt> buildRawMaterialRestoreReceipts(ProductionPlan planSnapshot) {
        // 校验生产计划状态，只有已扣除材料的计划才有返还凭证
        if (planSnapshot == null || Boolean.FALSE.equals(planSnapshot.getMaterialsDeducted())) {
            return new ArrayList<>();
        }

        // 从快照中提取原材料类型的扣减凭证
        List<InventoryDeductionReceipt> storedReceipts = planSnapshot.getMaterialDeductionReceipts() == null
                ? new ArrayList<>()
                : planSnapshot.getMaterialDeductionReceipts().stream()
                .filter(receipt -> receipt != null && "RAW_MATERIAL".equals(receipt.getItemType()))
                .collect(Collectors.toList());

        // 如果存在已存储的凭证则直接返回，否则通过批次号重新构建历史凭证
        if (!storedReceipts.isEmpty()) {
            return storedReceipts;
        }
        return rebuildLegacyRawMaterialRestoreReceipts(planSnapshot.getBatchNo());
    }


    /**
     * 重新构建历史原材料返还的库存扣减凭证
     * <p>
     * 该方法用于处理旧版本数据，当生产计划快照中未存储扣减凭证时，通过批次号从库存记录中
     * 重新构建凭证信息。主要兼容早期版本的FIFO扣减记录。
     * </p>
     *
     * @param batchNo 生产计划批次号，用于筛选对应的库存扣减记录
     * @return 重新构建的库存扣减凭证列表，仅包含与指定批次号相关的RAW_MATERIAL类型出库记录
     */
    private List<InventoryDeductionReceipt> rebuildLegacyRawMaterialRestoreReceipts(String batchNo) {
        // 校验批次号有效性
        if (!StringUtils.hasText(batchNo)) {
            return new ArrayList<>();
        }

        // 构建原因前缀，用于匹配相关的库存记录
        String reasonPrefix = "生产计划-" + batchNo + "-FIFO扣减";

        // 遍历所有库存记录，筛选出符合条件的出库记录并转换为凭证
        return inventoryRecordRepository.findAll().stream()
                .filter(record -> "OUT".equals(record.getInventoryType()))
                .filter(record -> "RAW_MATERIAL".equals(record.getItemType()))
                .filter(record -> StringUtils.hasText(record.getReason()) && record.getReason().startsWith(reasonPrefix))
                .map(this::buildReceiptFromInventoryRecord)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }


    /**
     * 从库存记录构建库存扣减凭证
     * <p>
     * 该方法将库存出库记录转换为标准的扣减凭证对象，提取FIFO扣减详情并构建位置扣减明细。
     * 用于历史数据的凭证重建，支持总量扣减和分位置扣减两种模式。
     * </p>
     *
     * @param record 库存记录，包含物品ID、名称、数量和扣减原因等信息
     * @return 库存扣减凭证对象，如果记录无效或无法解析则返回null
     */
    private InventoryDeductionReceipt buildReceiptFromInventoryRecord(InventoryRecord record) {
        Integer recordedQuantity = record.getQuantity();

        // 校验必要字段的有效性
        if (!StringUtils.hasText(record.getItemId()) || recordedQuantity == null || recordedQuantity == 0) {
            return null;
        }

        // 从扣减原因中提取FIFO详情
        String fifoDetail = extractFifoDetail(record.getReason());
        if (!StringUtils.hasText(fifoDetail)) {
            return null;
        }

        // 取数量的绝对值作为扣减数量
        int quantity = Math.abs(recordedQuantity);

        // 处理总量扣减模式，不包含具体位置信息
        if (fifoDetail.startsWith("TOTAL(") && fifoDetail.endsWith(")")) {
            return new InventoryDeductionReceipt(
                    "RAW_MATERIAL",
                    record.getItemId(),
                    record.getItemName(),
                    quantity,
                    true,
                    new ArrayList<>());
        }

        // 解析分位置扣减明细
        List<InventoryDeductionReceipt.LocationDeduction> locationDeductions = parseLocationDeductions(fifoDetail);
        if (locationDeductions.isEmpty()) {
            return null;
        }

        // 构建包含位置扣减明细的凭证
        return new InventoryDeductionReceipt(
                "RAW_MATERIAL",
                record.getItemId(),
                record.getItemName(),
                quantity,
                false,
                locationDeductions);
    }


    /**
     * 从扣减原因字符串中提取FIFO详情信息
     * <p>
     * 该方法解析包含[FIFO:xxx]格式的文本，提取方括号内的FIFO扣减详细信息。
     * 用于从库存记录的reason字段中分离出FIFO扣减的具体内容。
     * </p>
     *
     * @param reason 扣减原因字符串，可能包含[FIFO:详情]格式的信息
     * @return FIFO详情字符串，如果未找到或格式不正确则返回null
     */
    private String extractFifoDetail(String reason) {
        if (!StringUtils.hasText(reason)) {
            return null;
        }

        // 查找最后一个[FIFO:标记和对应的结束方括号位置
        int start = reason.lastIndexOf("[FIFO:");
        int end = reason.lastIndexOf(']');
        if (start < 0 || end <= start + 6) {
            return null;
        }

        // 提取并返回FIFO标记与结束方括号之间的内容
        return reason.substring(start + 6, end).trim();
    }


    /**
     * 解析FIFO详情字符串，提取位置扣减明细列表
     * <p>
     * 该方法使用正则表达式匹配"位置名称(数量)"格式的文本，将其转换为位置扣减对象列表。
     * 要求格式严格匹配，任何格式错误或不连续的内容都会导致返回空列表。
     * 例如："仓库A(10)仓库B(20)" 会被解析为两个位置扣减记录。
     * </p>
     *
     * @param fifoDetail FIFO详情字符串，包含一个或多个"位置名称(数量)"格式的扣减信息
     * @return 位置扣减明细列表，如果格式不正确或解析失败则返回空列表
     */
    private List<InventoryDeductionReceipt.LocationDeduction> parseLocationDeductions(String fifoDetail) {
        List<InventoryDeductionReceipt.LocationDeduction> deductions = new ArrayList<>();

        // 使用正则表达式匹配"位置名称(数量)"格式
        Matcher matcher = Pattern.compile("([^()]+?)\\((\\d+)\\)").matcher(fifoDetail);
        int cursor = 0;

        // 遍历所有匹配项，确保格式严格连续
        while (matcher.find()) {
            // 检查匹配是否从当前位置开始，确保没有遗漏字符
            if (matcher.start() != cursor) {
                return new ArrayList<>();
            }

            // 创建位置扣减对象并添加到列表
            deductions.add(new InventoryDeductionReceipt.LocationDeduction(
                    matcher.group(1),
                    Integer.parseInt(matcher.group(2)),
                    null));
            cursor = matcher.end();
        }

        // 验证是否完整解析了整个字符串
        if (cursor != fifoDetail.length()) {
            return new ArrayList<>();
        }

        return deductions;
    }


    /**
     * 回滚已返还的原材料库存
     * <p>
     * 该方法用于撤销之前执行的原材料返还操作，通过逆序遍历返还请求列表并执行对应的出库操作，
     * 将已返还到库存的原材料重新扣除，确保在生产计划状态变更时库存数据的一致性。
     * </p>
     *
     * @param restoredRequests 已执行的返还请求列表，包含需要回滚的原材料信息
     * @param batchNo 批次号，用于生成回滚操作的原因说明
     */
    private void rollbackRestoredRawMaterials(List<StockInOutRequest> restoredRequests, String batchNo) {
        // 逆序遍历返还请求列表，确保回滚顺序与执行顺序相反
        for (int i = restoredRequests.size() - 1; i >= 0; i--) {
            StockInOutRequest restoredRequest = restoredRequests.get(i);

            // 构建对应的出库请求以撤销返还操作
            StockInOutRequest rollbackRequest = new StockInOutRequest();
            rollbackRequest.setItemType(restoredRequest.getItemType());
            rollbackRequest.setItemId(restoredRequest.getItemId());
            rollbackRequest.setQuantity(restoredRequest.getQuantity());
            rollbackRequest.setReason("生产计划-" + batchNo + "-取消返还回滚");

            // 执行出库操作完成回滚
            inventoryService.stockOut(rollbackRequest, "system");
        }
    }


    /**
     * 回滚已返还的原材料库存扣减凭证
     * <p>
     * 该方法用于撤销之前执行的原材料返还凭证记录，通过逆序遍历凭证列表并执行对应的出库操作，
     * 将已返还的原材料重新从库存中扣除，确保在生产计划状态变更时库存数据的一致性。
     * </p>
     *
     * @param restoredReceipts 已执行的返还凭证列表，包含需要回滚的原材料信息
     * @param batchNo 批次号，用于生成回滚操作的原因说明
     */
    private void rollbackRestoredRawMaterialReceipts(List<InventoryDeductionReceipt> restoredReceipts, String batchNo) {
        // 逆序遍历返还凭证列表，确保回滚顺序与执行顺序相反
        for (int i = restoredReceipts.size() - 1; i >= 0; i--) {
            InventoryDeductionReceipt restoredReceipt = restoredReceipts.get(i);

            // 构建对应的出库请求以撤销返还操作
            StockInOutRequest rollbackRequest = new StockInOutRequest();
            rollbackRequest.setItemType(restoredReceipt.getItemType());
            rollbackRequest.setItemId(restoredReceipt.getItemId());
            rollbackRequest.setQuantity(restoredReceipt.getQuantity());
            rollbackRequest.setReason("生产计划-" + batchNo + "-取消返还回滚");

            // 执行出库操作完成回滚
            inventoryService.stockOut(rollbackRequest, "system");
        }
    }


    /**
     * 执行原材料库存返还操作
     * <p>
     * 该方法遍历所有返还请求并执行入库操作，同时记录已成功执行的请求以便在失败时进行回滚。
     * 如果返还过程中发生异常，会自动触发补偿机制回滚已完成的返还操作。
     * 若回滚也失败，则抛出包含原始异常和回滚异常的致命异常，需要人工介入处理。
     * </p>
     *
     * @param restoreRequests 原材料返还请求列表，包含需要返还的原材料信息
     * @param batchNo 批次号，用于回滚操作时生成原因说明
     */
    private void executeRawMaterialRestore(List<StockInOutRequest> restoreRequests, String batchNo) {
        List<StockInOutRequest> restoredRequests = new ArrayList<>();

        try {
            // 遍历所有返还请求，执行入库操作并记录成功的请求
            for (StockInOutRequest restoreRequest : restoreRequests) {
                inventoryService.stockIn(restoreRequest, "system");
                restoredRequests.add(restoreRequest);
            }
        } catch (RuntimeException ex) {
            // 返还失败时，尝试回滚已成功执行的返还操作
            try {
                rollbackRestoredRawMaterials(restoredRequests, batchNo);
            } catch (RuntimeException rollbackEx) {
                // 回滚也失败时，抛出包含两个异常的致命异常
                IllegalStateException fatal = new IllegalStateException("原材料返还失败且补偿回滚失败，请立即人工核对库存", rollbackEx);
                fatal.addSuppressed(ex);
                throw fatal;
            }
            throw ex;
        }
    }


    /**
     * 完成生产计划的原材料返还标记更新
     * <p>
     * 该方法在原材料库存返还操作成功执行后调用，用于更新生产计划的返还状态标记。
     * 如果标记更新失败，说明数据一致性存在问题，需要人工介入核对。
     * </p>
     *
     * @param planId 生产计划ID，用于标识需要更新返还标记的计划
     */
    private void completePlanMaterialRestore(String planId) {
        // 通过原子操作更新计划返还标记，确保数据一致性
        if (!mongoAtomicOpsService.completePlanMaterialsRestore(planId)) {
            throw new IllegalStateException("原材料返还成功，但计划返还标记未能完成更新，请立即人工核对生产计划");
        }
    }


    /**
     * 释放生产计划的原材料返还标记
     * <p>
     * 该方法在原材料返还操作失败时调用，用于回滚之前设置的返还状态标记，使生产计划恢复到可重新执行返还的状态。
     * 如果标记释放失败，说明数据一致性存在严重问题，需要人工介入核对。
     * </p>
     *
     * @param planId 生产计划ID，用于标识需要释放返还标记的计划
     * @param ex 原始异常，作为嵌套异常的原因传递
     */
    private void releasePlanMaterialRestore(String planId, RuntimeException ex) {
        // 通过原子操作释放计划返还标记，确保可以重新执行返还操作
        if (!mongoAtomicOpsService.releasePlanMaterialsRestore(planId)) {
            throw new IllegalStateException("原材料返还失败，且计划返还标记未能回滚，请立即人工核对生产计划", ex);
        }
    }


    /**
     * 格式化双精度浮点数为字符串
     * <p>
     * 该方法将数值转换为字符串表示，对于整数值去除小数部分，对于小数值保留完整精度。
     * 例如：5.0 转换为 "5"，5.5 转换为 "5.5"
     * </p>
     *
     * @param value 需要格式化的双精度浮点数值
     * @return 格式化后的字符串，整数不带小数点，小数保留完整精度
     */
    private String formatDecimal(double value) {
        // 判断是否为整数值，如果是则转换为整数格式
        if (value == Math.floor(value)) {
            return String.valueOf((int) value);
        }

        // 小数值直接转换为字符串
        return String.valueOf(value);
    }


    /**
     * 分页查询生产计划列表
     * <p>
     * 该方法支持根据关键词和状态筛选生产计划，并按创建时间倒序排序后返回分页结果。
     * 对于已完成状态的计划，还会筛选出仍有剩余库存未入库的计划。
     * </p>
     *
     * @param keyword 搜索关键词，用于匹配批次号或产品名称
     * @param status 计划状态筛选条件，如"PENDING"、"IN_PROGRESS"、"COMPLETED"等
     * @param pageable 分页参数，包含页码、每页大小和排序信息
     * @return 分页的生产计划视图对象列表
     */
    @Override
    public Page<PlanVO> getPlanList(String keyword, String status, Pageable pageable) {
        List<ProductionPlan> allPlans = productionPlanRepository.findAll();

        // 根据关键词、状态和库存状态过滤生产计划
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

                    // 对于已完成状态的计划，检查是否还有剩余库存未入库
                    boolean hasRemainingStock = true;
                    if ("COMPLETED".equals(status)) {
                        int stockedIn = plan.getStockedInQuantity() != null ? plan.getStockedInQuantity() : 0;
                        int completed = plan.getCompletedQuantity() != null ? plan.getCompletedQuantity() : 0;
                        hasRemainingStock = stockedIn < completed;
                    }

                    return matchKeyword && matchStatus && hasRemainingStock;
                })
                .collect(Collectors.toList());

        // 按创建时间倒序排序
        filtered.sort((a, b) -> {
            if (a.getCreateTime() == null && b.getCreateTime() == null) return 0;
            if (a.getCreateTime() == null) return 1;
            if (b.getCreateTime() == null) return -1;
            return b.getCreateTime().compareTo(a.getCreateTime());
        });

        // 计算分页范围并提取当前页数据
        int start = (int) pageable.getOffset();
        int end = Math.min(start + pageable.getPageSize(), filtered.size());
        List<ProductionPlan> pageContent = start < filtered.size() ? filtered.subList(start, end) : new ArrayList<>();

        // 转换为视图对象并返回分页结果
        List<PlanVO> voList = pageContent.stream()
                .map(this::convertToVO)
                .collect(Collectors.toList());

        return new PageImpl<>(voList, pageable, filtered.size());
    }


    /**
     * 根据ID查询生产计划详情
     * <p>
     * 该方法从数据库中获取指定ID的生产计划，并将其转换为视图对象返回。
     * 如果计划不存在，则抛出业务异常。
     * </p>
     *
     * @param id 生产计划的唯一标识符
     * @return 生产计划视图对象，包含计划的详细信息
     */
    @Override
    public PlanVO getPlanById(String id) {
        // 从数据库查询生产计划，不存在时抛出异常
        ProductionPlan plan = productionPlanRepository.findById(id)
                .orElseThrow(() -> new BusinessException("生产计划不存在"));

        // 转换为视图对象并返回
        return convertToVO(plan);
    }


    /**
     * 更新生产计划信息
     * <p>
     * 该方法支持更新生产计划的多个字段，包括批次号、产品定义、数量、日期等。
     * 对于已审批通过的计划，限制修改批次号和产品定义。
     * 当修改计划数量时，会自动调整相关库存并同步任务计划数量。
     * 如果更新过程中发生异常，会自动回滚所有已执行的操作以保证数据一致性。
     * </p>
     *
     * @param id 生产计划ID，用于标识需要更新的计划
     * @param request 更新请求对象，包含需要更新的字段信息
     * @return 更新后的生产计划视图对象
     */
    @Override
    public PlanVO updatePlan(String id, PlanUpdateRequest request) {
        ProductionPlan plan = productionPlanRepository.findById(id)
                .orElseThrow(() -> new BusinessException("生产计划不存在"));

        // 初始化库存调整结果和任务回滚快照，用于异常时的回滚操作
        MaterialAdjustmentResult adjustmentResult = MaterialAdjustmentResult.empty();
        List<TaskQuantitySnapshot> taskRollbackSnapshots = new ArrayList<>();

        // 校验计划状态，已取消的计划不允许编辑
        if ("CANCELLED".equals(plan.getStatus())) {
            throw new BusinessException("已取消的生产计划不允许编辑");
        }

        // 判断计划是否已审批通过，用于后续的权限控制
        boolean isApproved = "APPROVED".equals(plan.getStatus());

        // 处理批次号更新：已审批计划禁止修改，未审批计划允许修改
        if (isApproved && request.getBatchNo() != null) {
            throw new BusinessException("已审批通过的计划不允许修改批次号");
        }
        if (!isApproved && request.getBatchNo() != null) {
            plan.setBatchNo(request.getBatchNo());
        }

        // 处理产品定义更新：已审批计划禁止修改，未审批计划允许修改并同步产品信息
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

        // 处理产品名称更新（仅未审批计划）
        if (!isApproved && request.getProductName() != null) {
            plan.setProductName(request.getProductName());
        }

        try {
            Integer oldQuantity = plan.getQuantity();

            // 处理计划数量更新，需要校验并完成库存调整和任务同步
            if (request.getQuantity() != null) {
                int newQuantity = request.getQuantity();

                // 捕获当前任务数量快照，用于后续可能的回滚
                List<TaskQuantitySnapshot> quantityTaskSnapshots = captureTaskQuantitySnapshots(plan.getId());
                int completedQuantity = resolveCompletedQuantity(plan, quantityTaskSnapshots);

                // 校验新数量不能小于已完成数量
                if (newQuantity < completedQuantity) {
                    throw new BusinessException("计划数量不能小于当前已完成生产数量：" + completedQuantity);
                }

                int quantityDiff = newQuantity - oldQuantity;
                if (quantityDiff != 0) {
                    // 执行库存调整以响应数量变化
                    adjustmentResult = adjustInventoryForQuantityChange(plan, oldQuantity, newQuantity);
                    plan.setMaterialDeductionReceipts(adjustmentResult.getUpdatedReceipts());

                    // 保存任务快照并同步任务计划数量
                    taskRollbackSnapshots = quantityTaskSnapshots;
                    if (!taskRollbackSnapshots.isEmpty()) {
                        plan.setCompletedQuantity(syncTaskPlanQuantities(taskRollbackSnapshots, newQuantity));
                    }
                }
                plan.setQuantity(newQuantity);
            }

            // 处理其他可选字段的更新
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

            // 保存更新后的计划并转换为视图对象返回
            ProductionPlan saved = productionPlanRepository.save(plan);
            return convertToVO(saved);
        } catch (RuntimeException ex) {
            // 发生异常时，回滚任务数量调整和库存调整操作
            rollbackTaskPlanQuantities(taskRollbackSnapshots);
            rollbackPlanInventoryAdjustments(adjustmentResult);
            throw ex;
        }
    }


    /**
     * 执行原材料库存扣减凭证的返还操作
     * <p>
     * 该方法遍历所有返还凭证并执行库存恢复操作，同时记录已成功执行的凭证以便在失败时进行回滚。
     * 如果返还过程中发生异常，会自动触发补偿机制回滚已完成的返还操作。
     * 若回滚也失败，则抛出包含原始异常和回滚异常的致命异常，需要人工介入处理。
     * </p>
     *
     * @param restoreReceipts 原材料返还凭证列表，包含需要恢复的库存扣减信息
     * @param batchNo 批次号，用于生成返还操作的原因说明
     */
    private void executeRawMaterialReceiptRestore(List<InventoryDeductionReceipt> restoreReceipts, String batchNo) {
        List<InventoryDeductionReceipt> restoredReceipts = new ArrayList<>();

        try {
            // 遍历所有返还凭证，执行库存恢复操作并记录成功的凭证
            for (InventoryDeductionReceipt restoreReceipt : restoreReceipts) {
                inventoryService.restoreInventoryDeduction(restoreReceipt, "生产计划-" + batchNo + "-取消返还");
                restoredReceipts.add(restoreReceipt);
            }
        } catch (RuntimeException ex) {
            // 返还失败时，尝试回滚已成功执行的返还操作
            try {
                rollbackRestoredRawMaterialReceipts(restoredReceipts, batchNo);
            } catch (RuntimeException rollbackEx) {
                // 回滚也失败时，抛出包含两个异常的致命异常
                IllegalStateException fatal = new IllegalStateException("原材料返还失败且补偿回滚失败，请立即人工核对库存", rollbackEx);
                fatal.addSuppressed(ex);
                throw fatal;
            }
            throw ex;
        }
    }


    /**
     * 根据计划数量变化调整原材料库存
     * <p>
     * 该方法在生产计划数量变更时调用，负责调整相关原材料的库存。支持两种调整模式：
     * 1. 基于凭证的调整模式：使用FIFO扣减凭证进行精确的库存管理
     * 2. 传统库存调整模式：直接对原材料库存进行增减操作
     *
     * 当数量增加时，执行扣减操作；当数量减少时，执行返还操作。
     * 如果调整过程中发生异常，需要调用方通过MaterialAdjustmentResult中的信息进行回滚。
     * </p>
     *
     * @param plan 生产计划对象，包含批次号、产品定义ID等信息
     * @param oldQty 原计划数量
     * @param newQty 新计划数量
     * @return 库存调整结果，包含更新后的凭证列表、新增扣减凭证和返还凭证
     */
    private MaterialAdjustmentResult adjustInventoryForQuantityChange(ProductionPlan plan, int oldQty, int newQty) {
        // 获取产品定义信息，如果不存在或未配置材料则返回空结果
        ProductDefinition productDef = productDefinitionRepository.findById(plan.getProductDefinitionId()).orElse(null);
        if (productDef == null || productDef.getMaterials() == null || productDef.getMaterials().isEmpty()) {
            return MaterialAdjustmentResult.empty();
        }

        // 计算数量差值
        int diff = newQty - oldQty;

        // 初始化各种凭证列表
        List<InventoryDeductionReceipt> updatedReceipts = copyInventoryDeductionReceipts(plan.getMaterialDeductionReceipts());
        List<InventoryDeductionReceipt> deductedReceipts = new ArrayList<>();
        List<InventoryDeductionReceipt> restoredReceipts = new ArrayList<>();
        List<StockInOutRequest> rollbackRequests = new ArrayList<>();

        // 遍历所有原材料，根据数量变化执行相应的库存调整
        for (ProductDefinition.ProductMaterial material : productDef.getMaterials()) {
            double unitNeed = material.getQuantity();
            int adjustIntQty = (int) Math.round(unitNeed * Math.abs(diff));
            if (adjustIntQty <= 0) {
                continue;
            }

            // 判断是否使用基于凭证的数量调整模式
            if (shouldUseReceiptBasedQuantityAdjustment()) {
                if (diff > 0) {
                    // 数量增加：执行FIFO扣减并记录凭证
                    InventoryDeductionReceipt receipt = inventoryService.fifoDeductRawMaterialWithReceipt(
                            material.getMaterialId(),
                            adjustIntQty,
                            "生产计划-" + plan.getBatchNo() + "-数量调整(从" + oldQty + "增至" + newQty + ")-扣减");
                    if (receipt != null) {
                        deductedReceipts.add(receipt);
                        updatedReceipts.add(receipt);
                    }
                } else {
                    // 数量减少：提取并恢复之前的扣减凭证
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

            // 传统库存调整模式
            if (diff > 0) {
                // 数量增加：检查库存充足性并执行扣减
                RawMaterial rawMaterial = rawMaterialRepository.findById(material.getMaterialId())
                        .orElseThrow(() -> new BusinessException("原材料【" + material.getMaterialName() + "】不存在"));

                double currentStock = rawMaterial.getQuantity();
                double neededForDiff = unitNeed * diff;

                // 校验库存是否充足
                if (currentStock < neededForDiff) {
                    double shortage = neededForDiff - currentStock;
                    throw new BusinessException(
                            "修改计划数量导致原材料库存不足：原材料【" + material.getMaterialName()
                                    + "】需额外扣减 " + formatDecimal(neededForDiff)
                                    + " " + (material.getUnit() != null ? material.getUnit() : "")
                                    + "，当前库存仅 " + formatDecimal(currentStock)
                                    + "，缺口 " + formatDecimal(shortage));
                }

                // 执行出库扣减操作
                StockInOutRequest stockOutRequest = new StockInOutRequest();
                stockOutRequest.setItemType("RAW_MATERIAL");
                stockOutRequest.setItemId(material.getMaterialId());
                stockOutRequest.setQuantity(adjustIntQty);
                stockOutRequest.setReason("生产计划-" + plan.getBatchNo()
                        + "-数量调整(从" + oldQty + "增至" + newQty + ")-扣减");
                inventoryService.stockOut(stockOutRequest, "system");

                // 准备回滚请求
                StockInOutRequest rollbackRequest = new StockInOutRequest();
                rollbackRequest.setItemType("RAW_MATERIAL");
                rollbackRequest.setItemId(material.getMaterialId());
                rollbackRequest.setQuantity(adjustIntQty);
                rollbackRequest.setReason("生产计划-" + plan.getBatchNo()
                        + "-数量调整(从" + oldQty + "增至" + newQty + ")-扣减回滚");
                rollbackRequests.add(rollbackRequest);
            } else {
                // 数量减少：执行入库返还操作
                StockInOutRequest stockInRequest = new StockInOutRequest();
                stockInRequest.setItemType("RAW_MATERIAL");
                stockInRequest.setItemId(material.getMaterialId());
                stockInRequest.setQuantity(adjustIntQty);
                stockInRequest.setReason("生产计划-" + plan.getBatchNo()
                        + "-数量调整(从" + oldQty + "减至" + newQty + ")-返还");
                inventoryService.stockIn(stockInRequest, "system");

                // 准备回滚请求
                StockInOutRequest rollbackRequest = new StockInOutRequest();
                rollbackRequest.setItemType("RAW_MATERIAL");
                rollbackRequest.setItemId(material.getMaterialId());
                rollbackRequest.setQuantity(adjustIntQty);
                rollbackRequest.setReason("生产计划-" + plan.getBatchNo()
                        + "-数量调整(从" + oldQty + "减至" + newQty + ")-返还回滚");
                rollbackRequests.add(rollbackRequest);
            }
        }

        // 返回库存调整结果
        return new MaterialAdjustmentResult(updatedReceipts, deductedReceipts, restoredReceipts);
    }


    /**
     * 回滚生产计划的库存调整操作
     * <p>
     * 该方法用于撤销之前执行的库存调整，通过逆序遍历回滚请求列表并执行相反的操作。
     * 对于"扣减回滚"类型的请求执行入库操作，其他类型执行出库操作，确保库存数据恢复到调整前的状态。
     * </p>
     *
     * @param rollbackRequests 库存调整回滚请求列表，包含需要回滚的原材料信息
     */
    private void rollbackPlanInventoryAdjustments(List<StockInOutRequest> rollbackRequests) {
        // 逆序遍历回滚请求列表，确保回滚顺序与执行顺序相反
        for (int i = rollbackRequests.size() - 1; i >= 0; i--) {
            StockInOutRequest rollbackRequest = rollbackRequests.get(i);

            // 根据回滚原因判断执行相反的操作：扣减回滚执行入库，返还回滚执行出库
            if (rollbackRequest.getReason() != null && rollbackRequest.getReason().contains("扣减回滚")) {
                inventoryService.stockIn(rollbackRequest, "system");
            } else {
                inventoryService.stockOut(rollbackRequest, "system");
            }
        }
    }


    /**
     * 回滚生产计划的库存调整操作（基于凭证模式）
     * <p>
     * 该方法用于撤销之前基于凭证的库存调整操作，通过逆序遍历调整结果中的凭证列表并执行相反的操作。
     * 对于已返还的凭证重新执行扣减，对于已扣减的凭证执行恢复操作，确保库存数据恢复到调整前的状态。
     * </p>
     *
     * @param adjustmentResult 库存调整结果对象，包含新增的扣减凭证和返还凭证列表
     */
    private void rollbackPlanInventoryAdjustments(MaterialAdjustmentResult adjustmentResult) {
        if (adjustmentResult == null) {
            return;
        }

        // 逆序遍历已返还的凭证，重新执行FIFO扣加以撤销返还操作
        for (int i = adjustmentResult.getRestoredReceipts().size() - 1; i >= 0; i--) {
            InventoryDeductionReceipt restoredReceipt = adjustmentResult.getRestoredReceipts().get(i);
            inventoryService.fifoDeductRawMaterialWithReceipt(
                    restoredReceipt.getItemId(),
                    restoredReceipt.getQuantity(),
                    "生产计划-数量调整返还回滚");
        }

        // 逆序遍历已扣减的凭证，执行恢复操作以撤销扣减
        for (int i = adjustmentResult.getDeductedReceipts().size() - 1; i >= 0; i--) {
            InventoryDeductionReceipt deductedReceipt = adjustmentResult.getDeductedReceipts().get(i);
            inventoryService.restoreInventoryDeduction(deductedReceipt, "生产计划-数量调整扣减回滚");
        }
    }


    /**
     * 判断是否使用基于凭证的数量调整模式
     * <p>
     * 该方法决定在进行生产计划数量调整时，是否采用基于FIFO扣减凭证的精确库存管理模式。
     * 当前默认启用该模式，以确保库存调整的精确性和可追溯性。
     * </p>
     *
     * @return true表示使用基于凭证的调整模式，false表示使用传统库存调整模式
     */
    private boolean shouldUseReceiptBasedQuantityAdjustment() {
        return true;
    }


    /**
     * 复制库存扣减凭证列表
     * <p>
     * 该方法对传入的凭证列表进行深拷贝，创建新的凭证对象列表，避免后续操作修改原始数据。
     * 会跳过null元素，只复制有效的凭证对象。
     * </p>
     *
     * @param receipts 原始库存扣减凭证列表，可以为null或空列表
     * @return 复制后的凭证列表，如果输入为null则返回空列表
     */
    private List<InventoryDeductionReceipt> copyInventoryDeductionReceipts(List<InventoryDeductionReceipt> receipts) {
        List<InventoryDeductionReceipt> copies = new ArrayList<>();

        // 处理输入为null的情况
        if (receipts == null) {
            return copies;
        }

        // 遍历并复制每个有效的凭证对象
        for (InventoryDeductionReceipt receipt : receipts) {
            if (receipt == null) {
                continue;
            }
            copies.add(copyInventoryDeductionReceipt(receipt));
        }

        return copies;
    }


    /**
     * 复制单个库存扣减凭证对象
     * <p>
     * 该方法创建一个新的库存扣减凭证对象，复制原凭证的所有属性，包括物品信息、数量和位置扣减明细。
     * 对于位置扣减明细列表会进行深拷贝，确保新对象与原对象相互独立。
     * </p>
     *
     * @param receipt 原始库存扣减凭证对象
     * @return 复制后的新库存扣减凭证对象
     */
    private InventoryDeductionReceipt copyInventoryDeductionReceipt(InventoryDeductionReceipt receipt) {
        // 创建新的凭证对象，复制所有属性并深拷贝位置扣减列表
        return new InventoryDeductionReceipt(
                receipt.getItemType(),
                receipt.getItemId(),
                receipt.getItemName(),
                receipt.getQuantity(),
                receipt.isTotalOnly(),
                copyLocationDeductions(receipt.getLocationDeductions()));
    }


    /**
     * 复制位置扣减明细列表
     * <p>
     * 该方法对传入的位置扣减明细列表进行深拷贝，创建新的位置扣减对象列表，避免后续操作修改原始数据。
     * 会跳过null元素，只复制有效的扣减明细对象。
     * </p>
     *
     * @param deductions 原始位置扣减明细列表，可以为null或空列表
     * @return 复制后的位置扣减明细列表，如果输入为null则返回空列表
     */
    private List<InventoryDeductionReceipt.LocationDeduction> copyLocationDeductions(
            List<InventoryDeductionReceipt.LocationDeduction> deductions) {
        List<InventoryDeductionReceipt.LocationDeduction> copies = new ArrayList<>();

        // 处理输入为null的情况
        if (deductions == null) {
            return copies;
        }

        // 遍历并复制每个有效的位置扣减明细对象
        for (InventoryDeductionReceipt.LocationDeduction deduction : deductions) {
            if (deduction == null) {
                continue;
            }

            // 创建新的位置扣减对象，复制位置、数量和时间信息
            copies.add(new InventoryDeductionReceipt.LocationDeduction(
                    deduction.getLocation(),
                    deduction.getQuantity(),
                    deduction.getCreatedAt()));
        }

        return copies;
    }


    /**
     * 从已更新的凭证列表中提取需要返还的凭证
     * <p>
     * 该方法逆序遍历凭证列表，提取指定物品的扣减凭证用于返还操作。支持部分提取，
     * 即从单个凭证中提取部分数量。如果可用凭证数量不足以满足返还需求，则抛出异常。
     * </p>
     *
     * @param updatedReceipts 已更新的凭证列表，方法执行过程中会修改此列表
     * @param itemId 物品ID，用于筛选需要返还的凭证
     * @param itemName 物品名称，用于创建新的凭证对象
     * @param quantityToRestore 需要返还的总数量
     * @return 提取的返还凭证列表，包含所有用于返还操作的凭证
     */
    private List<InventoryDeductionReceipt> extractRestoreReceipts(List<InventoryDeductionReceipt> updatedReceipts,
                                                                   String itemId,
                                                                   String itemName,
                                                                   int quantityToRestore) {
        int remaining = quantityToRestore;
        List<InventoryDeductionReceipt> restoreReceipts = new ArrayList<>();

        // 逆序遍历凭证列表，从后往前提取符合条件的凭证
        for (int i = updatedReceipts.size() - 1; i >= 0 && remaining > 0; i--) {
            InventoryDeductionReceipt receipt = updatedReceipts.get(i);

            // 跳过无效凭证或不匹配的凭证
            if (receipt == null || !Objects.equals(itemId, receipt.getItemId())
                    || receipt.getQuantity() == null || receipt.getQuantity() <= 0) {
                continue;
            }

            // 从当前凭证中提取部分或全部数量用于返还
            InventoryDeductionReceipt extractedReceipt = extractPartialReceipt(receipt, remaining, itemName);
            if (extractedReceipt == null || extractedReceipt.getQuantity() == null || extractedReceipt.getQuantity() <= 0) {
                continue;
            }

            // 更新剩余需要返还的数量并添加提取的凭证
            remaining -= extractedReceipt.getQuantity();
            restoreReceipts.add(extractedReceipt);

            // 如果原凭证数量已耗尽，则从列表中移除
            if (receipt.getQuantity() == null || receipt.getQuantity() <= 0) {
                updatedReceipts.remove(i);
            }
        }

        // 检查是否成功提取到足够的凭证数量
        if (remaining > 0) {
            throw new BusinessException("计划原材料扣减记录不足，无法返还数量调整库存");
        }

        return restoreReceipts;
    }


    /**
     * 从库存扣减凭证中提取部分数量用于返还
     * <p>
     * 该方法从原凭证中提取指定数量的物品，创建新的返还凭证对象，并更新原凭证的剩余数量。
     * 支持两种凭证类型：总量凭证（不包含位置信息）和分位置凭证（包含详细的位置扣减明细）。
     * 对于分位置凭证，会逆序遍历位置明细列表进行提取，并自动清理已耗尽的位置记录。
     * </p>
     *
     * @param receipt 原始库存扣减凭证，方法执行过程中会修改此对象的数量和位置明细
     * @param maxQuantity 最大可提取数量，实际提取数量不会超过此值
     * @param fallbackItemName 备用物品名称，当原凭证的物品名称为空时使用
     * @return 提取出的新凭证对象，如果无法提取则返回null
     */
    private InventoryDeductionReceipt extractPartialReceipt(InventoryDeductionReceipt receipt,
                                                            int maxQuantity,
                                                            String fallbackItemName) {
        int availableQuantity = receipt.getQuantity() != null ? receipt.getQuantity() : 0;

        // 校验可用数量和请求数量的有效性
        if (availableQuantity <= 0 || maxQuantity <= 0) {
            return null;
        }

        // 计算实际提取数量，取可用数量和请求数量的较小值
        int extractedQuantity = Math.min(availableQuantity, maxQuantity);
        String itemName = StringUtils.hasText(receipt.getItemName()) ? receipt.getItemName() : fallbackItemName;

        // 处理总量凭证或无位置明细的情况，直接提取数量
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

        // 处理分位置凭证，需要从各个位置明细中提取数量
        int remaining = extractedQuantity;
        List<InventoryDeductionReceipt.LocationDeduction> extractedDeductions = new ArrayList<>();
        List<InventoryDeductionReceipt.LocationDeduction> locationDeductions = receipt.getLocationDeductions();

        // 逆序遍历位置明细列表，从后往前提取数量
        for (int i = locationDeductions.size() - 1; i >= 0 && remaining > 0; i--) {
            InventoryDeductionReceipt.LocationDeduction locationDeduction = locationDeductions.get(i);
            if (locationDeduction == null || locationDeduction.getQuantity() == null || locationDeduction.getQuantity() <= 0) {
                continue;
            }

            // 从当前位置明细中提取数量
            int deductionQuantity = Math.min(locationDeduction.getQuantity(), remaining);
            locationDeduction.setQuantity(locationDeduction.getQuantity() - deductionQuantity);

            // 将提取的位置明细添加到列表头部，保持原有顺序
            extractedDeductions.add(0, new InventoryDeductionReceipt.LocationDeduction(
                    locationDeduction.getLocation(),
                    deductionQuantity,
                    locationDeduction.getCreatedAt()));
            remaining -= deductionQuantity;

            // 如果位置明细数量已耗尽，则从列表中移除
            if (locationDeduction.getQuantity() <= 0) {
                locationDeductions.remove(i);
            }
        }

        // 更新原凭证的剩余数量
        receipt.setQuantity(availableQuantity - extractedQuantity);

        // 返回包含提取位置明细的新凭证对象
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

        /**
         * 创建空的库存调整结果对象
         * <p>
         * 该方法返回一个不包含任何凭证信息的空结果，用于表示没有进行库存调整的情况。
         * </p>
         *
         * @return 空的库存调整结果对象，所有凭证列表均为空
         */
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

    /**
     * 删除生产计划
     * <p>
     * 该方法用于删除指定ID的生产计划。删除前会进行多项校验：
     * 1. 计划必须处于"已取消"状态
     * 2. 没有正在进行的原材料返还操作
     * 3. 如果计划已扣除材料，必须先完成原材料返还
     *
     * 对于需要返还原材料的计划，会自动执行库存恢复操作，并在完成后删除计划。
     * 如果返还过程中发生异常，会进行相应的回滚处理。
     * </p>
     *
     * @param id 生产计划的唯一标识符
     */
    @Override
    public void deletePlan(String id) {
        ProductionPlan plan = productionPlanRepository.findById(id)
                .orElseThrow(() -> new BusinessException("生产计划不存在"));

        // 校验计划状态，只有已取消的计划才能删除
        if (!"CANCELLED".equals(plan.getStatus())) {
            String statusText = getStatusText(plan.getStatus());
            throw new BusinessException("只有【已取消】状态的生产计划才能删除，当前计划状态为：" + statusText);
        }

        // 检查是否正在进行原材料返还操作
        if (Boolean.TRUE.equals(plan.getMaterialsRestoreInProgress())) {
            throw new BusinessException("计划原材料返还处理中，请稍后重试");
        }

        // 构建原材料返还凭证和请求
        List<InventoryDeductionReceipt> restoreReceipts = buildRawMaterialRestoreReceipts(plan);
        List<StockInOutRequest> restoreRequests = restoreReceipts.isEmpty()
                ? buildRawMaterialRestoreRequests(plan, plan.getBatchNo(), false)
                : new ArrayList<>();

        // 校验原材料返还信息的完整性
        if (Boolean.TRUE.equals(plan.getMaterialsDeducted()) && restoreReceipts.isEmpty() && restoreRequests.isEmpty()) {
            throw new BusinessException("原材料返还信息缺失，请先人工核对后再删除");
        }

        // 如果需要返还原材料，则执行返还操作
        if (!restoreReceipts.isEmpty() || !restoreRequests.isEmpty()) {
            // 标记计划进入返还处理状态
            if (!mongoAtomicOpsService.markPlanMaterialsRestoreInProgress(id)) {
                throw new BusinessException("计划状态已变更，请刷新后再操作");
            }

            try {
                // 根据凭证或请求类型执行相应的返还操作
                if (!restoreReceipts.isEmpty()) {
                    executeRawMaterialReceiptRestore(restoreReceipts, plan.getBatchNo());
                } else {
                    executeRawMaterialRestore(restoreRequests, plan.getBatchNo());
                }

                // 完成返还标记更新
                completePlanMaterialRestore(id);
            } catch (IllegalStateException fatalEx) {
                // 致命异常直接抛出
                throw fatalEx;
            } catch (RuntimeException ex) {
                // 业务异常时释放返还标记并重新抛出
                releasePlanMaterialRestore(id, ex);
                throw ex;
            }
        }

        // 删除生产计划
        productionPlanRepository.deleteById(id);
    }


    /**
     * 将计划状态代码转换为中文描述
     * <p>
     * 该方法将英文的状态代码映射为对应的中文显示文本，用于前端展示和错误提示信息。
     * </p>
     *
     * @param status 计划状态代码，如"PENDING"、"APPROVED"、"IN_PROGRESS"等
     * @return 对应的中文状态描述，如果状态代码未知则返回原值
     */
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


    /**
     * 断言计划状态是否发生变更
     * <p>
     * 该方法用于在执行关键操作前检查计划状态是否与预期一致，如果不一致则抛出业务异常。
     * 主要用于并发场景下的乐观锁控制，确保操作的原子性和数据一致性。
     * </p>
     *
     * @param changed 状态变更标志，true表示状态已变更，false表示状态未变更
     */
    private void assertPlanStatusChanged(boolean changed) {
        if (!changed) {
            throw new BusinessException("计划状态已变更，请刷新后再操作");
        }
    }


    /**
     * 审批生产计划
     * <p>
     * 该方法用于对待审批状态的生产计划进行审批操作，支持"审批通过"和"已取消"两种审批结果。
     * 如果审批结果为"已取消"且计划已扣除原材料，则会自动执行原材料返还操作。
     * 审批过程中会使用原子操作更新计划状态，确保并发场景下的数据一致性。
     * </p>
     *
     * @param id 生产计划的唯一标识符
     * @param status 审批结果状态，只能为"APPROVED"（审批通过）或"CANCELLED"（已取消）
     * @return 审批后的生产计划视图对象
     */
    @Override
    public PlanVO approvePlan(String id, String status) {
        ProductionPlan plan = productionPlanRepository.findById(id)
                .orElseThrow(() -> new BusinessException("生产计划不存在"));

        // 校验计划状态，只有待审批的计划才能进行审批
        if (!"PENDING".equals(plan.getStatus())) {
            throw new BusinessException("只有待审批状态的计划才能审批");
        }

        // 校验审批结果状态的合法性
        if (!"APPROVED".equals(status) && !"CANCELLED".equals(status)) {
            throw new BusinessException("审批状态只能为【审批通过】或【已取消】");
        }

        // 如果审批结果为取消，则构建原材料返还凭证和请求
        List<InventoryDeductionReceipt> restoreReceipts = "CANCELLED".equals(status)
                ? buildRawMaterialRestoreReceipts(plan)
                : new ArrayList<>();
        List<StockInOutRequest> restoreRequests = "CANCELLED".equals(status) && restoreReceipts.isEmpty()
                ? buildRawMaterialRestoreRequests(plan, plan.getBatchNo(), true)
                : new ArrayList<>();

        // 如果需要返还原材料，则在状态转换时标记返还处理中
        Document transitionExtraFields = (!restoreReceipts.isEmpty() || !restoreRequests.isEmpty())
                ? new Document("materialsRestoreInProgress", true)
                : null;

        // 使用原子操作进行状态转换，确保并发安全
        assertPlanStatusChanged(mongoAtomicOpsService.transitionPlanStatus(id, "PENDING", status, transitionExtraFields));

        // 如果需要返还原材料，则执行相应的返还操作
        if (!restoreReceipts.isEmpty() || !restoreRequests.isEmpty()) {
            try {
                // 根据凭证或请求类型执行相应的返还操作
                if (!restoreReceipts.isEmpty()) {
                    executeRawMaterialReceiptRestore(restoreReceipts, plan.getBatchNo());
                } else {
                    executeRawMaterialRestore(restoreRequests, plan.getBatchNo());
                }

                // 完成返还标记更新
                completePlanMaterialRestore(id);
            } catch (IllegalStateException fatalEx) {
                // 致命异常直接抛出
                throw fatalEx;
            } catch (RuntimeException ex) {
                // 业务异常时释放返还标记并重新抛出
                releasePlanMaterialRestore(id, ex);
                throw ex;
            }
        }

        // 返回更新后的计划视图对象
        return convertToVO(productionPlanRepository.findById(id)
                .orElseThrow(() -> new BusinessException("生产计划不存在")));
    }


    /**
     * 开始生产计划并创建生产任务
     * <p>
     * 该方法用于将已审批的生产计划转换为进行中的生产任务。主要流程包括：
     * 1. 校验计划状态和任务是否已存在
     * 2. 创建新的生产任务对象，继承计划的基本信息
     * 3. 使用原子操作将计划状态从"APPROVED"转换为"IN_PROGRESS"
     * 4. 保存生产任务到数据库
     *
     * 如果任务创建失败，会自动回滚计划状态到"APPROVED"，确保数据一致性。
     * 通过唯一键约束防止重复创建任务。
     * </p>
     *
     * @param planId 生产计划的唯一标识符
     * @param userId 操作用户ID，用于记录任务的创建者
     * @return 更新后的生产计划视图对象
     */
    @Override
    public PlanVO startProduction(String planId, String userId) {
        ProductionPlan plan = productionPlanRepository.findById(planId)
                .orElseThrow(() -> new BusinessException("生产计划不存在"));

        // 校验计划状态，只有已审批的计划才能开始生产
        if (!"APPROVED".equals(plan.getStatus())) {
            throw new BusinessException("只有已审批状态的计划才能开始生产");
        }

        // 检查是否已存在生产任务，防止重复创建
        List<ProductionTask> existingTasks = productionTaskRepository.findByPlanId(planId);
        if (!existingTasks.isEmpty()) {
            throw new BusinessException("该计划已生成任务，请勿重复操作");
        }

        // 创建新的生产任务对象，继承计划的基本信息
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

        // 使用原子操作将计划状态从APPROVED转换为IN_PROGRESS
        assertPlanStatusChanged(mongoAtomicOpsService.transitionPlanStatus(planId, "APPROVED", "IN_PROGRESS", null));

        try {
            // 保存生产任务到数据库
            productionTaskRepository.save(task);
        } catch (DuplicateKeyException ignored) {
            // 唯一键约束触发时忽略异常，防止并发场景下重复创建任务
        } catch (RuntimeException ex) {
            // 任务创建失败时，尝试回滚计划状态
            boolean rolledBack;
            try {
                rolledBack = mongoAtomicOpsService.transitionPlanStatus(planId, "IN_PROGRESS", "APPROVED", null);
            } catch (RuntimeException rollbackEx) {
                // 回滚也失败时，抛出致命异常需要人工介入
                IllegalStateException fatal = new IllegalStateException("任务创建失败，且计划状态回滚失败，请立即人工核对生产计划", rollbackEx);
                fatal.addSuppressed(ex);
                throw fatal;
            }

            // 检查回滚是否生效
            if (!rolledBack) {
                IllegalStateException fatal = new IllegalStateException("任务创建失败，且计划状态回滚未生效，请立即人工核对生产计划");
                fatal.addSuppressed(ex);
                throw fatal;
            }
            throw ex;
        }

        // 返回更新后的计划视图对象
        return convertToVO(productionPlanRepository.findById(planId)
                .orElseThrow(() -> new BusinessException("生产计划不存在")));
    }


    /**
     * 完成生产计划
     * <p>
     * 该方法用于将进行中的生产计划标记为已完成。完成前会进行多项校验：
     * 1. 计划必须处于"IN_PROGRESS"状态
     * 2. 计划必须关联了生产任务
     * 3. 所有关联的生产任务必须都处于"COMPLETED"状态
     *
     * 校验通过后，使用原子操作将计划状态转换为"COMPLETED"，并更新已完成数量。
     * </p>
     *
     * @param planId 生产计划的唯一标识符
     * @return 更新后的生产计划视图对象
     */
    @Override
    public PlanVO completePlan(String planId) {
        ProductionPlan plan = productionPlanRepository.findById(planId)
                .orElseThrow(() -> new BusinessException("生产计划不存在"));

        // 校验计划状态，只有进行中的计划才能完成
        if (!"IN_PROGRESS".equals(plan.getStatus())) {
            throw new BusinessException("只有进行中状态的计划才能完成");
        }

        // 检查计划是否关联了生产任务
        List<ProductionTask> tasks = productionTaskRepository.findByPlanId(planId);
        if (tasks.isEmpty()) {
            throw new BusinessException("该计划没有关联的生产任务");
        }

        // 校验所有关联的任务是否都已完成
        boolean allCompleted = tasks.stream()
                .allMatch(task -> "COMPLETED".equals(task.getStatus()));

        if (!allCompleted) {
            long completedCount = tasks.stream()
                    .filter(t -> "COMPLETED".equals(t.getStatus()))
                    .count();
            throw new BusinessException("还有" + (tasks.size() - completedCount) + "个任务未完成，无法确认完成");
        }

        // 使用原子操作将计划状态从IN_PROGRESS转换为COMPLETED，并更新已完成数量
        assertPlanStatusChanged(mongoAtomicOpsService.transitionPlanStatus(
                planId,
                "IN_PROGRESS",
                "COMPLETED",
                new Document("completedQuantity", plan.getQuantity())
        ));

        // 返回更新后的计划视图对象
        return convertToVO(productionPlanRepository.findById(planId)
                .orElseThrow(() -> new BusinessException("生产计划不存在")));
    }


    /**
     * 根据计划ID查询关联的生产任务列表
     * <p>
     * 该方法用于获取指定生产计划下的所有生产任务，并将其转换为视图对象返回。
     * 如果计划不存在，则抛出业务异常。
     * </p>
     *
     * @param planId 生产计划的唯一标识符
     * @return 生产任务视图对象列表
     */
    @Override
    public List<TaskVO> getTasksByPlanId(String planId) {
        // 校验生产计划是否存在
        if (!productionPlanRepository.existsById(planId)) {
            throw new BusinessException("生产计划不存在");
        }

        // 查询计划关联的所有生产任务
        List<ProductionTask> tasks = productionTaskRepository.findByPlanId(planId);

        // 转换为视图对象并返回列表
        return tasks.stream()
                .map(this::convertTaskToVO)
                .collect(Collectors.toList());
    }


    private static class TaskQuantitySnapshot {
        private final ProductionTask task;
        private final Integer planQuantity;
        private final Integer completedQuantity;
        private final Integer progress;
        private final String status;
        private final Date endDate;

        private TaskQuantitySnapshot(ProductionTask task) {
            this.task = task;
            this.planQuantity = task.getPlanQuantity();
            this.completedQuantity = task.getCompletedQuantity();
            this.progress = task.getProgress();
            this.status = task.getStatus();
            this.endDate = task.getEndDate();
        }

        private ProductionTask getTask() {
            return task;
        }

        /**
         * 恢复任务到快照记录的状态
         * <p>
         * 该方法将任务的计划数量、完成数量、进度、状态和结束日期等属性恢复到快照保存时的值。
         * 用于在操作失败时回滚任务状态的变更。
         * </p>
         */
        private void restore() {
            task.setPlanQuantity(planQuantity);
            task.setCompletedQuantity(completedQuantity);
            task.setProgress(progress);
            task.setStatus(status);
            task.setEndDate(endDate);
        }

    }

    /**
     * 将生产任务实体转换为视图对象
     * <p>
     * 该方法将数据库中的生产任务实体对象转换为前端展示的视图对象，包含任务的基本信息、
     * 进度信息、人员信息和时间戳等完整属性。
     * </p>
     *
     * @param task 生产任务实体对象
     * @return 生产任务视图对象
     */
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


    /**
     * 将生产计划实体转换为视图对象
     * <p>
     * 该方法将数据库中的生产计划实体对象转换为前端展示的视图对象，包含计划的完整信息。
     * 同时会关联查询创建人姓名和关联任务的时间范围信息：
     * - 创建人姓名：通过用户ID查询用户的真实姓名
     * - 任务开始时间：所有关联任务中最早的开始时间
     * - 任务结束时间：所有关联任务中最晚的结束时间
     * </p>
     *
     * @param plan 生产计划实体对象
     * @return 生产计划视图对象，包含扩展的展示信息
     */
    private PlanVO convertToVO(ProductionPlan plan) {
        // 查询创建人姓名
        String createByName = null;
        if (StringUtils.hasText(plan.getCreateBy())) {
            createByName = userRepository.findById(plan.getCreateBy())
                    .map(User::getRealName)
                    .orElse(null);
        }

        // 计算关联任务的时间范围（最早开始时间和最晚结束时间）
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

        // 构建并返回计划视图对象
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
