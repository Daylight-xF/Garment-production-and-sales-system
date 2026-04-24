package com.garment.service.support;

import com.garment.model.CounterSequence;
import com.garment.model.FinishedProduct;
import com.garment.model.Order;
import com.garment.model.ProductionPlan;
import com.garment.model.RawMaterial;
import org.bson.Document;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;

@Service
public class MongoAtomicOpsService {

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter ORDER_NO_DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final Set<String> RESERVED_STATUS_FIELDS = new HashSet<>(Arrays.asList(
            "status",
            "updateTime",
            "version"
    ));

    private final MongoTemplate mongoTemplate;

    public MongoAtomicOpsService(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    public String nextOrderNo(Date now) {
        String prefix = "ORD" + ORDER_NO_DATE_FORMATTER.format(
                Instant.ofEpochMilli(now.getTime()).atZone(BUSINESS_ZONE)
        );
        Query query = Query.query(Criteria.where("_id").is(prefix));
        Update update = new Update().inc("seq", 1L).currentDate("updateTime");
        CounterSequence sequence = mongoTemplate.findAndModify(
                query,
                update,
                FindAndModifyOptions.options().returnNew(true).upsert(true),
                CounterSequence.class
        );
        long next = sequence != null && sequence.getSeq() != null ? sequence.getSeq() : 1L;
        return prefix + String.format("%03d", next);
    }

    public boolean transitionOrderStatus(String orderId, String expectedStatus, String nextStatus, Document extraFields) {
        return transitionStatus(orderId, expectedStatus, nextStatus, extraFields, Order.class);
    }

    public boolean transitionPlanStatus(String planId, String expectedStatus, String nextStatus, Document extraFields) {
        return transitionStatus(planId, expectedStatus, nextStatus, extraFields, ProductionPlan.class);
    }

    public boolean markPlanMaterialsRestoreInProgress(String planId) {
        Query query = Query.query(Criteria.where("_id").is(planId)
                .and("materialsDeducted").is(true)
                .and("materialsRestoreInProgress").ne(true));
        Update update = new Update()
                .set("materialsRestoreInProgress", true)
                .inc("version", 1L)
                .currentDate("updateTime");
        return mongoTemplate.findAndModify(
                query,
                update,
                FindAndModifyOptions.options().returnNew(true),
                ProductionPlan.class
        ) != null;
    }

    public boolean completePlanMaterialsRestore(String planId) {
        Query query = Query.query(Criteria.where("_id").is(planId)
                .and("materialsRestoreInProgress").is(true));
        Update update = new Update()
                .set("materialsDeducted", false)
                .set("materialsRestoreInProgress", false)
                .inc("version", 1L)
                .currentDate("updateTime");
        return mongoTemplate.findAndModify(
                query,
                update,
                FindAndModifyOptions.options().returnNew(true),
                ProductionPlan.class
        ) != null;
    }

    public boolean releasePlanMaterialsRestore(String planId) {
        Query query = Query.query(Criteria.where("_id").is(planId)
                .and("materialsRestoreInProgress").is(true));
        Update update = new Update()
                .set("materialsRestoreInProgress", false)
                .inc("version", 1L)
                .currentDate("updateTime");
        return mongoTemplate.findAndModify(
                query,
                update,
                FindAndModifyOptions.options().returnNew(true),
                ProductionPlan.class
        ) != null;
    }

    public boolean changeRawMaterialQuantity(String materialId, int delta, Integer minimumAfterChange) {
        Query query = Query.query(Criteria.where("_id").is(materialId));
        if (minimumAfterChange != null) {
            query.addCriteria(Criteria.where("quantity").gte(minimumAfterChange - delta));
        }
        Update update = new Update().inc("quantity", delta).inc("version", 1L).currentDate("updateTime");
        return mongoTemplate.findAndModify(
                query,
                update,
                FindAndModifyOptions.options().returnNew(true),
                RawMaterial.class
        ) != null;
    }

    public boolean changeFinishedProductQuantity(String productId, int delta, Integer minimumAfterChange) {
        Query query = Query.query(Criteria.where("_id").is(productId));
        if (minimumAfterChange != null) {
            query.addCriteria(Criteria.where("quantity").gte(minimumAfterChange - delta));
        }
        Update update = new Update().inc("quantity", delta).inc("version", 1L).currentDate("updateTime");
        return mongoTemplate.findAndModify(
                query,
                update,
                FindAndModifyOptions.options().returnNew(true),
                FinishedProduct.class
        ) != null;
    }

    private boolean transitionStatus(String id, String expectedStatus, String nextStatus, Document extraFields,
                                     Class<?> entityType) {
        Query query = Query.query(Criteria.where("_id").is(id).and("status").is(expectedStatus));
        Update update = new Update().set("status", nextStatus).inc("version", 1L).currentDate("updateTime");
        if (extraFields != null) {
            extraFields.forEach((key, value) -> {
                if (!RESERVED_STATUS_FIELDS.contains(key)) {
                    update.set(key, value);
                }
            });
        }
        return mongoTemplate.findAndModify(
                query,
                update,
                FindAndModifyOptions.options().returnNew(true),
                entityType
        ) != null;
    }
}
