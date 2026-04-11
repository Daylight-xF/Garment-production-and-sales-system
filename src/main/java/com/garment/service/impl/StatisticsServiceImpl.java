package com.garment.service.impl;

import com.garment.dto.*;
import com.garment.model.*;
import com.garment.repository.*;
import com.garment.service.StatisticsService;
import org.springframework.stereotype.Service;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class StatisticsServiceImpl implements StatisticsService {

    private final ProductionPlanRepository productionPlanRepository;
    private final ProductionTaskRepository productionTaskRepository;
    private final RawMaterialRepository rawMaterialRepository;
    private final FinishedProductRepository finishedProductRepository;
    private final InventoryAlertRepository inventoryAlertRepository;
    private final SalesRecordRepository salesRecordRepository;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final CustomerRepository customerRepository;

    public StatisticsServiceImpl(ProductionPlanRepository productionPlanRepository,
                                  ProductionTaskRepository productionTaskRepository,
                                  RawMaterialRepository rawMaterialRepository,
                                  FinishedProductRepository finishedProductRepository,
                                  InventoryAlertRepository inventoryAlertRepository,
                                  SalesRecordRepository salesRecordRepository,
                                  OrderRepository orderRepository,
                                  OrderItemRepository orderItemRepository,
                                  CustomerRepository customerRepository) {
        this.productionPlanRepository = productionPlanRepository;
        this.productionTaskRepository = productionTaskRepository;
        this.rawMaterialRepository = rawMaterialRepository;
        this.finishedProductRepository = finishedProductRepository;
        this.inventoryAlertRepository = inventoryAlertRepository;
        this.salesRecordRepository = salesRecordRepository;
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.customerRepository = customerRepository;
    }

    @Override
    public ProductionOverviewVO getProductionOverview() {
        List<ProductionPlan> plans = productionPlanRepository.findAll();
        List<ProductionTask> tasks = productionTaskRepository.findAll();

        long totalPlans = plans.size();
        long completedPlans = plans.stream().filter(p -> "COMPLETED".equals(p.getStatus())).count();
        long inProgressPlans = plans.stream()
                .filter(p -> "APPROVED".equals(p.getStatus()) || "IN_PROGRESS".equals(p.getStatus()))
                .count();
        double planCompletionRate = totalPlans > 0 ? (double) completedPlans / totalPlans * 100 : 0;

        long totalTasks = tasks.size();
        long completedTasks = tasks.stream().filter(t -> "COMPLETED".equals(t.getStatus())).count();
        double taskCompletionRate = totalTasks > 0 ? (double) completedTasks / totalTasks * 100 : 0;

        return ProductionOverviewVO.builder()
                .totalPlans(totalPlans)
                .completedPlans(completedPlans)
                .inProgressPlans(inProgressPlans)
                .planCompletionRate(Math.round(planCompletionRate * 100.0) / 100.0)
                .totalTasks(totalTasks)
                .completedTasks(completedTasks)
                .taskCompletionRate(Math.round(taskCompletionRate * 100.0) / 100.0)
                .build();
    }

    @Override
    public List<PlanStatusDistributionVO> getPlanStatusDistribution() {
        List<ProductionPlan> plans = productionPlanRepository.findAll();

        Map<String, Long> statusCount = plans.stream()
                .collect(Collectors.groupingBy(
                        p -> p.getStatus() != null ? p.getStatus() : "UNKNOWN",
                        Collectors.counting()
                ));

        return statusCount.entrySet().stream()
                .map(entry -> PlanStatusDistributionVO.builder()
                        .status(entry.getKey())
                        .count(entry.getValue())
                        .build())
                .collect(Collectors.toList());
    }

    @Override
    public List<ProductProgressVO> getProductProgress() {
        List<ProductionPlan> plans = productionPlanRepository.findAll();

        Map<String, List<ProductionPlan>> productPlans = plans.stream()
                .filter(p -> p.getProductName() != null)
                .collect(Collectors.groupingBy(ProductionPlan::getProductName));

        return productPlans.entrySet().stream()
                .map(entry -> {
                    String productName = entry.getKey();
                    List<ProductionPlan> productPlanList = entry.getValue();
                    int plannedQuantity = productPlanList.stream()
                            .mapToInt(p -> p.getQuantity() != null ? p.getQuantity() : 0)
                            .sum();
                    int completedQuantity = productPlanList.stream()
                            .mapToInt(p -> p.getCompletedQuantity() != null ? p.getCompletedQuantity() : 0)
                            .sum();
                    double progress = plannedQuantity > 0
                            ? (double) completedQuantity / plannedQuantity * 100 : 0;

                    return ProductProgressVO.builder()
                            .productName(productName)
                            .plannedQuantity(plannedQuantity)
                            .completedQuantity(completedQuantity)
                            .progress(Math.round(progress * 100.0) / 100.0)
                            .build();
                })
                .sorted((a, b) -> Double.compare(b.getProgress(), a.getProgress()))
                .collect(Collectors.toList());
    }

    @Override
    public SalesOverviewVO getSalesOverview() {
        List<SalesRecord> allRecords = salesRecordRepository.findAll();
        List<Order> allOrders = orderRepository.findAll();
        List<Customer> allCustomers = customerRepository.findAll();

        double totalAmount = allRecords.stream()
                .mapToDouble(r -> r.getAmount() != null ? r.getAmount() : 0)
                .sum();

        Calendar cal = Calendar.getInstance();
        int currentYear = cal.get(Calendar.YEAR);
        int currentMonth = cal.get(Calendar.MONTH);

        double monthlyAmount = allRecords.stream()
                .filter(r -> {
                    if (r.getSaleDate() == null) return false;
                    Calendar saleCal = Calendar.getInstance();
                    saleCal.setTime(r.getSaleDate());
                    return saleCal.get(Calendar.YEAR) == currentYear
                            && saleCal.get(Calendar.MONTH) == currentMonth;
                })
                .mapToDouble(r -> r.getAmount() != null ? r.getAmount() : 0)
                .sum();

        long totalOrders = allOrders.size();

        long monthlyOrders = allOrders.stream()
                .filter(o -> {
                    if (o.getCreateTime() == null) return false;
                    Calendar orderCal = Calendar.getInstance();
                    orderCal.setTime(o.getCreateTime());
                    return orderCal.get(Calendar.YEAR) == currentYear
                            && orderCal.get(Calendar.MONTH) == currentMonth;
                })
                .count();

        double avgOrderAmount = totalOrders > 0 ? totalAmount / totalOrders : 0;

        return SalesOverviewVO.builder()
                .totalAmount(totalAmount)
                .monthlyAmount(monthlyAmount)
                .totalOrders(totalOrders)
                .monthlyOrders(monthlyOrders)
                .avgOrderAmount(avgOrderAmount)
                .customerCount((long) allCustomers.size())
                .build();
    }

    @Override
    public List<MonthlySalesVO> getMonthlySalesTrend(int months) {
        List<SalesRecord> allRecords = salesRecordRepository.findAll();
        List<Order> allOrders = orderRepository.findAll();

        SimpleDateFormat monthFormat = new SimpleDateFormat("yyyy-MM");
        Calendar cal = Calendar.getInstance();

        List<String> monthLabels = new ArrayList<>();
        for (int i = months - 1; i >= 0; i--) {
            cal.setTime(new Date());
            cal.add(Calendar.MONTH, -i);
            monthLabels.add(monthFormat.format(cal.getTime()));
        }

        Map<String, Double> monthAmountMap = new LinkedHashMap<>();
        Map<String, Long> monthOrderMap = new LinkedHashMap<>();
        for (String label : monthLabels) {
            monthAmountMap.put(label, 0.0);
            monthOrderMap.put(label, 0L);
        }

        for (SalesRecord record : allRecords) {
            if (record.getSaleDate() != null) {
                String monthKey = monthFormat.format(record.getSaleDate());
                if (monthAmountMap.containsKey(monthKey)) {
                    monthAmountMap.merge(monthKey, record.getAmount() != null ? record.getAmount() : 0, Double::sum);
                }
            }
        }

        for (Order order : allOrders) {
            if (order.getCreateTime() != null) {
                String monthKey = monthFormat.format(order.getCreateTime());
                if (monthOrderMap.containsKey(monthKey)) {
                    monthOrderMap.merge(monthKey, 1L, Long::sum);
                }
            }
        }

        return monthLabels.stream()
                .map(label -> MonthlySalesVO.builder()
                        .month(label)
                        .amount(monthAmountMap.getOrDefault(label, 0.0))
                        .orderCount(monthOrderMap.getOrDefault(label, 0L))
                        .build())
                .collect(Collectors.toList());
    }

    @Override
    public List<TopProductVO> getTopProducts(int limit) {
        List<SalesRecord> allRecords = salesRecordRepository.findAll();

        Map<String, TopProductVO> productMap = new LinkedHashMap<>();
        for (SalesRecord record : allRecords) {
            String productName = record.getProductName() != null ? record.getProductName() : "未知产品";
            TopProductVO vo = productMap.getOrDefault(productName,
                    TopProductVO.builder().productName(productName).quantity(0).amount(0.0).build());
            vo.setQuantity(vo.getQuantity() + (record.getQuantity() != null ? record.getQuantity() : 0));
            vo.setAmount(vo.getAmount() + (record.getAmount() != null ? record.getAmount() : 0));
            productMap.put(productName, vo);
        }

        return productMap.values().stream()
                .sorted((a, b) -> b.getQuantity() - a.getQuantity())
                .limit(limit)
                .collect(Collectors.toList());
    }

    @Override
    public InventoryOverviewVO getInventoryOverview() {
        List<RawMaterial> rawMaterials = rawMaterialRepository.findAll();
        List<FinishedProduct> finishedProducts = finishedProductRepository.findAll();
        List<InventoryAlert> alerts = inventoryAlertRepository.findAll();

        long rawMaterialTotalQuantity = rawMaterials.stream()
                .mapToLong(m -> m.getQuantity() != null ? m.getQuantity() : 0)
                .sum();
        long finishedProductTotalQuantity = finishedProducts.stream()
                .mapToLong(p -> p.getQuantity() != null ? p.getQuantity() : 0)
                .sum();
        long alertCount = alerts.stream()
                .filter(a -> "PENDING".equals(a.getStatus()))
                .count();

        return InventoryOverviewVO.builder()
                .rawMaterialCount((long) rawMaterials.size())
                .finishedProductCount((long) finishedProducts.size())
                .rawMaterialTotalQuantity(rawMaterialTotalQuantity)
                .finishedProductTotalQuantity(finishedProductTotalQuantity)
                .alertCount(alertCount)
                .build();
    }

    @Override
    public List<CategoryDistributionVO> getRawMaterialDistribution() {
        List<RawMaterial> rawMaterials = rawMaterialRepository.findAll();

        Map<String, Long> categoryMap = rawMaterials.stream()
                .collect(Collectors.groupingBy(
                        m -> m.getCategory() != null ? m.getCategory() : "未分类",
                        Collectors.summingLong(m -> m.getQuantity() != null ? m.getQuantity() : 0)
                ));

        return categoryMap.entrySet().stream()
                .map(entry -> CategoryDistributionVO.builder()
                        .category(entry.getKey())
                        .quantity(entry.getValue())
                        .build())
                .collect(Collectors.toList());
    }

    @Override
    public List<CategoryDistributionVO> getFinishedProductDistribution() {
        List<FinishedProduct> finishedProducts = finishedProductRepository.findAll();

        Map<String, Long> categoryMap = finishedProducts.stream()
                .collect(Collectors.groupingBy(
                        p -> p.getCategory() != null ? p.getCategory() : "未分类",
                        Collectors.summingLong(p -> p.getQuantity() != null ? p.getQuantity() : 0)
                ));

        return categoryMap.entrySet().stream()
                .map(entry -> CategoryDistributionVO.builder()
                        .category(entry.getKey())
                        .quantity(entry.getValue())
                        .build())
                .collect(Collectors.toList());
    }

    @Override
    public AlertStatsVO getAlertStats() {
        List<InventoryAlert> alerts = inventoryAlertRepository.findAll();

        long pendingCount = alerts.stream().filter(a -> "PENDING".equals(a.getStatus())).count();
        long handledCount = alerts.stream().filter(a -> "HANDLED".equals(a.getStatus())).count();
        long total = pendingCount + handledCount;
        double handleRate = total > 0 ? (double) handledCount / total * 100 : 0;

        return AlertStatsVO.builder()
                .pendingCount(pendingCount)
                .handledCount(handledCount)
                .handleRate(Math.round(handleRate * 100.0) / 100.0)
                .build();
    }
}
