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

@RestController
@RequestMapping("/api/production/plans")
public class ProductionPlanController {

    private final ProductionPlanService productionPlanService;

    public ProductionPlanController(ProductionPlanService productionPlanService) {
        this.productionPlanService = productionPlanService;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('PLAN_CREATE')")
    public Result<PlanVO> createPlan(@Valid @RequestBody PlanCreateRequest request,
                                      Authentication authentication) {
        String userId = (String) authentication.getPrincipal();
        PlanVO planVO = productionPlanService.createPlan(request, userId);
        return Result.success(planVO);
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('PLAN_READ', 'INVENTORY_IN')")
    public Result<Map<String, Object>> getPlanList(
            @RequestParam(defaultValue = "") String keyword,
            @RequestParam(defaultValue = "") String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        Pageable pageable = PageRequest.of(page - 1, size, Sort.by(Sort.Direction.DESC, "createTime"));
        Page<PlanVO> planPage = productionPlanService.getPlanList(keyword, status, pageable);

        Map<String, Object> result = new HashMap<>();
        result.put("list", planPage.getContent());
        result.put("total", planPage.getTotalElements());
        result.put("page", page);
        result.put("size", size);

        return Result.success(result);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('PLAN_READ')")
    public Result<PlanVO> getPlanById(@PathVariable String id) {
        PlanVO planVO = productionPlanService.getPlanById(id);
        return Result.success(planVO);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('PLAN_UPDATE')")
    public Result<PlanVO> updatePlan(@PathVariable String id, @RequestBody PlanUpdateRequest request) {
        PlanVO planVO = productionPlanService.updatePlan(id, request);
        return Result.success(planVO);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('PLAN_DELETE')")
    public Result<Void> deletePlan(@PathVariable String id) {
        productionPlanService.deletePlan(id);
        return Result.success();
    }

    @PutMapping("/{id}/approve")
    @PreAuthorize("hasAuthority('PLAN_APPROVE')")
    public Result<PlanVO> approvePlan(@PathVariable String id, @RequestBody Map<String, String> body) {
        String status = body.get("status");
        PlanVO planVO = productionPlanService.approvePlan(id, status);
        return Result.success(planVO);
    }

    @PostMapping("/{id}/start")
    @PreAuthorize("hasAuthority('PLAN_UPDATE')")
    public Result<PlanVO> startProduction(@PathVariable String id,
                                           Authentication authentication) {
        String userId = (String) authentication.getPrincipal();
        PlanVO planVO = productionPlanService.startProduction(id, userId);
        return Result.success(planVO);
    }

    @PostMapping("/{id}/complete")
    @PreAuthorize("hasAuthority('PLAN_UPDATE')")
    public Result<PlanVO> completePlan(@PathVariable String id) {
        PlanVO planVO = productionPlanService.completePlan(id);
        return Result.success(planVO);
    }

    @GetMapping("/{id}/tasks")
    @PreAuthorize("hasAuthority('PLAN_READ')")
    public Result<List<TaskVO>> getPlanTasks(@PathVariable String id) {
        List<TaskVO> tasks = productionPlanService.getTasksByPlanId(id);
        return Result.success(tasks);
    }
}
