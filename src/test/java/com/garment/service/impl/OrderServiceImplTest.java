package com.garment.service.impl;

import com.garment.dto.OrderApproveRequest;
import com.garment.dto.OrderCreateRequest;
import com.garment.dto.InventoryDeductionReceipt;
import com.garment.dto.OrderItemDTO;
import com.garment.dto.OrderUpdateRequest;
import com.garment.dto.OrderVO;
import com.garment.exception.BusinessException;
import com.garment.model.FinishedProduct;
import com.garment.model.LocationInfo;
import com.garment.model.Order;
import com.garment.model.OrderItem;
import com.garment.model.SalesRecord;
import com.garment.model.User;
import com.garment.repository.FinishedProductRepository;
import com.garment.repository.OrderItemRepository;
import com.garment.repository.OrderLogRepository;
import com.garment.repository.OrderRepository;
import com.garment.repository.SalesRecordRepository;
import com.garment.repository.UserRepository;
import com.garment.service.InventoryService;
import com.garment.service.support.MongoAtomicOpsService;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.OptimisticLockingFailureException;

import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderServiceImplTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderItemRepository orderItemRepository;

    @Mock
    private OrderLogRepository orderLogRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private FinishedProductRepository finishedProductRepository;

    @Mock
    private SalesRecordRepository salesRecordRepository;

    @Mock
    private InventoryService inventoryService;

    @Mock
    private MongoAtomicOpsService mongoAtomicOpsService;

    @InjectMocks
    private OrderServiceImpl orderService;

    @Test
    void createOrderShouldPersistProductCodeColorAndSizeFromRequest() {
        User creator = new User();
        creator.setId("sales-1");
        creator.setRealName("销售甲");

        OrderCreateRequest request = new OrderCreateRequest();
        request.setCustomerId("customer-1");
        request.setCustomerName("星河服饰");
        request.setItems(Arrays.asList(OrderItemDTO.builder()
                .productId("finished-1")
                .productCode("N1")
                .productName("T恤")
                .color("红色")
                .size("M")
                .quantity(2)
                .unitPrice(88.0)
                .amount(176.0)
                .build()));

        when(userRepository.findById("sales-1")).thenReturn(Optional.of(creator));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> {
            Order order = invocation.getArgument(0);
            order.setId("order-new");
            return order;
        });
        when(orderItemRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        orderService.createOrder(request, "sales-1");

        ArgumentCaptor<Iterable<OrderItem>> itemCaptor = ArgumentCaptor.forClass(Iterable.class);
        verify(orderItemRepository).saveAll(itemCaptor.capture());
        OrderItem savedItem = itemCaptor.getValue().iterator().next();

        assertThat(savedItem.getProductId()).isEqualTo("finished-1");
        assertThat(savedItem.getProductCode()).isEqualTo("N1");
        assertThat(savedItem.getProductName()).isEqualTo("T恤");
        assertThat(savedItem.getColor()).isEqualTo("红色");
        assertThat(savedItem.getSize()).isEqualTo("M");
    }

    @Test
    void createOrderShouldUseAtomicOrderNumberGenerator() {
        User creator = new User();
        creator.setId("sales-atomic");
        creator.setRealName("sales user");

        OrderCreateRequest request = new OrderCreateRequest();
        request.setCustomerId("customer-atomic");
        request.setCustomerName("customer name");
        request.setItems(Arrays.asList(OrderItemDTO.builder()
                .productId("finished-atomic")
                .productCode("N1")
                .productName("shirt")
                .quantity(1)
                .unitPrice(88.0)
                .build()));

        when(userRepository.findById("sales-atomic")).thenReturn(Optional.of(creator));
        when(mongoAtomicOpsService.nextOrderNo(any(Date.class))).thenReturn("ORD20260424001");
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> {
            Order order = invocation.getArgument(0);
            order.setId("order-atomic");
            return order;
        });
        when(orderItemRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        OrderVO result = orderService.createOrder(request, "sales-atomic");

        assertThat(result.getOrderNo()).isEqualTo("ORD20260424001");
    }

    @Test
    void approveOrderShouldReloadAndUseAtomicPayloadOnSuccess() {
        Order before = new Order();
        before.setId("order-approve-success");
        before.setStatus("PENDING_APPROVAL");

        Order after = new Order();
        after.setId("order-approve-success");
        after.setStatus("APPROVED");
        after.setApproveBy("manager-success");
        after.setApproveByName("manager user");
        after.setApproveTime(new Date(6000L));
        after.setApproveRemark("ok");

        User approver = new User();
        approver.setId("manager-success");
        approver.setRealName("manager user");

        OrderApproveRequest request = new OrderApproveRequest();
        request.setApproved(true);
        request.setRemark("ok");

        when(orderRepository.findById("order-approve-success")).thenReturn(Optional.of(before), Optional.of(after));
        when(userRepository.findById("manager-success")).thenReturn(Optional.of(approver));
        when(orderItemRepository.findByOrderId("order-approve-success")).thenReturn(Collections.emptyList());
        when(mongoAtomicOpsService.transitionOrderStatus(eq("order-approve-success"), eq("PENDING_APPROVAL"), eq("APPROVED"), any()))
                .thenReturn(true);

        OrderVO result = orderService.approveOrder("order-approve-success", request, "manager-success");

        ArgumentCaptor<Document> approveTransitionCaptor = ArgumentCaptor.forClass(Document.class);
        verify(mongoAtomicOpsService).transitionOrderStatus(eq("order-approve-success"), eq("PENDING_APPROVAL"), eq("APPROVED"), approveTransitionCaptor.capture());
        verify(orderRepository, times(2)).findById("order-approve-success");

        assertThat(result.getStatus()).isEqualTo("APPROVED");
        assertThat(result.getApproveBy()).isEqualTo("manager-success");
        assertThat(result.getApproveByName()).isEqualTo("manager user");
        assertThat(result.getApproveTime()).isEqualTo(after.getApproveTime());
        assertThat(result.getApproveRemark()).isEqualTo("ok");
        assertThat(approveTransitionCaptor.getValue().getString("approveBy")).isEqualTo("manager-success");
        assertThat(approveTransitionCaptor.getValue().getString("approveByName")).isEqualTo("manager user");
        assertThat(approveTransitionCaptor.getValue().getString("approveRemark")).isEqualTo("ok");
        assertThat(approveTransitionCaptor.getValue().getDate("approveTime")).isNotNull();
    }

    @Test
    void approveOrderShouldThrowWhenAtomicStatusTransitionFails() {
        Order order = new Order();
        order.setId("order-approve-atomic");
        order.setStatus("PENDING_APPROVAL");

        User approver = new User();
        approver.setId("manager-atomic");
        approver.setRealName("manager user");

        OrderApproveRequest request = new OrderApproveRequest();
        request.setApproved(true);
        request.setRemark("ok");

        when(orderRepository.findById("order-approve-atomic")).thenReturn(Optional.of(order));
        when(userRepository.findById("manager-atomic")).thenReturn(Optional.of(approver));
        when(mongoAtomicOpsService.transitionOrderStatus(any(), any(), any(), any())).thenReturn(false);

        assertThatThrownBy(() -> orderService.approveOrder("order-approve-atomic", request, "manager-atomic"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("订单状态已变更，请刷新后再操作");
    }

    @Test
    void updateOrderShouldTranslateOptimisticLockConflictWhenSaveFails() {
        Order order = new Order();
        order.setId("order-update-conflict");
        order.setStatus("PENDING_APPROVAL");

        OrderUpdateRequest request = new OrderUpdateRequest();
        request.setRemark("new remark");

        when(orderRepository.findById("order-update-conflict")).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class)))
                .thenThrow(new OptimisticLockingFailureException("order update conflict"));

        assertThatThrownBy(() -> orderService.updateOrder("order-update-conflict", request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("订单状态已变更");
    }

    @Test
    void cancelOrderShouldThrowBusinessConflictWhenAtomicCancellationFails() {
        Order order = new Order();
        order.setId("order-cancel-conflict");
        order.setStatus("APPROVED");

        when(orderRepository.findById("order-cancel-conflict")).thenReturn(Optional.of(order));
        when(mongoAtomicOpsService.transitionOrderStatus("order-cancel-conflict", "APPROVED", "CANCELLED", null))
                .thenReturn(false);

        assertThatThrownBy(() -> orderService.cancelOrder("order-cancel-conflict", "manager-cancel"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("订单状态已变更");

        verify(userRepository, never()).findById("manager-cancel");
        verify(orderRepository, never()).save(any(Order.class));
    }

    @Test
    void shipOrderShouldTransitionBeforeDeductingInventoryAndReloadPersistedOrder() {
        Order before = new Order();
        before.setId("order-ship-1");
        before.setOrderNo("ORD20260415001");
        before.setStatus("APPROVED");

        Order after = new Order();
        after.setId("order-ship-1");
        after.setOrderNo("ORD20260415001");
        after.setStatus("SHIPPED");
        after.setShipTime(new Date(5000L));

        OrderItem item = new OrderItem();
        item.setOrderId("order-ship-1");
        item.setProductId("finished-1");
        item.setProductCode("Y1");
        item.setProductName("T-shirt");
        item.setColor("White");
        item.setSize("M");
        item.setQuantity(3);

        FinishedProduct product = new FinishedProduct();
        product.setId("finished-1");
        product.setQuantity(5);
        product.setLocations(Arrays.asList(
                new LocationInfo("A-01", 2, new Date(1000L)),
                new LocationInfo("B-01", 3, new Date(2000L))
        ));

        User operator = new User();
        operator.setId("warehouse-1");
        operator.setRealName("仓管甲");

        when(orderRepository.findById("order-ship-1")).thenReturn(Optional.of(before), Optional.of(after));
        when(orderItemRepository.findByOrderId("order-ship-1")).thenReturn(Arrays.asList(item));
        when(finishedProductRepository.findById("finished-1")).thenReturn(Optional.of(product));
        when(mongoAtomicOpsService.transitionOrderStatus(eq("order-ship-1"), eq("APPROVED"), eq("SHIPPED"), any()))
                .thenReturn(true);
        when(userRepository.findById("warehouse-1")).thenReturn(Optional.of(operator));
        when(inventoryService.fifoDeductFinishedProductWithReceipt(eq("finished-1"), eq(3), any()))
                .thenReturn(new InventoryDeductionReceipt(
                        "FINISHED_PRODUCT",
                        "finished-1",
                        "T-shirt",
                        3,
                        false,
                        Arrays.asList(
                                new InventoryDeductionReceipt.LocationDeduction("A-01", 2, new Date(1000L)),
                                new InventoryDeductionReceipt.LocationDeduction("B-01", 1, new Date(2000L))
                        )));

        OrderVO result = orderService.shipOrder("order-ship-1", "warehouse-1");

        ArgumentCaptor<Document> shipTransitionCaptor = ArgumentCaptor.forClass(Document.class);
        verify(mongoAtomicOpsService).transitionOrderStatus(eq("order-ship-1"), eq("APPROVED"), eq("SHIPPED"), shipTransitionCaptor.capture());
        verify(orderRepository, times(2)).findById("order-ship-1");

        InOrder inOrder = inOrder(mongoAtomicOpsService, inventoryService);
        inOrder.verify(mongoAtomicOpsService).transitionOrderStatus(eq("order-ship-1"), eq("APPROVED"), eq("SHIPPED"), any());
        inOrder.verify(inventoryService).fifoDeductFinishedProductWithReceipt("finished-1", 3, "订单发货-ORD20260415001 | 商品:T-shirt-Y1/White/M");

        assertThat(result.getStatus()).isEqualTo("SHIPPED");
        assertThat(result.getShipTime()).isEqualTo(after.getShipTime());
        assertThat(shipTransitionCaptor.getValue().getDate("shipTime")).isNotNull();
    }

    @Test
    void shipOrderShouldDeductAcrossMatchingFinishedProductBatchesWhenProductIdIsBlank() {
        Order before = new Order();
        before.setId("order-ship-batches");
        before.setOrderNo("ORD20260426006");
        before.setStatus("APPROVED");

        Order after = new Order();
        after.setId("order-ship-batches");
        after.setOrderNo("ORD20260426006");
        after.setStatus("SHIPPED");
        after.setShipTime(new Date(5000L));

        OrderItem item = new OrderItem();
        item.setOrderId("order-ship-batches");
        item.setProductCode("n2");
        item.setProductName("连衣裙");
        item.setColor("粉红色");
        item.setSize("M");
        item.setQuantity(110);

        FinishedProduct firstBatch = new FinishedProduct();
        firstBatch.setId("batch-1310");
        firstBatch.setBatchNo("PC-20260426-1310");
        firstBatch.setProductCode("n2");
        firstBatch.setName("连衣裙");
        firstBatch.setColor("粉红色");
        firstBatch.setSize("M");
        firstBatch.setQuantity(99);
        firstBatch.setLocations(Collections.singletonList(new LocationInfo("D-06", 99, new Date(1000L))));
        firstBatch.setCreateTime(new Date(1000L));

        FinishedProduct secondBatch = new FinishedProduct();
        secondBatch.setId("batch-1311");
        secondBatch.setBatchNo("PC-20260426-1311");
        secondBatch.setProductCode("n2");
        secondBatch.setName("连衣裙");
        secondBatch.setColor("粉红色");
        secondBatch.setSize("M");
        secondBatch.setQuantity(100);
        secondBatch.setLocations(Collections.singletonList(new LocationInfo("G-01", 100, new Date(2000L))));
        secondBatch.setCreateTime(new Date(2000L));

        User operator = new User();
        operator.setId("warehouse-batches");
        operator.setRealName("仓库管理员");

        when(orderRepository.findById("order-ship-batches")).thenReturn(Optional.of(before), Optional.of(after));
        when(orderItemRepository.findByOrderId("order-ship-batches")).thenReturn(Arrays.asList(item));
        when(finishedProductRepository.findAll()).thenReturn(Arrays.asList(firstBatch, secondBatch));
        when(mongoAtomicOpsService.transitionOrderStatus(eq("order-ship-batches"), eq("APPROVED"), eq("SHIPPED"), any()))
                .thenReturn(true);
        when(userRepository.findById("warehouse-batches")).thenReturn(Optional.of(operator));
        when(inventoryService.fifoDeductFinishedProductWithReceipt(eq("batch-1310"), eq(99), any()))
                .thenReturn(new InventoryDeductionReceipt(
                        "FINISHED_PRODUCT",
                        "batch-1310",
                        "连衣裙",
                        99,
                        false,
                        Collections.singletonList(new InventoryDeductionReceipt.LocationDeduction("D-06", 99, new Date(1000L)))));
        when(inventoryService.fifoDeductFinishedProductWithReceipt(eq("batch-1311"), eq(11), any()))
                .thenReturn(new InventoryDeductionReceipt(
                        "FINISHED_PRODUCT",
                        "batch-1311",
                        "连衣裙",
                        11,
                        false,
                        Collections.singletonList(new InventoryDeductionReceipt.LocationDeduction("G-01", 11, new Date(2000L)))));

        OrderVO result = orderService.shipOrder("order-ship-batches", "warehouse-batches");

        assertThat(result.getStatus()).isEqualTo("SHIPPED");
        verify(inventoryService).fifoDeductFinishedProductWithReceipt(eq("batch-1310"), eq(99), any());
        verify(inventoryService).fifoDeductFinishedProductWithReceipt(eq("batch-1311"), eq(11), any());
    }

    @Test
    void shipOrderShouldFailWhenFinishedProductInventoryIsInsufficient() {
        Order order = new Order();
        order.setId("order-ship-2");
        order.setOrderNo("ORD20260415002");
        order.setStatus("APPROVED");

        OrderItem item = new OrderItem();
        item.setOrderId("order-ship-2");
        item.setProductId("finished-2");
        item.setProductCode("N1");
        item.setProductName("Hoodie");
        item.setColor("Black");
        item.setSize("XL");
        item.setQuantity(4);

        FinishedProduct product = new FinishedProduct();
        product.setId("finished-2");
        product.setQuantity(2);
        product.setLocations(Arrays.asList(
                new LocationInfo("C-01", 2, new Date(1000L))
        ));

        when(orderRepository.findById("order-ship-2")).thenReturn(Optional.of(order));
        when(orderItemRepository.findByOrderId("order-ship-2")).thenReturn(Arrays.asList(item));
        when(finishedProductRepository.findById("finished-2")).thenReturn(Optional.of(product));

        assertThatThrownBy(() -> orderService.shipOrder("order-ship-2", "warehouse-2"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("订单发货失败")
                .hasMessageContaining("Hoodie-N1/Black/XL")
                .hasMessageContaining("需 4，现有 2");

        verifyNoInteractions(mongoAtomicOpsService);
        verifyNoInteractions(inventoryService);
    }

    @Test
    void shipOrderShouldRevertTransitionWhenInventoryDeductionFails() {
        Order before = new Order();
        before.setId("order-ship-rollback");
        before.setOrderNo("ORD20260415003");
        before.setStatus("APPROVED");

        OrderItem item = new OrderItem();
        item.setOrderId("order-ship-rollback");
        item.setProductId("finished-rollback");
        item.setProductCode("RB1");
        item.setProductName("Hoodie");
        item.setColor("Black");
        item.setSize("M");
        item.setQuantity(2);

        FinishedProduct product = new FinishedProduct();
        product.setId("finished-rollback");
        product.setQuantity(2);
        product.setLocations(Collections.emptyList());

        when(orderRepository.findById("order-ship-rollback")).thenReturn(Optional.of(before));
        when(orderItemRepository.findByOrderId("order-ship-rollback")).thenReturn(Arrays.asList(item));
        when(finishedProductRepository.findById("finished-rollback")).thenReturn(Optional.of(product));
        when(mongoAtomicOpsService.transitionOrderStatus(eq("order-ship-rollback"), eq("APPROVED"), eq("SHIPPED"), any()))
                .thenReturn(true);
        when(mongoAtomicOpsService.transitionOrderStatus(eq("order-ship-rollback"), eq("SHIPPED"), eq("APPROVED"), any()))
                .thenReturn(true);
        doThrow(new RuntimeException("deduct failed"))
                .when(inventoryService)
                .fifoDeductFinishedProductWithReceipt(eq("finished-rollback"), eq(2), any());

        assertThatThrownBy(() -> orderService.shipOrder("order-ship-rollback", "warehouse-rollback"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("deduct failed");

        ArgumentCaptor<Document> shipPayloadCaptor = ArgumentCaptor.forClass(Document.class);
        ArgumentCaptor<Document> rollbackPayloadCaptor = ArgumentCaptor.forClass(Document.class);
        verify(mongoAtomicOpsService).transitionOrderStatus(eq("order-ship-rollback"), eq("APPROVED"), eq("SHIPPED"), shipPayloadCaptor.capture());
        verify(mongoAtomicOpsService).transitionOrderStatus(eq("order-ship-rollback"), eq("SHIPPED"), eq("APPROVED"), rollbackPayloadCaptor.capture());
        assertThat(shipPayloadCaptor.getValue().getDate("shipTime")).isNotNull();
        assertThat(rollbackPayloadCaptor.getValue().containsKey("shipTime")).isTrue();
        assertThat(rollbackPayloadCaptor.getValue().get("shipTime")).isNull();
    }

    @Test
    void shipOrderShouldRestorePreviouslyDeductedInventoryWhenLaterDeductionFails() {
        Order before = new Order();
        before.setId("order-ship-restore");
        before.setOrderNo("ORD20260415005");
        before.setStatus("APPROVED");

        OrderItem firstItem = new OrderItem();
        firstItem.setOrderId("order-ship-restore");
        firstItem.setProductId("finished-a");
        firstItem.setProductCode("A1");
        firstItem.setProductName("卫衣");
        firstItem.setColor("黑");
        firstItem.setSize("L");
        firstItem.setQuantity(2);

        OrderItem secondItem = new OrderItem();
        secondItem.setOrderId("order-ship-restore");
        secondItem.setProductId("finished-b");
        secondItem.setProductCode("B1");
        secondItem.setProductName("长裤");
        secondItem.setColor("蓝");
        secondItem.setSize("M");
        secondItem.setQuantity(1);

        FinishedProduct firstProduct = new FinishedProduct();
        firstProduct.setId("finished-a");
        firstProduct.setQuantity(5);
        firstProduct.setLocations(Collections.singletonList(new LocationInfo("A-01", 5, new Date(1000L))));

        FinishedProduct secondProduct = new FinishedProduct();
        secondProduct.setId("finished-b");
        secondProduct.setQuantity(4);
        secondProduct.setLocations(Collections.singletonList(new LocationInfo("B-01", 4, new Date(2000L))));

        InventoryDeductionReceipt firstReceipt = new InventoryDeductionReceipt(
                "FINISHED_PRODUCT",
                "finished-a",
                "卫衣",
                2,
                false,
                Collections.singletonList(new InventoryDeductionReceipt.LocationDeduction("A-01", 2, new Date(1000L))));

        when(orderRepository.findById("order-ship-restore")).thenReturn(Optional.of(before));
        when(orderItemRepository.findByOrderId("order-ship-restore")).thenReturn(Arrays.asList(firstItem, secondItem));
        when(finishedProductRepository.findById("finished-a")).thenReturn(Optional.of(firstProduct));
        when(finishedProductRepository.findById("finished-b")).thenReturn(Optional.of(secondProduct));
        when(mongoAtomicOpsService.transitionOrderStatus(eq("order-ship-restore"), eq("APPROVED"), eq("SHIPPED"), any()))
                .thenReturn(true);
        when(mongoAtomicOpsService.transitionOrderStatus(eq("order-ship-restore"), eq("SHIPPED"), eq("APPROVED"), any()))
                .thenReturn(true);
        when(inventoryService.fifoDeductFinishedProductWithReceipt(eq("finished-a"), eq(2), any()))
                .thenReturn(firstReceipt);
        doThrow(new RuntimeException("second deduct failed"))
                .when(inventoryService)
                .fifoDeductFinishedProductWithReceipt(eq("finished-b"), eq(1), any());

        assertThatThrownBy(() -> orderService.shipOrder("order-ship-restore", "warehouse-restore"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("second deduct failed");

        verify(inventoryService).restoreInventoryDeduction(eq(firstReceipt), eq("订单发货回滚-ORD20260415005"));
        verify(mongoAtomicOpsService).transitionOrderStatus(eq("order-ship-restore"), eq("SHIPPED"), eq("APPROVED"), any());
    }

    @Test
    void shipOrderShouldThrowWhenAtomicTransitionFails() {
        Order order = new Order();
        order.setId("order-ship-conflict");
        order.setOrderNo("ORD20260415004");
        order.setStatus("APPROVED");

        OrderItem item = new OrderItem();
        item.setOrderId("order-ship-conflict");
        item.setProductId("finished-conflict");
        item.setProductCode("CF1");
        item.setProductName("Polo");
        item.setColor("Blue");
        item.setSize("L");
        item.setQuantity(1);

        FinishedProduct product = new FinishedProduct();
        product.setId("finished-conflict");
        product.setQuantity(10);
        product.setLocations(Collections.emptyList());

        when(orderRepository.findById("order-ship-conflict")).thenReturn(Optional.of(order));
        when(orderItemRepository.findByOrderId("order-ship-conflict")).thenReturn(Arrays.asList(item));
        when(finishedProductRepository.findById("finished-conflict")).thenReturn(Optional.of(product));
        when(mongoAtomicOpsService.transitionOrderStatus(eq("order-ship-conflict"), eq("APPROVED"), eq("SHIPPED"), any()))
                .thenReturn(false);

        assertThatThrownBy(() -> orderService.shipOrder("order-ship-conflict", "warehouse-conflict"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("订单状态已变更，请刷新后再操作");

        verifyNoInteractions(inventoryService);
    }

    @Test
    void completeOrderShouldSetCompleteTimeAndCreateOrderLevelSalesRecord() {
        Order before = new Order();
        before.setId("order-1");
        before.setOrderNo("ORD20260413001");
        before.setCustomerId("customer-1");
        before.setCustomerName("星河服饰");
        before.setStatus("SHIPPED");
        before.setTotalAmount(460.0);
        before.setCreateBy("sales-1");
        before.setCreateByName("销售甲");
        before.setCreateTime(new Date(1000L));
        before.setShipTime(new Date(2000L));

        Order after = new Order();
        after.setId("order-1");
        after.setOrderNo("ORD20260413001");
        after.setCustomerId("customer-1");
        after.setCustomerName("星河服饰");
        after.setStatus("COMPLETED");
        after.setTotalAmount(460.0);
        after.setCreateBy("sales-1");
        after.setCreateByName("销售甲");
        after.setCreateTime(new Date(1000L));
        after.setShipTime(new Date(2000L));
        after.setCompleteTime(new Date(3000L));

        OrderItem shirt = new OrderItem();
        shirt.setOrderId("order-1");
        shirt.setProductId("product-1");
        shirt.setProductCode("P001");
        shirt.setProductName("衬衫");
        shirt.setColor("蓝色");
        shirt.setSize("L");
        shirt.setQuantity(3);
        shirt.setUnitPrice(80.0);
        shirt.setAmount(240.0);

        OrderItem coat = new OrderItem();
        coat.setOrderId("order-1");
        coat.setProductId("product-2");
        coat.setProductCode("P002");
        coat.setProductName("外套");
        coat.setColor("黑色");
        coat.setSize("XL");
        coat.setQuantity(2);
        coat.setUnitPrice(110.0);
        coat.setAmount(220.0);

        User operator = new User();
        operator.setId("manager-1");
        operator.setRealName("审核员");

        when(orderRepository.findById("order-1")).thenReturn(Optional.of(before), Optional.of(after));
        when(userRepository.findById("manager-1")).thenReturn(Optional.of(operator));
        when(orderItemRepository.findByOrderId("order-1")).thenReturn(Arrays.asList(shirt, coat));
        when(mongoAtomicOpsService.transitionOrderStatus(eq("order-1"), eq("SHIPPED"), eq("COMPLETED"), any()))
                .thenReturn(true);
        when(salesRecordRepository.findByOrderId("order-1")).thenReturn(Optional.empty());
        when(salesRecordRepository.save(any(SalesRecord.class))).thenAnswer(invocation -> {
            SalesRecord record = invocation.getArgument(0);
            record.setId("sales-1");
            return record;
        });

        OrderVO result = orderService.completeOrder("order-1", "manager-1");

        ArgumentCaptor<SalesRecord> recordCaptor = ArgumentCaptor.forClass(SalesRecord.class);
        ArgumentCaptor<Document> transitionCaptor = ArgumentCaptor.forClass(Document.class);
        verify(mongoAtomicOpsService).transitionOrderStatus(eq("order-1"), eq("SHIPPED"), eq("COMPLETED"), transitionCaptor.capture());
        verify(orderRepository, times(2)).findById("order-1");
        verify(salesRecordRepository).save(recordCaptor.capture());

        SalesRecord savedRecord = recordCaptor.getValue();
        Document transitionPayload = transitionCaptor.getValue();
        assertThat(result.getStatus()).isEqualTo("COMPLETED");
        assertThat(result.getCompleteTime()).isEqualTo(after.getCompleteTime());
        assertThat(transitionPayload.getDate("completeTime")).isNotNull();
        assertThat(savedRecord.getOrderId()).isEqualTo("order-1");
        assertThat(savedRecord.getOrderNo()).isEqualTo("ORD20260413001");
        assertThat(savedRecord.getProductCount()).isEqualTo(2);
        assertThat(savedRecord.getTotalQuantity()).isEqualTo(5);
        assertThat(savedRecord.getTotalAmount()).isEqualTo(460.0);
        assertThat(savedRecord.getOrderDate()).isEqualTo(after.getCreateTime());
        assertThat(savedRecord.getShipDate()).isEqualTo(after.getShipTime());
        assertThat(savedRecord.getCompleteDate()).isEqualTo(after.getCompleteTime());
        assertThat(savedRecord.getItems()).hasSize(2);
        assertThat(savedRecord.getItems()).extracting(SalesRecord.SalesRecordItem::getProductName)
                .containsExactly("衬衫", "外套");
        assertThat(savedRecord.getItems()).extracting(SalesRecord.SalesRecordItem::getColor)
                .containsExactly("蓝色", "黑色");
        assertThat(savedRecord.getItems()).extracting(SalesRecord.SalesRecordItem::getSize)
                .containsExactly("L", "XL");
    }

    @Test
    void completeOrderShouldThrowWhenAtomicTransitionFails() {
        Order order = new Order();
        order.setId("order-complete-conflict");
        order.setStatus("SHIPPED");

        when(orderRepository.findById("order-complete-conflict")).thenReturn(Optional.of(order));
        when(mongoAtomicOpsService.transitionOrderStatus(eq("order-complete-conflict"), eq("SHIPPED"), eq("COMPLETED"), any()))
                .thenReturn(false);

        assertThatThrownBy(() -> orderService.completeOrder("order-complete-conflict", "manager-conflict"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("订单状态已变更，请刷新后再操作");

        verify(orderItemRepository, never()).findByOrderId("order-complete-conflict");
        verify(salesRecordRepository, never()).save(any(SalesRecord.class));
    }

    @Test
    void completeOrderShouldNotCreateDuplicateSalesRecordWhenOrderAlreadyArchived() {
        Order before = new Order();
        before.setId("order-2");
        before.setStatus("SHIPPED");

        Order after = new Order();
        after.setId("order-2");
        after.setStatus("COMPLETED");
        after.setCompleteTime(new Date(4000L));

        User operator = new User();
        operator.setId("manager-2");
        operator.setRealName("审核员乙");

        SalesRecord existingRecord = new SalesRecord();
        existingRecord.setId("sales-existing");
        existingRecord.setOrderId("order-2");

        when(orderRepository.findById("order-2")).thenReturn(Optional.of(before), Optional.of(after));
        when(userRepository.findById("manager-2")).thenReturn(Optional.of(operator));
        when(orderItemRepository.findByOrderId("order-2")).thenReturn(Collections.emptyList());
        when(mongoAtomicOpsService.transitionOrderStatus(eq("order-2"), eq("SHIPPED"), eq("COMPLETED"), any()))
                .thenReturn(true);
        when(salesRecordRepository.findByOrderId("order-2")).thenReturn(Optional.of(existingRecord));

        OrderVO result = orderService.completeOrder("order-2", "manager-2");

        assertThat(result.getStatus()).isEqualTo("COMPLETED");
        assertThat(result.getCompleteTime()).isEqualTo(after.getCompleteTime());
        verify(salesRecordRepository, never()).save(any(SalesRecord.class));
    }

    @Test
    void completeOrderShouldTreatDuplicateArchiveAsAlreadyArchived() {
        Order before = new Order();
        before.setId("order-duplicate-archive");
        before.setOrderNo("ORD20260424002");
        before.setCustomerId("customer-archive");
        before.setCustomerName("customer name");
        before.setStatus("SHIPPED");
        before.setTotalAmount(88.0);
        before.setCreateBy("sales-atomic");
        before.setCreateByName("sales user");

        Order after = new Order();
        after.setId("order-duplicate-archive");
        after.setOrderNo("ORD20260424002");
        after.setCustomerId("customer-archive");
        after.setCustomerName("customer name");
        after.setStatus("COMPLETED");
        after.setTotalAmount(88.0);
        after.setCreateBy("sales-atomic");
        after.setCreateByName("sales user");
        after.setCompleteTime(new Date(7000L));

        OrderItem item = new OrderItem();
        item.setOrderId("order-duplicate-archive");
        item.setProductId("finished-atomic");
        item.setProductCode("N1");
        item.setProductName("shirt");
        item.setQuantity(1);
        item.setUnitPrice(88.0);
        item.setAmount(88.0);

        User operator = new User();
        operator.setId("manager-archive");
        operator.setRealName("manager user");

        when(orderRepository.findById("order-duplicate-archive")).thenReturn(Optional.of(before), Optional.of(after));
        when(userRepository.findById("manager-archive")).thenReturn(Optional.of(operator));
        when(orderItemRepository.findByOrderId("order-duplicate-archive")).thenReturn(Arrays.asList(item));
        when(mongoAtomicOpsService.transitionOrderStatus(eq("order-duplicate-archive"), eq("SHIPPED"), eq("COMPLETED"), any()))
                .thenReturn(true);
        when(salesRecordRepository.findByOrderId("order-duplicate-archive")).thenReturn(Optional.empty());
        when(salesRecordRepository.save(any(SalesRecord.class)))
                .thenThrow(new DuplicateKeyException("duplicate archive"));

        OrderVO result = orderService.completeOrder("order-duplicate-archive", "manager-archive");

        assertThat(result.getStatus()).isEqualTo("COMPLETED");
        assertThat(result.getCompleteTime()).isEqualTo(after.getCompleteTime());
        verify(salesRecordRepository).save(any(SalesRecord.class));
    }
}
