package com.garment.service;

import com.garment.dto.OrderApproveRequest;
import com.garment.dto.OrderCreateRequest;
import com.garment.dto.OrderUpdateRequest;
import com.garment.dto.OrderVO;
import com.garment.model.OrderLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface OrderService {

    OrderVO createOrder(OrderCreateRequest request, String userId);

    Page<OrderVO> getOrderList(String status, String customerName, String orderNo, Pageable pageable);

    OrderVO getOrderById(String id);

    OrderVO updateOrder(String id, OrderUpdateRequest request);

    void cancelOrder(String id, String userId);

    OrderVO approveOrder(String id, OrderApproveRequest request, String userId);

    List<OrderLog> getOrderLogs(String orderId);

    OrderVO shipOrder(String id, String userId);

    OrderVO completeOrder(String id, String userId);
}
