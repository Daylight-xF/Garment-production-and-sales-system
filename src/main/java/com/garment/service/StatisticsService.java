package com.garment.service;

import com.garment.dto.*;

import java.util.List;

public interface StatisticsService {

    ProductionOverviewVO getProductionOverview();

    List<PlanStatusDistributionVO> getPlanStatusDistribution();

    List<ProductProgressVO> getProductProgress();

    SalesOverviewVO getSalesOverview();

    List<MonthlySalesVO> getMonthlySalesTrend(int months);

    List<TopProductVO> getTopProducts(int limit);

    InventoryOverviewVO getInventoryOverview();

    List<CategoryDistributionVO> getRawMaterialDistribution();

    List<CategoryDistributionVO> getFinishedProductDistribution();

    AlertStatsVO getAlertStats();
}
