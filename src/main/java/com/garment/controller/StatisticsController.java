package com.garment.controller;

import com.garment.dto.*;
import com.garment.service.StatisticsService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/statistics")
public class StatisticsController {

    private final StatisticsService statisticsService;

    public StatisticsController(StatisticsService statisticsService) {
        this.statisticsService = statisticsService;
    }

    @GetMapping("/production/overview")
    @PreAuthorize("hasAuthority('STATS_PRODUCTION')")
    public Result<ProductionOverviewVO> getProductionOverview() {
        return Result.success(statisticsService.getProductionOverview());
    }

    @GetMapping("/production/plan-status-distribution")
    @PreAuthorize("hasAuthority('STATS_PRODUCTION')")
    public Result<List<PlanStatusDistributionVO>> getPlanStatusDistribution() {
        return Result.success(statisticsService.getPlanStatusDistribution());
    }

    @GetMapping("/production/product-progress")
    @PreAuthorize("hasAuthority('STATS_PRODUCTION')")
    public Result<List<ProductProgressVO>> getProductProgress() {
        return Result.success(statisticsService.getProductProgress());
    }

    @GetMapping("/sales/overview")
    @PreAuthorize("hasAuthority('STATS_SALES')")
    public Result<SalesOverviewVO> getSalesOverview() {
        return Result.success(statisticsService.getSalesOverview());
    }

    @GetMapping("/sales/monthly-trend")
    @PreAuthorize("hasAuthority('STATS_SALES')")
    public Result<List<MonthlySalesVO>> getMonthlySalesTrend(@RequestParam(defaultValue = "12") int months) {
        return Result.success(statisticsService.getMonthlySalesTrend(months));
    }

    @GetMapping("/sales/top-products")
    @PreAuthorize("hasAuthority('STATS_SALES')")
    public Result<List<TopProductVO>> getTopProducts(@RequestParam(defaultValue = "10") int limit) {
        return Result.success(statisticsService.getTopProducts(limit));
    }

    @GetMapping("/inventory/overview")
    @PreAuthorize("hasAuthority('STATS_INVENTORY')")
    public Result<InventoryOverviewVO> getInventoryOverview() {
        return Result.success(statisticsService.getInventoryOverview());
    }

    @GetMapping("/inventory/raw-material-distribution")
    @PreAuthorize("hasAuthority('STATS_INVENTORY')")
    public Result<List<CategoryDistributionVO>> getRawMaterialDistribution() {
        return Result.success(statisticsService.getRawMaterialDistribution());
    }

    @GetMapping("/inventory/finished-product-distribution")
    @PreAuthorize("hasAuthority('STATS_INVENTORY')")
    public Result<List<CategoryDistributionVO>> getFinishedProductDistribution() {
        return Result.success(statisticsService.getFinishedProductDistribution());
    }

    @GetMapping("/inventory/alert-stats")
    @PreAuthorize("hasAuthority('STATS_INVENTORY')")
    public Result<AlertStatsVO> getAlertStats() {
        return Result.success(statisticsService.getAlertStats());
    }
}
