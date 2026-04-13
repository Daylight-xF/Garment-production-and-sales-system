package com.garment.service;

import com.garment.dto.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface InventoryService {

    Page<RawMaterialVO> getRawMaterialList(String name, String category, Pageable pageable);

    RawMaterialVO getRawMaterialById(String id);

    RawMaterialVO createRawMaterial(RawMaterialCreateRequest request);

    RawMaterialVO updateRawMaterial(String id, RawMaterialUpdateRequest request);

    void deleteRawMaterial(String id);

    Page<FinishedProductVO> getFinishedProductList(String name, String category, Pageable pageable);

    FinishedProductVO getFinishedProductById(String id);

    FinishedProductVO createFinishedProduct(FinishedProductCreateRequest request);

    FinishedProductVO updateFinishedProduct(String id, FinishedProductUpdateRequest request);

    void deleteFinishedProduct(String id);

    InventoryRecordVO stockIn(StockInOutRequest request, String operatorId);

    InventoryRecordVO stockOut(StockInOutRequest request, String operatorId);

    Page<InventoryRecordVO> getInventoryRecords(String itemType, String itemId, Pageable pageable);

    Page<InventoryAlertVO> getAlerts(String status, Pageable pageable);

    InventoryAlertVO handleAlert(String id, AlertHandleRequest request);

    RawMaterialVO setRawMaterialThreshold(String id, ThresholdRequest request);

    FinishedProductVO setFinishedProductThreshold(String id, ThresholdRequest request);

    RawMaterialVO moveRawMaterialLocation(String id, MoveLocationRequest request);

    void fifoDeductRawMaterial(String materialId, int quantity, String reason);
}
