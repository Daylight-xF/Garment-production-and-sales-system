package com.garment.controller;

import com.garment.dto.*;
import com.garment.service.ProductDefinitionService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 产品定义控制器。
 *
 * <p>提供产品定义列表查询、详情查询、创建、更新和删除等接口。</p>
 */
@RestController
@RequestMapping("/api/product-definition")
public class ProductDefinitionController {
    // 产品定义业务服务，负责产品定义相关业务处理。

    private final ProductDefinitionService productDefinitionService;
    /**
     * 创建产品定义控制器。
     *
     * <p>通过构造器注入产品定义业务服务。</p>
     *
     * @param productDefinitionService 产品定义业务服务
     */

    public ProductDefinitionController(ProductDefinitionService productDefinitionService) {
        this.productDefinitionService = productDefinitionService;
    }

        /**
         * 查询产品定义列表，支持分页和多条件筛选
         * <p>
         * 接收接口请求参数，调用对应业务服务完成处理，并返回统一响应结果。
         * </p>
         *
         * @param name 产品名称，支持模糊查询
         * @param category 产品分类
         * @param status 产品状态
         * @param page 页码，从1开始
         * @param size 每页大小
         * @return 统一响应结果，包含产品定义列表、总数、页码和每页大小
         */
        @GetMapping
        @PreAuthorize("hasAnyAuthority('PRODUCT_DEFINITION_READ', 'INVENTORY_IN')")
        public Result<Map<String, Object>> getProductDefinitionList(
                @RequestParam(defaultValue = "") String name,
                @RequestParam(defaultValue = "") String category,
                @RequestParam(defaultValue = "") String status,
                @RequestParam(defaultValue = "1") int page,
                @RequestParam(defaultValue = "10") int size) {

            // 构建分页和排序参数。
            Pageable pageable = PageRequest.of(page - 1, size, Sort.by(Sort.Direction.DESC, "createTime"));
            Page<ProductDefinitionVO> pageResult = productDefinitionService.getProductDefinitionList(name, category, status, pageable);

            // 组装分页返回结构。
            Map<String, Object> result = new HashMap<>();
            result.put("list", pageResult.getContent());
            result.put("total", pageResult.getTotalElements());
            result.put("page", page);
            result.put("size", size);

            // 返回分页查询结果。
            return Result.success(result);
        }


        /**
         * 根据ID查询产品定义详情
         * <p>
         * 接收接口请求参数，调用对应业务服务完成处理，并返回统一响应结果。
         * </p>
         *
         * @param id 产品定义ID
         * @return 统一响应结果，包含产品定义详细信息
         */
        @GetMapping("/{id}")
        @PreAuthorize("hasAuthority('PRODUCT_DEFINITION_READ')")
        public Result<ProductDefinitionVO> getProductDefinitionById(@PathVariable String id) {
            // 调用业务服务处理请求。
            ProductDefinitionVO vo = productDefinitionService.getProductDefinitionById(id);
            // 返回统一成功响应。
            return Result.success(vo);
        }


        /**
         * 创建产品定义
         * <p>
         * 接收接口请求参数，调用对应业务服务完成处理，并返回统一响应结果。
         * </p>
         *
         * @param request 产品定义创建请求参数
         * @return 统一响应结果，包含创建后的产品定义信息
         */
        @PostMapping
        @PreAuthorize("hasAuthority('PRODUCT_DEFINITION_CREATE')")
        public Result<ProductDefinitionVO> createProductDefinition(@Valid @RequestBody ProductDefinitionCreateRequest request) {
            // 调用业务服务处理请求。
            ProductDefinitionVO vo = productDefinitionService.createProductDefinition(request);
            // 返回统一成功响应。
            return Result.success(vo);
        }


        /**
         * 更新产品定义
         * <p>
         * 接收接口请求参数，调用对应业务服务完成处理，并返回统一响应结果。
         * </p>
         *
         * @param id 产品定义ID
         * @param request 产品定义更新请求参数
         * @return 统一响应结果，包含更新后的产品定义信息
         */
        @PutMapping("/{id}")
        @PreAuthorize("hasAuthority('PRODUCT_DEFINITION_UPDATE')")
        public Result<ProductDefinitionVO> updateProductDefinition(@PathVariable String id,
                                                                     @Valid @RequestBody ProductDefinitionUpdateRequest request) {
            // 调用业务服务处理请求。
            ProductDefinitionVO vo = productDefinitionService.updateProductDefinition(id, request);
            // 返回统一成功响应。
            return Result.success(vo);
        }


        /**
         * 删除产品定义
         * <p>
         * 接收接口请求参数，调用对应业务服务完成处理，并返回统一响应结果。
         * </p>
         *
         * @param id 产品定义ID
         * @return 统一响应结果
         */
        @DeleteMapping("/{id}")
        @PreAuthorize("hasAuthority('PRODUCT_DEFINITION_DELETE')")
        public Result<Void> deleteProductDefinition(@PathVariable String id) {
            // 调用业务服务删除记录。
            productDefinitionService.deleteProductDefinition(id);
            // 返回统一成功响应。
            return Result.success();
        }

}
