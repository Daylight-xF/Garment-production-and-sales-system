package com.garment.controller;

import com.garment.dto.Result;
import com.garment.dto.TaskCreateRequest;
import com.garment.dto.TaskUpdateRequest;
import com.garment.dto.TaskVO;
import com.garment.service.ProductionTaskService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.HashMap;
import java.util.Map;

/**
 * 生产任务控制器。
 *
 * <p>提供生产任务创建、查询、更新、分配、进度维护和产品信息迁移等接口。</p>
 */
@RestController
@RequestMapping("/api/production/tasks")
public class ProductionTaskController {
    // 生产任务业务服务，负责生产任务相关业务处理。

    private final ProductionTaskService productionTaskService;
    /**
     * 创建生产任务控制器。
     *
     * <p>通过构造器注入生产任务业务服务。</p>
     *
     * @param productionTaskService 生产任务业务服务
     */

    public ProductionTaskController(ProductionTaskService productionTaskService) {
        this.productionTaskService = productionTaskService;
    }

        /**
         * 创建生产任务
         * <p>
         * 接收接口请求参数，调用对应业务服务完成处理，并返回统一响应结果。
         * </p>
         *
         * @param request 生产任务创建请求参数
         * @param authentication 认证信息，用于获取用户ID
         * @return 统一响应结果，包含创建后的生产任务信息
         */
        @PostMapping
        @PreAuthorize("hasAuthority('PLAN_CREATE')")
        public Result<TaskVO> createTask(@Valid @RequestBody TaskCreateRequest request,
                                          Authentication authentication) {
            // 从认证上下文中获取当前用户 ID。
            String userId = (String) authentication.getPrincipal();
            // 调用业务服务处理请求。
            TaskVO taskVO = productionTaskService.createTask(request, userId);
            // 返回统一成功响应。
            return Result.success(taskVO);
        }


        /**
         * 查询生产任务列表，支持分页和多条件筛选
         * <p>
         * 接收接口请求参数，调用对应业务服务完成处理，并返回统一响应结果。
         * </p>
         *
         * @param planId 生产计划ID
         * @param assignee 负责人
         * @param status 任务状态
         * @param page 页码，从1开始
         * @param size 每页大小
         * @return 统一响应结果，包含任务列表、总数、页码和每页大小
         */
        @GetMapping
        @PreAuthorize("hasAuthority('PLAN_READ')")
        public Result<Map<String, Object>> getTaskList(
                @RequestParam(defaultValue = "") String planId,
                @RequestParam(defaultValue = "") String assignee,
                @RequestParam(defaultValue = "") String status,
                @RequestParam(defaultValue = "1") int page,
                @RequestParam(defaultValue = "10") int size) {
            // 构建分页和排序参数。
            Pageable pageable = PageRequest.of(page - 1, size, Sort.by(Sort.Direction.DESC, "createTime"));
            // 查询分页数据。
            Page<TaskVO> taskPage = productionTaskService.getTaskList(planId, assignee, status, pageable);

            // 组装分页返回结构。
            Map<String, Object> result = new HashMap<>();
            result.put("list", taskPage.getContent());
            result.put("total", taskPage.getTotalElements());
            result.put("page", page);
            result.put("size", size);

            // 返回分页查询结果。
            return Result.success(result);
        }


        /**
         * 根据ID查询生产任务详情
         * <p>
         * 接收接口请求参数，调用对应业务服务完成处理，并返回统一响应结果。
         * </p>
         *
         * @param id 生产任务ID
         * @return 统一响应结果，包含生产任务详细信息
         */
        @GetMapping("/{id}")
        @PreAuthorize("hasAuthority('PLAN_READ')")
        public Result<TaskVO> getTaskById(@PathVariable String id) {
            // 调用业务服务处理请求。
            TaskVO taskVO = productionTaskService.getTaskById(id);
            // 返回统一成功响应。
            return Result.success(taskVO);
        }


        /**
         * 更新生产任务
         * <p>
         * 接收接口请求参数，调用对应业务服务完成处理，并返回统一响应结果。
         * </p>
         *
         * @param id 生产任务ID
         * @param request 生产任务更新请求参数
         * @return 统一响应结果，包含更新后的生产任务信息
         */
        @PutMapping("/{id}")
        @PreAuthorize("hasAuthority('TASK_UPDATE')")
        public Result<TaskVO> updateTask(@PathVariable String id, @RequestBody TaskUpdateRequest request) {
            // 调用业务服务处理请求。
            TaskVO taskVO = productionTaskService.updateTask(id, request);
            // 返回统一成功响应。
            return Result.success(taskVO);
        }


        /**
         * 分配任务给指定负责人
         * <p>
         * 接收接口请求参数，调用对应业务服务完成处理，并返回统一响应结果。
         * </p>
         *
         * @param id 生产任务ID
         * @param body 请求体，包含负责人信息
         * @return 统一响应结果，包含分配后的生产任务信息
         */
        @PutMapping("/{id}/assign")
        @PreAuthorize("hasAuthority('TASK_ASSIGN')")
        public Result<TaskVO> assignTask(@PathVariable String id, @RequestBody Map<String, String> body) {
            // 从请求体中读取业务参数。
            String assignee = body.get("assignee");
            // 调用业务服务处理请求。
            TaskVO taskVO = productionTaskService.assignTask(id, assignee);
            // 返回统一成功响应。
            return Result.success(taskVO);
        }


        /**
         * 更新任务进度
         * <p>
         * 接收接口请求参数，调用对应业务服务完成处理，并返回统一响应结果。
         * </p>
         *
         * @param id 生产任务ID
         * @param body 请求体，包含进度值（0-100）
         * @return 统一响应结果，包含更新后的生产任务信息
         */
        @PutMapping("/{id}/progress")
        @PreAuthorize("hasAuthority('TASK_UPDATE')")
        public Result<TaskVO> updateProgress(@PathVariable String id, @RequestBody Map<String, Integer> body) {
            // 从请求体中读取业务参数。
            Integer progress = body.get("progress");
            // 调用业务服务处理请求。
            TaskVO taskVO = productionTaskService.updateProgress(id, progress);
            // 返回统一成功响应。
            return Result.success(taskVO);
        }


        /**
         * 批量迁移任务的产品信息，从产品定义中同步产品名称、规格和分类到所有任务
         * <p>
         * 接收接口请求参数，调用对应业务服务完成处理，并返回统一响应结果。
         * </p>
         *
         * @return 统一响应结果，包含迁移成功的任务数量
         */
        @PostMapping("/migrate-product-info")
        public Result<String> migrateProductInfo() {
            // 调用业务服务处理请求。
            int count = productionTaskService.migrateProductInfoForAllTasks();
            // 返回统一成功响应。
            return Result.success("成功迁移 " + count + " 条任务的产品信息");
        }

    
}
