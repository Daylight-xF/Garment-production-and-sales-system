package com.garment.controller;

import com.garment.dto.PlanCreateRequest;
import com.garment.dto.PlanUpdateRequest;
import com.garment.dto.PlanVO;
import com.garment.dto.Result;
import com.garment.dto.TaskVO;
import com.garment.service.ProductionPlanService;
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
 * 生产计划控制器。
 *
 * <p>提供生产计划创建、查询、更新、删除、审核、开始生产、完成生产和任务查询等接口。</p>
 */
@RestController
@RequestMapping("/api/production/plans")
public class ProductionPlanController {
    // 生产计划业务服务，负责生产计划生命周期管理。

    private final ProductionPlanService productionPlanService;
    /**
     * 创建生产计划控制器。
     *
     * <p>通过构造器注入生产计划业务服务。</p>
     *
     * @param productionPlanService 生产计划业务服务
     */

    public ProductionPlanController(ProductionPlanService productionPlanService) {
        this.productionPlanService = productionPlanService;
    }

        /**
         * 创建生产计划
         * <p>
         * 接收接口请求参数，调用对应业务服务完成处理，并返回统一响应结果。
         * </p>
         *
         * @param request 生产计划创建请求参数
         * @param authentication 认证信息，用于获取用户ID
         * @return 统一响应结果，包含创建后的生产计划信息
         */
        @PostMapping
        @PreAuthorize("hasAuthority('PLAN_CREATE')")
        public Result<PlanVO> createPlan(@Valid @RequestBody PlanCreateRequest request,
                                          Authentication authentication) {
            // 从认证上下文中获取当前用户 ID。
            String userId = (String) authentication.getPrincipal();
            // 调用业务服务处理请求。
            PlanVO planVO = productionPlanService.createPlan(request, userId);
            // 返回统一成功响应。
            return Result.success(planVO);
        }


        /**
         * 查询生产计划列表，支持分页和筛选
         * <p>
         * 接收接口请求参数，调用对应业务服务完成处理，并返回统一响应结果。
         * </p>
         *
         * @param keyword 搜索关键词，支持模糊查询
         * @param status 计划状态
         * @param page 页码，从1开始
         * @param size 每页大小
         * @return 统一响应结果，包含生产计划列表、总数、页码和每页大小
         */
        @GetMapping
        @PreAuthorize("hasAnyAuthority('PLAN_READ', 'INVENTORY_IN')")
        public Result<Map<String, Object>> getPlanList(
                @RequestParam(defaultValue = "") String keyword,
                @RequestParam(defaultValue = "") String status,
                @RequestParam(defaultValue = "1") int page,
                @RequestParam(defaultValue = "10") int size) {
            // 构建分页和排序参数。
            Pageable pageable = PageRequest.of(page - 1, size, Sort.by(Sort.Direction.DESC, "createTime"));
            // 查询分页数据。
            Page<PlanVO> planPage = productionPlanService.getPlanList(keyword, status, pageable);

            // 组装分页返回结构。
            Map<String, Object> result = new HashMap<>();
            result.put("list", planPage.getContent());
            result.put("total", planPage.getTotalElements());
            result.put("page", page);
            result.put("size", size);

            // 返回分页查询结果。
            return Result.success(result);
        }


        /**
         * 根据ID查询生产计划详情
         * <p>
         * 接收接口请求参数，调用对应业务服务完成处理，并返回统一响应结果。
         * </p>
         *
         * @param id 生产计划ID
         * @return 统一响应结果，包含生产计划详细信息
         */
        @GetMapping("/{id}")
        @PreAuthorize("hasAuthority('PLAN_READ')")
        public Result<PlanVO> getPlanById(@PathVariable String id) {
            // 调用业务服务处理请求。
            PlanVO planVO = productionPlanService.getPlanById(id);
            // 返回统一成功响应。
            return Result.success(planVO);
        }


        /**
         * 更新生产计划
         * <p>
         * 接收接口请求参数，调用对应业务服务完成处理，并返回统一响应结果。
         * </p>
         *
         * @param id 生产计划ID
         * @param request 生产计划更新请求参数
         * @return 统一响应结果，包含更新后的生产计划信息
         */
        @PutMapping("/{id}")
        @PreAuthorize("hasAuthority('PLAN_UPDATE')")
        public Result<PlanVO> updatePlan(@PathVariable String id, @RequestBody PlanUpdateRequest request) {
            // 调用业务服务处理请求。
            PlanVO planVO = productionPlanService.updatePlan(id, request);
            // 返回统一成功响应。
            return Result.success(planVO);
        }


        /**
         * 删除生产计划
         * <p>
         * 接收接口请求参数，调用对应业务服务完成处理，并返回统一响应结果。
         * </p>
         *
         * @param id 生产计划ID
         * @return 统一响应结果
         */
        @DeleteMapping("/{id}")
        @PreAuthorize("hasAuthority('PLAN_DELETE')")
        public Result<Void> deletePlan(@PathVariable String id) {
            // 调用业务服务删除记录。
            productionPlanService.deletePlan(id);
            // 返回统一成功响应。
            return Result.success();
        }


        /**
         * 审核生产计划
         * <p>
         * 接收接口请求参数，调用对应业务服务完成处理，并返回统一响应结果。
         * </p>
         *
         * @param id 生产计划ID
         * @param body 请求体，包含审核状态
         * @return 统一响应结果，包含审核后的生产计划信息
         */
        @PutMapping("/{id}/approve")
        @PreAuthorize("hasAuthority('PLAN_APPROVE')")
        public Result<PlanVO> approvePlan(@PathVariable String id, @RequestBody Map<String, String> body) {
            // 从请求体中读取业务参数。
            String status = body.get("status");
            // 调用业务服务处理请求。
            PlanVO planVO = productionPlanService.approvePlan(id, status);
            // 返回统一成功响应。
            return Result.success(planVO);
        }


        /**
         * 开始生产，将计划状态更新为生产中
         * <p>
         * 接收接口请求参数，调用对应业务服务完成处理，并返回统一响应结果。
         * </p>
         *
         * @param id 生产计划ID
         * @param authentication 认证信息，用于获取用户ID
         * @return 统一响应结果，包含更新后的生产计划信息
         */
        @PostMapping("/{id}/start")
        @PreAuthorize("hasAuthority('PLAN_UPDATE')")
        public Result<PlanVO> startProduction(@PathVariable String id,
                                               Authentication authentication) {
            // 从认证上下文中获取当前用户 ID。
            String userId = (String) authentication.getPrincipal();
            // 调用业务服务处理请求。
            PlanVO planVO = productionPlanService.startProduction(id, userId);
            // 返回统一成功响应。
            return Result.success(planVO);
        }


        /**
         * 完成生产计划
         * <p>
         * 接收接口请求参数，调用对应业务服务完成处理，并返回统一响应结果。
         * </p>
         *
         * @param id 生产计划ID
         * @return 统一响应结果，包含完成后的生产计划信息
         */
        @PostMapping("/{id}/complete")
        @PreAuthorize("hasAuthority('PLAN_UPDATE')")
        public Result<PlanVO> completePlan(@PathVariable String id) {
            // 调用业务服务处理请求。
            PlanVO planVO = productionPlanService.completePlan(id);
            // 返回统一成功响应。
            return Result.success(planVO);
        }


        /**
         * 查询生产计划的任务列表
         * <p>
         * 接收接口请求参数，调用对应业务服务完成处理，并返回统一响应结果。
         * </p>
         *
         * @param id 生产计划ID
         * @return 统一响应结果，包含任务列表
         */
        @GetMapping("/{id}/tasks")
        @PreAuthorize("hasAuthority('PLAN_READ')")
        public Result<List<TaskVO>> getPlanTasks(@PathVariable String id) {
            // 调用业务服务处理请求。
            List<TaskVO> tasks = productionPlanService.getTasksByPlanId(id);
            // 返回统一成功响应。
            return Result.success(tasks);
        }

    
}
