package com.garment.service.impl;

import com.garment.dto.AlertHandleRequest;
import com.garment.dto.FinishedProductVO;
import com.garment.dto.InventoryDeductionReceipt;
import com.garment.dto.StockInOutRequest;
import com.garment.exception.BusinessException;
import com.garment.model.FinishedProduct;
import com.garment.model.InventoryAlert;
import com.garment.model.InventoryRecord;
import com.garment.model.LocationInfo;
import com.garment.model.ProductDefinition;
import com.garment.model.ProductionPlan;
import com.garment.model.RawMaterial;
import com.garment.model.User;
import com.garment.repository.FinishedProductRepository;
import com.garment.repository.InventoryAlertRepository;
import com.garment.repository.InventoryRecordRepository;
import com.garment.repository.ProductDefinitionRepository;
import com.garment.repository.ProductionPlanRepository;
import com.garment.repository.RawMaterialRepository;
import com.garment.repository.UserRepository;
import com.garment.service.support.MongoAtomicOpsService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InventoryServiceImplTest {

    @Mock
    private RawMaterialRepository rawMaterialRepository;

    @Mock
    private FinishedProductRepository finishedProductRepository;

    @Mock
    private ProductionPlanRepository productionPlanRepository;

    @Mock
    private ProductDefinitionRepository productDefinitionRepository;

    @Mock
    private InventoryRecordRepository inventoryRecordRepository;

    @Mock
    private InventoryAlertRepository inventoryAlertRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private MongoAtomicOpsService mongoAtomicOpsService;

    @InjectMocks
    private InventoryServiceImpl inventoryService;

    @Test
    void stockInShouldCreateNewFinishedProductWhenBatchColorOrSizeDiffers() {
        ProductionPlan plan = buildPlan("plan-2", "BATCH-002", "休闲裤", "P001", "蓝色", "L");

        FinishedProduct existing = buildFinishedProduct("finished-1", "BATCH-001", "休闲裤", "P001", "黑色", "M");
        existing.setLocations(new ArrayList<>(Arrays.asList(new LocationInfo("A-01", 8, new Date()))));
        existing.setQuantity(8);

        User operator = new User();
        operator.setId("admin-1");
        operator.setRealName("系统管理员");

        StockInOutRequest request = new StockInOutRequest();
        request.setItemType("FINISHED_PRODUCT");
        request.setItemId("plan-2");
        request.setQuantity(10);
        request.setReason("生产批次BATCH-002入库 | 位置:B-02 | 首次入库");

        when(productionPlanRepository.findById("plan-2")).thenReturn(Optional.of(plan));
        when(finishedProductRepository.findAll()).thenReturn(Arrays.asList(existing));
        when(userRepository.findById("admin-1")).thenReturn(Optional.of(operator));
        when(finishedProductRepository.save(any(FinishedProduct.class))).thenAnswer(invocation -> {
            FinishedProduct product = invocation.getArgument(0);
            if (product.getId() == null) {
                product.setId("finished-new");
            }
            return product;
        });
        when(productionPlanRepository.save(any(ProductionPlan.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(inventoryRecordRepository.save(any(InventoryRecord.class))).thenAnswer(invocation -> invocation.getArgument(0));

        inventoryService.stockIn(request, "admin-1");

        ArgumentCaptor<FinishedProduct> productCaptor = ArgumentCaptor.forClass(FinishedProduct.class);
        verify(finishedProductRepository, atLeastOnce()).save(productCaptor.capture());

        assertThat(productCaptor.getAllValues())
                .anySatisfy(product -> {
                    assertThat(product.getBatchNo()).isEqualTo("BATCH-002");
                    assertThat(product.getName()).isEqualTo("休闲裤");
                    assertThat(product.getProductCode()).isEqualTo("P001");
                    assertThat(product.getColor()).isEqualTo("蓝色");
                    assertThat(product.getSize()).isEqualTo("L");
                });
    }

    @Test
    void stockInShouldCreateNewFinishedProductWithPlanCategory() {
        ProductionPlan plan = buildPlan("plan-category", "BATCH-CATEGORY", "Casual Pants", "P100", "Blue", "L");
        plan.setCategory("BOTTOM");

        FinishedProduct existing = buildFinishedProduct("finished-existing", "BATCH-OLD", "Casual Pants", "P100", "Black", "M");
        existing.setLocations(new ArrayList<>(Arrays.asList(new LocationInfo("A-01", 8, new Date()))));
        existing.setQuantity(8);

        User operator = new User();
        operator.setId("admin-1");
        operator.setRealName("Admin");

        StockInOutRequest request = new StockInOutRequest();
        request.setItemType("FINISHED_PRODUCT");
        request.setItemId("plan-category");
        request.setQuantity(10);
        request.setReason("Production batch BATCH-CATEGORY stock in | Location:B-02 | First stock in");

        when(productionPlanRepository.findById("plan-category")).thenReturn(Optional.of(plan));
        when(finishedProductRepository.findAll()).thenReturn(Arrays.asList(existing));
        when(userRepository.findById("admin-1")).thenReturn(Optional.of(operator));
        when(finishedProductRepository.save(any(FinishedProduct.class))).thenAnswer(invocation -> {
            FinishedProduct product = invocation.getArgument(0);
            if (product.getId() == null) {
                product.setId("finished-category");
            }
            return product;
        });
        when(productionPlanRepository.save(any(ProductionPlan.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(inventoryRecordRepository.save(any(InventoryRecord.class))).thenAnswer(invocation -> invocation.getArgument(0));

        inventoryService.stockIn(request, "admin-1");

        ArgumentCaptor<FinishedProduct> productCaptor = ArgumentCaptor.forClass(FinishedProduct.class);
        verify(finishedProductRepository, atLeastOnce()).save(productCaptor.capture());

        assertThat(productCaptor.getAllValues())
                .anySatisfy(product -> {
                    assertThat(product.getBatchNo()).isEqualTo("BATCH-CATEGORY");
                    assertThat(product.getProductCode()).isEqualTo("P100");
                    assertThat(product.getCategory()).isEqualTo("BOTTOM");
                });
    }

    @Test
    void stockInShouldReuseFinishedProductOnlyWhenAllIdentityFieldsMatch() {
        ProductionPlan plan = buildPlan("plan-3", "BATCH-003", "休闲裤", "P001", "黑色", "M");

        FinishedProduct existing = buildFinishedProduct("finished-3", "BATCH-003", "休闲裤", "P001", "黑色", "M");
        existing.setLocations(new ArrayList<>(Arrays.asList(new LocationInfo("A-01", 8, new Date()))));
        existing.setQuantity(8);

        User operator = new User();
        operator.setId("admin-1");
        operator.setRealName("系统管理员");

        StockInOutRequest request = new StockInOutRequest();
        request.setItemType("FINISHED_PRODUCT");
        request.setItemId("plan-3");
        request.setQuantity(5);
        request.setReason("生产批次BATCH-003入库 | 位置:A-01 | 补充入库");

        when(productionPlanRepository.findById("plan-3")).thenReturn(Optional.of(plan));
        when(finishedProductRepository.findAll()).thenReturn(Arrays.asList(existing));
        when(userRepository.findById("admin-1")).thenReturn(Optional.of(operator));
        when(finishedProductRepository.save(any(FinishedProduct.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(productionPlanRepository.save(any(ProductionPlan.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(inventoryRecordRepository.save(any(InventoryRecord.class))).thenAnswer(invocation -> invocation.getArgument(0));

        inventoryService.stockIn(request, "admin-1");

        ArgumentCaptor<FinishedProduct> productCaptor = ArgumentCaptor.forClass(FinishedProduct.class);
        verify(finishedProductRepository, atLeastOnce()).save(productCaptor.capture());

        FinishedProduct latestSaved = productCaptor.getAllValues().get(productCaptor.getAllValues().size() - 1);
        assertThat(latestSaved.getId()).isEqualTo("finished-3");
        assertThat(latestSaved.getLocations()).extracting(LocationInfo::getQuantity).containsExactly(13);
        assertThat(plan.getStockedInQuantity()).isEqualTo(5);
    }

    @Test
    void stockInShouldReuseExistingFinishedProductWhenConcurrentCreateHitsDuplicateKey() {
        ProductionPlan plan = buildPlan("plan-duplicate", "BATCH-DUP", "工装裤", "P900", "灰色", "L");
        FinishedProduct existing = buildFinishedProduct("finished-dup", "BATCH-DUP", "工装裤", "P900", "灰色", "L");
        existing.setQuantity(8);
        existing.setLocations(new ArrayList<>(Collections.singletonList(new LocationInfo("A-01", 8, new Date()))));

        User operator = new User();
        operator.setId("admin-dup");
        operator.setRealName("系统管理员");

        StockInOutRequest request = new StockInOutRequest();
        request.setItemType("FINISHED_PRODUCT");
        request.setItemId("plan-duplicate");
        request.setQuantity(2);
        request.setReason("生产批次BATCH-DUP入库 | 位置:A-01 | 并发重试");

        when(productionPlanRepository.findById("plan-duplicate")).thenReturn(Optional.of(plan));
        when(finishedProductRepository.findAll()).thenReturn(Collections.emptyList());
        when(finishedProductRepository.findFirstByProductCodeAndNameAndColorAndSizeAndBatchNo("P900", "工装裤", "灰色", "L", "BATCH-DUP"))
                .thenReturn(Optional.empty(), Optional.of(existing));
        when(userRepository.findById("admin-dup")).thenReturn(Optional.of(operator));
        when(finishedProductRepository.save(any(FinishedProduct.class)))
                .thenThrow(new DuplicateKeyException("duplicate finished product"))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(productionPlanRepository.save(any(ProductionPlan.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(inventoryRecordRepository.save(any(InventoryRecord.class))).thenAnswer(invocation -> invocation.getArgument(0));

        inventoryService.stockIn(request, "admin-dup");

        verify(finishedProductRepository, atLeastOnce())
                .findFirstByProductCodeAndNameAndColorAndSizeAndBatchNo("P900", "工装裤", "灰色", "L", "BATCH-DUP");
    }

    @Test
    void stockInShouldSupportExistingFinishedProductIdWithoutProductionPlan() {
        FinishedProduct existing = buildFinishedProduct("finished-existing-stockin", "BATCH-EXIST", "连衣裙", "P777", "白色", "M");
        existing.setQuantity(100);
        existing.setLocations(new ArrayList<>(Arrays.asList(
                new LocationInfo("H-09", 90, new Date()),
                new LocationInfo("H-08", 10, new Date())
        )));

        User operator = new User();
        operator.setId("admin-existing");
        operator.setRealName("Admin");

        StockInOutRequest request = new StockInOutRequest();
        request.setItemType("FINISHED_PRODUCT");
        request.setItemId("finished-existing-stockin");
        request.setQuantity(10);
        request.setReason("Manual stock in | 位置:H-08");

        when(finishedProductRepository.findById("finished-existing-stockin")).thenReturn(Optional.of(existing));
        when(userRepository.findById("admin-existing")).thenReturn(Optional.of(operator));
        when(finishedProductRepository.save(any(FinishedProduct.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(inventoryRecordRepository.save(any(InventoryRecord.class))).thenAnswer(invocation -> invocation.getArgument(0));

        inventoryService.stockIn(request, "admin-existing");

        ArgumentCaptor<FinishedProduct> productCaptor = ArgumentCaptor.forClass(FinishedProduct.class);
        verify(finishedProductRepository, atLeastOnce()).save(productCaptor.capture());
        FinishedProduct latestSaved = productCaptor.getAllValues().get(productCaptor.getAllValues().size() - 1);
        assertThat(latestSaved.getLocations())
                .extracting(LocationInfo::getLocation, LocationInfo::getQuantity)
                .containsExactly(
                        tuple("H-09", 90),
                        tuple("H-08", 20)
                );
        assertThat(latestSaved.getQuantity()).isEqualTo(110);
        verify(productionPlanRepository, never()).save(any(ProductionPlan.class));
    }

    @Test
    void getFinishedProductListShouldUseCurrentProductDefinitionUnitCostAndFallbackToNullWhenMissing() {
        FinishedProduct matchedProduct = buildFinishedProduct("finished-10", "BATCH-010", "T恤", "Y1", "红色", "M");
        matchedProduct.setQuantity(20);

        FinishedProduct unmatchedProduct = buildFinishedProduct("finished-11", "BATCH-011", "方法", "NO_MATCH", "白色", "L");
        unmatchedProduct.setQuantity(6);

        ProductDefinition.ProductMaterial fabric = new ProductDefinition.ProductMaterial("material-1", "涤纶", "面料", 2.0, "米");
        ProductDefinition.ProductMaterial button = new ProductDefinition.ProductMaterial("material-2", "纽扣", "辅料", 4.0, "颗");

        ProductDefinition definition = new ProductDefinition();
        definition.setProductCode("Y1");
        definition.setMaterials(Arrays.asList(fabric, button));

        RawMaterial fabricMaterial = new RawMaterial();
        fabricMaterial.setId("material-1");
        fabricMaterial.setPrice(8.0);

        RawMaterial buttonMaterial = new RawMaterial();
        buttonMaterial.setId("material-2");
        buttonMaterial.setPrice(0.5);

        when(finishedProductRepository.findAll()).thenReturn(Arrays.asList(matchedProduct, unmatchedProduct));
        when(productDefinitionRepository.findByProductCode("Y1")).thenReturn(Optional.of(definition));
        when(productDefinitionRepository.findByProductCode("NO_MATCH")).thenReturn(Optional.empty());
        when(rawMaterialRepository.findById("material-1")).thenReturn(Optional.of(fabricMaterial));
        when(rawMaterialRepository.findById("material-2")).thenReturn(Optional.of(buttonMaterial));

        Page<FinishedProductVO> result = inventoryService.getFinishedProductList("", "", PageRequest.of(0, 10));

        assertThat(result.getContent()).hasSize(2);
        assertThat(result.getContent().get(0).getCostPrice()).isEqualTo(18.0);
        assertThat(result.getContent().get(1).getCostPrice()).isNull();
    }

    @Test
    void getFinishedProductListShouldMatchBatchNoKeyword() {
        FinishedProduct batchMatched = buildFinishedProduct("finished-20", "PC-20260415-9710", "shirt-y1", "Y1", "red", "M");
        batchMatched.setCategory("TOP");

        FinishedProduct nameMatched = buildFinishedProduct("finished-21", "PC-20260415-3440", "pants-n1", "N1", "red", "S");
        nameMatched.setCategory("BOTTOM");

        when(finishedProductRepository.findAll()).thenReturn(Arrays.asList(batchMatched, nameMatched));
        when(productDefinitionRepository.findByProductCode("Y1")).thenReturn(Optional.empty());

        Page<FinishedProductVO> result = inventoryService.getFinishedProductList("9710", "", PageRequest.of(0, 10));

        assertThat(result.getContent()).extracting(FinishedProductVO::getBatchNo)
                .containsExactly("PC-20260415-9710");
    }

    @Test
    void stockInShouldUseAtomicTotalQuantityIncreaseForRawMaterialWithoutLocations() {
        RawMaterial material = new RawMaterial();
        material.setId("raw-stock-in");
        material.setName("Cotton");
        material.setQuantity(12);
        material.setLocations(new ArrayList<>());

        User operator = new User();
        operator.setId("warehouse-in");
        operator.setRealName("warehouse user");

        StockInOutRequest request = new StockInOutRequest();
        request.setItemType("RAW_MATERIAL");
        request.setItemId("raw-stock-in");
        request.setQuantity(5);
        request.setReason("manual stock in");

        when(rawMaterialRepository.findById("raw-stock-in")).thenReturn(Optional.of(material));
        when(userRepository.findById("warehouse-in")).thenReturn(Optional.of(operator));
        when(mongoAtomicOpsService.changeRawMaterialQuantity("raw-stock-in", 5, null)).thenReturn(true);
        when(inventoryRecordRepository.save(any(InventoryRecord.class))).thenAnswer(invocation -> invocation.getArgument(0));

        inventoryService.stockIn(request, "warehouse-in");

        verify(mongoAtomicOpsService).changeRawMaterialQuantity("raw-stock-in", 5, null);
        verify(rawMaterialRepository, never()).save(any(RawMaterial.class));
    }

    @Test
    void stockInShouldThrowWhenAtomicTotalQuantityIncreaseFails() {
        RawMaterial material = new RawMaterial();
        material.setId("raw-stock-in-fail");
        material.setName("Cotton");
        material.setQuantity(12);
        material.setLocations(new ArrayList<>());

        User operator = new User();
        operator.setId("warehouse-in-fail");
        operator.setRealName("warehouse user");

        StockInOutRequest request = new StockInOutRequest();
        request.setItemType("RAW_MATERIAL");
        request.setItemId("raw-stock-in-fail");
        request.setQuantity(5);
        request.setReason("manual stock in");

        when(rawMaterialRepository.findById("raw-stock-in-fail")).thenReturn(Optional.of(material));
        when(mongoAtomicOpsService.changeRawMaterialQuantity("raw-stock-in-fail", 5, null)).thenReturn(false);

        assertThatThrownBy(() -> inventoryService.stockIn(request, "warehouse-in-fail"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("库存更新失败");

        verify(rawMaterialRepository, never()).save(any(RawMaterial.class));
        verify(inventoryRecordRepository, never()).save(any(InventoryRecord.class));
    }

    @Test
    void stockInShouldUseChineseStatusTextWhenPlanIsNotCompleted() {
        ProductionPlan plan = buildPlan("plan-pending", "BATCH-PENDING", "夹克", "P002", "黑色", "L");
        plan.setStatus("PENDING");

        StockInOutRequest request = new StockInOutRequest();
        request.setItemType("FINISHED_PRODUCT");
        request.setItemId("plan-pending");
        request.setQuantity(3);
        request.setReason("生产批次BATCH-PENDING入库");

        when(productionPlanRepository.findById("plan-pending")).thenReturn(Optional.of(plan));

        assertThatThrownBy(() -> inventoryService.stockIn(request, "admin-1"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("当前状态：待审批")
                .hasMessageNotContaining("PENDING");
    }

    @Test
    void stockOutShouldUseAtomicTotalQuantityDeductionForRawMaterialWithoutLocations() {
        RawMaterial material = new RawMaterial();
        material.setId("raw-stock-out");
        material.setName("Cotton");
        material.setQuantity(12);
        material.setLocations(new ArrayList<>());

        User operator = new User();
        operator.setId("warehouse-out");
        operator.setRealName("warehouse user");

        StockInOutRequest request = new StockInOutRequest();
        request.setItemType("RAW_MATERIAL");
        request.setItemId("raw-stock-out");
        request.setQuantity(5);
        request.setReason("manual stock out");

        when(rawMaterialRepository.findById("raw-stock-out")).thenReturn(Optional.of(material));
        when(userRepository.findById("warehouse-out")).thenReturn(Optional.of(operator));
        when(mongoAtomicOpsService.changeRawMaterialQuantity("raw-stock-out", -5, 0)).thenReturn(true);
        when(inventoryRecordRepository.save(any(InventoryRecord.class))).thenAnswer(invocation -> invocation.getArgument(0));

        inventoryService.stockOut(request, "warehouse-out");

        verify(mongoAtomicOpsService).changeRawMaterialQuantity("raw-stock-out", -5, 0);
        verify(rawMaterialRepository, never()).save(any(RawMaterial.class));
    }

    @Test
    void stockOutShouldThrowWhenAtomicTotalQuantityDeductionFails() {
        RawMaterial material = new RawMaterial();
        material.setId("raw-stock-out-fail");
        material.setName("Cotton");
        material.setQuantity(12);
        material.setLocations(new ArrayList<>());

        User operator = new User();
        operator.setId("warehouse-out-fail");
        operator.setRealName("warehouse user");

        StockInOutRequest request = new StockInOutRequest();
        request.setItemType("RAW_MATERIAL");
        request.setItemId("raw-stock-out-fail");
        request.setQuantity(5);
        request.setReason("manual stock out");

        when(rawMaterialRepository.findById("raw-stock-out-fail")).thenReturn(Optional.of(material));
        when(mongoAtomicOpsService.changeRawMaterialQuantity("raw-stock-out-fail", -5, 0)).thenReturn(false);

        assertThatThrownBy(() -> inventoryService.stockOut(request, "warehouse-out-fail"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("库存不足或已被其他操作更新");

        verify(rawMaterialRepository, never()).save(any(RawMaterial.class));
        verify(inventoryRecordRepository, never()).save(any(InventoryRecord.class));
    }

    @Test
    void stockOutShouldUseFifoDeductionWhenRawMaterialHasLocationsButNoExplicitLocation() {
        RawMaterial material = new RawMaterial();
        material.setId("raw-stock-out-fifo");
        material.setName("Cotton");
        material.setQuantity(6);
        material.setLocations(new ArrayList<>(Arrays.asList(
                new LocationInfo("A-01", 2, new Date(1000L)),
                new LocationInfo("B-01", 4, new Date(2000L))
        )));

        User operator = new User();
        operator.setId("warehouse-out-fifo");
        operator.setRealName("warehouse user");

        StockInOutRequest request = new StockInOutRequest();
        request.setItemType("RAW_MATERIAL");
        request.setItemId("raw-stock-out-fifo");
        request.setQuantity(3);
        request.setReason("production plan quantity increase");

        when(rawMaterialRepository.findById("raw-stock-out-fifo")).thenReturn(Optional.of(material));
        when(userRepository.findById("warehouse-out-fifo")).thenReturn(Optional.of(operator));
        when(rawMaterialRepository.save(any(RawMaterial.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(inventoryRecordRepository.save(any(InventoryRecord.class))).thenAnswer(invocation -> invocation.getArgument(0));

        inventoryService.stockOut(request, "warehouse-out-fifo");

        verify(mongoAtomicOpsService, never()).changeRawMaterialQuantity("raw-stock-out-fifo", -3, 0);

        ArgumentCaptor<RawMaterial> materialCaptor = ArgumentCaptor.forClass(RawMaterial.class);
        verify(rawMaterialRepository).save(materialCaptor.capture());
        RawMaterial savedMaterial = materialCaptor.getValue();
        assertThat(savedMaterial.getQuantity()).isEqualTo(3);
        assertThat(savedMaterial.getLocations())
                .extracting(LocationInfo::getLocation, LocationInfo::getQuantity)
                .containsExactly(tuple("B-01", 3));

        ArgumentCaptor<InventoryRecord> recordCaptor = ArgumentCaptor.forClass(InventoryRecord.class);
        verify(inventoryRecordRepository).save(recordCaptor.capture());
        assertThat(recordCaptor.getValue().getReason()).contains("[FIFO:A-01(2)B-01(1)]");
    }

    @Test
    void fifoDeductFinishedProductShouldConsumeOldestLocationsFirst() {
        FinishedProduct product = buildFinishedProduct("finished-30", "BATCH-030", "T-shirt", "Y1", "white", "M");
        product.setAlertThreshold(1);
        product.setLocations(new ArrayList<>(Arrays.asList(
                new LocationInfo("A-01", 2, new Date(1000L)),
                new LocationInfo("B-01", 5, new Date(2000L))
        )));
        product.setQuantity(7);

        when(finishedProductRepository.findById("finished-30")).thenReturn(Optional.of(product));
        when(finishedProductRepository.save(any(FinishedProduct.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(inventoryRecordRepository.save(any(InventoryRecord.class))).thenAnswer(invocation -> invocation.getArgument(0));

        inventoryService.fifoDeductFinishedProduct("finished-30", 3, "订单发货-ORD001");

        ArgumentCaptor<FinishedProduct> productCaptor = ArgumentCaptor.forClass(FinishedProduct.class);
        verify(finishedProductRepository).save(productCaptor.capture());

        FinishedProduct savedProduct = productCaptor.getValue();
        assertThat(savedProduct.getQuantity()).isEqualTo(4);
        assertThat(savedProduct.getLocations()).hasSize(1);
        assertThat(savedProduct.getLocations().get(0).getLocation()).isEqualTo("B-01");
        assertThat(savedProduct.getLocations().get(0).getQuantity()).isEqualTo(4);

        ArgumentCaptor<InventoryRecord> recordCaptor = ArgumentCaptor.forClass(InventoryRecord.class);
        verify(inventoryRecordRepository).save(recordCaptor.capture());
        assertThat(recordCaptor.getValue().getReason()).contains("订单发货-ORD001");
        assertThat(recordCaptor.getValue().getReason()).contains("FIFO:A-01(2)B-01(1)");
    }

    @Test
    void fifoDeductRawMaterialShouldUseAtomicTotalQuantityDeductionWithoutLocations() {
        RawMaterial material = new RawMaterial();
        material.setId("raw-fifo");
        material.setName("Cotton");
        material.setQuantity(8);
        material.setLocations(Collections.emptyList());

        when(rawMaterialRepository.findById("raw-fifo")).thenReturn(Optional.of(material));
        when(mongoAtomicOpsService.changeRawMaterialQuantity("raw-fifo", -3, 0)).thenReturn(true);
        when(inventoryRecordRepository.save(any(InventoryRecord.class))).thenAnswer(invocation -> invocation.getArgument(0));

        inventoryService.fifoDeductRawMaterial("raw-fifo", 3, "ship raw");

        verify(mongoAtomicOpsService).changeRawMaterialQuantity("raw-fifo", -3, 0);
        verify(rawMaterialRepository, never()).save(any(RawMaterial.class));
    }

    @Test
    void fifoDeductRawMaterialShouldUseChineseSystemOperatorNameWithoutLocations() {
        RawMaterial material = new RawMaterial();
        material.setId("raw-fifo-operator");
        material.setName("Cotton");
        material.setQuantity(8);
        material.setLocations(Collections.emptyList());

        when(rawMaterialRepository.findById("raw-fifo-operator")).thenReturn(Optional.of(material));
        when(mongoAtomicOpsService.changeRawMaterialQuantity("raw-fifo-operator", -3, 0)).thenReturn(true);
        when(inventoryRecordRepository.save(any(InventoryRecord.class))).thenAnswer(invocation -> invocation.getArgument(0));

        inventoryService.fifoDeductRawMaterial("raw-fifo-operator", 3, "生产扣减");

        ArgumentCaptor<InventoryRecord> recordCaptor = ArgumentCaptor.forClass(InventoryRecord.class);
        verify(inventoryRecordRepository).save(recordCaptor.capture());
        assertThat(recordCaptor.getValue().getOperatorName()).isEqualTo("系统自动");
    }

    @Test
    void fifoDeductRawMaterialShouldThrowWhenAtomicTotalQuantityDeductionFails() {
        RawMaterial material = new RawMaterial();
        material.setId("raw-fifo-fail");
        material.setName("Cotton");
        material.setQuantity(2);
        material.setLocations(Collections.emptyList());

        when(rawMaterialRepository.findById("raw-fifo-fail")).thenReturn(Optional.of(material));
        when(mongoAtomicOpsService.changeRawMaterialQuantity("raw-fifo-fail", -3, 0)).thenReturn(false);

        assertThatThrownBy(() -> inventoryService.fifoDeductRawMaterial("raw-fifo-fail", 3, "ship raw"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("库存不足或已被其他操作更新");

        verify(rawMaterialRepository, never()).save(any(RawMaterial.class));
        verify(inventoryRecordRepository, never()).save(any(InventoryRecord.class));
    }

    @Test
    void fifoDeductRawMaterialShouldInitializeLegacyVersionWhenSaveHitsDuplicateKey() {
        RawMaterial material = new RawMaterial();
        material.setId("raw-legacy");
        material.setName("Legacy Cotton");
        material.setQuantity(6);
        material.setAlertThreshold(1);
        material.setLocations(new ArrayList<>(Arrays.asList(
                new LocationInfo("A-01", 4, new Date(1000L)),
                new LocationInfo("A-02", 2, new Date(2000L))
        )));

        when(rawMaterialRepository.findById("raw-legacy")).thenReturn(Optional.of(material));
        when(rawMaterialRepository.save(any(RawMaterial.class)))
                .thenAnswer(invocation -> {
                    RawMaterial entity = invocation.getArgument(0);
                    entity.setVersion(0L);
                    throw new DuplicateKeyException("duplicate raw material");
                })
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(mongoAtomicOpsService.initializeRawMaterialVersionIfMissing("raw-legacy")).thenReturn(true);
        when(inventoryRecordRepository.save(any(InventoryRecord.class))).thenAnswer(invocation -> invocation.getArgument(0));

        inventoryService.fifoDeductRawMaterial("raw-legacy", 3, "production plan");

        verify(mongoAtomicOpsService).initializeRawMaterialVersionIfMissing("raw-legacy");
        assertThat(material.getQuantity()).isEqualTo(3);
        assertThat(material.getVersion()).isEqualTo(0L);
        assertThat(material.getLocations())
                .extracting(LocationInfo::getLocation, LocationInfo::getQuantity)
                .containsExactly(tuple("A-01", 1), tuple("A-02", 2));
    }

    @Test
    void fifoDeductRawMaterialWithReceiptShouldReturnLocationDetails() {
        RawMaterial material = new RawMaterial();
        material.setId("raw-receipt");
        material.setName("Cotton");
        material.setQuantity(6);
        material.setLocations(new ArrayList<>(Arrays.asList(
                new LocationInfo("A-01", 4, new Date(1000L)),
                new LocationInfo("B-01", 2, new Date(2000L))
        )));

        when(rawMaterialRepository.findById("raw-receipt")).thenReturn(Optional.of(material));
        when(rawMaterialRepository.save(any(RawMaterial.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(inventoryRecordRepository.save(any(InventoryRecord.class))).thenAnswer(invocation -> invocation.getArgument(0));

        InventoryDeductionReceipt receipt = inventoryService.fifoDeductRawMaterialWithReceipt("raw-receipt", 5, "production plan");

        assertThat(receipt).isNotNull();
        assertThat(receipt.getItemType()).isEqualTo("RAW_MATERIAL");
        assertThat(receipt.getItemId()).isEqualTo("raw-receipt");
        assertThat(receipt.getQuantity()).isEqualTo(5);
        assertThat(receipt.isTotalOnly()).isFalse();
        assertThat(receipt.getLocationDeductions())
                .extracting(InventoryDeductionReceipt.LocationDeduction::getLocation,
                        InventoryDeductionReceipt.LocationDeduction::getQuantity)
                .containsExactly(tuple("A-01", 4), tuple("B-01", 1));

        assertThat(material.getQuantity()).isEqualTo(1);
        assertThat(material.getLocations())
                .extracting(LocationInfo::getLocation, LocationInfo::getQuantity)
                .containsExactly(tuple("B-01", 1));
    }

    @Test
    void fifoDeductRawMaterialWithReceiptShouldMarkTotalOnlyWhenNoLocationsExist() {
        RawMaterial material = new RawMaterial();
        material.setId("raw-total-only");
        material.setName("Cotton");
        material.setQuantity(8);
        material.setLocations(Collections.emptyList());

        when(rawMaterialRepository.findById("raw-total-only")).thenReturn(Optional.of(material));
        when(mongoAtomicOpsService.changeRawMaterialQuantity("raw-total-only", -3, 0)).thenReturn(true);
        when(inventoryRecordRepository.save(any(InventoryRecord.class))).thenAnswer(invocation -> invocation.getArgument(0));

        InventoryDeductionReceipt receipt = inventoryService.fifoDeductRawMaterialWithReceipt("raw-total-only", 3, "production plan");

        assertThat(receipt).isNotNull();
        assertThat(receipt.isTotalOnly()).isTrue();
        assertThat(receipt.getQuantity()).isEqualTo(3);
        assertThat(receipt.getLocationDeductions()).isEmpty();
    }

    @Test
    void restoreInventoryDeductionShouldRestoreRawMaterialLocationsFromReceipt() {
        RawMaterial material = new RawMaterial();
        material.setId("raw-restore");
        material.setName("Cotton");
        material.setQuantity(1);
        material.setLocations(new ArrayList<>(Collections.singletonList(
                new LocationInfo("B-01", 1, new Date(2000L))
        )));

        InventoryDeductionReceipt receipt = new InventoryDeductionReceipt(
                "RAW_MATERIAL",
                "raw-restore",
                "Cotton",
                5,
                false,
                Arrays.asList(
                        new InventoryDeductionReceipt.LocationDeduction("A-01", 4, new Date(1000L)),
                        new InventoryDeductionReceipt.LocationDeduction("B-01", 1, new Date(2000L))
                )
        );

        when(rawMaterialRepository.findById("raw-restore")).thenReturn(Optional.of(material));
        when(rawMaterialRepository.save(any(RawMaterial.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(inventoryRecordRepository.save(any(InventoryRecord.class))).thenAnswer(invocation -> invocation.getArgument(0));

        inventoryService.restoreInventoryDeduction(receipt, "plan rejected");

        assertThat(material.getQuantity()).isEqualTo(6);
        assertThat(material.getLocations())
                .extracting(LocationInfo::getLocation, LocationInfo::getQuantity)
                .containsExactly(tuple("B-01", 2), tuple("A-01", 4));

        ArgumentCaptor<InventoryRecord> recordCaptor = ArgumentCaptor.forClass(InventoryRecord.class);
        verify(inventoryRecordRepository).save(recordCaptor.capture());
        assertThat(recordCaptor.getValue().getInventoryType()).isEqualTo("IN");
        assertThat(recordCaptor.getValue().getQuantity()).isEqualTo(5);
        assertThat(recordCaptor.getValue().getReason()).contains("plan rejected");
    }

    @Test
    void fifoDeductFinishedProductShouldFailWhenAtomicTotalQuantityDeductionMisses() {
        FinishedProduct product = buildFinishedProduct("finished-atomic-miss", "BATCH-032", "Hoodie", "W1", "black", "XL");
        product.setQuantity(2);
        product.setLocations(Collections.emptyList());

        when(finishedProductRepository.findById("finished-atomic-miss")).thenReturn(Optional.of(product));
        when(mongoAtomicOpsService.changeFinishedProductQuantity("finished-atomic-miss", -3, 0)).thenReturn(false);

        assertThatThrownBy(() -> inventoryService.fifoDeductFinishedProduct("finished-atomic-miss", 3, "ship"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("库存不足或已被其他操作更新");

        verify(finishedProductRepository, never()).save(any(FinishedProduct.class));
        verify(inventoryRecordRepository, never()).save(any(InventoryRecord.class));
    }

    @Test
    void fifoDeductFinishedProductShouldUseChineseSystemOperatorNameWithoutLocations() {
        FinishedProduct product = buildFinishedProduct("finished-operator", "BATCH-OP", "Hoodie", "W1", "black", "XL");
        product.setQuantity(8);
        product.setLocations(Collections.emptyList());

        when(finishedProductRepository.findById("finished-operator")).thenReturn(Optional.of(product));
        when(mongoAtomicOpsService.changeFinishedProductQuantity("finished-operator", -3, 0)).thenReturn(true);
        when(inventoryRecordRepository.save(any(InventoryRecord.class))).thenAnswer(invocation -> invocation.getArgument(0));

        inventoryService.fifoDeductFinishedProduct("finished-operator", 3, "订单发货");

        ArgumentCaptor<InventoryRecord> recordCaptor = ArgumentCaptor.forClass(InventoryRecord.class);
        verify(inventoryRecordRepository).save(recordCaptor.capture());
        assertThat(recordCaptor.getValue().getOperatorName()).isEqualTo("系统自动");
    }

    @Test
    void fifoDeductFinishedProductShouldThrowWhenTotalStockIsInsufficient() {
        FinishedProduct product = buildFinishedProduct("finished-31", "BATCH-031", "Hoodie", "W1", "black", "XL");
        product.setLocations(new ArrayList<>(Collections.singletonList(
                new LocationInfo("A-02", 1, new Date(1000L))
        )));
        product.setQuantity(1);

        when(finishedProductRepository.findById("finished-31")).thenReturn(Optional.of(product));

        assertThatThrownBy(() -> inventoryService.fifoDeductFinishedProduct("finished-31", 2, "订单发货-ORD002"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("FIFO扣减失败");

        verify(finishedProductRepository, never()).save(any(FinishedProduct.class));
        verify(inventoryRecordRepository, never()).save(any(InventoryRecord.class));
    }

    @Test
    void stockOutShouldIgnoreDuplicatePendingAlertCreation() {
        RawMaterial material = new RawMaterial();
        material.setId("raw-alert-dup");
        material.setName("Cotton");
        material.setQuantity(5);
        material.setAlertThreshold(2);
        material.setLocations(new ArrayList<>());

        User operator = new User();
        operator.setId("warehouse-alert");
        operator.setRealName("warehouse user");

        StockInOutRequest request = new StockInOutRequest();
        request.setItemType("RAW_MATERIAL");
        request.setItemId("raw-alert-dup");
        request.setQuantity(3);
        request.setReason("manual stock out");

        when(rawMaterialRepository.findById("raw-alert-dup")).thenReturn(Optional.of(material));
        when(userRepository.findById("warehouse-alert")).thenReturn(Optional.of(operator));
        when(mongoAtomicOpsService.changeRawMaterialQuantity("raw-alert-dup", -3, 0)).thenReturn(true);
        when(inventoryAlertRepository.save(any(InventoryAlert.class)))
                .thenThrow(new DuplicateKeyException("duplicate pending alert"));
        when(inventoryRecordRepository.save(any(InventoryRecord.class))).thenAnswer(invocation -> invocation.getArgument(0));

        inventoryService.stockOut(request, "warehouse-alert");

        verify(inventoryAlertRepository).save(any(InventoryAlert.class));
        verify(inventoryRecordRepository).save(any(InventoryRecord.class));
    }

    @Test
    void handleAlertShouldThrowBusinessConflictWhenConcurrentHandleFails() {
        InventoryAlert alert = new InventoryAlert();
        alert.setId("alert-1");
        alert.setStatus("PENDING");

        AlertHandleRequest request = new AlertHandleRequest();
        request.setHandleBy("manager-1");

        when(inventoryAlertRepository.findById("alert-1")).thenReturn(Optional.of(alert));
        when(inventoryAlertRepository.save(any(InventoryAlert.class)))
                .thenThrow(new OptimisticLockingFailureException("alert conflict"));

        assertThatThrownBy(() -> inventoryService.handleAlert("alert-1", request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("预警状态已变更");
    }

    private ProductionPlan buildPlan(String id, String batchNo, String productName, String productCode, String color, String size) {
        ProductionPlan plan = new ProductionPlan();
        plan.setId(id);
        plan.setBatchNo(batchNo);
        plan.setProductName(productName);
        plan.setProductCode(productCode);
        plan.setColor(color);
        plan.setSize(size);
        plan.setUnit("件");
        plan.setStatus("COMPLETED");
        plan.setCompletedQuantity(20);
        plan.setStockedInQuantity(0);
        return plan;
    }

    private FinishedProduct buildFinishedProduct(String id, String batchNo, String productName, String productCode, String color, String size) {
        FinishedProduct product = new FinishedProduct();
        product.setId(id);
        product.setBatchNo(batchNo);
        product.setName(productName);
        product.setProductCode(productCode);
        product.setColor(color);
        product.setSize(size);
        product.setUnit("件");
        return product;
    }
}
