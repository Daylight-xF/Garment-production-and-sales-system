package com.garment.service.impl;

import com.garment.dto.FinishedProductVO;
import com.garment.dto.StockInOutRequest;
import com.garment.exception.BusinessException;
import com.garment.model.FinishedProduct;
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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
