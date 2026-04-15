package com.garment.service.impl;

import com.garment.dto.*;
import com.garment.exception.BusinessException;
import com.garment.model.Order;
import com.garment.model.OrderItem;
import com.garment.model.OrderLog;
import com.garment.model.SalesRecord;
import com.garment.model.User;
import com.garment.repository.OrderItemRepository;
import com.garment.repository.OrderLogRepository;
import com.garment.repository.OrderRepository;
import com.garment.repository.SalesRecordRepository;
import com.garment.repository.UserRepository;
import com.garment.service.OrderService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class OrderServiceImpl implements OrderService {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final OrderLogRepository orderLogRepository;
    private final UserRepository userRepository;
    private final SalesRecordRepository salesRecordRepository;

    public OrderServiceImpl(OrderRepository orderRepository,
                            OrderItemRepository orderItemRepository,
                            OrderLogRepository orderLogRepository,
                            UserRepository userRepository,
                            SalesRecordRepository salesRecordRepository) {
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.orderLogRepository = orderLogRepository;
        this.userRepository = userRepository;
        this.salesRecordRepository = salesRecordRepository;
    }

    @Override
    public OrderVO createOrder(OrderCreateRequest request, String userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException("用户不存在"));

        Order order = new Order();
        order.setOrderNo(generateOrderNo());
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
            order.setStatus("APPROVED");
            order.setApproveBy(userId);
            order.setApproveByName(user.getRealName());
            order.setApproveTime(new Date());
            order.setApproveRemark(request.getRemark());
            orderRepository.save(order);
            saveLog(id, userId, user.getRealName(), "APPROVE", "审核通过" + (StringUtils.hasText(request.getRemark()) ? "：" + request.getRemark() : ""));
        } else {
            order.setStatus("CANCELLED");
            order.setApproveBy(userId);
            order.setApproveByName(user.getRealName());
            order.setApproveTime(new Date());
            order.setApproveRemark(request.getRemark());
            orderRepository.save(order);
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

        order.setStatus("SHIPPED");
        order.setShipTime(new Date());
        orderRepository.save(order);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException("用户不存在"));
        saveLog(id, userId, user.getRealName(), "SHIP", "订单发货");

        List<OrderItem> items = orderItemRepository.findByOrderId(id);
        return convertToVO(order, items, null);
    }

    @Override
    public OrderVO completeOrder(String id, String userId) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new BusinessException("订单不存在"));

        if (!"SHIPPED".equals(order.getStatus())) {
            throw new BusinessException("仅已发货的订单可完成");
        }

        order.setStatus("COMPLETED");
        order.setCompleteTime(new Date());
        orderRepository.save(order);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException("用户不存在"));
        saveLog(id, userId, user.getRealName(), "COMPLETE", "订单完成");

        List<OrderItem> items = orderItemRepository.findByOrderId(id);
        archiveCompletedOrder(order, items);
        return convertToVO(order, items, null);
    }

    private String generateOrderNo() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd");
        String dateStr = sdf.format(new Date());
        String prefix = "ORD" + dateStr;
        long count = orderRepository.countByOrderNoStartingWith(prefix);
        return prefix + String.format("%03d", count + 1);
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

        salesRecordRepository.save(salesRecord);
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
}
