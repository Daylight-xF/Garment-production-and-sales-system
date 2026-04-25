package com.garment.service.support;

import com.garment.model.CounterSequence;
import com.garment.model.FinishedProduct;
import com.garment.model.Order;
import com.garment.model.ProductionPlan;
import com.garment.model.RawMaterial;
import com.mongodb.client.result.UpdateResult;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MongoAtomicOpsServiceTest {

    @Mock
    private MongoTemplate mongoTemplate;

    @InjectMocks
    private MongoAtomicOpsService mongoAtomicOpsService;

    @Test
    void nextOrderNoShouldUseDailyAtomicCounterInBusinessTimezone() {
        CounterSequence sequence = new CounterSequence();
        sequence.setId("ORD20260424");
        sequence.setSeq(7L);

        when(mongoTemplate.findAndModify(any(Query.class), any(Update.class),
                any(FindAndModifyOptions.class), eq(CounterSequence.class)))
                .thenReturn(sequence);

        Date now = Date.from(ZonedDateTime.of(2026, 4, 24, 0, 30, 0, 0, ZoneId.of("Asia/Shanghai")).toInstant());
        String orderNo = mongoAtomicOpsService.nextOrderNo(now);

        ArgumentCaptor<Query> queryCaptor = ArgumentCaptor.forClass(Query.class);
        ArgumentCaptor<Update> updateCaptor = ArgumentCaptor.forClass(Update.class);
        ArgumentCaptor<FindAndModifyOptions> optionsCaptor = ArgumentCaptor.forClass(FindAndModifyOptions.class);
        verify(mongoTemplate).findAndModify(queryCaptor.capture(), updateCaptor.capture(),
                optionsCaptor.capture(), eq(CounterSequence.class));

        assertThat(orderNo).isEqualTo("ORD20260424007");
        assertThat(queryCaptor.getValue().getQueryObject()).isEqualTo(new Document("_id", "ORD20260424"));
        assertThat(updateCaptor.getValue().getUpdateObject()).isEqualTo(new Document("$inc", new Document("seq", 1L))
                .append("$currentDate", new Document("updateTime", true)));
        assertThat(optionsCaptor.getValue().isUpsert()).isTrue();
        assertThat(optionsCaptor.getValue().isReturnNew()).isTrue();
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

    @Test
    void transitionOrderStatusShouldIgnoreReservedExtraFields() {
        when(mongoTemplate.findAndModify(any(Query.class), any(Update.class),
                any(FindAndModifyOptions.class), eq(Order.class)))
                .thenReturn(new Order());

        mongoAtomicOpsService.transitionOrderStatus(
                "order-2",
                "PENDING_APPROVAL",
                "APPROVED",
                new Document("status", "BROKEN")
                        .append("updateTime", new Date(0))
                        .append("version", 99L)
                        .append("approveBy", "manager-2")
        );

        ArgumentCaptor<Update> updateCaptor = ArgumentCaptor.forClass(Update.class);
        verify(mongoTemplate).findAndModify(any(Query.class), updateCaptor.capture(),
                any(FindAndModifyOptions.class), eq(Order.class));

        Document updateObject = updateCaptor.getValue().getUpdateObject();
        assertThat(updateObject.get("$set", Document.class)).isEqualTo(
                new Document("status", "APPROVED").append("approveBy", "manager-2")
        );
        assertThat(updateObject.get("$inc", Document.class)).isEqualTo(
                new Document("version", 1L)
        );
        assertThat(updateObject.get("$currentDate", Document.class)).isEqualTo(
                new Document("updateTime", true)
        );
    }

    @Test
    void changeRawMaterialQuantityShouldBuildAtomicIncrementWithMinimumGuard() {
        when(mongoTemplate.findAndModify(any(Query.class), any(Update.class),
                any(FindAndModifyOptions.class), eq(RawMaterial.class)))
                .thenReturn(new RawMaterial());

        boolean changed = mongoAtomicOpsService.changeRawMaterialQuantity("raw-1", -5, 0);

        ArgumentCaptor<Query> queryCaptor = ArgumentCaptor.forClass(Query.class);
        ArgumentCaptor<Update> updateCaptor = ArgumentCaptor.forClass(Update.class);
        verify(mongoTemplate).findAndModify(queryCaptor.capture(), updateCaptor.capture(),
                any(FindAndModifyOptions.class), eq(RawMaterial.class));

        assertThat(changed).isTrue();
        assertThat(queryCaptor.getValue().getQueryObject()).isEqualTo(
                new Document("_id", "raw-1").append("quantity", new Document("$gte", 5))
        );
        assertThat(updateCaptor.getValue().getUpdateObject()).isEqualTo(
                new Document("$inc", new Document("quantity", -5).append("version", 1L))
                        .append("$currentDate", new Document("updateTime", true))
        );
    }

    @Test
    void changeFinishedProductQuantityShouldBuildAtomicIncrementWithoutMinimumGuard() {
        when(mongoTemplate.findAndModify(any(Query.class), any(Update.class),
                any(FindAndModifyOptions.class), eq(FinishedProduct.class)))
                .thenReturn(new FinishedProduct());

        boolean changed = mongoAtomicOpsService.changeFinishedProductQuantity("finished-1", 3, null);

        ArgumentCaptor<Query> queryCaptor = ArgumentCaptor.forClass(Query.class);
        ArgumentCaptor<Update> updateCaptor = ArgumentCaptor.forClass(Update.class);
        verify(mongoTemplate).findAndModify(queryCaptor.capture(), updateCaptor.capture(),
                any(FindAndModifyOptions.class), eq(FinishedProduct.class));

        assertThat(changed).isTrue();
        assertThat(queryCaptor.getValue().getQueryObject()).isEqualTo(new Document("_id", "finished-1"));
        assertThat(updateCaptor.getValue().getUpdateObject()).isEqualTo(
                new Document("$inc", new Document("quantity", 3).append("version", 1L))
                        .append("$currentDate", new Document("updateTime", true))
        );
    }

    @Test
    void initializeRawMaterialVersionIfMissingShouldMatchMissingOrNullVersion() {
        when(mongoTemplate.updateFirst(any(Query.class), any(Update.class), eq(RawMaterial.class)))
                .thenReturn(UpdateResult.acknowledged(1L, 1L, null));

        boolean initialized = mongoAtomicOpsService.initializeRawMaterialVersionIfMissing("raw-legacy");

        ArgumentCaptor<Query> queryCaptor = ArgumentCaptor.forClass(Query.class);
        ArgumentCaptor<Update> updateCaptor = ArgumentCaptor.forClass(Update.class);
        verify(mongoTemplate).updateFirst(queryCaptor.capture(), updateCaptor.capture(), eq(RawMaterial.class));

        assertThat(initialized).isTrue();
        assertThat(queryCaptor.getValue().getQueryObject()).isEqualTo(
                new Document("_id", "raw-legacy")
                        .append("$or", java.util.Arrays.asList(
                                new Document("version", new Document("$exists", false)),
                                new Document("version", null)
                        ))
        );
        assertThat(updateCaptor.getValue().getUpdateObject()).isEqualTo(
                new Document("$set", new Document("version", 0L))
                        .append("$currentDate", new Document("updateTime", true))
        );
    }

    @Test
    void markPlanMaterialsRestoreInProgressShouldGuardOnRestoreOwnership() {
        when(mongoTemplate.findAndModify(any(Query.class), any(Update.class),
                any(FindAndModifyOptions.class), eq(ProductionPlan.class)))
                .thenReturn(new ProductionPlan());

        boolean changed = mongoAtomicOpsService.markPlanMaterialsRestoreInProgress("plan-1");

        ArgumentCaptor<Query> queryCaptor = ArgumentCaptor.forClass(Query.class);
        ArgumentCaptor<Update> updateCaptor = ArgumentCaptor.forClass(Update.class);
        verify(mongoTemplate).findAndModify(queryCaptor.capture(), updateCaptor.capture(),
                any(FindAndModifyOptions.class), eq(ProductionPlan.class));

        assertThat(changed).isTrue();
        assertThat(queryCaptor.getValue().getQueryObject()).isEqualTo(
                new Document("_id", "plan-1")
                        .append("materialsDeducted", true)
                        .append("materialsRestoreInProgress", new Document("$ne", true))
        );
        assertThat(updateCaptor.getValue().getUpdateObject()).isEqualTo(
                new Document("$set", new Document("materialsRestoreInProgress", true))
                        .append("$inc", new Document("version", 1L))
                        .append("$currentDate", new Document("updateTime", true))
        );
    }

    @Test
    void completePlanMaterialsRestoreShouldClearFlagAndProgressMarker() {
        when(mongoTemplate.findAndModify(any(Query.class), any(Update.class),
                any(FindAndModifyOptions.class), eq(ProductionPlan.class)))
                .thenReturn(new ProductionPlan());

        boolean changed = mongoAtomicOpsService.completePlanMaterialsRestore("plan-2");

        ArgumentCaptor<Query> queryCaptor = ArgumentCaptor.forClass(Query.class);
        ArgumentCaptor<Update> updateCaptor = ArgumentCaptor.forClass(Update.class);
        verify(mongoTemplate).findAndModify(queryCaptor.capture(), updateCaptor.capture(),
                any(FindAndModifyOptions.class), eq(ProductionPlan.class));

        assertThat(changed).isTrue();
        assertThat(queryCaptor.getValue().getQueryObject()).isEqualTo(
                new Document("_id", "plan-2").append("materialsRestoreInProgress", true)
        );
        assertThat(updateCaptor.getValue().getUpdateObject()).isEqualTo(
                new Document("$set", new Document("materialsDeducted", false).append("materialsRestoreInProgress", false))
                        .append("$inc", new Document("version", 1L))
                        .append("$currentDate", new Document("updateTime", true))
        );
    }

    @Test
    void releasePlanMaterialsRestoreShouldOnlyClearInProgressMarker() {
        when(mongoTemplate.findAndModify(any(Query.class), any(Update.class),
                any(FindAndModifyOptions.class), eq(ProductionPlan.class)))
                .thenReturn(new ProductionPlan());

        boolean changed = mongoAtomicOpsService.releasePlanMaterialsRestore("plan-3");

        ArgumentCaptor<Query> queryCaptor = ArgumentCaptor.forClass(Query.class);
        ArgumentCaptor<Update> updateCaptor = ArgumentCaptor.forClass(Update.class);
        verify(mongoTemplate).findAndModify(queryCaptor.capture(), updateCaptor.capture(),
                any(FindAndModifyOptions.class), eq(ProductionPlan.class));

        assertThat(changed).isTrue();
        assertThat(queryCaptor.getValue().getQueryObject()).isEqualTo(
                new Document("_id", "plan-3").append("materialsRestoreInProgress", true)
        );
        assertThat(updateCaptor.getValue().getUpdateObject()).isEqualTo(
                new Document("$set", new Document("materialsRestoreInProgress", false))
                        .append("$inc", new Document("version", 1L))
                        .append("$currentDate", new Document("updateTime", true))
        );
    }
}
