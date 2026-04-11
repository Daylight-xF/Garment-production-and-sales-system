package com.garment.controller;

import com.garment.dto.*;
import com.garment.service.SalesService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/sales")
public class SalesController {

    private final SalesService salesService;

    public SalesController(SalesService salesService) {
        this.salesService = salesService;
    }

    @PostMapping("/records")
    @PreAuthorize("hasAuthority('SALES_CREATE')")
    public Result<SalesRecordVO> createSalesRecord(@Valid @RequestBody SalesRecordCreateRequest request,
                                                    Authentication authentication) {
        String userId = (String) authentication.getPrincipal();
        SalesRecordVO vo = salesService.createSalesRecord(request, userId);
        return Result.success(vo);
    }

    @GetMapping("/records")
    @PreAuthorize("hasAuthority('SALES_READ')")
    public Result<Map<String, Object>> querySalesRecords(
            @RequestParam(required = false) String customerId,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") Date startDate,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") Date endDate,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        Pageable pageable = PageRequest.of(page - 1, size, Sort.by(Sort.Direction.DESC, "createTime"));
        Page<SalesRecordVO> recordPage = salesService.querySalesRecords(customerId, startDate, endDate, keyword, pageable);

        Map<String, Object> result = new HashMap<>();
        result.put("list", recordPage.getContent());
        result.put("total", recordPage.getTotalElements());
        result.put("page", page);
        result.put("size", size);

        return Result.success(result);
    }

    @GetMapping("/records/{id}")
    @PreAuthorize("hasAuthority('SALES_READ')")
    public Result<SalesRecordVO> getSalesRecordById(@PathVariable String id) {
        SalesRecordVO vo = salesService.getSalesRecordById(id);
        return Result.success(vo);
    }

    @PutMapping("/records/{id}")
    @PreAuthorize("hasAuthority('SALES_CREATE')")
    public Result<SalesRecordVO> updateSalesRecord(@PathVariable String id,
                                                    @Valid @RequestBody SalesRecordCreateRequest request) {
        SalesRecordVO vo = salesService.updateSalesRecord(id, request);
        return Result.success(vo);
    }

    @DeleteMapping("/records/{id}")
    @PreAuthorize("hasAuthority('SALES_CREATE')")
    public Result<Void> deleteSalesRecord(@PathVariable String id) {
        salesService.deleteSalesRecord(id);
        return Result.success();
    }

    @GetMapping("/report/overview")
    @PreAuthorize("hasAuthority('SALES_REPORT')")
    public Result<SalesOverviewVO> getSalesOverview() {
        SalesOverviewVO vo = salesService.getSalesOverview();
        return Result.success(vo);
    }

    @GetMapping("/report/trend")
    @PreAuthorize("hasAuthority('SALES_REPORT')")
    public Result<List<SalesTrendVO>> getSalesTrend(
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") Date startDate,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") Date endDate) {
        List<SalesTrendVO> list = salesService.getSalesTrend(startDate, endDate);
        return Result.success(list);
    }

    @GetMapping("/report/product-ranking")
    @PreAuthorize("hasAuthority('SALES_REPORT')")
    public Result<List<ProductRankingVO>> getProductRanking(
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") Date startDate,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") Date endDate,
            @RequestParam(defaultValue = "10") int limit) {
        List<ProductRankingVO> list = salesService.getProductRanking(startDate, endDate, limit);
        return Result.success(list);
    }

    @GetMapping("/report/category-distribution")
    @PreAuthorize("hasAuthority('SALES_REPORT')")
    public Result<List<CategoryDistributionVO>> getCategoryDistribution(
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") Date startDate,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") Date endDate) {
        List<CategoryDistributionVO> list = salesService.getCategoryDistribution(startDate, endDate);
        return Result.success(list);
    }

    @PostMapping("/customers")
    @PreAuthorize("hasAuthority('CUSTOMER_MANAGE')")
    public Result<CustomerVO> createCustomer(@Valid @RequestBody CustomerCreateRequest request,
                                              Authentication authentication) {
        String userId = (String) authentication.getPrincipal();
        CustomerVO vo = salesService.createCustomer(request, userId);
        return Result.success(vo);
    }

    @GetMapping("/customers")
    @PreAuthorize("hasAuthority('CUSTOMER_MANAGE')")
    public Result<Map<String, Object>> queryCustomers(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String level,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        Pageable pageable = PageRequest.of(page - 1, size, Sort.by(Sort.Direction.DESC, "createTime"));
        Page<CustomerVO> customerPage = salesService.queryCustomers(keyword, level, pageable);

        Map<String, Object> result = new HashMap<>();
        result.put("list", customerPage.getContent());
        result.put("total", customerPage.getTotalElements());
        result.put("page", page);
        result.put("size", size);

        return Result.success(result);
    }

    @GetMapping("/customers/{id}")
    @PreAuthorize("hasAuthority('CUSTOMER_MANAGE')")
    public Result<CustomerVO> getCustomerById(@PathVariable String id) {
        CustomerVO vo = salesService.getCustomerById(id);
        return Result.success(vo);
    }

    @PutMapping("/customers/{id}")
    @PreAuthorize("hasAuthority('CUSTOMER_MANAGE')")
    public Result<CustomerVO> updateCustomer(@PathVariable String id,
                                              @RequestBody CustomerUpdateRequest request) {
        CustomerVO vo = salesService.updateCustomer(id, request);
        return Result.success(vo);
    }

    @DeleteMapping("/customers/{id}")
    @PreAuthorize("hasAuthority('CUSTOMER_MANAGE')")
    public Result<Void> deleteCustomer(@PathVariable String id) {
        salesService.deleteCustomer(id);
        return Result.success();
    }
}
