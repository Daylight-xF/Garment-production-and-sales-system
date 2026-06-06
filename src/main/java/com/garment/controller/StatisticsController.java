package com.garment.controller;

import com.garment.dto.*;
import com.garment.service.StatisticsService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 统计分析控制器。
 *
 * <p>提供生产、销售和库存相关统计看板接口，统一委托 StatisticsService 计算统计数据。</p>
 */
@RestController
@RequestMapping("/api/statistics")
public class StatisticsController {
    // 统计业务服务，负责生产、销售和库存统计数据计算。

    private final StatisticsService statisticsService;
    /**
     * 创建统计分析控制器。
     *
     * <p>通过构造器注入统计业务服务。</p>
     *
     * @param statisticsService 统计业务服务
     */

    public StatisticsController(StatisticsService statisticsService) {
        this.statisticsService = statisticsService;
    }


        /**
         * 获取生产概览统计数据
         * <p>
         * 接收接口请求参数，调用对应业务服务完成处理，并返回统一响应结果。
         * </p>
         *
         * @return 统一响应结果，包含生产概览数据
         */
        @GetMapping("/production/overview")
        @PreAuthorize("hasAuthority('STATS_PRODUCTION')")
        public Result<ProductionOverviewVO> getProductionOverview() {
            // 返回统一成功响应。
            return Result.success(statisticsService.getProductionOverview());
        }


        /**
         * 获取生产计划状态分布统计
         * <p>
         * 接收接口请求参数，调用对应业务服务完成处理，并返回统一响应结果。
         * </p>
         *
         * @return 统一响应结果，包含计划状态分布数据列表
         */
        @GetMapping("/production/plan-status-distribution")
        @PreAuthorize("hasAuthority('STATS_PRODUCTION')")
        public Result<List<PlanStatusDistributionVO>> getPlanStatusDistribution() {
            // 返回统一成功响应。
            return Result.success(statisticsService.getPlanStatusDistribution());
        }


        /**
         * 获取产品生产进度统计
         * <p>
         * 接收接口请求参数，调用对应业务服务完成处理，并返回统一响应结果。
         * </p>
         *
         * @return 统一响应结果，包含产品生产进度数据列表
         */
        @GetMapping("/production/product-progress")
        @PreAuthorize("hasAuthority('STATS_PRODUCTION')")
        public Result<List<ProductProgressVO>> getProductProgress() {
            // 返回统一成功响应。
            return Result.success(statisticsService.getProductProgress());
        }


        /**
         * 获取销售概览统计数据
         * <p>
         * 接收接口请求参数，调用对应业务服务完成处理，并返回统一响应结果。
         * </p>
         *
         * @return 统一响应结果，包含销售概览数据
         */
        @GetMapping("/sales/overview")
        @PreAuthorize("hasAuthority('STATS_SALES')")
        public Result<SalesOverviewVO> getSalesOverview() {
            // 返回统一成功响应。
            return Result.success(statisticsService.getSalesOverview());
        }


        /**
         * 获取月度销售趋势统计
         * <p>
         * 接收接口请求参数，调用对应业务服务完成处理，并返回统一响应结果。
         * </p>
         *
         * @param months 查询月数，默认12个月
         * @return 统一响应结果，包含月度销售趋势数据列表
         */
        @GetMapping("/sales/monthly-trend")
        @PreAuthorize("hasAuthority('STATS_SALES')")
        public Result<List<MonthlySalesVO>> getMonthlySalesTrend(@RequestParam(defaultValue = "12") int months) {
            // 返回统一成功响应。
            return Result.success(statisticsService.getMonthlySalesTrend(months));
        }


        /**
         * 获取热销产品排行榜
         * <p>
         * 接收接口请求参数，调用对应业务服务完成处理，并返回统一响应结果。
         * </p>
         *
         * @param limit 排行数量，默认10个
         * @return 统一响应结果，包含热销产品列表
         */
        @GetMapping("/sales/top-products")
        @PreAuthorize("hasAuthority('STATS_SALES')")
        public Result<List<TopProductVO>> getTopProducts(@RequestParam(defaultValue = "10") int limit) {
            // 返回统一成功响应。
            return Result.success(statisticsService.getTopProducts(limit));
        }
    

        /**
         * 获取库存概览统计数据
         * <p>
         * 接收接口请求参数，调用对应业务服务完成处理，并返回统一响应结果。
         * </p>
         *
         * @return 统一响应结果，包含库存概览数据
         */
        @GetMapping("/inventory/overview")
        @PreAuthorize("hasAuthority('STATS_INVENTORY')")
        public Result<InventoryOverviewVO> getInventoryOverview() {
            // 返回统一成功响应。
            return Result.success(statisticsService.getInventoryOverview());
        }


        /**
         * 获取原材料分类分布统计
         * <p>
         * 接收接口请求参数，调用对应业务服务完成处理，并返回统一响应结果。
         * </p>
         *
         * @return 统一响应结果，包含原材料分类分布数据列表
         */
        @GetMapping("/inventory/raw-material-distribution")
        @PreAuthorize("hasAuthority('STATS_INVENTORY')")
        public Result<List<CategoryDistributionVO>> getRawMaterialDistribution() {
            // 返回统一成功响应。
            return Result.success(statisticsService.getRawMaterialDistribution());
        }


        /**
         * 获取成品分类分布统计
         * <p>
         * 接收接口请求参数，调用对应业务服务完成处理，并返回统一响应结果。
         * </p>
         *
         * @return 统一响应结果，包含成品分类分布数据列表
         */
        @GetMapping("/inventory/finished-product-distribution")
        @PreAuthorize("hasAuthority('STATS_INVENTORY')")
        public Result<List<CategoryDistributionVO>> getFinishedProductDistribution() {
            // 返回统一成功响应。
            return Result.success(statisticsService.getFinishedProductDistribution());
        }


        /**
         * 获取库存预警统计数据
         * <p>
         * 接收接口请求参数，调用对应业务服务完成处理，并返回统一响应结果。
         * </p>
         *
         * @return 统一响应结果，包含库存预警统计信息
         */
        @GetMapping("/inventory/alert-stats")
        @PreAuthorize("hasAuthority('STATS_INVENTORY')")
        public Result<AlertStatsVO> getAlertStats() {
            // 返回统一成功响应。
            return Result.success(statisticsService.getAlertStats());
        }

    
}
