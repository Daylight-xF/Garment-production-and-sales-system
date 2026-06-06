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

    /**
     * 创建生产任务
     * <p>
     * 该方法用于创建新的生产任务，并关联到指定的生产计划。主要功能包括：
     * 1. 校验关联的生产计划是否存在
     * 2. 继承计划的基本信息（批次号等）
     * 3. 设置任务的初始状态为"PENDING"（待处理），进度为0
     * 4. 如果指定了负责人，则查询用户信息并设置负责人姓名
     * 5. 保存任务到数据库，处理可能的命名冲突
     * </p>
     *
     * @param request 任务创建请求对象，包含计划ID、任务名称、日期范围、描述和负责人等信息
     * @param userId 当前操作用户ID，用于记录任务的创建者
     * @return 创建后的生产任务视图对象
     */
    @Override
    public TaskVO createTask(TaskCreateRequest request, String userId) {
        // 校验关联的生产计划是否存在
        ProductionPlan plan = productionPlanRepository.findById(request.getPlanId())
                .orElseThrow(() -> new BusinessException("关联的生产计划不存在"));

        // 创建新的生产任务对象，初始化基本信息
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

        // 如果指定了负责人，则查询用户信息并设置负责人姓名
        if (StringUtils.hasText(request.getAssignee())) {
            User assigneeUser = userRepository.findById(request.getAssignee())
                    .orElseThrow(() -> new BusinessException("分配人不存在"));
            task.setAssignee(request.getAssignee());
            task.setAssigneeName(assigneeUser.getRealName());
        }

        // 保存任务到数据库，处理可能的命名冲突
        ProductionTask saved = saveTaskWithConflictTranslation(task);

        // 转换为视图对象并返回
        return convertToVO(saved);
    }


    /**
     * 分页查询生产任务列表
     * <p>
     * 该方法支持根据计划ID、负责人和状态等多个条件筛选生产任务，并按创建时间倒序排序后返回分页结果。
     * 所有筛选条件都是可选的，可以组合使用以实现精确查询。
     * </p>
     *
     * @param planId 关联的生产计划ID，用于筛选特定计划下的任务
     * @param assignee 负责人ID，用于筛选特定负责任务
     * @param status 任务状态，如"PENDING"、"IN_PROGRESS"、"COMPLETED"等
     * @param pageable 分页参数，包含页码、每页大小和排序信息
     * @return 分页的生产任务视图对象列表
     */
    @Override
    public Page<TaskVO> getTaskList(String planId, String assignee, String status, Pageable pageable) {
        List<ProductionTask> allTasks = productionTaskRepository.findAll();

        // 根据计划ID、负责人和状态过滤生产任务
        List<ProductionTask> filtered = allTasks.stream()
                .filter(task -> {
                    boolean matchPlanId = true;
                    if (StringUtils.hasText(planId)) {
                        matchPlanId = planId.equals(task.getPlanId());
                    }

                    boolean matchAssignee = true;
                    if (StringUtils.hasText(assignee)) {
                        matchAssignee = matchesAssignee(task, assignee);
                    }

                    boolean matchStatus = true;
                    if (StringUtils.hasText(status)) {
                        matchStatus = status.equals(task.getStatus());
                    }

                    return matchPlanId && matchAssignee && matchStatus;
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
        List<ProductionTask> pageContent = start < filtered.size() ? filtered.subList(start, end) : new ArrayList<>();

        // 转换为视图对象并返回分页结果
        List<TaskVO> voList = pageContent.stream()
                .map(this::convertToVO)
                .collect(Collectors.toList());

        return new PageImpl<>(voList, pageable, filtered.size());
    }


    /**
     * 判断任务的负责人是否匹配搜索关键词
     * <p>
     * 该方法支持对负责人ID和负责人姓名进行模糊匹配，忽略大小写差异。
     * 只要ID或姓名中任意一个包含关键词即视为匹配。
     * </p>
     *
     * @param task 生产任务对象
     * @param keyword 搜索关键词，用于匹配负责人ID或姓名
     * @return true表示匹配成功，false表示不匹配
     */
    private boolean matchesAssignee(ProductionTask task, String keyword) {
        String normalizedKeyword = keyword.trim().toLowerCase();

        // 检查关键词是否出现在负责人ID或姓名中（忽略大小写）
        return containsIgnoreCase(task.getAssignee(), normalizedKeyword)
                || containsIgnoreCase(task.getAssigneeName(), normalizedKeyword);
    }


    /**
     * 判断字符串是否包含指定关键词（忽略大小写）
     * <p>
     * 该方法将源字符串和关键词都转换为小写后进行包含关系判断，确保源字符串不为空。
     * </p>
     *
     * @param value 源字符串，需要被检查的文本
     * @param normalizedKeyword 已标准化的搜索关键词（小写格式）
     * @return true表示源字符串包含关键词，false表示不包含或源字符串为空
     */
    private boolean containsIgnoreCase(String value, String normalizedKeyword) {
        return StringUtils.hasText(value)
                && value.toLowerCase().contains(normalizedKeyword);
    }


    /**
     * 根据ID查询生产任务详情
     * <p>
     * 该方法从数据库中获取指定ID的生产任务，并将其转换为视图对象返回。
     * 如果任务不存在，则抛出业务异常。
     * </p>
     *
     * @param id 生产任务的唯一标识符
     * @return 生产任务视图对象，包含任务的详细信息
     */
    @Override
    public TaskVO getTaskById(String id) {
        // 从数据库查询生产任务，不存在时抛出异常
        ProductionTask task = productionTaskRepository.findById(id)
                .orElseThrow(() -> new BusinessException("生产任务不存在"));

        // 转换为视图对象并返回
        return convertToVO(task);
    }


    /**
     * 更新生产任务信息
     * <p>
     * 该方法支持更新生产任务的多个字段，包括任务名称、日期范围、描述和状态等。
     * 只有请求中提供的非空字段才会被更新，其他字段保持不变。
     * 更新后会处理可能的任务命名冲突。
     * </p>
     *
     * @param id 生产任务的唯一标识符
     * @param request 更新请求对象，包含需要更新的字段信息（任务名称、开始日期、结束日期、描述、状态）
     * @return 更新后的生产任务视图对象
     */
    @Override
    public TaskVO updateTask(String id, TaskUpdateRequest request) {
        ProductionTask task = productionTaskRepository.findById(id)
                .orElseThrow(() -> new BusinessException("生产任务不存在"));

        // 选择性更新任务字段，只更新请求中提供的非空字段
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

        // 保存更新后的任务，处理可能的命名冲突
        ProductionTask saved = saveTaskWithConflictTranslation(task);

        // 转换为视图对象并返回
        return convertToVO(saved);
    }


    /**
     * 分配生产任务给指定负责人
     * <p>
     * 该方法用于将生产任务分配给指定的用户，主要功能包括：
     * 1. 校验任务和负责人是否存在
     * 2. 设置任务的负责人ID和姓名
     * 3. 如果任务未设置开始日期，则自动设置为当前日期
     * 4. 如果任务状态为"PENDING"（待处理），则自动更新为"IN_PROGRESS"（进行中）
     * </p>
     *
     * @param id 生产任务的唯一标识符
     * @param assignee 负责人的用户ID
     * @return 更新后的生产任务视图对象
     */
    @Override
    public TaskVO assignTask(String id, String assignee) {
        ProductionTask task = productionTaskRepository.findById(id)
                .orElseThrow(() -> new BusinessException("生产任务不存在"));

        // 校验负责人是否存在
        User assigneeUser = userRepository.findById(assignee)
                .orElseThrow(() -> new BusinessException("分配人不存在"));

        // 设置任务负责人信息
        task.setAssignee(assignee);
        task.setAssigneeName(assigneeUser.getRealName());

        // 如果任务未设置开始日期，则自动设置为当前日期
        if (task.getStartDate() == null) {
            task.setStartDate(new Date());
        }

        // 如果任务状态为PENDING，则更新为IN_PROGRESS
        if ("PENDING".equals(task.getStatus())) {
            task.setStatus("IN_PROGRESS");
        }

        // 保存更新后的任务，处理可能的命名冲突
        ProductionTask saved = saveTaskWithConflictTranslation(task);

        // 转换为视图对象并返回
        return convertToVO(saved);
    }


    /**
     * 更新生产任务的进度
     * <p>
     * 该方法用于更新任务的完成进度百分比，并根据进度自动调整任务状态和完成数量：
     * 1. 校验进度值必须在0-100之间
     * 2. 根据进度百分比计算完成数量：完成数量 = 计划数量 × 进度 / 100
     * 3. 当进度达到100%时，自动将任务状态设置为"COMPLETED"并设置结束日期
     * 4. 当进度从0或100变更为其他值时，将任务状态设置为"IN_PROGRESS"
     * 5. 同步更新关联生产计划的完成数量
     * 如果更新计划完成数量失败，会回滚任务进度的修改以保证数据一致性。
     * </p>
     *
     * @param id 生产任务的唯一标识符
     * @param progress 进度百分比，取值范围0-100
     * @return 更新后的生产任务视图对象
     */
    @Override
    public TaskVO updateProgress(String id, Integer progress) {
        ProductionTask task = productionTaskRepository.findById(id)
                .orElseThrow(() -> new BusinessException("生产任务不存在"));

        // 校验进度值的有效性
        if (progress < 0 || progress > 100) {
            throw new BusinessException("进度必须在0-100之间");
        }

        // 创建任务进度快照，用于异常时的回滚
        TaskProgressSnapshot snapshot = new TaskProgressSnapshot(task);

        // 更新任务进度
        task.setProgress(progress);

        // 根据进度百分比计算完成数量
        if (task.getPlanQuantity() != null && task.getPlanQuantity() > 0) {
            int completed = (int) Math.round(task.getPlanQuantity() * progress / 100.0);
            task.setCompletedQuantity(completed);
        }

        // 根据进度值调整任务状态
        if (progress == 100) {
            // 进度达到100%，标记为已完成并设置结束日期
            task.setStatus("COMPLETED");
            if (task.getEndDate() == null) {
                task.setEndDate(new Date());
            }
        } else if ("PENDING".equals(task.getStatus()) || "COMPLETED".equals(task.getStatus())) {
            // 从待处理或已完成状态变更为其他进度，标记为进行中
            task.setStatus("IN_PROGRESS");
        }

        // 保存更新后的任务
        ProductionTask saved = saveTaskWithConflictTranslation(task);

        try {
            // 同步更新关联生产计划的完成数量
            updatePlanCompletedQuantityEnhanced(task.getPlanId());
        } catch (RuntimeException ex) {
            // 更新失败时回滚任务进度
            rollbackTaskProgress(task, snapshot, ex);
            throw ex;
        }

        // 转换为视图对象并返回
        return convertToVO(saved);
    }


    /**
     * 更新生产计划的已完成数量（增强版）
     * <p>
     * 该方法通过汇总关联任务列表的完成数量来计算并更新生产计划的已完成数量。
     * 主要流程包括：
     * 1. 查询指定计划下的所有生产任务
     * 2. 累加所有任务的完成数量得到总完成数
     * 3. 更新生产计划的completedQuantity字段
     * 使用乐观锁机制处理并发更新，如果检测到并发冲突则抛出业务异常提示用户刷新。
     * </p>
     *
     * @param planId 生产计划的唯一标识符
     */
    private void updatePlanCompletedQuantityEnhanced(String planId) {
        if (!StringUtils.hasText(planId)) return;

        // 查询指定计划下的所有生产任务
        List<ProductionTask> tasks = productionTaskRepository.findByPlanId(planId);

        // 累加所有任务的完成数量得到总完成数
        int totalCompleted = tasks.stream()
                .mapToInt(t -> t.getCompletedQuantity() != null ? t.getCompletedQuantity() : 0)
                .sum();

        // 更新生产计划的已完成数量
        ProductionPlan plan = productionPlanRepository.findById(planId).orElse(null);
        if (plan != null) {
            plan.setCompletedQuantity(totalCompleted);

            try {
                // 保存更新后的计划，使用乐观锁检测并发冲突
                productionPlanRepository.save(plan);
            } catch (OptimisticLockingFailureException ex) {
                // 发生乐观锁冲突时，提示用户刷新后重试
                throw new BusinessException("生产计划汇总已更新，请刷新后重试");
            }
        }
    }


    /**
     * 保存生产任务并处理并发冲突
     * <p>
     * 该方法封装了生产任务的保存操作，通过捕获乐观锁异常来处理并发场景下的数据冲突。
     * 当检测到并发修改时，抛出友好的业务异常提示用户刷新数据后重试。
     * </p>
     *
     * @param task 需要保存的生产任务对象
     * @return 保存成功后的生产任务对象
     */
    private ProductionTask saveTaskWithConflictTranslation(ProductionTask task) {
        try {
            return productionTaskRepository.save(task);
        } catch (OptimisticLockingFailureException ex) {
            // 发生乐观锁冲突时，转换为业务异常提示用户刷新
            throw new BusinessException("生产任务已发生变更，请刷新后重试");
        }
    }


    /**
     * 回滚任务进度到快照记录的状态
     * <p>
     * 该方法在任务进度更新失败时调用，用于将任务恢复到修改前的状态。
     * 如果回滚过程中发生乐观锁冲突，说明数据已被其他操作修改，此时抛出致命异常需要人工介入核对。
     * </p>
     *
     * @param task 当前任务对象，需要被回滚
     * @param snapshot 任务进度快照，包含回滚前的状态信息
     * @param originalEx 原始异常，即导致需要回滚的异常
     */
    private void rollbackTaskProgress(ProductionTask task, TaskProgressSnapshot snapshot, RuntimeException originalEx) {
        // 恢复任务到快照记录的状态
        snapshot.restore(task);

        try {
            // 保存回滚后的任务状态
            productionTaskRepository.save(task);
        } catch (OptimisticLockingFailureException rollbackEx) {
            // 回滚失败时，抛出包含原始异常和回滚异常地致命异常
            IllegalStateException fatal = new IllegalStateException("任务进度更新失败，且任务状态回滚失败，请立即人工核对");
            fatal.addSuppressed(originalEx);
            fatal.addSuppressed(rollbackEx);
            throw fatal;
        }
    }


    /**
     * 迁移所有生产任务的产品信息
     * <p>
     * 该方法用于批量更新所有生产任务的产品名称和产品编码信息，从关联的生产计划中获取最新数据。
     * 主要用途是修复历史数据中缺失的产品信息字段。
     * 迁移规则：
     * 1. 只更新产品名称或产品编码为空的任务
     * 2. 从关联的生产计划中获取对应的产品信息
     * 3. 只有实际发生了更新的任務才会计入迁移数量
     * </p>
     *
     * @return 成功迁移的任务数量
     */
    @Override
    public int migrateProductInfoForAllTasks() {
        List<ProductionTask> allTasks = productionTaskRepository.findAll();
        int migratedCount = 0;

        // 遍历所有任务，检查并补充缺失的产品信息
        for (ProductionTask task : allTasks) {
            if (StringUtils.hasText(task.getPlanId())) {
                ProductionPlan plan = productionPlanRepository.findById(task.getPlanId()).orElse(null);
                if (plan != null) {
                    boolean needUpdate = false;

                    // 如果任务缺少产品名称但计划中有，则从计划中获取
                    if (!StringUtils.hasText(task.getProductName()) && StringUtils.hasText(plan.getProductName())) {
                        task.setProductName(plan.getProductName());
                        needUpdate = true;
                    }

                    // 如果任务缺少产品编码但计划中有，则从计划中获取
                    if (!StringUtils.hasText(task.getProductCode()) && StringUtils.hasText(plan.getProductCode())) {
                        task.setProductCode(plan.getProductCode());
                        needUpdate = true;
                    }

                    // 如果有字段需要更新，则保存任务并增加迁移计数
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

        /**
         * 恢复任务到快照记录的状态
         * <p>
         * 该方法将任务的进度、完成数量、状态和结束日期等属性恢复到快照保存时的值。
         * 用于在操作失败时回滚任务状态的变更，保证数据一致性。
         * </p>
         */
        private void restore(ProductionTask task) {
            task.setProgress(progress);
            task.setCompletedQuantity(completedQuantity);
            task.setStatus(status);
            task.setEndDate(endDate);
        }

    }

    /**
     * 将生产任务实体转换为视图对象
     * <p>
     * 该方法将数据库中的生产任务实体对象转换为前端展示的视图对象。
     * 对于产品名称、产品编码、颜色、尺寸等字段，会从关联的生产计划中获取最新数据，
     * 确保展示信息与生产计划保持一致。如果关联的计划不存在，则使用空字符串作为默认值。
     * </p>
     *
     * @param task 生产任务实体对象
     * @return 生产任务视图对象，包含从计划中获取的产品信息
     */
    private TaskVO convertToVO(ProductionTask task) {
        String productName = "";
        String productCode = "";
        String color = "";
        String size = "";

        // 从关联的生产计划中获取产品信息
        if (StringUtils.hasText(task.getPlanId())) {
            ProductionPlan plan = productionPlanRepository.findById(task.getPlanId()).orElse(null);
            if (plan != null) {
                productName = plan.getProductName();
                productCode = plan.getProductCode();
                color = plan.getColor();
                size = plan.getSize();
            }
        }

        // 构建并返回任务视图对象
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
