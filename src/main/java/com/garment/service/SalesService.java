package com.garment.service;

import com.garment.dto.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Date;
import java.util.List;

public interface SalesService {

    SalesRecordVO createSalesRecord(SalesRecordCreateRequest request, String userId);

    Page<SalesRecordVO> querySalesRecords(String customerId, Date startDate, Date endDate, String keyword, Pageable pageable);

    SalesRecordVO getSalesRecordById(String id);

    SalesRecordVO updateSalesRecord(String id, SalesRecordCreateRequest request);

    void deleteSalesRecord(String id);

    SalesOverviewVO getSalesOverview();

    List<SalesTrendVO> getSalesTrend(Date startDate, Date endDate);

    List<ProductRankingVO> getProductRanking(Date startDate, Date endDate, int limit);

    List<CategoryDistributionVO> getCategoryDistribution(Date startDate, Date endDate);

    CustomerVO createCustomer(CustomerCreateRequest request, String userId);

    Page<CustomerVO> queryCustomers(String keyword, String level, Pageable pageable);

    CustomerVO getCustomerById(String id);

    CustomerVO updateCustomer(String id, CustomerUpdateRequest request);

    void deleteCustomer(String id);
}
