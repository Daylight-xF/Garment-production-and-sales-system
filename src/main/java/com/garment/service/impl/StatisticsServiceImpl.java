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

    /**
     * 获取生产概览统计信息
     * <p>
     * 该方法用于统计系统的整体生产情况，包括生产计划和生产任务的完成情况。
     * 主要计算指标：
     * 1. 计划总数、已完成计划数、进行中计划数
     * 2. 计划完成率 = 已完成计划数 / 计划总数 × 100%
     * 3. 任务总数、已完成任务数
     * 4. 任务完成率 = 已完成任务数 / 任务总数 × 100%
     * </p>
     *
     * @return 生产概览视图对象，包含计划和任务的各项统计指标
     */
    @Override
    public ProductionOverviewVO getProductionOverview() {
        // 查询所有生产计划和生产任务
        List<ProductionPlan> plans = productionPlanRepository.findAll();
        List<ProductionTask> tasks = productionTaskRepository.findAll();

        // 统计计划相关指标
        long totalPlans = plans.size();
        long completedPlans = plans.stream().filter(p -> "COMPLETED".equals(p.getStatus())).count();
        long inProgressPlans = plans.stream()
                .filter(p -> "APPROVED".equals(p.getStatus()) || "IN_PROGRESS".equals(p.getStatus()))
                .count();
        double planCompletionRate = totalPlans > 0 ? (double) completedPlans / totalPlans * 100 : 0;

        // 统计任务相关指标
        long totalTasks = tasks.size();
        long completedTasks = tasks.stream().filter(t -> "COMPLETED".equals(t.getStatus())).count();
        double taskCompletionRate = totalTasks > 0 ? (double) completedTasks / totalTasks * 100 : 0;

        // 构建并返回生产概览视图对象
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

    /**
     * 获取计划状态分布统计
     * <p>
     * 该方法用于统计各个状态的生产计划数量分布情况。
     * 按状态分组统计计划数量，如待审批、已审批、进行中、已完成、已取消等。
     * </p>
     *
     * @return 计划状态分布视图对象列表，每个对象包含状态名称和对应的计划数量
     */
    @Override
    public List<PlanStatusDistributionVO> getPlanStatusDistribution() {
        List<ProductionPlan> plans = productionPlanRepository.findAll();

        // 按状态分组统计计划数量
        Map<String, Long> statusCount = plans.stream()
                .collect(Collectors.groupingBy(
                        p -> p.getStatus() != null ? p.getStatus() : "UNKNOWN",
                        Collectors.counting()
                ));

        // 转换为状态分布视图对象列表并返回
        return statusCount.entrySet().stream()
                .map(entry -> PlanStatusDistributionVO.builder()
                        .status(entry.getKey())
                        .count(entry.getValue())
                        .build())
                .collect(Collectors.toList());
    }

    /**
     * 获取产品生产进度统计
     * <p>
     * 该方法用于统计各产品的生产进度情况，按产品名称分组汇总计划数量和完成数量。
     * 主要功能包括：
     * 1. 按产品名称分组所有生产计划
     * 2. 计算每个产品的计划总量和完成总量
     * 3. 计算生产进度百分比 = 完成总量 / 计划总量 × 100%
     * 4. 按进度降序排序返回
     * </p>
     *
     * @return 产品生产进度视图对象列表，按进度降序排列
     */
    @Override
    public List<ProductProgressVO> getProductProgress() {
        List<ProductionPlan> plans = productionPlanRepository.findAll();

        // 按产品名称分组所有生产计划
        Map<String, List<ProductionPlan>> productPlans = plans.stream()
                .filter(p -> p.getProductName() != null)
                .collect(Collectors.groupingBy(ProductionPlan::getProductName));

        // 计算每个产品的生产进度并转换为视图对象
        return productPlans.entrySet().stream()
                .map(entry -> {
                    String productName = entry.getKey();
                    List<ProductionPlan> productPlanList = entry.getValue();
                    
                    // 累加计划数量和完成数量
                    int plannedQuantity = productPlanList.stream()
                            .mapToInt(p -> p.getQuantity() != null ? p.getQuantity() : 0)
                            .sum();
                    int completedQuantity = productPlanList.stream()
                            .mapToInt(p -> p.getCompletedQuantity() != null ? p.getCompletedQuantity() : 0)
                            .sum();
                    
                    // 计算进度百分比
                    double progress = plannedQuantity > 0
                            ? (double) completedQuantity / plannedQuantity * 100 : 0;

                    return ProductProgressVO.builder()
                            .productName(productName)
                            .plannedQuantity(plannedQuantity)
                            .completedQuantity(completedQuantity)
                            .progress(Math.round(progress * 100.0) / 100.0)
                            .build();
                })
                // 按进度降序排序
                .sorted((a, b) -> Double.compare(b.getProgress(), a.getProgress()))
                .collect(Collectors.toList());
    }

    /**
     * 获取销售概览统计信息
     * <p>
     * 该方法用于统计系统的整体销售情况，包括总销售额、本月销售额、订单统计和客户统计。
     * 主要计算指标：
     * 1. 销售总额：所有销售记录的金额总和
     * 2. 本月销售额：当前月份的銷售金额
     * 3. 订单总数和本月订单数
     * 4. 平均订单金额 = 销售总额 / 订单总数
     * 5. 客户总数
     * </p>
     *
     * @return 销售概览视图对象，包含销售和客户的各项统计指标
     */
    @Override
    public SalesOverviewVO getSalesOverview() {
        // 查询所有销售记录、订单和客户
        List<SalesRecord> allRecords = salesRecordRepository.findAll();
        List<Order> allOrders = orderRepository.findAll();
        List<Customer> allCustomers = customerRepository.findAll();

        // 计算销售总额
        double totalAmount = allRecords.stream()
                .mapToDouble(r -> r.getAmount() != null ? r.getAmount() : 0)
                .sum();

        // 获取当前年月用于筛选本月数据
        Calendar cal = Calendar.getInstance();
        int currentYear = cal.get(Calendar.YEAR);
        int currentMonth = cal.get(Calendar.MONTH);

        // 计算本月销售额
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

        // 统计订单总数和本月订单数
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

        // 计算平均订单金额
        double avgOrderAmount = totalOrders > 0 ? totalAmount / totalOrders : 0;

        // 构建并返回销售概览视图对象
        return SalesOverviewVO.builder()
                .totalAmount(totalAmount)
                .monthlyAmount(monthlyAmount)
                .totalOrders(totalOrders)
                .monthlyOrders(monthlyOrders)
                .avgOrderAmount(avgOrderAmount)
                .customerCount((long) allCustomers.size())
                .build();
    }

    /**
     * 获取月度销售趋势统计
     * <p>
     * 该方法用于统计指定月份数内的销售趋势，按月份聚合销售金额和订单数量。
     * 主要功能包括：
     * 1. 生成最近N个月的月份标签列表
     * 2. 按月份分组统计销售金额
     * 3. 按月份分组统计订单数量
     * 4. 返回按月排列的趋势数据
     * </p>
     *
     * @param months 需要统计的月份数，通常为6、12等
     * @return 月度销售视图对象列表，按月份升序排列，每个对象包含月份、销售额和订单数
     */
    @Override
    public List<MonthlySalesVO> getMonthlySalesTrend(int months) {
        // 查询所有销售记录和订单
        List<SalesRecord> allRecords = salesRecordRepository.findAll();
        List<Order> allOrders = orderRepository.findAll();

        // 初始化日期格式化器和日历
        SimpleDateFormat monthFormat = new SimpleDateFormat("yyyy-MM");
        Calendar cal = Calendar.getInstance();

        // 生成最近N个月的月份标签列表
        List<String> monthLabels = new ArrayList<>();
        for (int i = months - 1; i >= 0; i--) {
            cal.setTime(new Date());
            cal.add(Calendar.MONTH, -i);
            monthLabels.add(monthFormat.format(cal.getTime()));
        }

        // 初始化月份金额和订单数量的映射表
        Map<String, Double> monthAmountMap = new LinkedHashMap<>();
        Map<String, Long> monthOrderMap = new LinkedHashMap<>();
        for (String label : monthLabels) {
            monthAmountMap.put(label, 0.0);
            monthOrderMap.put(label, 0L);
        }

        // 统计每个月的销售金额
        for (SalesRecord record : allRecords) {
            if (record.getSaleDate() != null) {
                String monthKey = monthFormat.format(record.getSaleDate());
                if (monthAmountMap.containsKey(monthKey)) {
                    monthAmountMap.merge(monthKey, record.getAmount() != null ? record.getAmount() : 0, Double::sum);
                }
            }
        }

        // 统计每个月的订单数量
        for (Order order : allOrders) {
            if (order.getCreateTime() != null) {
                String monthKey = monthFormat.format(order.getCreateTime());
                if (monthOrderMap.containsKey(monthKey)) {
                    monthOrderMap.merge(monthKey, 1L, Long::sum);
                }
            }
        }

        // 转换为月度销售视图对象列表并返回
        return monthLabels.stream()
                .map(label -> MonthlySalesVO.builder()
                        .month(label)
                        .amount(monthAmountMap.getOrDefault(label, 0.0))
                        .orderCount(monthOrderMap.getOrDefault(label, 0L))
                        .build())
                .collect(Collectors.toList());
    }

    /**
     * 获取畅销产品排行榜
     * <p>
     * 该方法用于统计销售数量最多的前N个产品，按销售数量降序排列。
     * 主要功能包括：
     * 1. 遍历所有销售记录，按产品名称分组
     * 2. 累加每个产品的销售数量和金额
     * 3. 按销售数量降序排序
     * 4. 取前limit个产品返回
     * </p>
     *
     * @param limit 排行榜显示的产品数量限制，通常取前5或前10
     * @return 畅销产品视图对象列表，按销售数量降序排列
     */
    @Override
    public List<TopProductVO> getTopProducts(int limit) {
        List<SalesRecord> allRecords = salesRecordRepository.findAll();

        // 按产品名称分组并累加销售数量和金额
        Map<String, TopProductVO> productMap = new LinkedHashMap<>();
        for (SalesRecord record : allRecords) {
            String productName = record.getProductName() != null ? record.getProductName() : "未知产品";
            TopProductVO vo = productMap.getOrDefault(productName,
                    TopProductVO.builder().productName(productName).quantity(0).amount(0.0).build());
            vo.setQuantity(vo.getQuantity() + (record.getQuantity() != null ? record.getQuantity() : 0));
            vo.setAmount(vo.getAmount() + (record.getAmount() != null ? record.getAmount() : 0));
            productMap.put(productName, vo);
        }

        // 按销售数量降序排序，取前limit个产品并返回
        return productMap.values().stream()
                .sorted((a, b) -> b.getQuantity() - a.getQuantity())
                .limit(limit)
                .collect(Collectors.toList());
    }

    /**
     * 获取库存概览统计信息
     * <p>
     * 该方法用于统计系统的整体库存情况，包括原材料和成品的库存数量以及预警信息。
     * 主要计算指标：
     * 1. 原材料种类数和总数量
     * 2. 成品种类数和总数量
     * 3. 待处理预警数量（状态为PENDING的预警）
     * </p>
     *
     * @return 库存概览视图对象，包含原材料、成品和预警的统计指标
     */
    @Override
    public InventoryOverviewVO getInventoryOverview() {
        // 查询所有原材料、成品和库存预警
        List<RawMaterial> rawMaterials = rawMaterialRepository.findAll();
        List<FinishedProduct> finishedProducts = finishedProductRepository.findAll();
        List<InventoryAlert> alerts = inventoryAlertRepository.findAll();

        // 计算原材料总数量
        long rawMaterialTotalQuantity = rawMaterials.stream()
                .mapToLong(m -> m.getQuantity() != null ? m.getQuantity() : 0)
                .sum();
        
        // 计算成品总数量
        long finishedProductTotalQuantity = finishedProducts.stream()
                .mapToLong(p -> p.getQuantity() != null ? p.getQuantity() : 0)
                .sum();
        
        // 统计待处理预警数量
        long alertCount = alerts.stream()
                .filter(a -> "PENDING".equals(a.getStatus()))
                .count();

        // 构建并返回库存概览视图对象
        return InventoryOverviewVO.builder()
                .rawMaterialCount((long) rawMaterials.size())
                .finishedProductCount((long) finishedProducts.size())
                .rawMaterialTotalQuantity(rawMaterialTotalQuantity)
                .finishedProductTotalQuantity(finishedProductTotalQuantity)
                .alertCount(alertCount)
                .build();
    }

    /**
     * 获取原材料分类分布统计
     * <p>
     * 该方法用于统计各类别原材料的库存数量分布情况。
     * 按类别分组并累加每个类别的库存总量，如棉布、丝绸、化纤等。
     * </p>
     *
     * @return 分类分布视图对象列表，每个对象包含类别名称和对应的库存数量
     */
    @Override
    public List<CategoryDistributionVO> getRawMaterialDistribution() {
        List<RawMaterial> rawMaterials = rawMaterialRepository.findAll();

        // 按类别分组并统计每个类别的原材料总数量
        Map<String, Long> categoryMap = rawMaterials.stream()
                .collect(Collectors.groupingBy(
                        m -> m.getCategory() != null ? m.getCategory() : "未分类",
                        Collectors.summingLong(m -> m.getQuantity() != null ? m.getQuantity() : 0)
                ));

        // 转换为分类分布视图对象列表并返回
        return categoryMap.entrySet().stream()
                .map(entry -> CategoryDistributionVO.builder()
                        .category(entry.getKey())
                        .quantity(entry.getValue())
                        .build())
                .collect(Collectors.toList());
    }

    /**
     * 获取成品分类分布统计
     * <p>
     * 该方法用于统计各类别成品的库存数量分布情况。
     * 按类别分组并累加每个类别的库存总量，如上衣、裤子、裙子等。
     * </p>
     *
     * @return 分类分布视图对象列表，每个对象包含类别名称和对应的库存数量
     */
    @Override
    public List<CategoryDistributionVO> getFinishedProductDistribution() {
        List<FinishedProduct> finishedProducts = finishedProductRepository.findAll();

        // 按类别分组并统计每个类别的成品总数量
        Map<String, Long> categoryMap = finishedProducts.stream()
                .collect(Collectors.groupingBy(
                        p -> p.getCategory() != null ? p.getCategory() : "未分类",
                        Collectors.summingLong(p -> p.getQuantity() != null ? p.getQuantity() : 0)
                ));

        // 转换为分类分布视图对象列表并返回
        return categoryMap.entrySet().stream()
                .map(entry -> CategoryDistributionVO.builder()
                        .category(entry.getKey())
                        .quantity(entry.getValue())
                        .build())
                .collect(Collectors.toList());
    }

    /**
     * 获取库存预警统计信息
     * <p>
     * 该方法用于统计库存预警的处理情况，包括待处理预警数、已处理预警数和处理率。
     * 主要计算指标：
     * 1. 待处理预警数量（状态为PENDING）
     * 2. 已处理预警数量（状态为HANDLED）
     * 3. 处理率 = 已处理数量 / 总数量 × 100%
     * </p>
     *
     * @return 预警统计视图对象，包含待处理数、已处理数和处理率
     */
    @Override
    public AlertStatsVO getAlertStats() {
        List<InventoryAlert> alerts = inventoryAlertRepository.findAll();

        // 统计待处理和已处理的预警数量
        long pendingCount = alerts.stream().filter(a -> "PENDING".equals(a.getStatus())).count();
        long handledCount = alerts.stream().filter(a -> "HANDLED".equals(a.getStatus())).count();
        long total = pendingCount + handledCount;
        
        // 计算处理率
        double handleRate = total > 0 ? (double) handledCount / total * 100 : 0;

        // 构建并返回预警统计视图对象
        return AlertStatsVO.builder()
                .pendingCount(pendingCount)
                .handledCount(handledCount)
                .handleRate(Math.round(handleRate * 100.0) / 100.0)
                .build();
    }
}
