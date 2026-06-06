package com.garment.controller;

import com.garment.dto.*;
import com.garment.service.InventoryService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 库存管理控制器。
 *
 * <p>提供原材料、成品、库存出入库、库存记录、库存预警和库位移动相关接口。</p>
 */
@RestController
@RequestMapping("/api/inventory")
public class InventoryController {

    // 库存业务服务，负责具体库存查询、维护、出入库和预警处理逻辑。
    private final InventoryService inventoryService;

    /**
     * 创建库存管理控制器。
     *
     * <p>通过构造器注入库存业务服务。</p>
     *
     * @param inventoryService 库存业务服务
     */
    public InventoryController(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }


        /**
         * 查询原材料库存列表，支持分页和筛选
         * <p>
         * 根据名称、分类和分页参数查询原材料库存，并组装前端列表分页结构。
         * </p>
         *
         * @param name 原材料名称，支持模糊查询
         * @param category 分类名称
         * @param page 页码，从1开始
         * @param size 每页大小
         * @return 统一响应结果，包含原材料列表、总数、页码和每页大小
         */
        @GetMapping("/raw-materials")
        @PreAuthorize("hasAuthority('INVENTORY_READ')")
        public Result<Map<String, Object>> getRawMaterialList(
                @RequestParam(defaultValue = "") String name,
                @RequestParam(defaultValue = "") String category,
                @RequestParam(defaultValue = "1") int page,
                @RequestParam(defaultValue = "10") int size) {
            // 构建分页和排序参数。
            Pageable pageable = PageRequest.of(page - 1, size, Sort.by(Sort.Direction.DESC, "createTime"));

            // 查询原材料分页数据。
            Page<RawMaterialVO> materialPage = inventoryService.getRawMaterialList(name, category, pageable);

            // 组装列表分页返回结构。
            Map<String, Object> result = new HashMap<>();
            result.put("list", materialPage.getContent());
            result.put("total", materialPage.getTotalElements());
            result.put("page", page);
            result.put("size", size);

            // 返回原材料列表查询结果。
            return Result.success(result);
        }


        /**
         * 根据ID查询原材料详情
         * <p>
         * 根据路径中的原材料 ID 查询单条原材料库存详情。
         * </p>
         *
         * @param id 原材料ID
         * @return 统一响应结果，包含原材料详细信息
         */
        @GetMapping("/raw-materials/{id}")
        @PreAuthorize("hasAuthority('INVENTORY_READ')")
        public Result<RawMaterialVO> getRawMaterialById(@PathVariable String id) {
            // 查询原材料详情。
            RawMaterialVO vo = inventoryService.getRawMaterialById(id);

            // 返回原材料详情结果。
            return Result.success(vo);
        }


        /**
         * 创建原材料入库记录
         * <p>
         * 接收原材料创建请求，校验通过后创建原材料库存记录。
         * </p>
         *
         * @param request 原材料创建请求参数
         * @return 统一响应结果，包含创建后的原材料信息
         */
        @PostMapping("/raw-materials")
        @PreAuthorize("hasAuthority('INVENTORY_IN')")
        public Result<RawMaterialVO> createRawMaterial(@Valid @RequestBody RawMaterialCreateRequest request) {
            // 调用库存服务创建原材料。
            RawMaterialVO vo = inventoryService.createRawMaterial(request);

            // 返回创建后的原材料信息。
            return Result.success(vo);
        }


        /**
         * 更新原材料信息
         * <p>
         * 根据原材料 ID 和更新请求修改原材料基础信息。
         * </p>
         *
         * @param id 原材料ID
         * @param request 原材料更新请求参数
         * @return 统一响应结果，包含更新后的原材料信息
         */
        @PutMapping("/raw-materials/{id}")
        @PreAuthorize("hasAuthority('INVENTORY_IN')")
        public Result<RawMaterialVO> updateRawMaterial(@PathVariable String id,
                                                         @RequestBody RawMaterialUpdateRequest request) {
            // 调用库存服务更新原材料信息。
            RawMaterialVO vo = inventoryService.updateRawMaterial(id, request);

            // 返回更新后的原材料信息。
            return Result.success(vo);
        }


        /**
         * 删除原材料记录
         * <p>
         * 根据原材料 ID 删除原材料库存记录。
         * </p>
         *
         * @param id 原材料ID
         * @return 统一响应结果
         */
        @DeleteMapping("/raw-materials/{id}")
        @PreAuthorize("hasAuthority('INVENTORY_IN')")
        public Result<Void> deleteRawMaterial(@PathVariable String id) {
            // 调用库存服务删除原材料。
            inventoryService.deleteRawMaterial(id);

            // 返回删除成功响应。
            return Result.success();
        }


        /**
         * 查询成品库存列表，支持分页和筛选
         * <p>
         * 根据关键词、分类和分页参数查询成品库存，并组装前端列表分页结构。
         * </p>
         *
         * @param keyword 搜索关键词，支持模糊查询
         * @param category 分类名称
         * @param page 页码，从1开始
         * @param size 每页大小
         * @return 统一响应结果，包含成品列表、总数、页码和每页大小
         */
        @GetMapping("/finished-products")
        @PreAuthorize("hasAnyAuthority('INVENTORY_READ', 'ORDER_CREATE')")
        public Result<Map<String, Object>> getFinishedProductList(
                @RequestParam(defaultValue = "") String keyword,
                @RequestParam(defaultValue = "") String category,
                @RequestParam(defaultValue = "1") int page,
                @RequestParam(defaultValue = "10") int size) {
            // 构建分页和排序参数。
            Pageable pageable = PageRequest.of(page - 1, size, Sort.by(Sort.Direction.DESC, "createTime"));

            // 查询成品分页数据。
            Page<FinishedProductVO> productPage = inventoryService.getFinishedProductList(keyword, category, pageable);

            // 组装列表分页返回结构。
            Map<String, Object> result = new HashMap<>();
            result.put("list", productPage.getContent());
            result.put("total", productPage.getTotalElements());
            result.put("page", page);
            result.put("size", size);

            // 返回成品列表查询结果。
            return Result.success(result);
        }


        /**
         * 获取成品分类列表
         * <p>
         * 查询当前成品库存中可用的分类名称列表。
         * </p>
         *
         * @return 统一响应结果，包含所有成品分类名称列表
         */
        @GetMapping("/finished-products/categories")
        @PreAuthorize("hasAnyAuthority('INVENTORY_READ', 'ORDER_CREATE')")
        public Result<List<String>> getFinishedProductCategories() {
            // 查询并返回成品分类列表。
            return Result.success(inventoryService.getFinishedProductCategories());
        }


        /**
         * 根据ID查询成品详情
         * <p>
         * 根据路径中的成品 ID 查询单条成品库存详情。
         * </p>
         *
         * @param id 成品ID
         * @return 统一响应结果，包含成品详细信息
         */
        @GetMapping("/finished-products/{id}")
        @PreAuthorize("hasAuthority('INVENTORY_READ')")
        public Result<FinishedProductVO> getFinishedProductById(@PathVariable String id) {
            // 查询成品详情。
            FinishedProductVO vo = inventoryService.getFinishedProductById(id);

            // 返回成品详情结果。
            return Result.success(vo);
        }


        /**
         * 创建成品入库记录
         * <p>
         * 接收成品创建请求，校验通过后创建成品库存记录。
         * </p>
         *
         * @param request 成品创建请求参数
         * @return 统一响应结果，包含创建后的成品信息
         */
        @PostMapping("/finished-products")
        @PreAuthorize("hasAuthority('INVENTORY_IN')")
        public Result<FinishedProductVO> createFinishedProduct(@Valid @RequestBody FinishedProductCreateRequest request) {
            // 调用库存服务创建成品。
            FinishedProductVO vo = inventoryService.createFinishedProduct(request);

            // 返回创建后的成品信息。
            return Result.success(vo);
        }


        /**
         * 更新成品信息
         * <p>
         * 根据成品 ID 和更新请求修改成品基础信息。
         * </p>
         *
         * @param id 成品ID
         * @param request 成品更新请求参数
         * @return 统一响应结果，包含更新后的成品信息
         */
        @PutMapping("/finished-products/{id}")
        @PreAuthorize("hasAuthority('INVENTORY_IN')")
        public Result<FinishedProductVO> updateFinishedProduct(@PathVariable String id,
                                                                 @RequestBody FinishedProductUpdateRequest request) {
            // 调用库存服务更新成品信息。
            FinishedProductVO vo = inventoryService.updateFinishedProduct(id, request);

            // 返回更新后的成品信息。
            return Result.success(vo);
        }


        /**
         * 删除成品记录
         * <p>
         * 根据成品 ID 删除成品库存记录。
         * </p>
         *
         * @param id 成品ID
         * @return 统一响应结果
         */
        @DeleteMapping("/finished-products/{id}")
        @PreAuthorize("hasAuthority('INVENTORY_IN')")
        public Result<Void> deleteFinishedProduct(@PathVariable String id) {
            // 调用库存服务删除成品。
            inventoryService.deleteFinishedProduct(id);

            // 返回删除成功响应。
            return Result.success();
        }


        /**
         * 入库操作，包括原材料或成品入库
         * <p>
         * 从认证信息中获取操作员 ID，并提交原材料或成品入库请求。
         * </p>
         *
         * @param request 入库请求参数
         * @param authentication 认证信息，用于获取操作员ID
         * @return 统一响应结果，包含入库记录信息
         */
        @PostMapping("/stock-in")
        @PreAuthorize("hasAuthority('INVENTORY_IN')")
        public Result<InventoryRecordVO> stockIn(@Valid @RequestBody StockInOutRequest request,
                                                  Authentication authentication) {
            // 从认证上下文中获取当前操作员 ID。
            String operatorId = (String) authentication.getPrincipal();

            // 调用库存服务执行入库操作。
            InventoryRecordVO vo = inventoryService.stockIn(request, operatorId);

            // 返回入库记录信息。
            return Result.success(vo);
        }
    

        /**
         * 出库操作，包括原材料或成品出库
         * <p>
         * 从认证信息中获取操作员 ID，并提交原材料或成品出库请求。
         * </p>
         *
         * @param request 出库请求参数
         * @param authentication 认证信息，用于获取操作员ID
         * @return 统一响应结果，包含出库记录信息
         */
        @PostMapping("/stock-out")
        @PreAuthorize("hasAuthority('INVENTORY_OUT')")
        public Result<InventoryRecordVO> stockOut(@Valid @RequestBody StockInOutRequest request,
                                                   Authentication authentication) {
            // 从认证上下文中获取当前操作员 ID。
            String operatorId = (String) authentication.getPrincipal();

            // 调用库存服务执行出库操作。
            InventoryRecordVO vo = inventoryService.stockOut(request, operatorId);

            // 返回出库记录信息。
            return Result.success(vo);
        }


        /**
         * 查询库存记录列表，支持分页和筛选
         * <p>
         * 根据物品类型、物品 ID 和分页参数查询库存流水，并组装前端列表分页结构。
         * </p>
         *
         * @param itemType 物品类型（raw_material-原材料，finished_product-成品）
         * @param itemId 物品ID
         * @param page 页码，从1开始
         * @param size 每页大小
         * @return 统一响应结果，包含库存记录列表、总数、页码和每页大小
         */
        @GetMapping("/records")
        @PreAuthorize("hasAuthority('INVENTORY_READ')")
        public Result<Map<String, Object>> getInventoryRecords(
                @RequestParam(defaultValue = "") String itemType,
                @RequestParam(defaultValue = "") String itemId,
                @RequestParam(defaultValue = "1") int page,
                @RequestParam(defaultValue = "10") int size) {
            // 构建分页和排序参数。
            Pageable pageable = PageRequest.of(page - 1, size, Sort.by(Sort.Direction.DESC, "createTime"));

            // 查询库存流水分页数据。
            Page<InventoryRecordVO> recordPage = inventoryService.getInventoryRecords(itemType, itemId, pageable);

            // 组装列表分页返回结构。
            Map<String, Object> result = new HashMap<>();
            result.put("list", recordPage.getContent());
            result.put("total", recordPage.getTotalElements());
            result.put("page", page);
            result.put("size", size);

            // 返回库存记录查询结果。
            return Result.success(result);
        }


        /**
         * 查询库存预警列表，支持分页和状态筛选
         * <p>
         * 根据预警状态和分页参数查询库存预警，并组装前端列表分页结构。
         * </p>
         *
         * @param status 预警状态（active-活跃，resolved-已解决）
         * @param page 页码，从1开始
         * @param size 每页大小
         * @return 统一响应结果，包含预警列表、总数、页码和每页大小
         */
        @GetMapping("/alerts")
        @PreAuthorize("hasAuthority('INVENTORY_ALERT')")
        public Result<Map<String, Object>> getAlerts(
                @RequestParam(defaultValue = "") String status,
                @RequestParam(defaultValue = "1") int page,
                @RequestParam(defaultValue = "10") int size) {
            // 构建分页和排序参数。
            Pageable pageable = PageRequest.of(page - 1, size, Sort.by(Sort.Direction.DESC, "createTime"));

            // 查询库存预警分页数据。
            Page<InventoryAlertVO> alertPage = inventoryService.getAlerts(status, pageable);

            // 组装列表分页返回结构。
            Map<String, Object> result = new HashMap<>();
            result.put("list", alertPage.getContent());
            result.put("total", alertPage.getTotalElements());
            result.put("page", page);
            result.put("size", size);

            // 返回库存预警查询结果。
            return Result.success(result);
        }


        /**
         * 处理库存预警
         * <p>
         * 根据预警 ID 和处理请求更新库存预警状态。
         * </p>
         *
         * @param id 预警ID
         * @param request 预警处理请求参数
         * @return 统一响应结果，包含处理后的预警信息
         */
        @PutMapping("/alerts/{id}/handle")
        @PreAuthorize("hasAuthority('INVENTORY_ALERT')")
        public Result<InventoryAlertVO> handleAlert(@PathVariable String id,
                                                      @RequestBody AlertHandleRequest request) {
            // 调用库存服务处理预警。
            InventoryAlertVO vo = inventoryService.handleAlert(id, request);

            // 返回处理后的预警信息。
            return Result.success(vo);
        }


        /**
         * 设置原材料库存预警阈值
         * <p>
         * 根据原材料 ID 更新该原材料的库存预警阈值。
         * </p>
         *
         * @param id 原材料ID
         * @param request 阈值设置请求参数
         * @return 统一响应结果，包含更新阈值后的原材料信息
         */
        @PutMapping("/raw-materials/{id}/threshold")
        @PreAuthorize("hasAuthority('INVENTORY_ALERT')")
        public Result<RawMaterialVO> setRawMaterialThreshold(@PathVariable String id,
                                                               @Valid @RequestBody ThresholdRequest request) {
            // 调用库存服务设置原材料预警阈值。
            RawMaterialVO vo = inventoryService.setRawMaterialThreshold(id, request);

            // 返回更新阈值后的原材料信息。
            return Result.success(vo);
        }


        /**
         * 设置成品库存预警阈值
         * <p>
         * 根据成品 ID 更新该成品的库存预警阈值。
         * </p>
         *
         * @param id 成品ID
         * @param request 阈值设置请求参数
         * @return 统一响应结果，包含更新阈值后的成品信息
         */
        @PutMapping("/finished-products/{id}/threshold")
        @PreAuthorize("hasAuthority('INVENTORY_ALERT')")
        public Result<FinishedProductVO> setFinishedProductThreshold(@PathVariable String id,
                                                                       @Valid @RequestBody ThresholdRequest request) {
            // 调用库存服务设置成品预警阈值。
            FinishedProductVO vo = inventoryService.setFinishedProductThreshold(id, request);

            // 返回更新阈值后的成品信息。
            return Result.success(vo);
        }


        /**
         * 移动原材料存放位置
         * <p>
         * 根据原材料 ID 和移动请求，将指定数量库存从源位置移动到目标位置。
         * </p>
         *
         * @param id 原材料ID
         * @param request 位置移动请求参数
         * @return 统一响应结果，包含移动后的原材料信息
         */
        @PostMapping("/raw-materials/{id}/move-location")
        @PreAuthorize("hasAuthority('INVENTORY_IN')")
        public Result<RawMaterialVO> moveRawMaterialLocation(@PathVariable String id,
                                                              @Valid @RequestBody MoveLocationRequest request) {
            // 调用库存服务移动原材料库位。
            RawMaterialVO vo = inventoryService.moveRawMaterialLocation(id, request);

            // 返回移动后的原材料信息。
            return Result.success(vo);
        }


        /**
         * 移动成品存放位置
         * <p>
         * 根据成品 ID 和移动请求，将指定数量库存从源位置移动到目标位置。
         * </p>
         *
         * @param id 成品ID
         * @param request 位置移动请求参数
         * @return 统一响应结果，包含移动后的成品信息
         */
        @PostMapping("/finished-products/{id}/move-location")
        @PreAuthorize("hasAuthority('INVENTORY_IN')")
        public Result<FinishedProductVO> moveFinishedProductLocation(@PathVariable String id,
                                                                       @Valid @RequestBody MoveLocationRequest request) {
            // 调用库存服务移动成品库位。
            FinishedProductVO vo = inventoryService.moveFinishedProductLocation(id, request);

            // 返回移动后的成品信息。
            return Result.success(vo);
        }

    
}
