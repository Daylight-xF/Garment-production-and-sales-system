package com.garment.service.impl;

import com.garment.dto.*;
import com.garment.exception.BusinessException;
import com.garment.model.Customer;
import com.garment.model.ProductDefinition;
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
import java.util.*;
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

        SalesRecord record = new SalesRecord();
        record.setCustomerId(request.getCustomerId());
        record.setCustomerName(customer.getName());
        record.setProductId(request.getProductId());
        record.setProductName(request.getProductName());
        record.setQuantity(request.getQuantity());
        record.setUnitPrice(request.getUnitPrice());
        record.setAmount(request.getQuantity() * request.getUnitPrice());
        record.setSaleDate(request.getSaleDate() != null ? request.getSaleDate() : new Date());
        record.setRemark(request.getRemark());
        record.setCreateBy(userId);

        if (request.getProductId() != null) {
            productDefinitionRepository.findById(request.getProductId())
                    .ifPresent(productDef -> record.setProductCode(productDef.getProductCode()));
        }

        Optional<User> userOpt = userRepository.findById(userId);
        userOpt.ifPresent(user -> record.setCreateByName(user.getRealName()));

        SalesRecord saved = salesRecordRepository.save(record);
        return convertToSalesRecordVO(saved);
    }

    @Override
    public Page<SalesRecordVO> querySalesRecords(String customerId, Date startDate, Date endDate, String keyword, Pageable pageable) {
        List<SalesRecord> allRecords = salesRecordRepository.findAll();

        List<SalesRecord> filtered = allRecords.stream()
                .filter(r -> {
                    if (StringUtils.hasText(customerId) && !customerId.equals(r.getCustomerId())) {
                        return false;
                    }
                    if (startDate != null && r.getSaleDate() != null && r.getSaleDate().before(startDate)) {
                        return false;
                    }
                    if (endDate != null && r.getSaleDate() != null && r.getSaleDate().after(endDate)) {
                        return false;
                    }
                    if (StringUtils.hasText(keyword)) {
                        boolean match = (r.getCustomerName() != null && r.getCustomerName().contains(keyword))
                                || (r.getProductName() != null && r.getProductName().contains(keyword));
                        if (!match) return false;
                    }
                    return true;
                })
                .sorted((a, b) -> {
                    if (a.getCreateTime() == null && b.getCreateTime() == null) return 0;
                    if (a.getCreateTime() == null) return 1;
                    if (b.getCreateTime() == null) return -1;
                    return b.getCreateTime().compareTo(a.getCreateTime());
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
            record.setAmount(record.getQuantity() * record.getUnitPrice());
        }
        if (request.getSaleDate() != null) {
            record.setSaleDate(request.getSaleDate());
        }
        if (request.getRemark() != null) {
            record.setRemark(request.getRemark());
        }

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
                .mapToDouble(r -> r.getAmount() != null ? r.getAmount() : 0)
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
        List<SalesRecord> records;
        if (startDate != null && endDate != null) {
            records = salesRecordRepository.findBySaleDateBetween(startDate, endDate);
        } else {
            records = salesRecordRepository.findAll();
        }

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
        Map<String, Double> dateAmountMap = new TreeMap<>();

        for (SalesRecord record : records) {
            if (record.getSaleDate() != null) {
                String dateKey = sdf.format(record.getSaleDate());
                dateAmountMap.merge(dateKey, record.getAmount() != null ? record.getAmount() : 0, Double::sum);
            }
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
        List<SalesRecord> records;
        if (startDate != null && endDate != null) {
            records = salesRecordRepository.findBySaleDateBetween(startDate, endDate);
        } else {
            records = salesRecordRepository.findAll();
        }

        Map<String, ProductRankingVO> productMap = new LinkedHashMap<>();
        for (SalesRecord record : records) {
            String productName = record.getProductName() != null ? record.getProductName() : "未知产品";
            ProductRankingVO vo = productMap.getOrDefault(productName,
                    ProductRankingVO.builder().productName(productName).quantity(0).amount(0.0).build());
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
    public List<CategoryDistributionVO> getCategoryDistribution(Date startDate, Date endDate) {
        List<SalesRecord> records;
        if (startDate != null && endDate != null) {
            records = salesRecordRepository.findBySaleDateBetween(startDate, endDate);
        } else {
            records = salesRecordRepository.findAll();
        }

        Map<String, Double> categoryMap = new LinkedHashMap<>();
        for (SalesRecord record : records) {
            String category = extractCategory(record.getProductName());
            categoryMap.merge(category, record.getAmount() != null ? record.getAmount() : 0, Double::sum);
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
                        if (!match) return false;
                    }
                    if (StringUtils.hasText(level) && !level.equals(c.getLevel())) {
                        return false;
                    }
                    return true;
                })
                .sorted((a, b) -> {
                    if (a.getCreateTime() == null && b.getCreateTime() == null) return 0;
                    if (a.getCreateTime() == null) return 1;
                    if (b.getCreateTime() == null) return -1;
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
        return SalesRecordVO.builder()
                .id(record.getId())
                .customerId(record.getCustomerId())
                .customerName(record.getCustomerName())
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

    private String extractCategory(String productName) {
        if (productName == null) return "其他";
        if (productName.contains("衬衫") || productName.contains("T恤") || productName.contains("上衣")) {
            return "上装";
        } else if (productName.contains("裤") || productName.contains("裙")) {
            return "下装";
        } else if (productName.contains("外套") || productName.contains("夹克") || productName.contains("大衣")) {
            return "外套";
        } else if (productName.contains("西服") || productName.contains("西装")) {
            return "正装";
        } else {
            return "其他";
        }
    }
}
