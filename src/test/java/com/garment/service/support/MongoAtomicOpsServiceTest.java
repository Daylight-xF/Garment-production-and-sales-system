package com.garment.service.support;

import com.garment.model.CounterSequence;
import com.garment.model.Order;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MongoAtomicOpsServiceTest {

    @Mock
    private MongoTemplate mongoTemplate;

    @InjectMocks
    private MongoAtomicOpsService mongoAtomicOpsService;

    @Test
    void nextOrderNoShouldUseDailyAtomicCounter() {
        CounterSequence sequence = new CounterSequence();
        sequence.setId("ORD20260424");
        sequence.setSeq(7L);

        when(mongoTemplate.findAndModify(any(Query.class), any(Update.class),
                any(FindAndModifyOptions.class), eq(CounterSequence.class)))
                .thenReturn(sequence);

        String orderNo = mongoAtomicOpsService.nextOrderNo(new Date(1776998400000L));

        assertThat(orderNo).isEqualTo("ORD20260424007");
    }

    @Test
    void transitionOrderStatusShouldReturnFalseWhenCurrentStatusDoesNotMatch() {
        when(mongoTemplate.findAndModify(any(Query.class), any(Update.class),
                any(FindAndModifyOptions.class), eq(Order.class)))
                .thenReturn(null);

        boolean changed = mongoAtomicOpsService.transitionOrderStatus(
                "order-1",
                "PENDING_APPROVAL",
                "APPROVED",
                new Document("approveBy", "manager-1").append("approveByName", "审核员")
        );

        assertThat(changed).isFalse();
    }
}
