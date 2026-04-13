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

    @Override
    public SalesRecordVO createSalesRecord(SalesRecordCreateRequest request, String userId) {
        Customer customer = customerRepository.findById(request.getCustomerId())
                .orElseThrow(() -> new BusinessException("客户不存在"));

        Date saleDate = request.getSaleDate() != null ? request.getSaleDate() : new Date();
        SalesRecord record = new SalesRecord();
        record.setCustomerId(request.getCustomerId());
        record.setCustomerName(customer.getName());
        record.setProductId(request.getProductId());
        record.setProductName(request.getProductName());
        record.setQuantity(request.getQuantity());
        record.setUnitPrice(request.getUnitPrice());
        record.setAmount(request.getQuantity() * request.getUnitPrice());
        record.setSaleDate(saleDate);
        record.setOrderDate(saleDate);
        record.setCompleteDate(saleDate);
        record.setRemark(request.getRemark());
        record.setCreateBy(userId);

        if (request.getProductId() != null) {
            productDefinitionRepository.findById(request.getProductId())
                    .ifPresent(productDef -> record.setProductCode(productDef.getProductCode()));
        }

        Optional<User> userOpt = userRepository.findById(userId);
        userOpt.ifPresent(user -> record.setCreateByName(user.getRealName()));

        syncRecordSummary(record);
        SalesRecord saved = salesRecordRepository.save(record);
        return convertToSalesRecordVO(saved);
    }

    @Override
    public Page<SalesRecordVO> querySalesRecords(String customerId, Date startDate, Date endDate, String keyword, Pageable pageable) {
        List<SalesRecord> filtered = salesRecordRepository.findAll().stream()
                .filter(record -> !StringUtils.hasText(customerId) || customerId.equals(record.getCustomerId()))
                .filter(record -> isWithinDateRange(resolveRecordDate(record), startDate, endDate))
                .filter(record -> !StringUtils.hasText(keyword) || matchesKeyword(record, keyword))
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

        int start = (int) pageable.getOffset();
        int end = Math.min(start + pageable.getPageSize(), filtered.size());
        List<SalesRecord> pageContent = start < filtered.size() ? filtered.subList(start, end) : new ArrayList<>();

        List<SalesRecordVO> voList = pageContent.stream()
                .map(this::convertToSalesRecordVO)
                .collect(Collectors.toList());

        return new PageImpl<>(voList, pageable, filtered.size());
    }

    @Override
    public SalesRecordVO getSalesRecordById(String id) {
        SalesRecord record = salesRecordRepository.findById(id)
                .orElseThrow(() -> new BusinessException("销售记录不存在"));
        return convertToSalesRecordVO(record);
    }

    @Override
    public SalesRecordVO updateSalesRecord(String id, SalesRecordCreateRequest request) {
        SalesRecord record = salesRecordRepository.findById(id)
                .orElseThrow(() -> new BusinessException("销售记录不存在"));

        if (StringUtils.hasText(request.getCustomerId())) {
            Customer customer = customerRepository.findById(request.getCustomerId())
                    .orElseThrow(() -> new BusinessException("客户不存在"));
            record.setCustomerId(request.getCustomerId());
            record.setCustomerName(customer.getName());
        }
        if (request.getProductName() != null) {
            record.setProductName(request.getProductName());
        }
        if (request.getProductId() != null) {
            record.setProductId(request.getProductId());
        }
        if (request.getQuantity() != null) {
            record.setQuantity(request.getQuantity());
        }
        if (request.getUnitPrice() != null) {
            record.setUnitPrice(request.getUnitPrice());
        }
        if (request.getQuantity() != null || request.getUnitPrice() != null) {
            double quantity = record.getQuantity() != null ? record.getQuantity() : 0;
            double unitPrice = record.getUnitPrice() != null ? record.getUnitPrice() : 0;
            record.setAmount(quantity * unitPrice);
        }
        if (request.getSaleDate() != null) {
            record.setSaleDate(request.getSaleDate());
            if (!StringUtils.hasText(record.getOrderId())) {
                record.setOrderDate(request.getSaleDate());
                record.setCompleteDate(request.getSaleDate());
            }
        }
        if (request.getRemark() != null) {
            record.setRemark(request.getRemark());
        }

        syncRecordSummary(record);
        SalesRecord saved = salesRecordRepository.save(record);
        return convertToSalesRecordVO(saved);
    }

    @Override
    public void deleteSalesRecord(String id) {
        if (!salesRecordRepository.existsById(id)) {
            throw new BusinessException("销售记录不存在");
        }
        salesRecordRepository.deleteById(id);
    }

    @Override
    public SalesOverviewVO getSalesOverview() {
        List<SalesRecord> allRecords = salesRecordRepository.findAll();
        List<Customer> allCustomers = customerRepository.findAll();

        double totalAmount = allRecords.stream()
                .mapToDouble(this::getSafeTotalAmount)
                .sum();
        long totalOrders = allRecords.size();
        double avgOrderAmount = totalOrders > 0 ? totalAmount / totalOrders : 0;

        return SalesOverviewVO.builder()
                .totalAmount(totalAmount)
                .totalOrders(totalOrders)
                .avgOrderAmount(avgOrderAmount)
                .customerCount((long) allCustomers.size())
                .build();
    }

    @Override
    public List<SalesTrendVO> getSalesTrend(Date startDate, Date endDate) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
        Map<String, Double> dateAmountMap = new TreeMap<>();

        for (SalesRecord record : salesRecordRepository.findAll()) {
            Date recordDate = resolveRecordDate(record);
            if (!isWithinDateRange(recordDate, startDate, endDate)) {
                continue;
            }
            String dateKey = sdf.format(recordDate);
            dateAmountMap.merge(dateKey, getSafeTotalAmount(record), Double::sum);
        }

        return dateAmountMap.entrySet().stream()
                .map(entry -> SalesTrendVO.builder()
                        .date(entry.getKey())
                        .amount(entry.getValue())
                        .build())
                .collect(Collectors.toList());
    }

    @Override
    public List<ProductRankingVO> getProductRanking(Date startDate, Date endDate, int limit) {
        Map<String, ProductRankingVO> productMap = new LinkedHashMap<>();

        for (SalesRecord record : salesRecordRepository.findAll()) {
            if (!isWithinDateRange(resolveRecordDate(record), startDate, endDate)) {
                continue;
            }

            for (SalesRecord.SalesRecordItem item : getNormalizedItems(record)) {
                String productName = StringUtils.hasText(item.getProductName()) ? item.getProductName() : "未知产品";
                ProductRankingVO vo = productMap.getOrDefault(productName,
                        ProductRankingVO.builder().productName(productName).quantity(0).amount(0.0).build());
                vo.setQuantity(vo.getQuantity() + (item.getQuantity() != null ? item.getQuantity() : 0));
                vo.setAmount(vo.getAmount() + (item.getAmount() != null ? item.getAmount() : 0));
                productMap.put(productName, vo);
            }
        }

        return productMap.values().stream()
                .sorted((a, b) -> Integer.compare(b.getQuantity(), a.getQuantity()))
                .limit(limit)
                .collect(Collectors.toList());
    }

    @Override
    public List<CategoryDistributionVO> getCategoryDistribution(Date startDate, Date endDate) {
        Map<String, Double> categoryMap = new LinkedHashMap<>();

        for (SalesRecord record : salesRecordRepository.findAll()) {
            if (!isWithinDateRange(resolveRecordDate(record), startDate, endDate)) {
                continue;
            }

            for (SalesRecord.SalesRecordItem item : getNormalizedItems(record)) {
                String category = extractCategory(item.getProductName());
                categoryMap.merge(category, item.getAmount() != null ? item.getAmount() : 0, Double::sum);
            }
        }

        return categoryMap.entrySet().stream()
                .map(entry -> CategoryDistributionVO.builder()
                        .category(entry.getKey())
                        .amount(entry.getValue())
                        .build())
                .collect(Collectors.toList());
    }

    @Override
    public CustomerVO createCustomer(CustomerCreateRequest request, String userId) {
        Customer customer = new Customer();
        customer.setName(request.getName());
        customer.setContactPerson(request.getContactPerson());
        customer.setPhone(request.getPhone());
        customer.setEmail(request.getEmail());
        customer.setAddress(request.getAddress());
        customer.setLevel(request.getLevel() != null ? request.getLevel() : "NEW");
        customer.setRemark(request.getRemark());
        customer.setCreateBy(userId);

        Customer saved = customerRepository.save(customer);
        return convertToCustomerVO(saved);
    }

    @Override
    public Page<CustomerVO> queryCustomers(String keyword, String level, Pageable pageable) {
        List<Customer> allCustomers = customerRepository.findAll();

        List<Customer> filtered = allCustomers.stream()
                .filter(c -> {
                    if (StringUtils.hasText(keyword)) {
                        boolean match = (c.getName() != null && c.getName().contains(keyword))
                                || (c.getContactPerson() != null && c.getContactPerson().contains(keyword))
                                || (c.getPhone() != null && c.getPhone().contains(keyword));
                        if (!match) {
                            return false;
                        }
                    }
                    return !StringUtils.hasText(level) || level.equals(c.getLevel());
                })
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

        int start = (int) pageable.getOffset();
        int end = Math.min(start + pageable.getPageSize(), filtered.size());
        List<Customer> pageContent = start < filtered.size() ? filtered.subList(start, end) : new ArrayList<>();

        List<CustomerVO> voList = pageContent.stream()
                .map(this::convertToCustomerVO)
                .collect(Collectors.toList());

        return new PageImpl<>(voList, pageable, filtered.size());
    }

    @Override
    public CustomerVO getCustomerById(String id) {
        Customer customer = customerRepository.findById(id)
                .orElseThrow(() -> new BusinessException("客户不存在"));
        return convertToCustomerVO(customer);
    }

    @Override
    public CustomerVO updateCustomer(String id, CustomerUpdateRequest request) {
        Customer customer = customerRepository.findById(id)
                .orElseThrow(() -> new BusinessException("客户不存在"));

        if (request.getName() != null) {
            customer.setName(request.getName());
        }
        if (request.getContactPerson() != null) {
            customer.setContactPerson(request.getContactPerson());
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

        Customer saved = customerRepository.save(customer);
        return convertToCustomerVO(saved);
    }

    @Override
    public void deleteCustomer(String id) {
        if (!customerRepository.existsById(id)) {
            throw new BusinessException("客户不存在");
        }
        customerRepository.deleteById(id);
    }

    private SalesRecordVO convertToSalesRecordVO(SalesRecord record) {
        List<SalesRecordVO.SalesRecordItemVO> itemVOs = getNormalizedItems(record).stream()
                .map(item -> SalesRecordVO.SalesRecordItemVO.builder()
                        .productId(item.getProductId())
                        .productCode(item.getProductCode())
                        .productName(item.getProductName())
                        .specification(item.getSpecification())
                        .quantity(item.getQuantity())
                        .unitPrice(item.getUnitPrice())
                        .amount(item.getAmount())
                        .build())
                .collect(Collectors.toList());

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

    private CustomerVO convertToCustomerVO(Customer customer) {
        return CustomerVO.builder()
                .id(customer.getId())
                .name(customer.getName())
                .contactPerson(customer.getContactPerson())
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

    private void syncRecordSummary(SalesRecord record) {
        List<SalesRecord.SalesRecordItem> items = getNormalizedItems(record);
        if (record.getItems() == null || record.getItems().isEmpty()) {
            record.setItems(items.isEmpty() ? null : items);
        }

        int totalQuantity = items.stream()
                .mapToInt(item -> item.getQuantity() != null ? item.getQuantity() : 0)
                .sum();
        double totalAmount = items.stream()
                .mapToDouble(item -> item.getAmount() != null ? item.getAmount() : 0D)
                .sum();

        if (totalAmount == 0 && record.getAmount() != null) {
            totalAmount = record.getAmount();
        }
        if (totalQuantity == 0 && record.getQuantity() != null) {
            totalQuantity = record.getQuantity();
        }

        record.setProductCount(items.isEmpty() ? 0 : items.size());
        record.setTotalQuantity(totalQuantity);
        record.setTotalAmount(totalAmount);

        if (!StringUtils.hasText(record.getOrderId())) {
            if (record.getOrderDate() == null) {
                record.setOrderDate(record.getSaleDate());
            }
            if (record.getCompleteDate() == null) {
                record.setCompleteDate(record.getSaleDate());
            }
        }
        if (record.getSaleDate() == null) {
            record.setSaleDate(record.getCompleteDate() != null ? record.getCompleteDate() : record.getOrderDate());
        }

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

    private List<SalesRecord.SalesRecordItem> getNormalizedItems(SalesRecord record) {
        if (record.getItems() != null && !record.getItems().isEmpty()) {
            return record.getItems();
        }

        boolean hasLegacyProduct = StringUtils.hasText(record.getProductName())
                || StringUtils.hasText(record.getProductId())
                || StringUtils.hasText(record.getProductCode());
        if (!hasLegacyProduct) {
            return new ArrayList<>();
        }

        List<SalesRecord.SalesRecordItem> items = new ArrayList<>();
        items.add(new SalesRecord.SalesRecordItem(
                record.getProductId(),
                record.getProductCode(),
                record.getProductName(),
                null,
                record.getQuantity(),
                record.getUnitPrice(),
                record.getAmount()));
        return items;
    }

    private boolean matchesKeyword(SalesRecord record, String keyword) {
        if (contains(record.getCustomerName(), keyword) || contains(record.getOrderNo(), keyword) || contains(record.getProductName(), keyword)) {
            return true;
        }

        for (SalesRecord.SalesRecordItem item : getNormalizedItems(record)) {
            if (contains(item.getProductName(), keyword)
                    || contains(item.getProductCode(), keyword)
                    || contains(item.getSpecification(), keyword)) {
                return true;
            }
        }
        return false;
    }

    private boolean contains(String source, String keyword) {
        return source != null && source.contains(keyword);
    }

    private boolean isWithinDateRange(Date recordDate, Date startDate, Date endDate) {
        if (recordDate == null) {
            return startDate == null && endDate == null;
        }
        if (startDate != null && recordDate.before(startDate)) {
            return false;
        }
        return endDate == null || !recordDate.after(endDate);
    }

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

    private Date resolveSortDate(SalesRecord record) {
        Date recordDate = resolveRecordDate(record);
        return recordDate != null ? recordDate : record.getUpdateTime();
    }

    private int getSafeTotalQuantity(SalesRecord record) {
        if (record.getTotalQuantity() != null) {
            return record.getTotalQuantity();
        }
        return getNormalizedItems(record).stream()
                .mapToInt(item -> item.getQuantity() != null ? item.getQuantity() : 0)
                .sum();
    }

    private double getSafeTotalAmount(SalesRecord record) {
        if (record.getTotalAmount() != null) {
            return record.getTotalAmount();
        }
        double amount = getNormalizedItems(record).stream()
                .mapToDouble(item -> item.getAmount() != null ? item.getAmount() : 0D)
                .sum();
        if (amount > 0) {
            return amount;
        }
        return record.getAmount() != null ? record.getAmount() : 0D;
    }

    private String extractCategory(String productName) {
        if (productName == null) {
            return "其他";
        }
        if (productName.contains("衬衫") || productName.contains("T恤") || productName.contains("上衣")) {
            return "上装";
        }
        if (productName.contains("裤") || productName.contains("裙")) {
            return "下装";
        }
        if (productName.contains("外套") || productName.contains("夹克") || productName.contains("大衣")) {
            return "外套";
        }
        if (productName.contains("西服") || productName.contains("西装")) {
            return "正装";
        }
        return "其他";
    }
}
