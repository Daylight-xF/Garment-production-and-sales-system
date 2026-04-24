package com.garment.service.impl;

import com.garment.dto.OrderCreateRequest;
import com.garment.dto.OrderApproveRequest;
import com.garment.dto.OrderItemDTO;
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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import java.util.Arrays;
import java.util.Date;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
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
    void completeOrderShouldSetCompleteTimeAndCreateOrderLevelSalesRecord() {
        Order order = new Order();
        order.setId("order-1");
        order.setOrderNo("ORD20260413001");
        order.setCustomerId("customer-1");
        order.setCustomerName("星河服饰");
        order.setStatus("SHIPPED");
        order.setTotalAmount(460.0);
        order.setCreateBy("sales-1");
        order.setCreateByName("销售甲");
        order.setCreateTime(new Date(1000L));
        order.setShipTime(new Date(2000L));

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

        when(orderRepository.findById("order-1")).thenReturn(Optional.of(order));
        when(userRepository.findById("manager-1")).thenReturn(Optional.of(operator));
        when(orderItemRepository.findByOrderId("order-1")).thenReturn(Arrays.asList(shirt, coat));
        when(mongoAtomicOpsService.transitionOrderStatus(any(), any(), any(), any())).thenReturn(true);
        when(salesRecordRepository.findByOrderId("order-1")).thenReturn(Optional.empty());
        when(salesRecordRepository.save(any(SalesRecord.class))).thenAnswer(invocation -> {
            SalesRecord record = invocation.getArgument(0);
            record.setId("sales-1");
            return record;
        });

        OrderVO result = orderService.completeOrder("order-1", "manager-1");

        ArgumentCaptor<SalesRecord> recordCaptor = ArgumentCaptor.forClass(SalesRecord.class);
        verify(salesRecordRepository).save(recordCaptor.capture());

        SalesRecord savedRecord = recordCaptor.getValue();
        assertThat(result.getStatus()).isEqualTo("COMPLETED");
        assertThat(result.getCompleteTime()).isNotNull();
        assertThat(savedRecord.getOrderId()).isEqualTo("order-1");
        assertThat(savedRecord.getOrderNo()).isEqualTo("ORD20260413001");
        assertThat(savedRecord.getProductCount()).isEqualTo(2);
        assertThat(savedRecord.getTotalQuantity()).isEqualTo(5);
        assertThat(savedRecord.getTotalAmount()).isEqualTo(460.0);
        assertThat(savedRecord.getOrderDate()).isEqualTo(order.getCreateTime());
        assertThat(savedRecord.getShipDate()).isEqualTo(order.getShipTime());
        assertThat(savedRecord.getCompleteDate()).isEqualTo(result.getCompleteTime());
        assertThat(savedRecord.getItems()).hasSize(2);
        assertThat(savedRecord.getItems()).extracting(SalesRecord.SalesRecordItem::getProductName)
                .containsExactly("衬衫", "外套");
        assertThat(savedRecord.getItems()).extracting(SalesRecord.SalesRecordItem::getColor)
                .containsExactly("蓝色", "黑色");
        assertThat(savedRecord.getItems()).extracting(SalesRecord.SalesRecordItem::getSize)
                .containsExactly("L", "XL");
    }

    @Test
    void completeOrderShouldNotCreateDuplicateSalesRecordWhenOrderAlreadyArchived() {
        Order order = new Order();
        order.setId("order-2");
        order.setStatus("SHIPPED");

        User operator = new User();
        operator.setId("manager-2");
        operator.setRealName("审核员乙");

        SalesRecord existingRecord = new SalesRecord();
        existingRecord.setId("sales-existing");
        existingRecord.setOrderId("order-2");

        when(orderRepository.findById("order-2")).thenReturn(Optional.of(order));
        when(userRepository.findById("manager-2")).thenReturn(Optional.of(operator));
        when(orderItemRepository.findByOrderId("order-2")).thenReturn(Arrays.asList());
        when(mongoAtomicOpsService.transitionOrderStatus(any(), any(), any(), any())).thenReturn(true);
        when(salesRecordRepository.findByOrderId("order-2")).thenReturn(Optional.of(existingRecord));

        OrderVO result = orderService.completeOrder("order-2", "manager-2");

        assertThat(result.getStatus()).isEqualTo("COMPLETED");
        assertThat(result.getCompleteTime()).isNotNull();
        verify(salesRecordRepository, never()).save(any(SalesRecord.class));
    }

    @Test
    void shipOrderShouldDeductFinishedProductInventoryBeforeMarkingShipped() {
        Order order = new Order();
        order.setId("order-ship-1");
        order.setOrderNo("ORD20260415001");
        order.setStatus("APPROVED");

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

        when(orderRepository.findById("order-ship-1")).thenReturn(Optional.of(order));
        when(orderItemRepository.findByOrderId("order-ship-1")).thenReturn(Arrays.asList(item));
        when(finishedProductRepository.findById("finished-1")).thenReturn(Optional.of(product));
        when(mongoAtomicOpsService.transitionOrderStatus(any(), any(), any(), any())).thenReturn(true);
        when(userRepository.findById("warehouse-1")).thenReturn(Optional.of(operator));

        OrderVO result = orderService.shipOrder("order-ship-1", "warehouse-1");

        assertThat(result.getStatus()).isEqualTo("SHIPPED");
        assertThat(result.getShipTime()).isNotNull();
        verify(inventoryService).fifoDeductFinishedProduct("finished-1", 3, "订单发货-ORD20260415001 | 商品:T-shirt-Y1/White/M");
        verify(mongoAtomicOpsService).transitionOrderStatus(any(), any(), any(), any());
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

        verify(orderRepository, never()).save(any(Order.class));
        verifyNoInteractions(inventoryService);
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
                .hasMessageContaining("璁㈠崟鐘舵€佸凡鍙樺寲");
    }

    @Test
    void completeOrderShouldTreatDuplicateArchiveAsAlreadyArchived() {
        Order order = new Order();
        order.setId("order-duplicate-archive");
        order.setOrderNo("ORD20260424002");
        order.setCustomerId("customer-archive");
        order.setCustomerName("customer name");
        order.setStatus("SHIPPED");
        order.setTotalAmount(88.0);
        order.setCreateBy("sales-atomic");
        order.setCreateByName("sales user");

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

        when(orderRepository.findById("order-duplicate-archive")).thenReturn(Optional.of(order));
        when(userRepository.findById("manager-archive")).thenReturn(Optional.of(operator));
        when(orderItemRepository.findByOrderId("order-duplicate-archive")).thenReturn(Arrays.asList(item));
        when(mongoAtomicOpsService.transitionOrderStatus(any(), any(), any(), any())).thenReturn(true);
        when(salesRecordRepository.findByOrderId("order-duplicate-archive")).thenReturn(Optional.empty());
        when(salesRecordRepository.save(any(SalesRecord.class)))
                .thenThrow(new DuplicateKeyException("duplicate archive"));

        OrderVO result = orderService.completeOrder("order-duplicate-archive", "manager-archive");

        assertThat(result.getStatus()).isEqualTo("COMPLETED");
        verify(salesRecordRepository).save(any(SalesRecord.class));
    }
}

