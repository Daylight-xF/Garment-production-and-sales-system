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

import java.text.SimpleDateFormat;
import java.util.Date;

@Service
public class MongoAtomicOpsService {

    private final MongoTemplate mongoTemplate;

    public MongoAtomicOpsService(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    public String nextOrderNo(Date now) {
        String prefix = "ORD" + new SimpleDateFormat("yyyyMMdd").format(now);
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

    public boolean changeRawMaterialQuantity(String materialId, int delta, Integer minimumAfterChange) {
        Query query = Query.query(Criteria.where("_id").is(materialId));
        if (minimumAfterChange != null) {
            query.addCriteria(Criteria.where("quantity").gte(minimumAfterChange - delta));
        }
        Update update = new Update().inc("quantity", delta).currentDate("updateTime");
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
        Update update = new Update().inc("quantity", delta).currentDate("updateTime");
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
        Update update = new Update().set("status", nextStatus).currentDate("updateTime");
        if (extraFields != null) {
            extraFields.forEach(update::set);
        }
        return mongoTemplate.findAndModify(
                query,
                update,
                FindAndModifyOptions.options().returnNew(true),
                entityType
        ) != null;
    }
}
