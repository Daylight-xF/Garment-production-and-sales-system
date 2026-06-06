package com.garment.service.support;

import com.garment.model.CounterSequence;
import com.garment.model.FinishedProduct;
import com.garment.model.InventoryAlert;
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

/**
 * MongoDB 原子操作服务。
 *
 * <p>集中封装订单编号生成、状态流转、库存数量变更和告警处理等需要原子性的写操作。</p>
 */
@Service
public class MongoAtomicOpsService {

    // 业务日期按上海时区生成，避免服务器时区不同导致订单号日期漂移。
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");
    // 订单号中的日期部分固定为 yyyyMMdd，便于按天分组递增。
    private static final DateTimeFormatter ORDER_NO_DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");
    // 状态流转时保留这些核心字段，防止额外字段覆盖状态、更新时间或版本号。
    private static final Set<String> RESERVED_STATUS_FIELDS = new HashSet<>(Arrays.asList(
            "status",
            "updateTime",
            "version"
    ));

    // Spring MongoTemplate 用于执行 findAndModify 和 updateFirst 等原子写操作。
    private final MongoTemplate mongoTemplate;

    /**
     * 创建 MongoDB 原子操作服务。
     *
     * @param mongoTemplate Spring MongoDB 操作模板
     */
    public MongoAtomicOpsService(MongoTemplate mongoTemplate) {
        // 保存模板引用，供后续所有原子更新方法复用。
        this.mongoTemplate = mongoTemplate;
    }

    /**
     * 生成下一条订单编号。
     *
     * <p>订单编号格式为 ORD + 业务日期 + 三位递增序号，例如 ORD20260606001。</p>
     *
     * @param now 当前时间
     * @return 新生成的订单编号
     */
    public String nextOrderNo(Date now) {
        // 根据业务时区格式化日期前缀，确保同一天的订单使用同一个计数器。
        String prefix = "ORD" + ORDER_NO_DATE_FORMATTER.format(
                Instant.ofEpochMilli(now.getTime()).atZone(BUSINESS_ZONE)
        );
        // 使用日期前缀作为计数器主键，按天维护独立递增序列。
        Query query = Query.query(Criteria.where("_id").is(prefix));
        // 原子递增序号并刷新更新时间；upsert 时会自动创建当天计数器。
        Update update = new Update().inc("seq", 1L).currentDate("updateTime");
        // findAndModify 保证多个请求并发生成订单号时不会拿到相同序号。
        CounterSequence sequence = mongoTemplate.findAndModify(
                query,
                update,
                FindAndModifyOptions.options().returnNew(true).upsert(true),
                CounterSequence.class
        );
        // 防御性兜底：极端情况下未返回序号时从 1 开始。
        long next = sequence != null && sequence.getSeq() != null ? sequence.getSeq() : 1L;
        // 将递增序号补齐为三位，拼接为完整订单编号。
        return prefix + String.format("%03d", next);
    }

    /**
     * 原子流转订单状态。
     *
     * @param orderId 订单 ID
     * @param expectedStatus 期望的当前状态
     * @param nextStatus 目标状态
     * @param extraFields 状态流转时需要同步写入的额外字段
     * @return 状态流转成功返回 true，当前状态不匹配或订单不存在返回 false
     */
    public boolean transitionOrderStatus(String orderId, String expectedStatus, String nextStatus, Document extraFields) {
        // 订单状态流转复用通用状态迁移逻辑。
        return transitionStatus(orderId, expectedStatus, nextStatus, extraFields, Order.class);
    }

    /**
     * 原子流转生产计划状态。
     *
     * @param planId 生产计划 ID
     * @param expectedStatus 期望的当前状态
     * @param nextStatus 目标状态
     * @param extraFields 状态流转时需要同步写入的额外字段
     * @return 状态流转成功返回 true，当前状态不匹配或计划不存在返回 false
     */
    public boolean transitionPlanStatus(String planId, String expectedStatus, String nextStatus, Document extraFields) {
        // 生产计划状态流转复用通用状态迁移逻辑。
        return transitionStatus(planId, expectedStatus, nextStatus, extraFields, ProductionPlan.class);
    }

    /**
     * 将生产计划的物料恢复流程标记为进行中。
     *
     * @param planId 生产计划 ID
     * @return 成功抢占恢复流程返回 true，不满足条件或计划不存在返回 false
     */
    public boolean markPlanMaterialsRestoreInProgress(String planId) {
        // 仅允许已扣减物料且当前未处于恢复中的计划进入恢复流程。
        Query query = Query.query(Criteria.where("_id").is(planId)
                .and("materialsDeducted").is(true)
                .and("materialsRestoreInProgress").ne(true));
        // 设置恢复中标记，同时递增版本号并刷新更新时间。
        Update update = new Update()
                .set("materialsRestoreInProgress", true)
                .inc("version", 1L)
                .currentDate("updateTime");
        // 原子抢占恢复流程，避免并发请求重复恢复物料。
        return mongoTemplate.findAndModify(
                query,
                update,
                FindAndModifyOptions.options().returnNew(true),
                ProductionPlan.class
        ) != null;
    }

    /**
     * 完成生产计划的物料恢复流程。
     *
     * @param planId 生产计划 ID
     * @return 成功完成恢复返回 true，未处于恢复中或计划不存在返回 false
     */
    public boolean completePlanMaterialsRestore(String planId) {
        // 只处理已经被标记为恢复中的生产计划。
        Query query = Query.query(Criteria.where("_id").is(planId)
                .and("materialsRestoreInProgress").is(true));
        // 恢复完成后清除物料已扣减标记和恢复中标记，并推进版本号。
        Update update = new Update()
                .set("materialsDeducted", false)
                .set("materialsRestoreInProgress", false)
                .inc("version", 1L)
                .currentDate("updateTime");
        // 原子完成恢复流程，保证状态标记一次性落库。
        return mongoTemplate.findAndModify(
                query,
                update,
                FindAndModifyOptions.options().returnNew(true),
                ProductionPlan.class
        ) != null;
    }

    /**
     * 释放生产计划的物料恢复中标记。
     *
     * <p>用于恢复流程失败或中断后解除占用，让后续流程可以重新尝试。</p>
     *
     * @param planId 生产计划 ID
     * @return 成功释放返回 true，未处于恢复中或计划不存在返回 false
     */
    public boolean releasePlanMaterialsRestore(String planId) {
        // 只释放处于恢复中的生产计划，避免无意义更新。
        Query query = Query.query(Criteria.where("_id").is(planId)
                .and("materialsRestoreInProgress").is(true));
        // 清除恢复中标记，同时递增版本号并刷新更新时间。
        Update update = new Update()
                .set("materialsRestoreInProgress", false)
                .inc("version", 1L)
                .currentDate("updateTime");
        // 原子释放恢复标记，避免并发流程互相覆盖。
        return mongoTemplate.findAndModify(
                query,
                update,
                FindAndModifyOptions.options().returnNew(true),
                ProductionPlan.class
        ) != null;
    }

    /**
     * 原子调整原材料库存数量。
     *
     * @param materialId 原材料 ID
     * @param delta 库存变化量，正数表示增加，负数表示扣减
     * @param minimumAfterChange 变更后允许的最低库存，传 null 表示不限制
     * @return 调整成功返回 true，库存不足或原材料不存在返回 false
     */
    public boolean changeRawMaterialQuantity(String materialId, int delta, Integer minimumAfterChange) {
        // 先按原材料主键定位需要更新的库存记录。
        Query query = Query.query(Criteria.where("_id").is(materialId));
        // 如果指定了变更后的最低库存，则把库存保护条件下推到 MongoDB 查询中。
        if (minimumAfterChange != null) {
            // 变更前数量必须不低于“最低库存 - 本次变化量”，才能保证变更后达标。
            query.addCriteria(Criteria.where("quantity").gte(minimumAfterChange - delta));
        }
        // 原子增减库存数量、推进版本号并刷新更新时间。
        Update update = new Update().inc("quantity", delta).inc("version", 1L).currentDate("updateTime");
        // findAndModify 同时校验条件和更新数量，避免并发扣减导致库存穿透。
        return mongoTemplate.findAndModify(
                query,
                update,
                FindAndModifyOptions.options().returnNew(true),
                RawMaterial.class
        ) != null;
    }

    /**
     * 初始化缺失的原材料版本号。
     *
     * @param materialId 原材料 ID
     * @return 成功初始化返回 true，版本号已存在或原材料不存在返回 false
     */
    public boolean initializeRawMaterialVersionIfMissing(String materialId) {
        // 先按原材料主键定位目标记录。
        Query query = Query.query(Criteria.where("_id").is(materialId));
        // 仅匹配 version 字段不存在或值为 null 的历史数据。
        query.addCriteria(new Criteria().orOperator(
                Criteria.where("version").exists(false),
                Criteria.where("version").is(null)
        ));
        // 将缺失版本号初始化为 0，并刷新更新时间。
        Update update = new Update().set("version", 0L).currentDate("updateTime");
        // 只更新第一条匹配记录，根据修改数量判断是否真正初始化。
        return mongoTemplate.updateFirst(query, update, RawMaterial.class).getModifiedCount() > 0;
    }

    /**
     * 初始化缺失的成品版本号。
     *
     * @param productId 成品 ID
     * @return 成功初始化返回 true，版本号已存在或成品不存在返回 false
     */
    public boolean initializeFinishedProductVersionIfMissing(String productId) {
        // 先按成品主键定位目标记录。
        Query query = Query.query(Criteria.where("_id").is(productId));
        // 仅匹配 version 字段不存在或值为 null 的历史数据。
        query.addCriteria(new Criteria().orOperator(
                Criteria.where("version").exists(false),
                Criteria.where("version").is(null)
        ));
        // 将缺失版本号初始化为 0，并刷新更新时间。
        Update update = new Update().set("version", 0L).currentDate("updateTime");
        // 只更新第一条匹配记录，根据修改数量判断是否真正初始化。
        return mongoTemplate.updateFirst(query, update, FinishedProduct.class).getModifiedCount() > 0;
    }

    /**
     * 原子调整成品库存数量。
     *
     * @param productId 成品 ID
     * @param delta 库存变化量，正数表示增加，负数表示扣减
     * @param minimumAfterChange 变更后允许的最低库存，传 null 表示不限制
     * @return 调整成功返回 true，库存不足或成品不存在返回 false
     */
    public boolean changeFinishedProductQuantity(String productId, int delta, Integer minimumAfterChange) {
        // 先按成品主键定位需要更新的库存记录。
        Query query = Query.query(Criteria.where("_id").is(productId));
        // 如果指定了变更后的最低库存，则把库存保护条件下推到 MongoDB 查询中。
        if (minimumAfterChange != null) {
            // 变更前数量必须不低于“最低库存 - 本次变化量”，才能保证变更后达标。
            query.addCriteria(Criteria.where("quantity").gte(minimumAfterChange - delta));
        }
        // 原子增减库存数量、推进版本号并刷新更新时间。
        Update update = new Update().inc("quantity", delta).inc("version", 1L).currentDate("updateTime");
        // findAndModify 同时校验条件和更新数量，避免并发扣减导致库存穿透。
        return mongoTemplate.findAndModify(
                query,
                update,
                FindAndModifyOptions.options().returnNew(true),
                FinishedProduct.class
        ) != null;
    }

    /**
     * 原子处理库存告警。
     *
     * @param alertId 告警 ID
     * @param handleBy 处理人
     * @param handleTime 处理时间
     * @return 处理成功返回 true，告警不是待处理状态或不存在返回 false
     */
    public boolean handleInventoryAlert(String alertId, String handleBy, Date handleTime) {
        // 只允许处理仍处于待处理状态的告警。
        Query query = Query.query(Criteria.where("_id").is(alertId).and("status").is("PENDING"));
        // 标记告警已处理，记录处理信息，并移除打开告警的唯一键。
        Update update = new Update()
                .set("status", "HANDLED")
                .set("handleTime", handleTime)
                .set("handleBy", handleBy)
                .unset("openAlertKey")
                .inc("version", 1L)
                .currentDate("updateTime");
        // 原子处理告警，避免同一条待处理告警被重复处理。
        return mongoTemplate.findAndModify(
                query,
                update,
                FindAndModifyOptions.options().returnNew(true),
                InventoryAlert.class
        ) != null;
    }

    /**
     * 通用状态流转原子操作。
     *
     * @param id 实体 ID
     * @param expectedStatus 期望的当前状态
     * @param nextStatus 目标状态
     * @param extraFields 状态流转时附带更新的额外字段
     * @param entityType 实体类型
     * @return 状态流转成功返回 true，当前状态不匹配或实体不存在返回 false
     */
    private boolean transitionStatus(String id, String expectedStatus, String nextStatus, Document extraFields,
                                     Class<?> entityType) {
        // 同时匹配 ID 和期望状态，确保只有处于正确状态的实体才能流转。
        Query query = Query.query(Criteria.where("_id").is(id).and("status").is(expectedStatus));
        // 写入目标状态、递增版本号并刷新更新时间。
        Update update = new Update().set("status", nextStatus).inc("version", 1L).currentDate("updateTime");
        // 如果调用方传入额外字段，则逐项写入允许被覆盖的业务字段。
        if (extraFields != null) {
            // 过滤保留字段，避免调用方通过 extraFields 破坏状态流转核心字段。
            extraFields.forEach((key, value) -> {
                // 只有非保留字段才会追加到本次原子更新中。
                if (!RESERVED_STATUS_FIELDS.contains(key)) {
                    // 将额外业务字段设置到同一个 Update 对象，保证和状态流转一起提交。
                    update.set(key, value);
                }
            });
        }
        // 使用 findAndModify 原子执行“校验当前状态 + 更新目标状态”。
        return mongoTemplate.findAndModify(
                query,
                update,
                FindAndModifyOptions.options().returnNew(true),
                entityType
        ) != null;
    }
}
