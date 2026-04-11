package com.garment.controller;

import com.garment.dto.*;
import com.garment.model.OrderLog;
import com.garment.service.OrderService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('ORDER_CREATE')")
    public Result<OrderVO> createOrder(@Valid @RequestBody OrderCreateRequest request,
                                       Authentication authentication) {
        String userId = (String) authentication.getPrincipal();
        OrderVO vo = orderService.createOrder(request, userId);
        return Result.success(vo);
    }

    @GetMapping
    @PreAuthorize("hasAuthority('ORDER_READ')")
    public Result<Map<String, Object>> getOrderList(
            @RequestParam(defaultValue = "") String status,
            @RequestParam(defaultValue = "") String customerName,
            @RequestParam(defaultValue = "") String orderNo,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        Pageable pageable = PageRequest.of(page - 1, size, Sort.by(Sort.Direction.DESC, "createTime"));
        Page<OrderVO> orderPage = orderService.getOrderList(status, customerName, orderNo, pageable);

        Map<String, Object> result = new HashMap<>();
        result.put("list", orderPage.getContent());
        result.put("total", orderPage.getTotalElements());
        result.put("page", page);
        result.put("size", size);

        return Result.success(result);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('ORDER_READ')")
    public Result<OrderVO> getOrderById(@PathVariable String id) {
        OrderVO vo = orderService.getOrderById(id);
        return Result.success(vo);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('ORDER_UPDATE')")
    public Result<OrderVO> updateOrder(@PathVariable String id,
                                       @RequestBody OrderUpdateRequest request) {
        OrderVO vo = orderService.updateOrder(id, request);
        return Result.success(vo);
    }

    @PutMapping("/{id}/cancel")
    @PreAuthorize("hasAuthority('ORDER_CANCEL')")
    public Result<Void> cancelOrder(@PathVariable String id,
                                    Authentication authentication) {
        String userId = (String) authentication.getPrincipal();
        orderService.cancelOrder(id, userId);
        return Result.success();
    }

    @PutMapping("/{id}/approve")
    @PreAuthorize("hasAuthority('ORDER_APPROVE')")
    public Result<OrderVO> approveOrder(@PathVariable String id,
                                        @Valid @RequestBody OrderApproveRequest request,
                                        Authentication authentication) {
        String userId = (String) authentication.getPrincipal();
        OrderVO vo = orderService.approveOrder(id, request, userId);
        return Result.success(vo);
    }

    @PutMapping("/{id}/reject")
    @PreAuthorize("hasAuthority('ORDER_APPROVE')")
    public Result<OrderVO> rejectOrder(@PathVariable String id,
                                       @Valid @RequestBody OrderApproveRequest request,
                                       Authentication authentication) {
        String userId = (String) authentication.getPrincipal();
        OrderApproveRequest rejectRequest = new OrderApproveRequest();
        rejectRequest.setApproved(false);
        rejectRequest.setRemark(request.getRemark());
        OrderVO vo = orderService.approveOrder(id, rejectRequest, userId);
        return Result.success(vo);
    }

    @GetMapping("/{id}/logs")
    @PreAuthorize("hasAuthority('ORDER_READ')")
    public Result<List<OrderLog>> getOrderLogs(@PathVariable String id) {
        List<OrderLog> logs = orderService.getOrderLogs(id);
        return Result.success(logs);
    }

    @PutMapping("/{id}/ship")
    @PreAuthorize("hasAuthority('ORDER_UPDATE')")
    public Result<OrderVO> shipOrder(@PathVariable String id,
                                     Authentication authentication) {
        String userId = (String) authentication.getPrincipal();
        OrderVO vo = orderService.shipOrder(id, userId);
        return Result.success(vo);
    }

    @PutMapping("/{id}/complete")
    @PreAuthorize("hasAuthority('ORDER_UPDATE')")
    public Result<OrderVO> completeOrder(@PathVariable String id,
                                         Authentication authentication) {
        String userId = (String) authentication.getPrincipal();
        OrderVO vo = orderService.completeOrder(id, userId);
        return Result.success(vo);
    }
}
