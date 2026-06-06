package com.garment.service.impl;

import com.garment.dto.*;
import com.garment.exception.BusinessException;
import com.garment.model.FinishedProduct;
import com.garment.model.Order;
import com.garment.model.OrderItem;
import com.garment.model.OrderLog;
import com.garment.model.SalesRecord;
import com.garment.model.User;
import com.garment.repository.FinishedProductRepository;
import com.garment.repository.OrderItemRepository;
import com.garment.repository.OrderLogRepository;
import com.garment.repository.OrderRepository;
import com.garment.repository.SalesRecordRepository;
import com.garment.repository.UserRepository;
import com.garment.service.InventoryService;
import com.garment.service.OrderService;
import com.garment.service.support.MongoAtomicOpsService;
import org.bson.Document;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 订单业务实现类。
 *
 * <p>负责订单创建、查询、审核、取消、发货、完成、日志记录以及完成订单归档等核心业务流程。</p>
 */
@Service
public class OrderServiceImpl implements OrderService {

    // 订单主表仓库，用于保存和查询订单基础信息。
    private final OrderRepository orderRepository;
    // 订单明细仓库，用于维护订单中的商品明细项。
    private final OrderItemRepository orderItemRepository;
    // 订单日志仓库，用于记录订单生命周期中的操作轨迹。
    private final OrderLogRepository orderLogRepository;
    // 用户仓库，用于校验操作人并读取操作人姓名。
    private final UserRepository userRepository;
    // 销售记录仓库，用于在订单完成后生成归档销售数据。
    private final SalesRecordRepository salesRecordRepository;
    // 成品仓库，用于发货前匹配可扣减的成品库存。
    private final FinishedProductRepository finishedProductRepository;
    // 库存服务，用于执行成品 FIFO 扣减和扣减回滚。
    private final InventoryService inventoryService;
    // Mongo 原子操作服务，用于保障状态流转和编号生成的并发安全。
    private final MongoAtomicOpsService mongoAtomicOpsService;

    /**
     * 构造订单业务实现类。
     *
     * <p>通过构造器注入订单业务所需的仓库、库存服务和原子操作服务。</p>
     *
     * @param orderRepository 订单主表仓库
     * @param orderItemRepository 订单明细仓库
     * @param orderLogRepository 订单日志仓库
     * @param userRepository 用户仓库
     * @param salesRecordRepository 销售记录仓库
     * @param finishedProductRepository 成品仓库
     * @param inventoryService 库存服务
     * @param mongoAtomicOpsService Mongo 原子操作服务
     */
    public OrderServiceImpl(OrderRepository orderRepository,
                            OrderItemRepository orderItemRepository,
                            OrderLogRepository orderLogRepository,
                            UserRepository userRepository,
                            SalesRecordRepository salesRecordRepository,
                            FinishedProductRepository finishedProductRepository,
                            InventoryService inventoryService,
                            MongoAtomicOpsService mongoAtomicOpsService) {
        // 保存订单主表仓库引用。
        this.orderRepository = orderRepository;
        // 保存订单明细仓库引用。
        this.orderItemRepository = orderItemRepository;
        // 保存订单日志仓库引用。
        this.orderLogRepository = orderLogRepository;
        // 保存用户仓库引用。
        this.userRepository = userRepository;
        // 保存销售记录仓库引用。
        this.salesRecordRepository = salesRecordRepository;
        // 保存成品仓库引用。
        this.finishedProductRepository = finishedProductRepository;
        // 保存库存服务引用。
        this.inventoryService = inventoryService;
        // 保存 Mongo 原子操作服务引用。
        this.mongoAtomicOpsService = mongoAtomicOpsService;
    }
    

        /**
         * 创建订单，生成订单号并保存订单及明细项
         * <p>
         * 该方法用于创建新订单，主要功能包括：
         * 1. 验证创建用户是否存在
         * 2. 使用原子操作生成唯一订单号
         * 3. 设置订单初始状态为“PENDING_APPROVAL”（待审核）
         * 4. 计算订单总金额
         * 5. 保存订单主记录和订单项明细
         * 6. 记录订单创建日志
         * </p>
         *
         * @param request 订单创建请求参数，包含客户信息、订单明细项列表、备注等
         * @param userId 创建人用户ID
         * @return 创建后的订单视图对象，包含订单基本信息和明细项
         * @throws BusinessException 如果用户不存在时抛出业务异常
         */
        @Override
        public OrderVO createOrder(OrderCreateRequest request, String userId) {
            // 查找并验证创建用户
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new BusinessException("用户不存在"));

            // 创建订单对象并设置基本信息
            Order order = new Order();
            order.setOrderNo(mongoAtomicOpsService.nextOrderNo(new Date()));
            order.setCustomerId(request.getCustomerId());
            order.setCustomerName(request.getCustomerName());
            order.setStatus("PENDING_APPROVAL");
            order.setCreateBy(userId);
            order.setCreateByName(user.getRealName());
            order.setRemark(request.getRemark());

            // 计算订单总金额并构建订单项列表
            double totalAmount = 0;
            List<OrderItem> items = new ArrayList<>();
            for (OrderItemDTO itemDTO : request.getItems()) {
                double amount = itemDTO.getUnitPrice() * itemDTO.getQuantity();
                totalAmount += amount;

                OrderItem item = new OrderItem();
                item.setOrderId(order.getId());
                item.setProductId(itemDTO.getProductId());
                item.setProductCode(itemDTO.getProductCode());
                item.setProductName(itemDTO.getProductName());
                item.setColor(itemDTO.getColor());
                item.setSize(itemDTO.getSize());
                item.setQuantity(itemDTO.getQuantity());
                item.setUnitPrice(itemDTO.getUnitPrice());
                item.setAmount(amount);
                items.add(item);
            }
            order.setTotalAmount(totalAmount);

            // 保存订单主记录
            Order savedOrder = orderRepository.save(order);

            // 关联订单项与订单ID并批量保存
            for (OrderItem item : items) {
                item.setOrderId(savedOrder.getId());
            }
            orderItemRepository.saveAll(items);

            // 记录订单创建日志
            saveLog(savedOrder.getId(), userId, user.getRealName(), "CREATE", "创建订单");

            return convertToVO(savedOrder, items, null);
        }


        /**
         * 查询订单列表，支持状态、客户名称、订单号筛选及分页，按创建时间降序排序
         * <p>
         * 该方法从数据库中获取所有订单记录，并根据筛选条件进行过滤。
         * 筛选结果按创建时间降序排列后返回分页数据。
         * </p>
         *
         * @param status 订单状态，精确匹配（如PENDING_APPROVAL、APPROVED等）
         * @param customerName 客户名称，支持模糊查询（包含匹配）
         * @param orderNo 订单编号，支持模糊查询（包含匹配）
         * @param pageable 分页参数，包含页码、每页大小和排序信息
         * @return 分页的订单视图对象列表，按创建时间降序排列
         */
        @Override
        public Page<OrderVO> getOrderList(String status, String customerName, String orderNo, Pageable pageable) {
            // 获取所有订单记录
            List<Order> allOrders = orderRepository.findAll();

            // 根据状态、客户名称、订单号筛选
            List<Order> filtered = allOrders.stream()
                    .filter(o -> !StringUtils.hasText(status) || status.equals(o.getStatus()))
                    .filter(o -> !StringUtils.hasText(customerName) || (o.getCustomerName() != null && o.getCustomerName().contains(customerName)))
                    .filter(o -> !StringUtils.hasText(orderNo) || (o.getOrderNo() != null && o.getOrderNo().contains(orderNo)))
                    .collect(Collectors.toList());

            // 按创建时间降序排序
            filtered.sort((a, b) -> {
                if (a.getCreateTime() == null && b.getCreateTime() == null) return 0;
                if (a.getCreateTime() == null) return 1;
                if (b.getCreateTime() == null) return -1;
                return b.getCreateTime().compareTo(a.getCreateTime());
            });

            // 计算分页范围
            int start = (int) pageable.getOffset();
            int end = Math.min(start + pageable.getPageSize(), filtered.size());
            List<Order> pageContent = start < filtered.size() ? filtered.subList(start, end) : new ArrayList<>();

            // 转换为视图对象
            List<OrderVO> voList = pageContent.stream()
                    .map(o -> convertToVO(o, null, null))
                    .collect(Collectors.toList());

            return new PageImpl<>(voList, pageable, filtered.size());
        }


        /**
         * 根据ID查询订单详情，包含订单项和操作日志
         * <p>
         * 该方法根据订单ID从数据库中查找对应的订单记录，
         * 并加载关联的订单项列表和操作日志列表，
         * 转换为完整的视图对象返回。
         * </p>
         *
         * @param id 订单ID
         * @return 订单视图对象，包含订单基本信息、订单项列表和操作日志列表
         * @throws BusinessException 如果订单不存在时抛出业务异常
         */
        @Override
        public OrderVO getOrderById(String id) {
            // 查找订单记录
            Order order = orderRepository.findById(id)
                    .orElseThrow(() -> new BusinessException("订单不存在"));

            // 查询订单项列表
            List<OrderItem> items = orderItemRepository.findByOrderId(id);
            // 查询操作日志列表（按创建时间降序）
            List<OrderLog> logs = orderLogRepository.findByOrderIdOrderByCreateTimeDesc(id);

            return convertToVO(order, items, logs);
        }


        /**
         * 更新订单信息，仅允许更新待审核状态的订单
         * <p>
         * 该方法用于更新订单的备注信息，主要功能包括：
         * 1. 查找并验证订单是否存在
         * 2. 验证订单状态必须为“PENDING_APPROVAL”（待审核）
         * 3. 选择性更新订单备注字段
         * 4. 使用乐观锁处理并发更新冲突
         * 5. 保存更新后的订单记录
         * </p>
         *
         * @param id 订单ID
         * @param request 订单更新请求参数，包含需要更新的字段（如备注）
         * @return 更新后的订单视图对象
         * @throws BusinessException 如果订单不存在或订单状态不是待审核时抛出业务异常
         */
        @Override
        public OrderVO updateOrder(String id, OrderUpdateRequest request) {
            // 查找订单记录
            Order order = orderRepository.findById(id)
                    .orElseThrow(() -> new BusinessException("订单不存在"));

            // 验证订单状态是否为待审核
            if (!"PENDING_APPROVAL".equals(order.getStatus())) {
                throw new BusinessException("仅待审核状态的订单可更新");
            }

            // 选择性更新备注字段
            if (request.getRemark() != null) {
                order.setRemark(request.getRemark());
            }

            // 保存更新，处理乐观锁冲突
            Order saved;
            try {
                saved = orderRepository.save(order);
            } catch (OptimisticLockingFailureException ex) {
                throw new BusinessException("订单状态已变更，请刷新后再操作");
            }
            // 查询订单项并返回视图对象
            List<OrderItem> items = orderItemRepository.findByOrderId(id);
            return convertToVO(saved, items, null);
        }


        /**
         * 取消订单，仅允许取消待审核或已审核状态的订单
         * <p>
         * 该方法用于取消订单，主要功能包括：
         * 1. 查找并验证订单是否存在
         * 2. 验证订单状态必须为“PENDING_APPROVAL”或“APPROVED”
         * 3. 使用原子操作将订单状态更新为“CANCELLED”
         * 4. 记录取消订单的操作日志
         * </p>
         *
         * @param id 订单ID
         * @param userId 操作人用户ID
         * @throws BusinessException 如果订单不存在、状态不允许取消或状态已被其他操作修改时抛出业务异常
         */
        @Override
        public void cancelOrder(String id, String userId) {
            // 查找订单记录
            Order order = orderRepository.findById(id)
                    .orElseThrow(() -> new BusinessException("订单不存在"));

            // 验证订单状态是否允许取消
            if (!"PENDING_APPROVAL".equals(order.getStatus()) && !"APPROVED".equals(order.getStatus())) {
                throw new BusinessException("仅待审核或已审核的订单可取消");
            }

            // 使用原子操作更新订单状态为已取消
            boolean cancelled = mongoAtomicOpsService.transitionOrderStatus(id, order.getStatus(), "CANCELLED", null);
            if (!cancelled) {
                throw new BusinessException("订单状态已变更，请刷新后再操作");
            }

            // 查找操作人并记录操作日志
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new BusinessException("用户不存在"));
            saveLog(id, userId, user.getRealName(), "CANCEL", "取消订单");
        }


        /**
         * 审核订单，根据审核结果将订单状态变更为已审核或已取消
         * <p>
         * 该方法用于审核订单，主要功能包括：
         * 1. 查找并验证订单是否存在
         * 2. 验证订单状态必须为“PENDING_APPROVAL”（待审核）
         * 3. 查找审核人用户信息
         * 4. 根据审核结果（通过/拒绝）更新订单状态：
         *    - 审核通过：状态更新为“APPROVED”，记录审核人和审核时间
         *    - 审核拒绝：状态更新为“CANCELLED”，记录审核人和审核时间
         * 5. 使用原子操作保证状态更新的并发安全
         * 6. 记录审核操作日志
         * </p>
         *
         * @param id 订单ID
         * @param request 审核请求参数，包含是否通过（approved）和审核备注（remark）
         * @param userId 审核人用户ID
         * @return 审核后的订单视图对象
         * @throws BusinessException 如果订单不存在、状态不是待审核或状态已被其他操作修改时抛出业务异常
         */
        @Override
        public OrderVO approveOrder(String id, OrderApproveRequest request, String userId) {
            // 查找订单记录
            Order order = orderRepository.findById(id)
                    .orElseThrow(() -> new BusinessException("订单不存在"));

            // 验证订单状态是否为待审核
            if (!"PENDING_APPROVAL".equals(order.getStatus())) {
                throw new BusinessException("仅待审核状态的订单可审核");
            }

            // 查找审核人信息
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new BusinessException("用户不存在"));

            // 根据审核结果更新订单状态
            if (Boolean.TRUE.equals(request.getApproved())) {
                // 审核通过：更新状态为APPROVED，记录审核信息
                Date approveTime = new Date();
                boolean changed = mongoAtomicOpsService.transitionOrderStatus(
                        id,
                        "PENDING_APPROVAL",
                        "APPROVED",
                        new Document("approveBy", userId)
                                .append("approveByName", user.getRealName())
                                .append("approveTime", approveTime)
                                .append("approveRemark", request.getRemark())
                );
                if (!changed) {
                    throw new BusinessException("订单状态已变更，请刷新后再操作");
                }
                // 记录审核通过日志
                saveLog(id, userId, user.getRealName(), "APPROVE",
                        "审核通过" + (StringUtils.hasText(request.getRemark()) ? "：" + request.getRemark() : ""));
            } else {
                // 审核拒绝：更新状态为CANCELLED，记录审核信息
                Date approveTime = new Date();
                boolean changed = mongoAtomicOpsService.transitionOrderStatus(
                        id,
                        "PENDING_APPROVAL",
                        "CANCELLED",
                        new Document("approveBy", userId)
                                .append("approveByName", user.getRealName())
                                .append("approveTime", approveTime)
                                .append("approveRemark", request.getRemark())
                );
                if (!changed) {
                    throw new BusinessException("订单状态已变更，请刷新后再操作");
                }
                // 记录审核拒绝日志
                saveLog(id, userId, user.getRealName(), "REJECT",
                        "审核拒绝" + (StringUtils.hasText(request.getRemark()) ? "：" + request.getRemark() : ""));
            }

            // 查询最新订单信息和订单项，返回视图对象
            Order latestOrder = orderRepository.findById(id)
                    .orElseThrow(() -> new BusinessException("订单不存在"));
            List<OrderItem> items = orderItemRepository.findByOrderId(id);
            return convertToVO(latestOrder, items, null);
        }


        /**
         * 查询订单操作日志列表，按创建时间降序排序
         * <p>
         * 该方法根据订单ID从数据库中查询该订单的所有操作日志，
         * 日志已按创建时间降序排列。
         * </p>
         *
         * @param orderId 订单ID
         * @return 订单操作日志列表，按创建时间降序排列
         */
        @Override
        public List<OrderLog> getOrderLogs(String orderId) {
            // 查询订单操作日志（已按创建时间降序排列）
            return orderLogRepository.findByOrderIdOrderByCreateTimeDesc(orderId);
        }


        /**
         * 订单发货，扣减库存并更新订单状态为已发货，支持失败回滚
         * <p>
         * 该方法用于将已审核订单推进到发货状态，主要功能包括：
         * 1. 校验订单存在且状态为“APPROVED”
         * 2. 构建发货库存扣减计划并提前校验库存是否充足
         * 3. 先原子更新订单状态为“SHIPPED”
         * 4. 按计划执行成品 FIFO 扣减，并收集扣减凭证
         * 5. 扣减失败时回滚库存和订单状态，避免数据不一致
         * 6. 记录发货日志并返回最新订单视图
         * </p>
         *
         * @param id 订单ID
         * @param userId 操作人用户ID
         * @return 发货后的订单视图对象
         * @throws BusinessException 如果订单不存在、状态不允许发货、库存不足或状态已被并发修改时抛出业务异常
         */
        @Override
        public OrderVO shipOrder(String id, String userId) {
            // 查找订单记录，确保发货操作有明确的目标订单。
            Order order = orderRepository.findById(id)
                    .orElseThrow(() -> new BusinessException("订单不存在"));

            // 校验订单必须处于已审核状态，避免跳过审核直接发货。
            if (!"APPROVED".equals(order.getStatus())) {
                throw new BusinessException("仅已审核的订单可发货");
            }

            // 查询订单明细并按商品维度构建库存扣减计划。
            List<OrderItem> items = orderItemRepository.findByOrderId(id);
            List<ShippingDeductionPlan> deductionPlans = buildShippingDeductionPlans(items);
            // 汇总库存不足或无法匹配成品的扣减计划，用于一次性返回完整错误信息。
            List<String> insufficientItems = deductionPlans.stream()
                    .filter(plan -> plan.products.isEmpty() || plan.availableQuantity < plan.requiredQuantity)
                    .map(plan -> String.format("%s（需 %d，现有 %d）", plan.itemLabel, plan.requiredQuantity, plan.availableQuantity))
                    .collect(Collectors.toList());
            // 如果存在库存不足的商品，则发货前直接中断，不进入状态变更和扣减流程。
            if (!insufficientItems.isEmpty()) {
                throw new BusinessException("订单发货失败，以下商品库存不足：" + String.join("；", insufficientItems));
            }

            // 记录发货时间，并将其作为状态流转的附加字段写入订单。
            Date shipTime = new Date();
            // 先原子更新订单状态，保证只有仍处于 APPROVED 的订单可以进入发货流程。
            boolean shipped = mongoAtomicOpsService.transitionOrderStatus(
                    id,
                    "APPROVED",
                    "SHIPPED",
                    new Document("shipTime", shipTime)
            );
            // 如果状态更新失败，说明订单已被其他请求修改，当前发货操作应终止。
            if (!shipped) {
                throw new BusinessException("订单状态已变更，请刷新后再操作");
            }

            // 保存每次库存扣减的凭证，便于后续发生异常时按凭证恢复库存。
            List<InventoryDeductionReceipt> receipts = new ArrayList<>();
            // 执行实际库存扣减，任一扣减失败都进入补偿回滚流程。
            try {
                // 按每个发货计划逐项扣减库存。
                for (ShippingDeductionPlan plan : deductionPlans) {
                    // 需求量小于等于 0 的计划不需要扣减。
                    if (plan.requiredQuantity <= 0) {
                        continue;
                    }
                    // 对当前计划执行 FIFO 扣减，并把扣减凭证追加到 receipts。
                    deductShippingPlan(order, plan, receipts);
                }
            } catch (RuntimeException ex) {
                // 先保留原始异常，后续回滚失败时把它作为被压制异常一起抛出。
                RuntimeException failure = ex;
                // 库存扣减失败后，优先尝试恢复已经扣减成功的库存。
                try {
                    rollbackShippingReceipts(receipts, order.getOrderNo());
                } catch (RuntimeException rollbackEx) {
                    // 库存回滚失败属于需要人工介入的严重状态，保留原始失败原因。
                    IllegalStateException fatal = new IllegalStateException("订单发货失败，且库存补偿回滚失败，请立即人工核对库存", rollbackEx);
                    fatal.addSuppressed(ex);
                    failure = fatal;
                }
                // 库存补偿后继续尝试把订单状态从 SHIPPED 回滚为 APPROVED。
                try {
                    boolean reverted = mongoAtomicOpsService.transitionOrderStatus(
                            id,
                            "SHIPPED",
                            "APPROVED",
                            new Document("shipTime", null)
                    );
                    // 如果订单状态未能回滚，抛出需要人工核对的严重异常。
                    if (!reverted) {
                        IllegalStateException fatal = new IllegalStateException("订单发货失败，且订单状态回滚未生效，请立即人工核对订单");
                        fatal.addSuppressed(failure);
                        throw fatal;
                    }
                } catch (RuntimeException rollbackStatusEx) {
                    // 订单状态回滚过程本身失败时，同样保留库存扣减失败或库存回滚失败的上下文。
                    IllegalStateException fatal = new IllegalStateException("订单发货失败，且订单状态回滚失败，请立即人工核对订单", rollbackStatusEx);
                    fatal.addSuppressed(failure);
                    throw fatal;
                }
                // 库存和状态都处理完成后，向上抛出原始失败或增强后的严重失败。
                throw failure;
            }

            // 重新读取订单，确保返回的是状态流转后的最新数据。
            Order latestOrder = orderRepository.findById(id)
                    .orElseThrow(() -> new BusinessException("订单不存在"));

            // 查询操作人并记录发货日志。
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new BusinessException("用户不存在"));
            saveLog(id, userId, user.getRealName(), "SHIP", "订单发货");

            // 返回包含订单明细的发货后订单视图。
            return convertToVO(latestOrder, items, null);
        }


        /**
         * 回滚订单发货时的库存扣减操作，按相反顺序恢复已扣减的库存
         * <p>
         * 该方法用于在发货扣减失败后执行库存补偿，按照扣减发生的逆序逐条恢复库存。
         * </p>
         *
         * @param receipts 库存扣减凭证列表
         * @param orderNo 订单编号，用于生成回滚原因
         */
        private void rollbackShippingReceipts(List<InventoryDeductionReceipt> receipts, String orderNo) {
            // 按逆序回滚，尽量与库存扣减发生顺序相反，降低部分恢复时的数据偏差。
            for (int i = receipts.size() - 1; i >= 0; i--) {
                // 根据扣减凭证恢复库存，并写入订单发货回滚原因。
                inventoryService.restoreInventoryDeduction(receipts.get(i), "订单发货回滚-" + orderNo);
            }
        }


        /**
         * 执行订单发货的库存扣减，按FIFO原则从多个成品中扣减所需数量
         * <p>
         * 该方法根据发货扣减计划遍历可用成品，逐个扣减可用库存，直到满足订单需求数量。
         * </p>
         *
         * @param order 订单对象
         * @param plan 发货扣减计划，包含需要扣减的成品列表和数量
         * @param receipts 用于收集扣减凭证的列表
         * @throws BusinessException 如果所有可用成品仍无法满足需求数量时抛出业务异常
         */
        private void deductShippingPlan(Order order, ShippingDeductionPlan plan, List<InventoryDeductionReceipt> receipts) {
            // 初始化当前计划还需要扣减的数量。
            int remainingQuantity = plan.requiredQuantity;
            // 按计划中的成品列表逐个尝试扣减库存。
            for (FinishedProduct product : plan.products) {
                // 如果需求已经扣满，提前结束循环。
                if (remainingQuantity <= 0) {
                    break;
                }

                // 计算当前成品可用于发货的库存数量。
                int availableQuantity = getAvailableFinishedProductQuantity(product);
                // 当前成品没有可用库存时跳过，继续尝试下一个成品。
                if (availableQuantity <= 0) {
                    continue;
                }

                // 本次扣减数量取剩余需求和当前可用库存中的较小值。
                int deductionQuantity = Math.min(remainingQuantity, availableQuantity);
                // 调用库存服务按 FIFO 规则扣减成品，并返回可用于回滚的扣减凭证。
                InventoryDeductionReceipt receipt = inventoryService.fifoDeductFinishedProductWithReceipt(
                        product.getId(),
                        deductionQuantity,
                        "订单发货-" + order.getOrderNo() + " | 商品:" + plan.itemLabel
                );
                // 收集扣减凭证，方便发货失败时恢复库存。
                receipts.add(receipt);
                // 扣减完成后减少剩余需求数量。
                remainingQuantity -= deductionQuantity;
            }

            // 遍历所有匹配成品后仍有缺口，说明库存不足或库存扣减过程中发生变化。
            if (remainingQuantity > 0) {
                throw new BusinessException(String.format(
                        "订单发货失败，以下商品库存不足：%s（需 %d，现有 %d）",
                        plan.itemLabel, plan.requiredQuantity, plan.requiredQuantity - remainingQuantity));
            }
        }


        /**
         * 完成订单，将已发货的订单状态更新为已完成，并归档订单数据
         * <p>
         * 该方法用于完成已发货订单，主要功能包括：
         * 1. 校验订单存在且状态为“SHIPPED”
         * 2. 原子更新订单状态为“COMPLETED”并记录完成时间
         * 3. 记录订单完成日志
         * 4. 将完成订单归档为销售记录
         * 5. 返回完成后的订单视图
         * </p>
         *
         * @param id 订单ID
         * @param userId 操作人用户ID
         * @return 完成后的订单视图对象
         * @throws BusinessException 如果订单不存在、状态不允许完成或状态已被并发修改时抛出业务异常
         */
        @Override
        public OrderVO completeOrder(String id, String userId) {
            // 查找订单记录，确保完成操作有明确的目标订单。
            Order order = orderRepository.findById(id)
                    .orElseThrow(() -> new BusinessException("订单不存在"));

            // 只有已发货订单才能进入完成状态。
            if (!"SHIPPED".equals(order.getStatus())) {
                throw new BusinessException("仅已发货的订单可完成");
            }

            // 记录完成时间，并随状态流转一起写入订单。
            Date completeTime = new Date();
            // 原子更新订单状态，避免并发请求重复完成或覆盖状态。
            boolean completed = mongoAtomicOpsService.transitionOrderStatus(
                    id,
                    "SHIPPED",
                    "COMPLETED",
                    new Document("completeTime", completeTime)
            );
            // 状态流转失败表示订单已被其他请求修改。
            if (!completed) {
                throw new BusinessException("订单状态已变更，请刷新后再操作");
            }

            // 重新读取订单，拿到完成时间和状态更新后的最新数据。
            Order latestOrder = orderRepository.findById(id)
                    .orElseThrow(() -> new BusinessException("订单不存在"));

            // 查询操作人并记录完成日志。
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new BusinessException("用户不存在"));
            saveLog(id, userId, user.getRealName(), "COMPLETE", "订单完成");

            // 查询订单明细，用于归档销售记录和组装返回视图。
            List<OrderItem> items = orderItemRepository.findByOrderId(id);
            // 将完成订单归档为销售记录，重复归档会在方法内部幂等处理。
            archiveCompletedOrder(latestOrder, items);
            // 返回完成后的订单视图。
            return convertToVO(latestOrder, items, null);
        }


        /**
         * 保存订单操作日志
         * <p>
         * 该方法用于记录订单的各种操作，包括创建、审核、取消、发货、完成等。
         * 每次操作都会创建一条日志记录，包含操作人、操作类型和备注信息。
         * </p>
         *
         * @param orderId 订单ID
         * @param operatorId 操作人ID
         * @param operatorName 操作人姓名
         * @param action 操作类型（CREATE-创建、APPROVE-审核通过、REJECT-审核拒绝、CANCEL-取消、SHIP-发货、COMPLETE-完成）
         * @param remark 操作备注
         */
        private void saveLog(String orderId, String operatorId, String operatorName, String action, String remark) {
            // 创建订单日志对象并设置属性
            OrderLog log = new OrderLog();
            log.setOrderId(orderId);
            log.setOperator(operatorId);
            log.setOperatorName(operatorName);
            log.setAction(action);
            log.setRemark(remark);
            // 保存日志记录
            orderLogRepository.save(log);
        }


        /**
         * 归档已完成的订单，将订单数据转换为销售记录保存到数据库
         * <p>
         * 该方法用于在订单完成后将其数据归档为销售记录，主要功能包括：
         * 1. 检查是否已经存在该订单的销售记录（避免重复归档）
         * 2. 计算订单的总数量和总金额
         * 3. 创建销售记录对象并设置相关信息（客户信息、日期、明细项等）
         * 4. 对于单商品订单，保留原始字段；对于多商品订单，合并产品名称
         * 5. 保存销售记录到数据库
         * </p>
         *
         * @param order 已完成的订单对象
         * @param items 订单明细项列表
         */
        private void archiveCompletedOrder(Order order, List<OrderItem> items) {
            // 检查是否已存在销售记录，避免重复归档
            if (salesRecordRepository.findByOrderId(order.getId()).isPresent()) {
                return;
            }

            // 安全处理订单项列表
            List<OrderItem> safeItems = items != null ? items : new ArrayList<>();
            // 计算订单总数量
            int totalQuantity = safeItems.stream()
                    .mapToInt(item -> item.getQuantity() != null ? item.getQuantity() : 0)
                    .sum();
            // 计算订单总金额
            double totalAmount = order.getTotalAmount() != null
                    ? order.getTotalAmount()
                    : safeItems.stream()
                    .mapToDouble(item -> item.getAmount() != null ? item.getAmount() : 0D)
                    .sum();

            // 创建销售记录对象并设置基本信息
            SalesRecord salesRecord = new SalesRecord();
            salesRecord.setOrderId(order.getId());
            salesRecord.setOrderNo(order.getOrderNo());
            salesRecord.setCustomerId(order.getCustomerId());
            salesRecord.setCustomerName(order.getCustomerName());
            salesRecord.setProductCount(safeItems.size());
            salesRecord.setTotalQuantity(totalQuantity);
            salesRecord.setTotalAmount(totalAmount);
            salesRecord.setOrderDate(order.getCreateTime());
            salesRecord.setShipDate(order.getShipTime());
            salesRecord.setCompleteDate(order.getCompleteTime());
            salesRecord.setSaleDate(order.getCompleteTime());
            salesRecord.setCreateBy(order.getCreateBy());
            salesRecord.setCreateByName(order.getCreateByName());
            // 转换订单项为销售记录项
            salesRecord.setItems(safeItems.stream()
                    .map(item -> new SalesRecord.SalesRecordItem(
                            item.getProductId(),
                            item.getProductCode(),
                            item.getProductName(),
                            item.getColor(),
                            item.getSize(),
                            item.getQuantity(),
                            item.getUnitPrice(),
                            item.getAmount()))
                    .collect(Collectors.toList()));

            // 根据订单项数量设置不同的字段
            if (safeItems.size() == 1) {
                // 单商品订单：保留原始字段
                OrderItem firstItem = safeItems.get(0);
                salesRecord.setProductId(firstItem.getProductId());
                salesRecord.setProductCode(firstItem.getProductCode());
                salesRecord.setProductName(firstItem.getProductName());
                salesRecord.setQuantity(firstItem.getQuantity());
                salesRecord.setUnitPrice(firstItem.getUnitPrice());
                salesRecord.setAmount(firstItem.getAmount());
            } else {
                // 多商品订单：设置汇总数据和合并的产品名称
                salesRecord.setQuantity(totalQuantity);
                salesRecord.setAmount(totalAmount);
                salesRecord.setProductName(safeItems.stream()
                        .map(OrderItem::getProductName)
                        .filter(StringUtils::hasText)
                        .distinct()
                        .collect(Collectors.joining("、")));
            }

            // 保存销售记录，处理重复键异常
            try {
                salesRecordRepository.save(salesRecord);
            } catch (DuplicateKeyException ex) {
                // Another request archived the order first; treat that as success.
            }
        }


        /**
         * 将订单实体转换为视图对象，包含订单项和操作日志
         * <p>
         * 该方法把订单实体、订单明细和操作日志统一组装为前端接口返回的 OrderVO。
         * </p>
         *
         * @param order 订单实体对象
         * @param items 订单项列表
         * @param logs 订单操作日志列表
         * @return 订单视图对象
         */
        private OrderVO convertToVO(Order order, List<OrderItem> items, List<OrderLog> logs) {
            // 默认不加载订单项，只有调用方传入 items 时才进行转换。
            List<OrderItemDTO> itemDTOs = null;
            // 将订单项实体列表转换为订单项 DTO 列表。
            if (items != null) {
                itemDTOs = items.stream()
                        .map(item -> OrderItemDTO.builder()
                                .productId(item.getProductId())
                                .productCode(item.getProductCode())
                                .productName(item.getProductName())
                                .color(item.getColor())
                                .size(item.getSize())
                                .quantity(item.getQuantity())
                                .unitPrice(item.getUnitPrice())
                                .amount(item.getAmount())
                                .build())
                        .collect(Collectors.toList());
            }

            // 默认不加载日志，只有调用方传入 logs 时才进行转换。
            List<OrderVO.OrderLogVO> logVOs = null;
            // 将日志实体列表转换为订单视图中的日志子对象列表。
            if (logs != null) {
                logVOs = logs.stream()
                        .map(log -> OrderVO.OrderLogVO.builder()
                                .id(log.getId())
                                .operator(log.getOperator())
                                .operatorName(log.getOperatorName())
                                .action(log.getAction())
                                .remark(log.getRemark())
                                .createTime(log.getCreateTime())
                                .build())
                        .collect(Collectors.toList());
            }

            // 使用构建器汇总订单基础字段、明细字段和日志字段。
            return OrderVO.builder()
                    .id(order.getId())
                    .orderNo(order.getOrderNo())
                    .customerId(order.getCustomerId())
                    .customerName(order.getCustomerName())
                    .totalAmount(order.getTotalAmount())
                    .status(order.getStatus())
                    .createBy(order.getCreateBy())
                    .createByName(order.getCreateByName())
                    .approveBy(order.getApproveBy())
                    .approveByName(order.getApproveByName())
                    .approveTime(order.getApproveTime())
                    .shipTime(order.getShipTime())
                    .completeTime(order.getCompleteTime())
                    .approveRemark(order.getApproveRemark())
                    .remark(order.getRemark())
                    .createTime(order.getCreateTime())
                    .updateTime(order.getUpdateTime())
                    .items(itemDTOs)
                    .logs(logVOs)
                    .build();
        }


        /**
         * 构建订单发货的库存扣减计划，将订单项映射到对应的成品库存
         * <p>
         * 该方法先把订单项按商品身份合并，再计算每个商品可用库存，供发货前校验和扣减使用。
         * </p>
         *
         * @param items 订单项列表
         * @return 发货扣减计划列表，每个计划包含需要扣减的成品和数量
         */
        private List<ShippingDeductionPlan> buildShippingDeductionPlans(List<OrderItem> items) {
            // 使用有序 Map 保持订单项原始处理顺序，并按计划键合并相同商品。
            Map<String, ShippingDeductionPlan> plans = new LinkedHashMap<>();
            // 对空明细做安全兜底，避免后续遍历出现空指针。
            List<OrderItem> safeItems = items != null ? items : new ArrayList<>();

            // 遍历订单明细，构建每个商品维度的扣减需求。
            for (OrderItem item : safeItems) {
                // 空数量按 0 处理，避免拆箱空指针。
                int requiredQuantity = item.getQuantity() != null ? item.getQuantity() : 0;
                // 非正数数量不产生扣减需求。
                if (requiredQuantity <= 0) {
                    continue;
                }

                // 解析订单项可匹配的成品库存列表。
                List<FinishedProduct> matchedProducts = resolveFinishedProducts(item);
                // 构建用于错误提示和扣减备注的商品标签。
                String itemLabel = formatOrderItemLabel(item);
                // 构建合并相同商品需求的唯一计划键。
                String planKey = buildShippingDeductionPlanKey(item);

                // 相同商品复用已有计划，首次出现时创建新计划。
                ShippingDeductionPlan plan = plans.computeIfAbsent(planKey,
                        key -> new ShippingDeductionPlan(matchedProducts, itemLabel));
                // 累加该商品的发货需求数量。
                plan.requiredQuantity += requiredQuantity;
            }

            // 为每个扣减计划汇总匹配成品的可用库存数量。
            plans.values().forEach(plan -> plan.availableQuantity = plan.products.stream()
                    .mapToInt(this::getAvailableFinishedProductQuantity)
                    .sum());
            // 返回计划列表，供发货流程继续校验和扣减。
            return new ArrayList<>(plans.values());
        }


        /**
         * 构建订单发货扣减计划的唯一键，用于合并相同商品的扣减需求
         * <p>
         * 优先使用产品 ID 作为唯一键；如果订单项没有产品 ID，则使用编码、名称、颜色和尺寸组合识别商品。
         * </p>
         *
         * @param item 订单项对象
         * @return 计划唯一键，基于产品ID或产品属性组合
         */
        private String buildShippingDeductionPlanKey(OrderItem item) {
            // 产品 ID 最稳定，存在时直接作为扣减计划唯一键。
            if (StringUtils.hasText(item.getProductId())) {
                return "id:" + normalizeText(item.getProductId());
            }
            // 没有产品 ID 时，退化为商品关键属性组合键。
            return "identity:"
                    + normalizeText(item.getProductCode()) + "|"
                    + normalizeText(item.getProductName()) + "|"
                    + normalizeText(item.getColor()) + "|"
                    + normalizeText(item.getSize());
        }


        /**
         * 解析订单项对应的成品列表，优先按ID精确查找，否则按产品属性模糊匹配并排序
         * <p>
         * 该方法用于把订单项映射到实际可扣减的成品库存，支持产品 ID 精确匹配和商品属性组合匹配。
         * </p>
         *
         * @param item 订单项对象
         * @return 匹配的成品列表，按创建时间升序排列
         */
        private List<FinishedProduct> resolveFinishedProducts(OrderItem item) {
            // 如果订单项携带产品 ID，则优先按 ID 精确查找成品。
            if (StringUtils.hasText(item.getProductId())) {
                // 使用列表承载 Optional 查询结果，便于与属性匹配分支保持统一返回类型。
                List<FinishedProduct> products = new ArrayList<>();
                // 找到成品时追加到结果列表，找不到则返回空列表。
                finishedProductRepository.findById(item.getProductId()).ifPresent(products::add);
                return products;
            }

            // 没有产品 ID 时，遍历成品并按编码、名称、颜色和尺寸组合匹配。
            return finishedProductRepository.findAll().stream()
                    .filter(product -> sameText(product.getProductCode(), item.getProductCode()))
                    .filter(product -> sameText(product.getName(), item.getProductName()))
                    .filter(product -> sameText(product.getColor(), item.getColor()))
                    .filter(product -> sameText(product.getSize(), item.getSize()))
                    // 按创建时间升序排序，使 FIFO 扣减时优先处理更早入库的成品。
                    .sorted(Comparator.comparing(FinishedProduct::getCreateTime, Comparator.nullsLast(Date::compareTo)))
                    .collect(Collectors.toList());
        }


        /**
         * 获取成品的可用库存数量，优先从位置库存中计算，否则使用总库存
         * <p>
         * 该方法统一计算发货前可用库存，兼容有库位明细和只有总库存两种成品数据结构。
         * </p>
         *
         * @param product 成品对象
         * @return 可用库存数量
         */
        private int getAvailableFinishedProductQuantity(FinishedProduct product) {
            // 空成品没有可用库存，直接按 0 处理。
            if (product == null) {
                return 0;
            }

            // 如果存在库位库存，则以库位数量汇总作为可用库存。
            if (product.getLocations() != null && !product.getLocations().isEmpty()) {
                return product.getLocations().stream()
                        .mapToInt(location -> location.getQuantity() != null ? location.getQuantity() : 0)
                        .sum();
            }

            // 没有库位库存时，退回使用成品总库存数量。
            return product.getQuantity() != null ? product.getQuantity() : 0;
        }


        /**
         * 格式化订单项标签，用于显示商品名称、编码、颜色和尺寸信息
         * <p>
         * 该方法用于生成库存不足提示和库存扣减备注中的商品标识文本。
         * </p>
         *
         * @param item 订单项对象
         * @return 格式化后的商品标签字符串
         */
        private String formatOrderItemLabel(OrderItem item) {
            // 使用 StringBuilder 逐段拼接商品展示标签。
            StringBuilder label = new StringBuilder();
            // 商品名称为空时使用兜底文本，避免错误提示缺少主体信息。
            label.append(item.getProductName() != null ? item.getProductName() : "未知商品");
            // 商品编码存在时追加到名称后，便于区分同名商品。
            if (StringUtils.hasText(item.getProductCode())) {
                label.append("-").append(item.getProductCode());
            }
            // 追加颜色信息，缺失时使用占位符。
            label.append("/").append(StringUtils.hasText(item.getColor()) ? item.getColor() : "-");
            // 追加尺寸信息，缺失时使用占位符。
            label.append("/").append(StringUtils.hasText(item.getSize()) ? item.getSize() : "-");
            // 返回完整商品标签。
            return label.toString();
        }
    

        /**
         * 比较两个文本是否相同（忽略大小写和首尾空格）
         * <p>
         * 该方法通过统一标准化空值和首尾空格，降低商品属性匹配时的文本差异影响。
         * </p>
         *
         * @param left 第一个文本
         * @param right 第二个文本
         * @return 如果标准化后相等则返回true
         */
        private boolean sameText(String left, String right) {
            // 两侧文本标准化后再比较，空值会统一转换为空字符串。
            return normalizeText(left).equals(normalizeText(right));
        }


        /**
         * 标准化文本，去除首尾空格，空值转换为空字符串
         * <p>
         * 该方法用于商品属性比较和扣减计划键构建，避免 null 与空白文本造成匹配异常。
         * </p>
         *
         * @param value 待标准化的文本
         * @return 标准化后的文本
         */
        private String normalizeText(String value) {
            // 有实际内容时去除首尾空格，否则统一返回空字符串。
            return StringUtils.hasText(value) ? value.trim() : "";
        }


    /**
     * 订单发货扣减计划。
     *
     * <p>用于记录某个商品维度需要扣减的成品列表、展示标签、需求数量和可用数量。</p>
     */
    private static class ShippingDeductionPlan {
        // 可用于该商品发货扣减的成品库存列表。
        private final List<FinishedProduct> products;
        // 商品展示标签，用于错误提示和库存变动备注。
        private final String itemLabel;
        // 当前商品维度累计需要扣减的数量。
        private int requiredQuantity;
        // 当前商品维度可用库存汇总数量。
        private int availableQuantity;

        /**
         * 创建发货扣减计划。
         *
         * <p>初始化匹配到的成品库存列表和商品展示标签，数量字段由外部构建流程继续累加和计算。</p>
         *
         * @param products 可扣减的成品列表
         * @param itemLabel 商品展示标签
         */
        private ShippingDeductionPlan(List<FinishedProduct> products, String itemLabel) {
            // 保存可扣减成品列表。
            this.products = products;
            // 保存商品展示标签。
            this.itemLabel = itemLabel;
        }
    }
}
