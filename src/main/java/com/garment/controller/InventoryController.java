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
import java.util.Map;

@RestController
@RequestMapping("/api/inventory")
public class InventoryController {

    private final InventoryService inventoryService;

    public InventoryController(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @GetMapping("/raw-materials")
    @PreAuthorize("hasAuthority('INVENTORY_READ')")
    public Result<Map<String, Object>> getRawMaterialList(
            @RequestParam(defaultValue = "") String name,
            @RequestParam(defaultValue = "") String category,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        Pageable pageable = PageRequest.of(page - 1, size, Sort.by(Sort.Direction.DESC, "createTime"));
        Page<RawMaterialVO> materialPage = inventoryService.getRawMaterialList(name, category, pageable);

        Map<String, Object> result = new HashMap<>();
        result.put("list", materialPage.getContent());
        result.put("total", materialPage.getTotalElements());
        result.put("page", page);
        result.put("size", size);

        return Result.success(result);
    }

    @GetMapping("/raw-materials/{id}")
    @PreAuthorize("hasAuthority('INVENTORY_READ')")
    public Result<RawMaterialVO> getRawMaterialById(@PathVariable String id) {
        RawMaterialVO vo = inventoryService.getRawMaterialById(id);
        return Result.success(vo);
    }

    @PostMapping("/raw-materials")
    @PreAuthorize("hasAuthority('INVENTORY_IN')")
    public Result<RawMaterialVO> createRawMaterial(@Valid @RequestBody RawMaterialCreateRequest request) {
        RawMaterialVO vo = inventoryService.createRawMaterial(request);
        return Result.success(vo);
    }

    @PutMapping("/raw-materials/{id}")
    @PreAuthorize("hasAuthority('INVENTORY_IN')")
    public Result<RawMaterialVO> updateRawMaterial(@PathVariable String id,
                                                     @RequestBody RawMaterialUpdateRequest request) {
        RawMaterialVO vo = inventoryService.updateRawMaterial(id, request);
        return Result.success(vo);
    }

    @DeleteMapping("/raw-materials/{id}")
    @PreAuthorize("hasAuthority('INVENTORY_IN')")
    public Result<Void> deleteRawMaterial(@PathVariable String id) {
        inventoryService.deleteRawMaterial(id);
        return Result.success();
    }

    @GetMapping("/finished-products")
    @PreAuthorize("hasAuthority('INVENTORY_READ')")
    public Result<Map<String, Object>> getFinishedProductList(
            @RequestParam(defaultValue = "") String name,
            @RequestParam(defaultValue = "") String category,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        Pageable pageable = PageRequest.of(page - 1, size, Sort.by(Sort.Direction.DESC, "createTime"));
        Page<FinishedProductVO> productPage = inventoryService.getFinishedProductList(name, category, pageable);

        Map<String, Object> result = new HashMap<>();
        result.put("list", productPage.getContent());
        result.put("total", productPage.getTotalElements());
        result.put("page", page);
        result.put("size", size);

        return Result.success(result);
    }

    @GetMapping("/finished-products/{id}")
    @PreAuthorize("hasAuthority('INVENTORY_READ')")
    public Result<FinishedProductVO> getFinishedProductById(@PathVariable String id) {
        FinishedProductVO vo = inventoryService.getFinishedProductById(id);
        return Result.success(vo);
    }

    @PostMapping("/finished-products")
    @PreAuthorize("hasAuthority('INVENTORY_IN')")
    public Result<FinishedProductVO> createFinishedProduct(@Valid @RequestBody FinishedProductCreateRequest request) {
        FinishedProductVO vo = inventoryService.createFinishedProduct(request);
        return Result.success(vo);
    }

    @PutMapping("/finished-products/{id}")
    @PreAuthorize("hasAuthority('INVENTORY_IN')")
    public Result<FinishedProductVO> updateFinishedProduct(@PathVariable String id,
                                                             @RequestBody FinishedProductUpdateRequest request) {
        FinishedProductVO vo = inventoryService.updateFinishedProduct(id, request);
        return Result.success(vo);
    }

    @DeleteMapping("/finished-products/{id}")
    @PreAuthorize("hasAuthority('INVENTORY_IN')")
    public Result<Void> deleteFinishedProduct(@PathVariable String id) {
        inventoryService.deleteFinishedProduct(id);
        return Result.success();
    }

    @PostMapping("/stock-in")
    @PreAuthorize("hasAuthority('INVENTORY_IN')")
    public Result<InventoryRecordVO> stockIn(@Valid @RequestBody StockInOutRequest request,
                                              Authentication authentication) {
        String operatorId = (String) authentication.getPrincipal();
        InventoryRecordVO vo = inventoryService.stockIn(request, operatorId);
        return Result.success(vo);
    }

    @PostMapping("/stock-out")
    @PreAuthorize("hasAuthority('INVENTORY_OUT')")
    public Result<InventoryRecordVO> stockOut(@Valid @RequestBody StockInOutRequest request,
                                               Authentication authentication) {
        String operatorId = (String) authentication.getPrincipal();
        InventoryRecordVO vo = inventoryService.stockOut(request, operatorId);
        return Result.success(vo);
    }

    @GetMapping("/records")
    @PreAuthorize("hasAuthority('INVENTORY_READ')")
    public Result<Map<String, Object>> getInventoryRecords(
            @RequestParam(defaultValue = "") String itemType,
            @RequestParam(defaultValue = "") String itemId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        Pageable pageable = PageRequest.of(page - 1, size, Sort.by(Sort.Direction.DESC, "createTime"));
        Page<InventoryRecordVO> recordPage = inventoryService.getInventoryRecords(itemType, itemId, pageable);

        Map<String, Object> result = new HashMap<>();
        result.put("list", recordPage.getContent());
        result.put("total", recordPage.getTotalElements());
        result.put("page", page);
        result.put("size", size);

        return Result.success(result);
    }

    @GetMapping("/alerts")
    @PreAuthorize("hasAuthority('INVENTORY_ALERT')")
    public Result<Map<String, Object>> getAlerts(
            @RequestParam(defaultValue = "") String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        Pageable pageable = PageRequest.of(page - 1, size, Sort.by(Sort.Direction.DESC, "createTime"));
        Page<InventoryAlertVO> alertPage = inventoryService.getAlerts(status, pageable);

        Map<String, Object> result = new HashMap<>();
        result.put("list", alertPage.getContent());
        result.put("total", alertPage.getTotalElements());
        result.put("page", page);
        result.put("size", size);

        return Result.success(result);
    }

    @PutMapping("/alerts/{id}/handle")
    @PreAuthorize("hasAuthority('INVENTORY_ALERT')")
    public Result<InventoryAlertVO> handleAlert(@PathVariable String id,
                                                  @RequestBody AlertHandleRequest request) {
        InventoryAlertVO vo = inventoryService.handleAlert(id, request);
        return Result.success(vo);
    }

    @PutMapping("/raw-materials/{id}/threshold")
    @PreAuthorize("hasAuthority('INVENTORY_ALERT')")
    public Result<RawMaterialVO> setRawMaterialThreshold(@PathVariable String id,
                                                           @Valid @RequestBody ThresholdRequest request) {
        RawMaterialVO vo = inventoryService.setRawMaterialThreshold(id, request);
        return Result.success(vo);
    }

    @PutMapping("/finished-products/{id}/threshold")
    @PreAuthorize("hasAuthority('INVENTORY_ALERT')")
    public Result<FinishedProductVO> setFinishedProductThreshold(@PathVariable String id,
                                                                   @Valid @RequestBody ThresholdRequest request) {
        FinishedProductVO vo = inventoryService.setFinishedProductThreshold(id, request);
        return Result.success(vo);
    }

    @PostMapping("/raw-materials/{id}/move-location")
    @PreAuthorize("hasAuthority('INVENTORY_IN')")
    public Result<RawMaterialVO> moveRawMaterialLocation(@PathVariable String id,
                                                          @Valid @RequestBody MoveLocationRequest request) {
        RawMaterialVO vo = inventoryService.moveRawMaterialLocation(id, request);
        return Result.success(vo);
    }

    @PostMapping("/finished-products/{id}/move-location")
    @PreAuthorize("hasAuthority('INVENTORY_IN')")
    public Result<FinishedProductVO> moveFinishedProductLocation(@PathVariable String id,
                                                                   @Valid @RequestBody MoveLocationRequest request) {
        FinishedProductVO vo = inventoryService.moveFinishedProductLocation(id, request);
        return Result.success(vo);
    }
}
