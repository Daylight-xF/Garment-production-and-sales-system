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

@RestController
@RequestMapping("/api/production/tasks")
public class ProductionTaskController {

    private final ProductionTaskService productionTaskService;

    public ProductionTaskController(ProductionTaskService productionTaskService) {
        this.productionTaskService = productionTaskService;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('PLAN_CREATE')")
    public Result<TaskVO> createTask(@Valid @RequestBody TaskCreateRequest request,
                                      Authentication authentication) {
        String userId = (String) authentication.getPrincipal();
        TaskVO taskVO = productionTaskService.createTask(request, userId);
        return Result.success(taskVO);
    }

    @GetMapping
    @PreAuthorize("hasAuthority('PLAN_READ')")
    public Result<Map<String, Object>> getTaskList(
            @RequestParam(defaultValue = "") String planId,
            @RequestParam(defaultValue = "") String assignee,
            @RequestParam(defaultValue = "") String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        Pageable pageable = PageRequest.of(page - 1, size, Sort.by(Sort.Direction.DESC, "createTime"));
        Page<TaskVO> taskPage = productionTaskService.getTaskList(planId, assignee, status, pageable);

        Map<String, Object> result = new HashMap<>();
        result.put("list", taskPage.getContent());
        result.put("total", taskPage.getTotalElements());
        result.put("page", page);
        result.put("size", size);

        return Result.success(result);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('PLAN_READ')")
    public Result<TaskVO> getTaskById(@PathVariable String id) {
        TaskVO taskVO = productionTaskService.getTaskById(id);
        return Result.success(taskVO);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('TASK_UPDATE')")
    public Result<TaskVO> updateTask(@PathVariable String id, @RequestBody TaskUpdateRequest request) {
        TaskVO taskVO = productionTaskService.updateTask(id, request);
        return Result.success(taskVO);
    }

    @PutMapping("/{id}/assign")
    @PreAuthorize("hasAuthority('TASK_ASSIGN')")
    public Result<TaskVO> assignTask(@PathVariable String id, @RequestBody Map<String, String> body) {
        String assignee = body.get("assignee");
        TaskVO taskVO = productionTaskService.assignTask(id, assignee);
        return Result.success(taskVO);
    }

    @PutMapping("/{id}/progress")
    @PreAuthorize("hasAuthority('TASK_UPDATE')")
    public Result<TaskVO> updateProgress(@PathVariable String id, @RequestBody Map<String, Integer> body) {
        Integer progress = body.get("progress");
        TaskVO taskVO = productionTaskService.updateProgress(id, progress);
        return Result.success(taskVO);
    }

    @PostMapping("/migrate-product-info")
    public Result<String> migrateProductInfo() {
        int count = productionTaskService.migrateProductInfoForAllTasks();
        return Result.success("成功迁移 " + count + " 条任务的产品信息");
    }
}
