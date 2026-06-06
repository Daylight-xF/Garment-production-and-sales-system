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

/**
 * 销售管理控制器。
 *
 * <p>提供销售记录、销售报表和客户资料相关接口，统一委托 SalesService 处理业务逻辑。</p>
 */
@RestController
@RequestMapping("/api/sales")
public class SalesController {
    // 销售业务服务，负责销售记录、报表和客户资料处理。

    private final SalesService salesService;
    /**
     * 创建销售管理控制器。
     *
     * <p>通过构造器注入销售业务服务。</p>
     *
     * @param salesService 销售业务服务
     */

    public SalesController(SalesService salesService) {
        this.salesService = salesService;
    }


        /**
         * 创建销售记录
         * <p>
         * 接收接口请求参数，调用对应业务服务完成处理，并返回统一响应结果。
         * </p>
         *
         * @param request 销售记录创建请求参数
         * @param authentication 认证信息，用于获取用户ID
         * @return 统一响应结果，包含创建后的销售记录信息
         */
        @PostMapping("/records")
        @PreAuthorize("hasAuthority('SALES_CREATE')")
        public Result<SalesRecordVO> createSalesRecord(@Valid @RequestBody SalesRecordCreateRequest request,
                                                        Authentication authentication) {
            // 从认证上下文中获取当前用户 ID。
            String userId = (String) authentication.getPrincipal();
            // 调用业务服务处理请求。
            SalesRecordVO vo = salesService.createSalesRecord(request, userId);
            // 返回统一成功响应。
            return Result.success(vo);
        }


        /**
         * 查询销售记录列表，支持分页和多条件筛选
         * <p>
         * 接收接口请求参数，调用对应业务服务完成处理，并返回统一响应结果。
         * </p>
         *
         * @param customerId 客户ID
         * @param startDate 开始日期
         * @param endDate 结束日期
         * @param keyword 搜索关键词，支持模糊查询
         * @param page 页码，从1开始
         * @param size 每页大小
         * @return 统一响应结果，包含销售记录列表、总数、页码和每页大小
         */
        @GetMapping("/records")
        @PreAuthorize("hasAuthority('SALES_READ')")
        public Result<Map<String, Object>> querySalesRecords(
                @RequestParam(required = false) String customerId,
                @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") Date startDate,
                @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") Date endDate,
                @RequestParam(required = false) String keyword,
                @RequestParam(defaultValue = "1") int page,
                @RequestParam(defaultValue = "10") int size) {
            // 构建分页和排序参数。
            Pageable pageable = PageRequest.of(page - 1, size, Sort.by(Sort.Direction.DESC, "createTime"));
            // 查询分页数据。
            Page<SalesRecordVO> recordPage = salesService.querySalesRecords(customerId, startDate, endDate, keyword, pageable);

            // 组装分页返回结构。
            Map<String, Object> result = new HashMap<>();
            result.put("list", recordPage.getContent());
            result.put("total", recordPage.getTotalElements());
            result.put("page", page);
            result.put("size", size);

            // 返回分页查询结果。
            return Result.success(result);
        }


        /**
         * 根据ID查询销售记录详情
         * <p>
         * 接收接口请求参数，调用对应业务服务完成处理，并返回统一响应结果。
         * </p>
         *
         * @param id 销售记录ID
         * @return 统一响应结果，包含销售记录详细信息
         */
        @GetMapping("/records/{id}")
        @PreAuthorize("hasAuthority('SALES_READ')")
        public Result<SalesRecordVO> getSalesRecordById(@PathVariable String id) {
            // 调用业务服务处理请求。
            SalesRecordVO vo = salesService.getSalesRecordById(id);
            // 返回统一成功响应。
            return Result.success(vo);
        }


        /**
         * 更新销售记录
         * <p>
         * 接收接口请求参数，调用对应业务服务完成处理，并返回统一响应结果。
         * </p>
         *
         * @param id 销售记录ID
         * @param request 销售记录更新请求参数
         * @return 统一响应结果，包含更新后的销售记录信息
         */
        @PutMapping("/records/{id}")
        @PreAuthorize("hasAuthority('SALES_CREATE')")
        public Result<SalesRecordVO> updateSalesRecord(@PathVariable String id,
                                                        @Valid @RequestBody SalesRecordCreateRequest request) {
            // 调用业务服务处理请求。
            SalesRecordVO vo = salesService.updateSalesRecord(id, request);
            // 返回统一成功响应。
            return Result.success(vo);
        }


        /**
         * 删除销售记录
         * <p>
         * 接收接口请求参数，调用对应业务服务完成处理，并返回统一响应结果。
         * </p>
         *
         * @param id 销售记录ID
         * @return 统一响应结果
         */
        @DeleteMapping("/records/{id}")
        @PreAuthorize("hasAuthority('SALES_CREATE')")
        public Result<Void> deleteSalesRecord(@PathVariable String id) {
            // 调用业务服务删除记录。
            salesService.deleteSalesRecord(id);
            // 返回统一成功响应。
            return Result.success();
        }
    

        /**
         * 获取销售概览统计信息
         * <p>
         * 接收接口请求参数，调用对应业务服务完成处理，并返回统一响应结果。
         * </p>
         *
         * @return 统一响应结果，包含销售概览数据
         */
        @GetMapping("/report/overview")
        @PreAuthorize("hasAuthority('SALES_REPORT')")
        public Result<SalesOverviewVO> getSalesOverview() {
            // 调用业务服务处理请求。
            SalesOverviewVO vo = salesService.getSalesOverview();
            // 返回统一成功响应。
            return Result.success(vo);
        }


        /**
         * 获取销售趋势数据
         * <p>
         * 接收接口请求参数，调用对应业务服务完成处理，并返回统一响应结果。
         * </p>
         *
         * @param startDate 开始日期
         * @param endDate 结束日期
         * @return 统一响应结果，包含销售趋势数据列表
         */
        @GetMapping("/report/trend")
        @PreAuthorize("hasAuthority('SALES_REPORT')")
        public Result<List<SalesTrendVO>> getSalesTrend(
                @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") Date startDate,
                @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") Date endDate) {
            // 调用业务服务处理请求。
            List<SalesTrendVO> list = salesService.getSalesTrend(startDate, endDate);
            // 返回统一成功响应。
            return Result.success(list);
        }


        /**
         * 获取产品销量排行榜
         * <p>
         * 接收接口请求参数，调用对应业务服务完成处理，并返回统一响应结果。
         * </p>
         *
         * @param startDate 开始日期
         * @param endDate 结束日期
         * @param limit 排行榜数量，默认为10
         * @return 统一响应结果，包含产品销量排行列表
         */
        @GetMapping("/report/product-ranking")
        @PreAuthorize("hasAuthority('SALES_REPORT')")
        public Result<List<ProductRankingVO>> getProductRanking(
                @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") Date startDate,
                @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") Date endDate,
                @RequestParam(defaultValue = "10") int limit) {
            // 调用业务服务处理请求。
            List<ProductRankingVO> list = salesService.getProductRanking(startDate, endDate, limit);
            // 返回统一成功响应。
            return Result.success(list);
        }


        /**
         * 获取销售分类分布数据
         * <p>
         * 接收接口请求参数，调用对应业务服务完成处理，并返回统一响应结果。
         * </p>
         *
         * @param startDate 开始日期
         * @param endDate 结束日期
         * @return 统一响应结果，包含分类分布数据列表
         */
        @GetMapping("/report/category-distribution")
        @PreAuthorize("hasAuthority('SALES_REPORT')")
        public Result<List<CategoryDistributionVO>> getCategoryDistribution(
                @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") Date startDate,
                @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") Date endDate) {
            // 调用业务服务处理请求。
            List<CategoryDistributionVO> list = salesService.getCategoryDistribution(startDate, endDate);
            // 返回统一成功响应。
            return Result.success(list);
        }


        /**
         * 创建客户信息
         * <p>
         * 接收接口请求参数，调用对应业务服务完成处理，并返回统一响应结果。
         * </p>
         *
         * @param request 客户创建请求参数
         * @param authentication 认证信息，用于获取用户ID
         * @return 统一响应结果，包含创建后的客户信息
         */
        @PostMapping("/customers")
        @PreAuthorize("hasAuthority('CUSTOMER_MANAGE')")
        public Result<CustomerVO> createCustomer(@Valid @RequestBody CustomerCreateRequest request,
                                                  Authentication authentication) {
            // 从认证上下文中获取当前用户 ID。
            String userId = (String) authentication.getPrincipal();
            // 调用业务服务处理请求。
            CustomerVO vo = salesService.createCustomer(request, userId);
            // 返回统一成功响应。
            return Result.success(vo);
        }


        /**
         * 查询客户列表，支持分页和筛选
         * <p>
         * 接收接口请求参数，调用对应业务服务完成处理，并返回统一响应结果。
         * </p>
         *
         * @param keyword 搜索关键词，支持模糊查询
         * @param level 客户等级
         * @param page 页码，从1开始
         * @param size 每页大小
         * @return 统一响应结果，包含客户列表、总数、页码和每页大小
         */
        @GetMapping("/customers")
        @PreAuthorize("hasAuthority('CUSTOMER_MANAGE')")
        public Result<Map<String, Object>> queryCustomers(
                @RequestParam(required = false) String keyword,
                @RequestParam(required = false) String level,
                @RequestParam(defaultValue = "1") int page,
                @RequestParam(defaultValue = "10") int size) {
            // 构建分页和排序参数。
            Pageable pageable = PageRequest.of(page - 1, size, Sort.by(Sort.Direction.DESC, "createTime"));
            // 查询分页数据。
            Page<CustomerVO> customerPage = salesService.queryCustomers(keyword, level, pageable);

            // 组装分页返回结构。
            Map<String, Object> result = new HashMap<>();
            result.put("list", customerPage.getContent());
            result.put("total", customerPage.getTotalElements());
            result.put("page", page);
            result.put("size", size);

            // 返回分页查询结果。
            return Result.success(result);
        }


        /**
         * 根据ID查询客户详情
         * <p>
         * 接收接口请求参数，调用对应业务服务完成处理，并返回统一响应结果。
         * </p>
         *
         * @param id 客户ID
         * @return 统一响应结果，包含客户详细信息
         */
        @GetMapping("/customers/{id}")
        @PreAuthorize("hasAuthority('CUSTOMER_MANAGE')")
        public Result<CustomerVO> getCustomerById(@PathVariable String id) {
            // 调用业务服务处理请求。
            CustomerVO vo = salesService.getCustomerById(id);
            // 返回统一成功响应。
            return Result.success(vo);
        }


        /**
         * 更新客户信息
         * <p>
         * 接收接口请求参数，调用对应业务服务完成处理，并返回统一响应结果。
         * </p>
         *
         * @param id 客户ID
         * @param request 客户更新请求参数
         * @return 统一响应结果，包含更新后的客户信息
         */
        @PutMapping("/customers/{id}")
        @PreAuthorize("hasAuthority('CUSTOMER_MANAGE')")
        public Result<CustomerVO> updateCustomer(@PathVariable String id,
                                                  @RequestBody CustomerUpdateRequest request) {
            // 调用业务服务处理请求。
            CustomerVO vo = salesService.updateCustomer(id, request);
            // 返回统一成功响应。
            return Result.success(vo);
        }


        /**
         * 删除客户记录
         * <p>
         * 接收接口请求参数，调用对应业务服务完成处理，并返回统一响应结果。
         * </p>
         *
         * @param id 客户ID
         * @return 统一响应结果
         */
        @DeleteMapping("/customers/{id}")
        @PreAuthorize("hasAuthority('CUSTOMER_MANAGE')")
        public Result<Void> deleteCustomer(@PathVariable String id) {
            // 调用业务服务删除记录。
            salesService.deleteCustomer(id);
            // 返回统一成功响应。
            return Result.success();
        }

    
}
