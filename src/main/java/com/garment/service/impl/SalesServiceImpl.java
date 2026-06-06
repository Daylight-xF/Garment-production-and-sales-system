package com.garment.service.impl;

import com.garment.dto.*;
import com.garment.exception.BusinessException;
import com.garment.model.Customer;
import com.garment.model.SalesRecord;
import com.garment.model.User;
import com.garment.repository.CustomerRepository;
import com.garment.repository.ProductDefinitionRepository;
import com.garment.repository.SalesRecordRepository;
import com.garment.repository.UserRepository;
import com.garment.service.SalesService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

@Service
public class SalesServiceImpl implements SalesService {

    private final SalesRecordRepository salesRecordRepository;
    private final CustomerRepository customerRepository;
    private final UserRepository userRepository;
    private final ProductDefinitionRepository productDefinitionRepository;

    public SalesServiceImpl(SalesRecordRepository salesRecordRepository,
                            CustomerRepository customerRepository,
                            UserRepository userRepository,
                            ProductDefinitionRepository productDefinitionRepository) {
        this.salesRecordRepository = salesRecordRepository;
        this.customerRepository = customerRepository;
        this.userRepository = userRepository;
        this.productDefinitionRepository = productDefinitionRepository;
    }

    /**
     * 创建销售记录
     * <p>
     * 该方法用于创建新的销售记录，主要功能包括：
     * 1. 校验客户是否存在，并获取客户名称
     * 2. 计算销售金额：数量 × 单价
     * 3. 设置销售日期、订单日期和完成日期（默认为当前日期）
     * 4. 如果指定了产品ID，则从产品定义中获取产品编码
     * 5. 查询创建人信息并设置创建人姓名
     * 6. 同步记录摘要信息
     * 7. 保存销售记录到数据库
     * </p>
     *
     * @param request 销售记录创建请求对象，包含客户ID、产品信息、数量、单价等
     * @param userId 当前操作用户ID，用于记录创建人和创建人姓名
     * @return 创建后的销售记录视图对象
     */
    @Override
    public SalesRecordVO createSalesRecord(SalesRecordCreateRequest request, String userId) {
        // 校验客户是否存在
        Customer customer = customerRepository.findById(request.getCustomerId())
                .orElseThrow(() -> new BusinessException("客户不存在"));

        // 确定销售日期，如果未指定则使用当前日期
        Date saleDate = request.getSaleDate() != null ? request.getSaleDate() : new Date();

        // 创建销售记录对象，初始化基本信息
        SalesRecord record = new SalesRecord();
        record.setCustomerId(request.getCustomerId());
        record.setCustomerName(customer.getName());
        record.setProductId(request.getProductId());
        record.setProductName(request.getProductName());
        record.setQuantity(request.getQuantity());
        record.setUnitPrice(request.getUnitPrice());

        // 计算销售总金额
        record.setAmount(request.getQuantity() * request.getUnitPrice());

        // 设置销售日期、订单日期和完成日期
        record.setSaleDate(saleDate);
        record.setOrderDate(saleDate);
        record.setCompleteDate(saleDate);
        record.setRemark(request.getRemark());
        record.setCreateBy(userId);

        // 如果指定了产品ID，则从产品定义中获取产品编码
        if (request.getProductId() != null) {
            productDefinitionRepository.findById(request.getProductId())
                    .ifPresent(productDef -> record.setProductCode(productDef.getProductCode()));
        }

        // 查询创建人信息并设置创建人姓名
        Optional<User> userOpt = userRepository.findById(userId);
        userOpt.ifPresent(user -> record.setCreateByName(user.getRealName()));

        // 同步记录摘要信息
        syncRecordSummary(record);

        // 保存销售记录到数据库并转换为视图对象返回
        SalesRecord saved = salesRecordRepository.save(record);
        return convertToSalesRecordVO(saved);
    }


    /**
     * 分页查询销售记录列表
     * <p>
     * 该方法支持根据客户ID、日期范围和关键词等多个条件筛选销售记录，并按日期倒序排序后返回分页结果。
     * 所有筛选条件都是可选的，可以组合使用以实现精确查询。
     *
     * 筛选逻辑：
     * - 客户ID：精确匹配
     * - 日期范围：检查记录日期是否在起始日期和结束日期之间
     * - 关键词：模糊匹配客户名称、产品名称等字段
     * </p>
     *
     * @param customerId 客户ID，用于筛选特定客户的销售记录
     * @param startDate 起始日期，用于筛选指定时间范围内的记录
     * @param endDate 结束日期，用于筛选指定时间范围内的记录
     * @param keyword 搜索关键词，用于模糊匹配客户名称或产品名称
     * @param pageable 分页参数，包含页码、每页大小和排序信息
     * @return 分页的销售记录视图对象列表
     */
    @Override
    public Page<SalesRecordVO> querySalesRecords(String customerId, Date startDate, Date endDate, String keyword, Pageable pageable) {
        // 根据客户ID、日期范围和关键词过滤销售记录
        List<SalesRecord> filtered = salesRecordRepository.findAll().stream()
                .filter(record -> !StringUtils.hasText(customerId) || customerId.equals(record.getCustomerId()))
                .filter(record -> isWithinDateRange(resolveRecordDate(record), startDate, endDate))
                .filter(record -> !StringUtils.hasText(keyword) || matchesKeyword(record, keyword))

                // 按记录日期倒序排序
                .sorted((a, b) -> {
                    Date dateA = resolveSortDate(a);
                    Date dateB = resolveSortDate(b);
                    if (dateA == null && dateB == null) {
                        return 0;
                    }
                    if (dateA == null) {
                        return 1;
                    }
                    if (dateB == null) {
                        return -1;
                    }
                    return dateB.compareTo(dateA);
                })
                .collect(Collectors.toList());

        // 计算分页范围并提取当前页数据
        int start = (int) pageable.getOffset();
        int end = Math.min(start + pageable.getPageSize(), filtered.size());
        List<SalesRecord> pageContent = start < filtered.size() ? filtered.subList(start, end) : new ArrayList<>();

        // 转换为视图对象并返回分页结果
        List<SalesRecordVO> voList = pageContent.stream()
                .map(this::convertToSalesRecordVO)
                .collect(Collectors.toList());

        return new PageImpl<>(voList, pageable, filtered.size());
    }


    /**
     * 根据ID查询销售记录详情
     * <p>
     * 该方法从数据库中获取指定ID的销售记录，并将其转换为视图对象返回。
     * 如果记录不存在，则抛出业务异常。
     * </p>
     *
     * @param id 销售记录的唯一标识符
     * @return 销售记录视图对象，包含销售的详细信息
     */
    @Override
    public SalesRecordVO getSalesRecordById(String id) {
        // 从数据库查询销售记录，不存在时抛出异常
        SalesRecord record = salesRecordRepository.findById(id)
                .orElseThrow(() -> new BusinessException("销售记录不存在"));

        // 转换为视图对象并返回
        return convertToSalesRecordVO(record);
    }


    /**
     * 更新销售记录信息
     * <p>
     * 该方法支持更新销售记录的多个字段，包括客户信息、产品信息、数量、单价、日期和备注等。
     * 只有请求中提供的非空字段才会被更新，其他字段保持不变。
     *
     * 主要功能包括：
     * 1. 校验销售记录和客户是否存在
     * 2. 选择性更新客户信息和产品信息
     * 3. 当数量或单价变更时，自动重新计算销售金额
     * 4. 如果未生成订单ID，则同步更新订单日期和完成日期
     * 5. 同步记录摘要信息
     * </p>
     *
     * @param id 销售记录的唯一标识符
     * @param request 更新请求对象，包含需要更新的字段信息
     * @return 更新后的销售记录视图对象
     */
    @Override
    public SalesRecordVO updateSalesRecord(String id, SalesRecordCreateRequest request) {
        SalesRecord record = salesRecordRepository.findById(id)
                .orElseThrow(() -> new BusinessException("销售记录不存在"));

        // 选择性更新客户信息
        if (StringUtils.hasText(request.getCustomerId())) {
            Customer customer = customerRepository.findById(request.getCustomerId())
                    .orElseThrow(() -> new BusinessException("客户不存在"));
            record.setCustomerId(request.getCustomerId());
            record.setCustomerName(customer.getName());
        }

        // 选择性更新产品相关信息
        if (request.getProductName() != null) {
            record.setProductName(request.getProductName());
        }
        if (request.getProductId() != null) {
            record.setProductId(request.getProductId());
        }

        // 选择性更新数量和单价
        if (request.getQuantity() != null) {
            record.setQuantity(request.getQuantity());
        }
        if (request.getUnitPrice() != null) {
            record.setUnitPrice(request.getUnitPrice());
        }

        // 当数量或单价变更时，重新计算销售金额
        if (request.getQuantity() != null || request.getUnitPrice() != null) {
            double quantity = record.getQuantity() != null ? record.getQuantity() : 0;
            double unitPrice = record.getUnitPrice() != null ? record.getUnitPrice() : 0;
            record.setAmount(quantity * unitPrice);
        }

        // 更新销售日期，如果未生成订单ID则同步更新订单日期和完成日期
        if (request.getSaleDate() != null) {
            record.setSaleDate(request.getSaleDate());
            if (!StringUtils.hasText(record.getOrderId())) {
                record.setOrderDate(request.getSaleDate());
                record.setCompleteDate(request.getSaleDate());
            }
        }

        // 选择性更新备注信息
        if (request.getRemark() != null) {
            record.setRemark(request.getRemark());
        }

        // 同步记录摘要信息
        syncRecordSummary(record);

        // 保存更新后的记录并转换为视图对象返回
        SalesRecord saved = salesRecordRepository.save(record);
        return convertToSalesRecordVO(saved);
    }


    /**
     * 删除销售记录
     * <p>
     * 该方法用于删除指定ID的销售记录。删除前会校验记录是否存在，如果不存在则抛出业务异常。
     * </p>
     *
     * @param id 销售记录的唯一标识符
     */
    @Override
    public void deleteSalesRecord(String id) {
        // 校验销售记录是否存在
        if (!salesRecordRepository.existsById(id)) {
            throw new BusinessException("销售记录不存在");
        }

        // 从数据库中删除销售记录
        salesRecordRepository.deleteById(id);
    }


    /**
     * 获取销售概览统计信息
     * <p>
     * 该方法用于计算并返回系统的整体销售统计数据，主要包含：
     * 1. 销售总额：所有销售记录的金额总和
     * 2. 订单总数：销售记录的总数量
     * 3. 平均订单金额：销售总额除以订单总数
     * 4. 客户总数：系统中注册的客户数量
     * </p>
     *
     * @return 销售概览视图对象，包含各项统计指标
     */
    @Override
    public SalesOverviewVO getSalesOverview() {
        List<SalesRecord> allRecords = salesRecordRepository.findAll();
        List<Customer> allCustomers = customerRepository.findAll();

        // 计算销售总额、订单总数和平均订单金额
        double totalAmount = allRecords.stream()
                .mapToDouble(this::getSafeTotalAmount)
                .sum();
        long totalOrders = allRecords.size();
        double avgOrderAmount = totalOrders > 0 ? totalAmount / totalOrders : 0;

        // 构建并返回销售概览视图对象
        return SalesOverviewVO.builder()
                .totalAmount(totalAmount)
                .totalOrders(totalOrders)
                .avgOrderAmount(avgOrderAmount)
                .customerCount((long) allCustomers.size())
                .build();
    }


    /**
     * 获取销售趋势数据
     * <p>
     * 该方法用于统计指定日期范围内的销售趋势，按天聚合销售金额并返回时间序列数据。
     * 主要功能包括：
     * 1. 遍历所有销售记录，筛选出在指定日期范围内的记录
     * 2. 按日期（yyyy-MM-dd格式）分组并累加每天的销售金额
     * 3. 使用TreeMap保证日期按自然顺序排序
     * 4. 转换为销售趋势视图对象列表返回
     * </p>
     *
     * @param startDate 起始日期，用于筛选指定时间范围内的销售记录
     * @param endDate 结束日期，用于筛选指定时间范围内的销售记录
     * @return 销售趋势视图对象列表，按日期升序排列，每个对象包含日期和对应的销售金额
     */
    @Override
    public List<SalesTrendVO> getSalesTrend(Date startDate, Date endDate) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
        Map<String, Double> dateAmountMap = new TreeMap<>();

        // 遍历所有销售记录，按日期聚合并累加销售金额
        for (SalesRecord record : salesRecordRepository.findAll()) {
            Date recordDate = resolveRecordDate(record);

            // 跳过不在指定日期范围内的记录
            if (!isWithinDateRange(recordDate, startDate, endDate)) {
                continue;
            }

            // 将记录按日期格式化并累加到对应日期的销售金额中
            String dateKey = sdf.format(recordDate);
            dateAmountMap.merge(dateKey, getSafeTotalAmount(record), Double::sum);
        }

        // 转换为销售趋势视图对象列表并返回
        return dateAmountMap.entrySet().stream()
                .map(entry -> SalesTrendVO.builder()
                        .date(entry.getKey())
                        .amount(entry.getValue())
                        .build())
                .collect(Collectors.toList());
    }


    /**
     * 获取产品销量排行榜
     * <p>
     * 该方法用于统计指定日期范围内的产品销售情况，按销售数量降序排列并返回前N个产品。
     * 主要功能包括：
     * 1. 遍历所有销售记录，筛选出在指定日期范围内的记录
     * 2. 从每条记录中提取销售明细项（支持多商品记录）
     * 3. 按产品名称聚合并累加销售数量和金额
     * 4. 按销售数量降序排序，取前limit个产品
     * </p>
     *
     * @param startDate 起始日期，用于筛选指定时间范围内的销售记录
     * @param endDate 结束日期，用于筛选指定时间范围内的销售记录
     * @param limit 排行榜显示的产品数量限制，默认返回前N个产品
     * @return 产品销量排行榜视图对象列表，按销售数量降序排列
     */
    @Override
    public List<ProductRankingVO> getProductRanking(Date startDate, Date endDate, int limit) {
        Map<String, ProductRankingVO> productMap = new LinkedHashMap<>();

        // 遍历所有销售记录，按产品聚合并统计销售数据
        for (SalesRecord record : salesRecordRepository.findAll()) {
            // 跳过不在指定日期范围内的记录
            if (!isWithinDateRange(resolveRecordDate(record), startDate, endDate)) {
                continue;
            }

            // 遍历记录中的每个销售明细项并累加到对应产品的统计数据中
            for (SalesRecord.SalesRecordItem item : getNormalizedItems(record)) {
                String productName = StringUtils.hasText(item.getProductName()) ? item.getProductName() : "未知产品";

                // 获取或创建产品统计对象
                ProductRankingVO vo = productMap.getOrDefault(productName,
                        ProductRankingVO.builder().productName(productName).quantity(0).amount(0.0).build());

                // 累加销售数量和金额
                vo.setQuantity(vo.getQuantity() + (item.getQuantity() != null ? item.getQuantity() : 0));
                vo.setAmount(vo.getAmount() + (item.getAmount() != null ? item.getAmount() : 0));
                productMap.put(productName, vo);
            }
        }

        // 按销售数量降序排序，取前limit个产品并返回列表
        return productMap.values().stream()
                .sorted((a, b) -> Integer.compare(b.getQuantity(), a.getQuantity()))
                .limit(limit)
                .collect(Collectors.toList());
    }


    /**
     * 获取产品分类销售分布统计
     * <p>
     * 该方法用于统计指定日期范围内各产品类别的销售金额分布情况。
     * 主要功能包括：
     * 1. 遍历所有销售记录，筛选出在指定日期范围内的记录
     * 2. 从每条记录中提取销售明细项
     * 3. 根据产品名称提取产品类别信息
     * 4. 按类别聚合并累加销售金额
     * 5. 转换为分类分布视图对象列表返回
     * </p>
     *
     * @param startDate 起始日期，用于筛选指定时间范围内的销售记录
     * @param endDate 结束日期，用于筛选指定时间范围内的销售记录
     * @return 产品分类分布视图对象列表，每个对象包含类别名称和对应的销售金额
     */
    @Override
    public List<CategoryDistributionVO> getCategoryDistribution(Date startDate, Date endDate) {
        Map<String, Double> categoryMap = new LinkedHashMap<>();

        // 遍历所有销售记录，按类别聚合并统计销售金额
        for (SalesRecord record : salesRecordRepository.findAll()) {
            // 跳过不在指定日期范围内的记录
            if (!isWithinDateRange(resolveRecordDate(record), startDate, endDate)) {
                continue;
            }

            // 遍历记录中的每个销售明细项并累加到对应类别的销售额中
            for (SalesRecord.SalesRecordItem item : getNormalizedItems(record)) {
                String category = extractCategory(item.getProductName());
                categoryMap.merge(category, item.getAmount() != null ? item.getAmount() : 0, Double::sum);
            }
        }

        // 转换为分类分布视图对象列表并返回
        return categoryMap.entrySet().stream()
                .map(entry -> CategoryDistributionVO.builder()
                        .category(entry.getKey())
                        .amount(entry.getValue())
                        .build())
                .collect(Collectors.toList());
    }


    /**
     * 创建客户信息
     * <p>
     * 该方法用于创建新的客户记录，主要功能包括：
     * 1. 初始化客户对象并设置基本信息（姓名、电话、邮箱、地址）
     * 2. 设置客户等级，如果未指定则默认为"NEW"（新客户）
     * 3. 记录备注信息和创建人ID
     * 4. 保存客户信息到数据库
     * </p>
     *
     * @param request 客户创建请求对象，包含姓名、电话、邮箱、地址、等级和备注等信息
     * @param userId 当前操作用户ID，用于记录创建人
     * @return 创建后的客户视图对象
     */
    @Override
    public CustomerVO createCustomer(CustomerCreateRequest request, String userId) {
        // 创建客户对象并初始化基本信息
        Customer customer = new Customer();
        customer.setName(request.getName());
        customer.setPhone(request.getPhone());
        customer.setEmail(request.getEmail());
        customer.setAddress(request.getAddress());

        // 设置客户等级，未指定时默认为NEW
        customer.setLevel(request.getLevel() != null ? request.getLevel() : "NEW");
        customer.setRemark(request.getRemark());
        customer.setCreateBy(userId);

        // 保存客户信息到数据库并转换为视图对象返回
        Customer saved = customerRepository.save(customer);
        return convertToCustomerVO(saved);
    }


    /**
     * 分页查询客户列表
     * <p>
     * 该方法支持根据关键词和客户等级筛选客户，并按创建时间倒序排序后返回分页结果。
     * 筛选逻辑：
     * - 关键词：匹配客户名称或手机号
     * - 客户等级：精确匹配客户等级（如NEW、VIP等）
     * </p>
     *
     * @param keyword 搜索关键词，用于匹配客户名称或手机号
     * @param level 客户等级，用于筛选特定等级的客户
     * @param pageable 分页参数，包含页码、每页大小和排序信息
     * @return 分页的客户视图对象列表
     */
    @Override
    public Page<CustomerVO> queryCustomers(String keyword, String level, Pageable pageable) {
        List<Customer> allCustomers = customerRepository.findAll();

        // 根据关键词和客户等级过滤客户
        List<Customer> filtered = allCustomers.stream()
                .filter(c -> {
                    // 关键词匹配：客户名称或手机号
                    if (StringUtils.hasText(keyword)) {
                        boolean match = (c.getName() != null && c.getName().contains(keyword))
                                || (c.getPhone() != null && c.getPhone().contains(keyword));
                        if (!match) {
                            return false;
                        }
                    }
                    // 等级匹配：精确匹配客户等级
                    return !StringUtils.hasText(level) || level.equals(c.getLevel());
                })
                
                // 按创建时间倒序排序
                .sorted((a, b) -> {
                    if (a.getCreateTime() == null && b.getCreateTime() == null) {
                        return 0;
                    }
                    if (a.getCreateTime() == null) {
                        return 1;
                    }
                    if (b.getCreateTime() == null) {
                        return -1;
                    }
                    return b.getCreateTime().compareTo(a.getCreateTime());
                })
                .collect(Collectors.toList());

        // 计算分页范围并提取当前页数据
        int start = (int) pageable.getOffset();
        int end = Math.min(start + pageable.getPageSize(), filtered.size());
        List<Customer> pageContent = start < filtered.size() ? filtered.subList(start, end) : new ArrayList<>();

        // 转换为视图对象并返回分页结果
        List<CustomerVO> voList = pageContent.stream()
                .map(this::convertToCustomerVO)
                .collect(Collectors.toList());

        return new PageImpl<>(voList, pageable, filtered.size());
    }

    /**
     * 根据ID查询客户详情
     * <p>
     * 该方法从数据库中获取指定ID的客户信息，并将其转换为视图对象返回。
     * 如果客户不存在，则抛出业务异常。
     * </p>
     *
     * @param id 客户的唯一标识符
     * @return 客户视图对象，包含客户的详细信息
     */
    @Override
    public CustomerVO getCustomerById(String id) {
        // 从数据库查询客户，不存在时抛出异常
        Customer customer = customerRepository.findById(id)
                .orElseThrow(() -> new BusinessException("客户不存在"));
        
        // 转换为视图对象并返回
        return convertToCustomerVO(customer);
    }

    /**
     * 更新客户信息
     * <p>
     * 该方法支持更新客户的多个字段，包括姓名、电话、邮箱、地址、等级和备注等。
     * 只有请求中提供的非空字段才会被更新，其他字段保持不变。
     * </p>
     *
     * @param id 客户的唯一标识符
     * @param request 更新请求对象，包含需要更新的字段信息
     * @return 更新后的客户视图对象
     */
    @Override
    public CustomerVO updateCustomer(String id, CustomerUpdateRequest request) {
        Customer customer = customerRepository.findById(id)
                .orElseThrow(() -> new BusinessException("客户不存在"));

        // 选择性更新客户字段，只更新请求中提供的非空字段
        if (request.getName() != null) {
            customer.setName(request.getName());
        }
        if (request.getPhone() != null) {
            customer.setPhone(request.getPhone());
        }
        if (request.getEmail() != null) {
            customer.setEmail(request.getEmail());
        }
        if (request.getAddress() != null) {
            customer.setAddress(request.getAddress());
        }
        if (request.getLevel() != null) {
            customer.setLevel(request.getLevel());
        }
        if (request.getRemark() != null) {
            customer.setRemark(request.getRemark());
        }

        // 保存更新后的客户并转换为视图对象返回
        Customer saved = customerRepository.save(customer);
        return convertToCustomerVO(saved);
    }

    /**
     * 删除客户
     * <p>
     * 该方法用于删除指定ID的客户。删除前会校验客户是否存在，如果不存在则抛出业务异常。
     * </p>
     *
     * @param id 客户的唯一标识符
     */
    @Override
    public void deleteCustomer(String id) {
        // 校验客户是否存在
        if (!customerRepository.existsById(id)) {
            throw new BusinessException("客户不存在");
        }
        
        // 从数据库中删除客户
        customerRepository.deleteById(id);
    }

    /**
     * 将销售记录实体转换为视图对象
     * <p>
     * 该方法将数据库中的销售记录实体对象转换为前端展示的视图对象，包含完整的销售信息。
     * 主要功能包括：
     * 1. 转换销售明细项列表（支持多商品记录）
     * 2. 计算产品数量、总数量和总金额
     * 3. 处理日期字段的默认值
     * 4. 对于单商品记录，保留原始字段；对于多商品记录，合并产品名称
     * </p>
     *
     * @param record 销售记录实体对象
     * @return 销售记录视图对象，包含扩展的展示信息
     */
    private SalesRecordVO convertToSalesRecordVO(SalesRecord record) {
        // 转换销售明细项列表
        List<SalesRecordVO.SalesRecordItemVO> itemVOs = getNormalizedItems(record).stream()
                .map(item -> SalesRecordVO.SalesRecordItemVO.builder()
                        .productId(item.getProductId())
                        .productCode(item.getProductCode())
                        .productName(item.getProductName())
                        .color(item.getColor())
                        .size(item.getSize())
                        .quantity(item.getQuantity())
                        .unitPrice(item.getUnitPrice())
                        .amount(item.getAmount())
                        .build())
                .collect(Collectors.toList());

        // 构建并返回销售记录视图对象
        return SalesRecordVO.builder()
                .id(record.getId())
                .orderId(record.getOrderId())
                .orderNo(record.getOrderNo())
                .customerId(record.getCustomerId())
                .customerName(record.getCustomerName())
                .productCount(record.getProductCount() != null ? record.getProductCount() : itemVOs.size())
                .totalQuantity(getSafeTotalQuantity(record))
                .totalAmount(getSafeTotalAmount(record))
                .orderDate(record.getOrderDate() != null ? record.getOrderDate() : record.getSaleDate())
                .shipDate(record.getShipDate())
                .completeDate(record.getCompleteDate() != null ? record.getCompleteDate() : record.getSaleDate())
                .items(itemVOs)
                .productId(record.getProductId())
                .productCode(record.getProductCode())
                .productName(record.getProductName())
                .quantity(record.getQuantity())
                .unitPrice(record.getUnitPrice())
                .amount(record.getAmount())
                .saleDate(record.getSaleDate())
                .createBy(record.getCreateBy())
                .createByName(record.getCreateByName())
                .remark(record.getRemark())
                .createTime(record.getCreateTime())
                .updateTime(record.getUpdateTime())
                .build();
    }

    /**
     * 将客户实体转换为视图对象
     * <p>
     * 该方法将数据库中的客户实体对象转换为前端展示的视图对象，包含客户的完整信息。
     * </p>
     *
     * @param customer 客户实体对象
     * @return 客户视图对象
     */
    private CustomerVO convertToCustomerVO(Customer customer) {
        return CustomerVO.builder()
                .id(customer.getId())
                .name(customer.getName())
                .phone(customer.getPhone())
                .email(customer.getEmail())
                .address(customer.getAddress())
                .level(customer.getLevel())
                .remark(customer.getRemark())
                .createBy(customer.getCreateBy())
                .createTime(customer.getCreateTime())
                .updateTime(customer.getUpdateTime())
                .build();
    }

    /**
     * 同步销售记录的摘要信息
     * <p>
     * 该方法用于更新销售记录的汇总字段，确保记录的一致性。主要功能包括：
     * 1. 标准化销售明细项列表
     * 2. 计算总数量和总金额
     * 3. 设置产品数量、订单日期和完成日期的默认值
     * 4. 对于单商品记录，同步产品信息到主字段
     * 5. 对于多商品记录，合并产品名称并设置汇总数据
     * </p>
     *
     * @param record 需要同步摘要信息的销售记录
     */
    private void syncRecordSummary(SalesRecord record) {
        // 获取标准化的销售明细项列表
        List<SalesRecord.SalesRecordItem> items = getNormalizedItems(record);
        if (record.getItems() == null || record.getItems().isEmpty()) {
            record.setItems(items.isEmpty() ? null : items);
        }

        // 计算总数量和总金额
        int totalQuantity = items.stream()
                .mapToInt(item -> item.getQuantity() != null ? item.getQuantity() : 0)
                .sum();
        double totalAmount = items.stream()
                .mapToDouble(item -> item.getAmount() != null ? item.getAmount() : 0D)
                .sum();

        // 如果明细项中没有数据，则使用主字段的值
        if (totalAmount == 0 && record.getAmount() != null) {
            totalAmount = record.getAmount();
        }
        if (totalQuantity == 0 && record.getQuantity() != null) {
            totalQuantity = record.getQuantity();
        }

        // 设置产品数量、总数量和总金额
        record.setProductCount(items.isEmpty() ? 0 : items.size());
        record.setTotalQuantity(totalQuantity);
        record.setTotalAmount(totalAmount);

        // 如果未生成订单ID，则设置订单日期和完成日期的默认值
        if (!StringUtils.hasText(record.getOrderId())) {
            if (record.getOrderDate() == null) {
                record.setOrderDate(record.getSaleDate());
            }
            if (record.getCompleteDate() == null) {
                record.setCompleteDate(record.getSaleDate());
            }
        }
        
        // 设置销售日期的默认值
        if (record.getSaleDate() == null) {
            record.setSaleDate(record.getCompleteDate() != null ? record.getCompleteDate() : record.getOrderDate());
        }

        // 处理单商品记录：同步产品信息到主字段
        if (items.size() == 1) {
            SalesRecord.SalesRecordItem item = items.get(0);
            record.setProductId(item.getProductId());
            record.setProductCode(item.getProductCode());
            record.setProductName(item.getProductName());
            record.setQuantity(item.getQuantity());
            record.setUnitPrice(item.getUnitPrice());
            record.setAmount(item.getAmount());
            return;
        }

        // 处理多商品记录：合并产品名称并设置汇总数据
        if (items.size() > 1) {
            Set<String> productNames = items.stream()
                    .map(SalesRecord.SalesRecordItem::getProductName)
                    .filter(StringUtils::hasText)
                    .collect(Collectors.toCollection(LinkedHashSet::new));
            record.setProductName(String.join("、", productNames));
            record.setQuantity(totalQuantity);
            record.setUnitPrice(null);
            record.setAmount(totalAmount);
        }
    }

    /**
     * 获取标准化的销售明细项列表
     * <p>
     * 该方法用于统一处理销售记录的明细项，支持新旧两种数据格式：
     * 1. 如果记录包含items字段，则直接返回
     * 2. 否则从旧格式的单个产品字段构建明细项列表
     * </p>
     *
     * @param record 销售记录对象
     * @return 标准化的销售明细项列表
     */
    private List<SalesRecord.SalesRecordItem> getNormalizedItems(SalesRecord record) {
        // 如果已有明细项列表，直接返回
        if (record.getItems() != null && !record.getItems().isEmpty()) {
            return record.getItems();
        }

        // 检查是否有旧格式的产品信息
        boolean hasLegacyProduct = StringUtils.hasText(record.getProductName())
                || StringUtils.hasText(record.getProductId())
                || StringUtils.hasText(record.getProductCode());
        if (!hasLegacyProduct) {
            return new ArrayList<>();
        }

        // 从旧格式的单个产品字段构建明细项列表
        List<SalesRecord.SalesRecordItem> items = new ArrayList<>();
        items.add(new SalesRecord.SalesRecordItem(
                record.getProductId(),
                record.getProductCode(),
                record.getProductName(),
                null,
                null,
                record.getQuantity(),
                record.getUnitPrice(),
                record.getAmount()));
        return items;
    }

    /**
     * 判断销售记录是否匹配搜索关键词
     * <p>
     * 该方法检查关键词是否出现在客户名称、订单号、产品名称或明细项中。
     * </p>
     *
     * @param record 销售记录对象
     * @param keyword 搜索关键词
     * @return true表示匹配，false表示不匹配
     */
    private boolean matchesKeyword(SalesRecord record, String keyword) {
        // 检查主字段是否包含关键词
        if (contains(record.getCustomerName(), keyword) || contains(record.getOrderNo(), keyword) || contains(record.getProductName(), keyword)) {
            return true;
        }

        // 检查明细项是否包含关键词
        for (SalesRecord.SalesRecordItem item : getNormalizedItems(record)) {
            if (contains(item.getProductName(), keyword)
                    || contains(item.getProductCode(), keyword)
                    || contains(item.getColor(), keyword)
                    || contains(item.getSize(), keyword)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 判断源字符串是否包含关键词（忽略大小写）
     * <p>
     * 该方法检查源字符串是否非空且包含指定的关键词。
     * </p>
     *
     * @param source 源字符串
     * @param keyword 关键词
     * @return true表示包含，false表示不包含或源字符串为空
     */
    private boolean contains(String source, String keyword) {
        return source != null && source.contains(keyword);
    }

    /**
     * 判断记录日期是否在指定日期范围内
     * <p>
     * 该方法检查记录日期是否在起始日期和结束日期之间（包含边界）。
     * </p>
     *
     * @param recordDate 记录日期
     * @param startDate 起始日期，null表示不限制
     * @param endDate 结束日期，null表示不限制
     * @return true表示在范围内，false表示不在范围内
     */
    private boolean isWithinDateRange(Date recordDate, Date startDate, Date endDate) {
        if (recordDate == null) {
            return startDate == null && endDate == null;
        }
        
        // 检查是否在起始日期之后
        if (startDate != null && recordDate.before(startDate)) {
            return false;
        }
        
        // 检查是否在结束日期之前
        return endDate == null || !recordDate.after(endDate);
    }

    /**
     * 解析销售记录的日期
     * <p>
     * 该方法按优先级返回销售记录的日期：完成日期 > 销售日期 > 订单日期 > 创建时间。
     * </p>
     *
     * @param record 销售记录对象
     * @return 解析后的日期，如果所有日期都为null则返回null
     */
    private Date resolveRecordDate(SalesRecord record) {
        if (record.getCompleteDate() != null) {
            return record.getCompleteDate();
        }
        if (record.getSaleDate() != null) {
            return record.getSaleDate();
        }
        if (record.getOrderDate() != null) {
            return record.getOrderDate();
        }
        return record.getCreateTime();
    }

    /**
     * 解析用于排序的日期
     * <p>
     * 该方法优先使用记录日期，如果为空则使用更新时间。
     * </p>
     *
     * @param record 销售记录对象
     * @return 用于排序的日期
     */
    private Date resolveSortDate(SalesRecord record) {
        Date recordDate = resolveRecordDate(record);
        return recordDate != null ? recordDate : record.getUpdateTime();
    }

    /**
     * 获取安全的总数量
     * <p>
     * 该方法优先使用记录的totalQuantity字段，如果为空则从明细项中累加计算。
     * </p>
     *
     * @param record 销售记录对象
     * @return 总数量
     */
    private int getSafeTotalQuantity(SalesRecord record) {
        // 如果已有总数量字段，直接返回
        if (record.getTotalQuantity() != null) {
            return record.getTotalQuantity();
        }
        
        // 从明细项中累加计算总数量
        return getNormalizedItems(record).stream()
                .mapToInt(item -> item.getQuantity() != null ? item.getQuantity() : 0)
                .sum();
    }

    /**
     * 获取安全的总金额
     * <p>
     * 该方法按优先级返回总金额：
     * 1. 使用记录的totalAmount字段
     * 2. 从明细项中累加计算
     * 3. 使用旧格式的amount字段
     * </p>
     *
     * @param record 销售记录对象
     * @return 总金额
     */
    private double getSafeTotalAmount(SalesRecord record) {
        // 优先使用totalAmount字段
        if (record.getTotalAmount() != null) {
            return record.getTotalAmount();
        }
        
        // 从明细项中累加计算总金额
        double amount = getNormalizedItems(record).stream()
                .mapToDouble(item -> item.getAmount() != null ? item.getAmount() : 0D)
                .sum();
        
        // 如果明细项中没有金额，则使用旧格式的amount字段
        if (amount > 0) {
            return amount;
        }
        return record.getAmount() != null ? record.getAmount() : 0D;
    }

    /**
     * 从产品名称中提取产品类别
     * <p>
     * 该方法根据产品名称判断其所属的类别，支持以下分类：
     * - 上装：衬衫、T恤、上衣等
     * - 下装：裤子、裙子等
     * - 外套：外套、夹克、大衣等
     * - 正装：西服、西装等
     * - 其他：无法归类的产品
     * </p>
     *
     * @param productName 产品名称
     * @return 产品类别名称
     */
    private String extractCategory(String productName) {
        if (productName == null) {
            return "其他";
        }
        
        // 判断是否为上装
        if (productName.contains("衬衫") || productName.contains("T恤") || productName.contains("上衣")) {
            return "上装";
        }
        
        // 判断是否为下装
        if (productName.contains("裤") || productName.contains("裙")) {
            return "下装";
        }
        
        // 判断是否为外套
        if (productName.contains("外套") || productName.contains("夹克") || productName.contains("大衣")) {
            return "外套";
        }
        
        // 判断是否为正装
        if (productName.contains("西服") || productName.contains("西装")) {
            return "正装";
        }
        
        // 默认归类为其他
        return "其他";
    }
}
