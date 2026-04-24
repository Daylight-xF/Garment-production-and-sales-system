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
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class OrderServiceImpl implements OrderService {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final OrderLogRepository orderLogRepository;
    private final UserRepository userRepository;
    private final SalesRecordRepository salesRecordRepository;
    private final FinishedProductRepository finishedProductRepository;
    private final InventoryService inventoryService;
    private final MongoAtomicOpsService mongoAtomicOpsService;

    public OrderServiceImpl(OrderRepository orderRepository,
                            OrderItemRepository orderItemRepository,
                            OrderLogRepository orderLogRepository,
                            UserRepository userRepository,
                            SalesRecordRepository salesRecordRepository,
                            FinishedProductRepository finishedProductRepository,
                            InventoryService inventoryService,
                            MongoAtomicOpsService mongoAtomicOpsService) {
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.orderLogRepository = orderLogRepository;
        this.userRepository = userRepository;
        this.salesRecordRepository = salesRecordRepository;
        this.finishedProductRepository = finishedProductRepository;
        this.inventoryService = inventoryService;
        this.mongoAtomicOpsService = mongoAtomicOpsService;
    }

    @Override
    public OrderVO createOrder(OrderCreateRequest request, String userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException("用户不存在"));

        Order order = new Order();
        order.setOrderNo(mongoAtomicOpsService.nextOrderNo(new Date()));
        order.setCustomerId(request.getCustomerId());
        order.setCustomerName(request.getCustomerName());
        order.setStatus("PENDING_APPROVAL");
        order.setCreateBy(userId);
        order.setCreateByName(user.getRealName());
        order.setRemark(request.getRemark());

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

        Order savedOrder = orderRepository.save(order);

        for (OrderItem item : items) {
            item.setOrderId(savedOrder.getId());
        }
        orderItemRepository.saveAll(items);

        saveLog(savedOrder.getId(), userId, user.getRealName(), "CREATE", "创建订单");

        return convertToVO(savedOrder, items, null);
    }

    @Override
    public Page<OrderVO> getOrderList(String status, String customerName, String orderNo, Pageable pageable) {
        List<Order> allOrders = orderRepository.findAll();

        List<Order> filtered = allOrders.stream()
                .filter(o -> !StringUtils.hasText(status) || status.equals(o.getStatus()))
                .filter(o -> !StringUtils.hasText(customerName) || (o.getCustomerName() != null && o.getCustomerName().contains(customerName)))
                .filter(o -> !StringUtils.hasText(orderNo) || (o.getOrderNo() != null && o.getOrderNo().contains(orderNo)))
                .collect(Collectors.toList());

        filtered.sort((a, b) -> {
            if (a.getCreateTime() == null && b.getCreateTime() == null) return 0;
            if (a.getCreateTime() == null) return 1;
            if (b.getCreateTime() == null) return -1;
            return b.getCreateTime().compareTo(a.getCreateTime());
        });

        int start = (int) pageable.getOffset();
        int end = Math.min(start + pageable.getPageSize(), filtered.size());
        List<Order> pageContent = start < filtered.size() ? filtered.subList(start, end) : new ArrayList<>();

        List<OrderVO> voList = pageContent.stream()
                .map(o -> convertToVO(o, null, null))
                .collect(Collectors.toList());

        return new PageImpl<>(voList, pageable, filtered.size());
    }

    @Override
    public OrderVO getOrderById(String id) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new BusinessException("订单不存在"));

        List<OrderItem> items = orderItemRepository.findByOrderId(id);
        List<OrderLog> logs = orderLogRepository.findByOrderIdOrderByCreateTimeDesc(id);

        return convertToVO(order, items, logs);
    }

    @Override
    public OrderVO updateOrder(String id, OrderUpdateRequest request) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new BusinessException("订单不存在"));

        if (!"PENDING_APPROVAL".equals(order.getStatus())) {
            throw new BusinessException("仅待审核状态的订单可更新");
        }

        if (request.getRemark() != null) {
            order.setRemark(request.getRemark());
        }

        Order saved = orderRepository.save(order);
        List<OrderItem> items = orderItemRepository.findByOrderId(id);
        return convertToVO(saved, items, null);
    }

    @Override
    public void cancelOrder(String id, String userId) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new BusinessException("订单不存在"));

        if (!"PENDING_APPROVAL".equals(order.getStatus()) && !"APPROVED".equals(order.getStatus())) {
            throw new BusinessException("仅待审核或已审核的订单可取消");
        }

        order.setStatus("CANCELLED");
        orderRepository.save(order);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException("用户不存在"));
        saveLog(id, userId, user.getRealName(), "CANCEL", "取消订单");
    }

    @Override
    public OrderVO approveOrder(String id, OrderApproveRequest request, String userId) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new BusinessException("订单不存在"));

        if (!"PENDING_APPROVAL".equals(order.getStatus())) {
            throw new BusinessException("仅待审核状态的订单可审核");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException("用户不存在"));

        if (Boolean.TRUE.equals(request.getApproved())) {
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
                throw new BusinessException("璁㈠崟鐘舵€佸凡鍙樺寲锛岃鍒锋柊鍚庡啀鎿嶄綔");
            }
            order.setStatus("APPROVED");
            order.setApproveBy(userId);
            order.setApproveByName(user.getRealName());
            order.setApproveTime(approveTime);
            order.setApproveRemark(request.getRemark());
            saveLog(id, userId, user.getRealName(), "APPROVE", "审核通过" + (StringUtils.hasText(request.getRemark()) ? "：" + request.getRemark() : ""));
        } else {
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
                throw new BusinessException("璁㈠崟鐘舵€佸凡鍙樺寲锛岃鍒锋柊鍚庡啀鎿嶄綔");
            }
            order.setStatus("CANCELLED");
            order.setApproveBy(userId);
            order.setApproveByName(user.getRealName());
            order.setApproveTime(approveTime);
            order.setApproveRemark(request.getRemark());
            saveLog(id, userId, user.getRealName(), "REJECT", "审核拒绝" + (StringUtils.hasText(request.getRemark()) ? "：" + request.getRemark() : ""));
        }

        List<OrderItem> items = orderItemRepository.findByOrderId(id);
        return convertToVO(order, items, null);
    }

    @Override
    public List<OrderLog> getOrderLogs(String orderId) {
        return orderLogRepository.findByOrderIdOrderByCreateTimeDesc(orderId);
    }

    @Override
    public OrderVO shipOrder(String id, String userId) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new BusinessException("订单不存在"));

        if (!"APPROVED".equals(order.getStatus())) {
            throw new BusinessException("仅已审核的订单可发货");
        }

        List<OrderItem> items = orderItemRepository.findByOrderId(id);
        List<ShippingDeductionPlan> deductionPlans = buildShippingDeductionPlans(items);
        List<String> insufficientItems = deductionPlans.stream()
                .filter(plan -> plan.product == null || plan.availableQuantity < plan.requiredQuantity)
                .map(plan -> String.format("%s（需 %d，现有 %d）", plan.itemLabel, plan.requiredQuantity, plan.availableQuantity))
                .collect(Collectors.toList());
        if (!insufficientItems.isEmpty()) {
            throw new BusinessException("订单发货失败，以下商品库存不足：" + String.join("；", insufficientItems));
        }

        for (ShippingDeductionPlan plan : deductionPlans) {
            if (plan.requiredQuantity <= 0) {
                continue;
            }
            inventoryService.fifoDeductFinishedProduct(
                    plan.product.getId(),
                    plan.requiredQuantity,
                    "订单发货-" + order.getOrderNo() + " | 商品:" + plan.itemLabel
            );
        }

        Date shipTime = new Date();
        boolean shipped = mongoAtomicOpsService.transitionOrderStatus(
                id,
                "APPROVED",
                "SHIPPED",
                new Document("shipTime", shipTime)
        );
        if (!shipped) {
            throw new BusinessException("璁㈠崟鐘舵€佸凡鍙樺寲锛岃鍒锋柊鍚庡啀鎿嶄綔");
        }
        order.setStatus("SHIPPED");
        order.setShipTime(shipTime);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException("用户不存在"));
        saveLog(id, userId, user.getRealName(), "SHIP", "订单发货");

        return convertToVO(order, items, null);
    }

    @Override
    public OrderVO completeOrder(String id, String userId) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new BusinessException("订单不存在"));

        if (!"SHIPPED".equals(order.getStatus())) {
            throw new BusinessException("仅已发货的订单可完成");
        }

        Date completeTime = new Date();
        boolean completed = mongoAtomicOpsService.transitionOrderStatus(
                id,
                "SHIPPED",
                "COMPLETED",
                new Document("completeTime", completeTime)
        );
        if (!completed) {
            throw new BusinessException("璁㈠崟鐘舵€佸凡鍙樺寲锛岃鍒锋柊鍚庡啀鎿嶄綔");
        }
        order.setStatus("COMPLETED");
        order.setCompleteTime(completeTime);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException("用户不存在"));
        saveLog(id, userId, user.getRealName(), "COMPLETE", "订单完成");

        List<OrderItem> items = orderItemRepository.findByOrderId(id);
        archiveCompletedOrder(order, items);
        return convertToVO(order, items, null);
    }

    private void saveLog(String orderId, String operatorId, String operatorName, String action, String remark) {
        OrderLog log = new OrderLog();
        log.setOrderId(orderId);
        log.setOperator(operatorId);
        log.setOperatorName(operatorName);
        log.setAction(action);
        log.setRemark(remark);
        orderLogRepository.save(log);
    }

    private void archiveCompletedOrder(Order order, List<OrderItem> items) {
        if (salesRecordRepository.findByOrderId(order.getId()).isPresent()) {
            return;
        }

        List<OrderItem> safeItems = items != null ? items : new ArrayList<>();
        int totalQuantity = safeItems.stream()
                .mapToInt(item -> item.getQuantity() != null ? item.getQuantity() : 0)
                .sum();
        double totalAmount = order.getTotalAmount() != null
                ? order.getTotalAmount()
                : safeItems.stream()
                .mapToDouble(item -> item.getAmount() != null ? item.getAmount() : 0D)
                .sum();

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

        if (safeItems.size() == 1) {
            OrderItem firstItem = safeItems.get(0);
            salesRecord.setProductId(firstItem.getProductId());
            salesRecord.setProductCode(firstItem.getProductCode());
            salesRecord.setProductName(firstItem.getProductName());
            salesRecord.setQuantity(firstItem.getQuantity());
            salesRecord.setUnitPrice(firstItem.getUnitPrice());
            salesRecord.setAmount(firstItem.getAmount());
        } else {
            salesRecord.setQuantity(totalQuantity);
            salesRecord.setAmount(totalAmount);
            salesRecord.setProductName(safeItems.stream()
                    .map(OrderItem::getProductName)
                    .filter(StringUtils::hasText)
                    .distinct()
                    .collect(Collectors.joining("、")));
        }

        try {
            salesRecordRepository.save(salesRecord);
        } catch (DuplicateKeyException ex) {
            // Another request archived the order first; treat that as success.
        }
    }

    private OrderVO convertToVO(Order order, List<OrderItem> items, List<OrderLog> logs) {
        List<OrderItemDTO> itemDTOs = null;
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

        List<OrderVO.OrderLogVO> logVOs = null;
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

    private List<ShippingDeductionPlan> buildShippingDeductionPlans(List<OrderItem> items) {
        Map<String, ShippingDeductionPlan> plans = new LinkedHashMap<>();
        List<OrderItem> safeItems = items != null ? items : new ArrayList<>();

        for (OrderItem item : safeItems) {
            int requiredQuantity = item.getQuantity() != null ? item.getQuantity() : 0;
            if (requiredQuantity <= 0) {
                continue;
            }

            FinishedProduct matchedProduct = resolveFinishedProduct(item);
            String itemLabel = formatOrderItemLabel(item);
            String planKey = matchedProduct != null ? matchedProduct.getId() : "missing:" + itemLabel;

            ShippingDeductionPlan plan = plans.computeIfAbsent(planKey,
                    key -> new ShippingDeductionPlan(matchedProduct, itemLabel));
            plan.requiredQuantity += requiredQuantity;
        }

        plans.values().forEach(plan -> plan.availableQuantity = getAvailableFinishedProductQuantity(plan.product));
        return new ArrayList<>(plans.values());
    }

    private FinishedProduct resolveFinishedProduct(OrderItem item) {
        if (StringUtils.hasText(item.getProductId())) {
            return finishedProductRepository.findById(item.getProductId()).orElse(null);
        }

        return finishedProductRepository.findAll().stream()
                .filter(product -> sameText(product.getProductCode(), item.getProductCode()))
                .filter(product -> sameText(product.getName(), item.getProductName()))
                .filter(product -> sameText(product.getColor(), item.getColor()))
                .filter(product -> sameText(product.getSize(), item.getSize()))
                .findFirst()
                .orElse(null);
    }

    private int getAvailableFinishedProductQuantity(FinishedProduct product) {
        if (product == null) {
            return 0;
        }

        if (product.getLocations() != null && !product.getLocations().isEmpty()) {
            return product.getLocations().stream()
                    .mapToInt(location -> location.getQuantity() != null ? location.getQuantity() : 0)
                    .sum();
        }

        return product.getQuantity() != null ? product.getQuantity() : 0;
    }

    private String formatOrderItemLabel(OrderItem item) {
        StringBuilder label = new StringBuilder();
        label.append(item.getProductName() != null ? item.getProductName() : "未知商品");
        if (StringUtils.hasText(item.getProductCode())) {
            label.append("-").append(item.getProductCode());
        }
        label.append("/").append(StringUtils.hasText(item.getColor()) ? item.getColor() : "-");
        label.append("/").append(StringUtils.hasText(item.getSize()) ? item.getSize() : "-");
        return label.toString();
    }

    private boolean sameText(String left, String right) {
        return normalizeText(left).equals(normalizeText(right));
    }

    private String normalizeText(String value) {
        return StringUtils.hasText(value) ? value.trim() : "";
    }

    private static class ShippingDeductionPlan {
        private final FinishedProduct product;
        private final String itemLabel;
        private int requiredQuantity;
        private int availableQuantity;

        private ShippingDeductionPlan(FinishedProduct product, String itemLabel) {
            this.product = product;
            this.itemLabel = itemLabel;
        }
    }
}
