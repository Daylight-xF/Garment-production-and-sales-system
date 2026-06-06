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

/**
 * 订单管理控制器。
 *
 * <p>提供订单创建、查询、更新、取消、审核、驳回、发货、完成和日志查询等接口。</p>
 */
@RestController
@RequestMapping("/api/orders")
public class OrderController {
    // 订单业务服务，负责订单生命周期相关业务处理。

    private final OrderService orderService;
    /**
     * 创建订单管理控制器。
     *
     * <p>通过构造器注入订单业务服务。</p>
     *
     * @param orderService 订单业务服务
     */

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

        /**
         * 创建订单
         * <p>
         * 接收接口请求参数，调用对应业务服务完成处理，并返回统一响应结果。
         * </p>
         *
         * @param request 订单创建请求参数
         * @param authentication 认证信息，用于获取用户ID
         * @return 统一响应结果，包含创建后的订单信息
         */
        @PostMapping
        @PreAuthorize("hasAuthority('ORDER_CREATE')")
        public Result<OrderVO> createOrder(@Valid @RequestBody OrderCreateRequest request,
                                           Authentication authentication) {
            // 从认证上下文中获取当前用户 ID。
            String userId = (String) authentication.getPrincipal();
            // 调用业务服务处理请求。
            OrderVO vo = orderService.createOrder(request, userId);
            // 返回统一成功响应。
            return Result.success(vo);
        }


        /**
         * 查询订单列表，支持分页和多条件筛选
         * <p>
         * 接收接口请求参数，调用对应业务服务完成处理，并返回统一响应结果。
         * </p>
         *
         * @param status 订单状态
         * @param customerName 客户名称，支持模糊查询
         * @param orderNo 订单编号，支持模糊查询
         * @param page 页码，从1开始
         * @param size 每页大小
         * @return 统一响应结果，包含订单列表、总数、页码和每页大小
         */
        @GetMapping
        @PreAuthorize("hasAuthority('ORDER_READ')")
        public Result<Map<String, Object>> getOrderList(
                @RequestParam(defaultValue = "") String status,
                @RequestParam(defaultValue = "") String customerName,
                @RequestParam(defaultValue = "") String orderNo,
                @RequestParam(defaultValue = "1") int page,
                @RequestParam(defaultValue = "10") int size) {
            // 构建分页和排序参数。
            Pageable pageable = PageRequest.of(page - 1, size, Sort.by(Sort.Direction.DESC, "createTime"));
            // 查询分页数据。
            Page<OrderVO> orderPage = orderService.getOrderList(status, customerName, orderNo, pageable);

            // 组装分页返回结构。
            Map<String, Object> result = new HashMap<>();
            result.put("list", orderPage.getContent());
            result.put("total", orderPage.getTotalElements());
            result.put("page", page);
            result.put("size", size);

            // 返回分页查询结果。
            return Result.success(result);
        }


        /**
         * 根据ID查询订单详情
         * <p>
         * 接收接口请求参数，调用对应业务服务完成处理，并返回统一响应结果。
         * </p>
         *
         * @param id 订单ID
         * @return 统一响应结果，包含订单详细信息
         */
        @GetMapping("/{id}")
        @PreAuthorize("hasAuthority('ORDER_READ')")
        public Result<OrderVO> getOrderById(@PathVariable String id) {
            // 调用业务服务处理请求。
            OrderVO vo = orderService.getOrderById(id);
            // 返回统一成功响应。
            return Result.success(vo);
        }


        /**
         * 更新订单信息
         * <p>
         * 接收接口请求参数，调用对应业务服务完成处理，并返回统一响应结果。
         * </p>
         *
         * @param id 订单ID
         * @param request 订单更新请求参数
         * @return 统一响应结果，包含更新后的订单信息
         */
        @PutMapping("/{id}")
        @PreAuthorize("hasAuthority('ORDER_UPDATE')")
        public Result<OrderVO> updateOrder(@PathVariable String id,
                                           @RequestBody OrderUpdateRequest request) {
            // 调用业务服务处理请求。
            OrderVO vo = orderService.updateOrder(id, request);
            // 返回统一成功响应。
            return Result.success(vo);
        }


        /**
         * 取消订单
         * <p>
         * 接收接口请求参数，调用对应业务服务完成处理，并返回统一响应结果。
         * </p>
         *
         * @param id 订单ID
         * @param authentication 认证信息，用于获取用户ID
         * @return 统一响应结果
         */
        @PutMapping("/{id}/cancel")
        @PreAuthorize("hasAuthority('ORDER_CANCEL')")
        public Result<Void> cancelOrder(@PathVariable String id,
                                        Authentication authentication) {
            // 从认证上下文中获取当前用户 ID。
            String userId = (String) authentication.getPrincipal();
            orderService.cancelOrder(id, userId);
            // 返回统一成功响应。
            return Result.success();
        }


        /**
         * 审核订单
         * <p>
         * 接收接口请求参数，调用对应业务服务完成处理，并返回统一响应结果。
         * </p>
         *
         * @param id 订单ID
         * @param request 订单审核请求参数
         * @param authentication 认证信息，用于获取用户ID
         * @return 统一响应结果，包含审核后的订单信息
         */
        @PutMapping("/{id}/approve")
        @PreAuthorize("hasAuthority('ORDER_APPROVE')")
        public Result<OrderVO> approveOrder(@PathVariable String id,
                                            @Valid @RequestBody OrderApproveRequest request,
                                            Authentication authentication) {
            // 从认证上下文中获取当前用户 ID。
            String userId = (String) authentication.getPrincipal();
            // 调用业务服务处理请求。
            OrderVO vo = orderService.approveOrder(id, request, userId);
            // 返回统一成功响应。
            return Result.success(vo);
        }


        /**
         * 驳回订单
         * <p>
         * 接收接口请求参数，调用对应业务服务完成处理，并返回统一响应结果。
         * </p>
         *
         * @param id 订单ID
         * @param request 订单审核请求参数，包含驳回原因
         * @param authentication 认证信息，用于获取用户ID
         * @return 统一响应结果，包含驳回后的订单信息
         */
        @PutMapping("/{id}/reject")
        @PreAuthorize("hasAuthority('ORDER_APPROVE')")
        public Result<OrderVO> rejectOrder(@PathVariable String id,
                                           @Valid @RequestBody OrderApproveRequest request,
                                           Authentication authentication) {
            // 从认证上下文中获取当前用户 ID。
            String userId = (String) authentication.getPrincipal();
            OrderApproveRequest rejectRequest = new OrderApproveRequest();
            rejectRequest.setApproved(false);
            rejectRequest.setRemark(request.getRemark());
            // 调用业务服务处理请求。
            OrderVO vo = orderService.approveOrder(id, rejectRequest, userId);
            // 返回统一成功响应。
            return Result.success(vo);
        }


        /**
         * 查询订单操作日志
         * <p>
         * 接收接口请求参数，调用对应业务服务完成处理，并返回统一响应结果。
         * </p>
         *
         * @param id 订单ID
         * @return 统一响应结果，包含订单操作日志列表
         */
        @GetMapping("/{id}/logs")
        @PreAuthorize("hasAuthority('ORDER_READ')")
        public Result<List<OrderLog>> getOrderLogs(@PathVariable String id) {
            // 调用业务服务处理请求。
            List<OrderLog> logs = orderService.getOrderLogs(id);
            // 返回统一成功响应。
            return Result.success(logs);
        }


        /**
         * 发货订单
         * <p>
         * 接收接口请求参数，调用对应业务服务完成处理，并返回统一响应结果。
         * </p>
         *
         * @param id 订单ID
         * @param authentication 认证信息，用于获取用户ID
         * @return 统一响应结果，包含发货后的订单信息
         */
        @PutMapping("/{id}/ship")
        @PreAuthorize("hasAuthority('ORDER_UPDATE')")
        public Result<OrderVO> shipOrder(@PathVariable String id,
                                         Authentication authentication) {
            // 从认证上下文中获取当前用户 ID。
            String userId = (String) authentication.getPrincipal();
            // 调用业务服务处理请求。
            OrderVO vo = orderService.shipOrder(id, userId);
            // 返回统一成功响应。
            return Result.success(vo);
        }


        /**
         * 完成订单
         * <p>
         * 接收接口请求参数，调用对应业务服务完成处理，并返回统一响应结果。
         * </p>
         *
         * @param id 订单ID
         * @param authentication 认证信息，用于获取用户ID
         * @return 统一响应结果，包含完成后的订单信息
         */
        @PutMapping("/{id}/complete")
        @PreAuthorize("hasAuthority('ORDER_UPDATE')")
        public Result<OrderVO> completeOrder(@PathVariable String id,
                                             Authentication authentication) {
            // 从认证上下文中获取当前用户 ID。
            String userId = (String) authentication.getPrincipal();
            // 调用业务服务处理请求。
            OrderVO vo = orderService.completeOrder(id, userId);
            // 返回统一成功响应。
            return Result.success(vo);
        }

    
}
