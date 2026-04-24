# MongoDB Concurrency Hardening Design

## Goal

为服装生产销售系统补齐最关键的并发保护能力，优先解决库存扣减、订单与生产计划状态流转、订单号生成、销售归档幂等这四类高风险问题，同时尽量保持现有 Controller 接口形状和页面交互不变。

## Current Context

当前后端基于 Spring Boot 2.7 + Spring Data MongoDB，核心业务写操作主要采用“先查询文档，再在 Java 内存中修改对象，最后 `save` 回 MongoDB”的模式。该模式在单用户或低并发下可工作，但在多用户同时操作时容易出现以下问题：

- 两次发货同时通过库存检查，导致成品超卖。
- 两次创建生产计划同时扣减原料，导致原料超扣。
- 两次审批、发货、完结同时发生，导致订单状态重复变更或后写覆盖前写。
- 两次开始生产同时通过“尚未生成任务”的检查，导致重复生成任务。
- 订单号通过 `count + 1` 生成，在并发创建订单时可能撞号。
- 订单完结后销售归档使用“先查再存”的方式，可能生成重复销售记录。

## Non-Goals

本次改造不包含以下内容：

- 不重构前端页面结构或接口协议。
- 不把库位 `locations` 完整拆分成独立集合。
- 不引入 Redis、Redisson、消息队列或分布式锁中间件。
- 不把所有 Repository 改造成复杂自定义实现。
- 不追求覆盖系统内所有低风险写操作，只优先覆盖最危险路径。

## Recommended Approach

采用“混合稳妥方案”：

- 对库存扣减、状态流转、序列号生成这类高风险写路径，使用 MongoDB 原子条件更新。
- 对普通编辑类更新，给关键文档加 `@Version`，通过乐观锁阻止静默覆盖写。
- 对销售归档等幂等场景，使用唯一索引保证“一次且仅一次”。

该方案比纯乐观锁更适合库存业务，也比引入分布式锁的方案更轻量，适合在当前代码结构上渐进落地。

## Scope

本次改造覆盖以下后端对象与服务：

- 模型：
  - `RawMaterial`
  - `FinishedProduct`
  - `Order`
  - `ProductionPlan`
  - `ProductionTask`
  - `SalesRecord`
- 服务：
  - `InventoryServiceImpl`
  - `OrderServiceImpl`
  - `ProductionPlanServiceImpl`
  - `ProductionTaskServiceImpl`
- 基础设施：
  - 新增一个 Mongo 原子更新支持组件
  - 新增一个订单号计数器集合

## Data Model Changes

### 1. Add Optimistic Lock Fields

以下模型新增 `@Version` 字段：

- `RawMaterial`
- `FinishedProduct`
- `Order`
- `ProductionPlan`
- `ProductionTask`

作用：

- 防止普通编辑型 `save` 操作覆盖别人刚提交的更改。
- 在更新冲突时显式失败，便于返回“数据已变化，请刷新后重试”。

说明：

- 库存扣减与状态流转不依赖乐观锁单独保证正确性，仍然使用原子条件更新。
- `SalesRecord` 本次不强制增加 `@Version`，因为其核心问题是归档幂等，而非高频多人编辑。

### 2. Add Unique Indexes

保留并依赖以下唯一约束：

- `Order.orderNo` 唯一索引

新增以下唯一约束：

- `SalesRecord.orderId` 唯一索引

作用：

- 保证同一订单只能归档生成一条销售记录。
- 当并发完结订单时，即使业务层发生竞争，也由数据库唯一约束进行兜底。

### 3. Add Counter Collection

新增计数器集合，例如 `counters`，用于维护按日期分组的订单号序列。集合文档建议包含：

- `id`：序列键，例如 `ORD20260424`
- `seq`：当前已分配流水号
- `updateTime`：最近更新时间

## New Infrastructure Component

新增一个专用的 Mongo 并发支持组件，建议命名为 `MongoAtomicOpsService` 或 `MongoConcurrencySupport`。该组件只负责高风险原子写能力，不承载普通 CRUD。

职责分为三类：

### 1. Atomic Inventory Mutation

提供原子加减库存能力：

- 原料总库存扣减
- 成品总库存扣减
- 原料总库存入库累加
- 成品总库存入库累加

实现原则：

- 扣减时必须携带条件 `quantity >= required`
- 更新采用 `findAndModify` 或等价的条件更新方式
- 失败时返回“未命中条件更新”，由业务层转换为“库存不足或数据已变化”

### 2. Atomic Status Transition

提供状态机式原子流转：

- `PENDING_APPROVAL -> APPROVED`
- `PENDING_APPROVAL -> CANCELLED`
- `APPROVED -> SHIPPED`
- `SHIPPED -> COMPLETED`
- `PENDING -> APPROVED`
- `PENDING -> CANCELLED`
- `APPROVED -> IN_PROGRESS`
- `IN_PROGRESS -> COMPLETED`

实现原则：

- 更新条件包含“当前状态必须等于预期旧状态”
- 只在条件满足时更新状态与相关时间字段
- 未命中时返回并发冲突，由业务层提示前端刷新

### 3. Atomic Order Number Generation

使用 `counters` 集合为订单号生成每日递增序列：

- 前缀格式仍保持 `ORDyyyyMMdd`
- 每天从 `001` 开始递增
- 通过原子 `inc` 保证并发下不会重复

## Service-Level Design

### InventoryServiceImpl

#### stockOut

现状：

- 先读库存，再做数量检查，再 `save`

改造后：

- 无库位场景：调用原子扣减组件执行条件扣减
- 有库位场景：保留现有 `locations` 读改写逻辑，但依赖文档 `@Version` 防止覆盖写
- 扣减成功后再写 `InventoryRecord`
- 若扣减后达到预警阈值，再补 `InventoryAlert`

返回语义：

- 扣减失败时返回“库存不足或已被其他操作更新，请刷新后重试”

#### fifoDeductRawMaterial / fifoDeductFinishedProduct

现状：

- 先把文档读入内存，再按 FIFO 算法遍历库位并 `save`

改造后：

- 若不存在库位库存，仅有总库存字段，则走原子条件扣减
- 若存在库位库存，沿用 FIFO 读改写算法，但通过 `@Version` 兜底并发覆盖
- 一旦保存阶段产生乐观锁冲突，业务层返回并发冲突错误，不自动重试

取舍说明：

- 要对 `locations` 子数组做真正细粒度原子 FIFO 扣减，复杂度很高，会显著扩大改造范围。
- 本次先把“总库存层面的安全性”与“库位文档级冲突感知”补齐，满足最小可落地目标。

#### stockIn

改造后：

- 无库位场景：总库存改为原子累加
- 有库位场景：保留原有库位增量逻辑，依赖 `@Version`
- 入库记录仍在成功后写入

#### moveRawMaterialLocation / moveFinishedProductLocation

改造后：

- 暂不做子数组级原子迁移
- 保留现有逻辑，但通过 `@Version` 感知并发冲突
- 错误提示统一为“库位库存已变化，请刷新后重试”

### OrderServiceImpl

#### createOrder

现状：

- `generateOrderNo()` 基于 `countByOrderNoStartingWith(prefix) + 1`

改造后：

- 用原子序列组件生成订单号
- `Order.orderNo` 唯一索引仍保留作为最终兜底

#### approveOrder

改造后：

- 仅通过原子状态流转更新 `PENDING_APPROVAL` 订单
- 审批通过与审批拒绝都不再依赖“先判断后保存”
- 未命中旧状态时返回“订单状态已变化，请刷新后再操作”

#### shipOrder

改造后：

- 先读取订单明细，计算需要扣减的成品数量
- 对每个目标成品执行库存扣减
- 库存全部扣减成功后，执行 `APPROVED -> SHIPPED` 原子状态流转
- 若状态流转失败，则返回并发冲突错误

说明：

- 在不引入跨集合事务基础设施的前提下，该流程依然存在“库存已扣减但状态更新失败”的边界场景。
- 本次通过“状态必须原子命中旧值”防止重复发货主路径出错，属于显著收敛风险的最小方案。
- 若后续需要进一步收敛该边界，可再引入 Mongo 事务管理器并梳理补偿策略。

#### completeOrder

改造后：

- 使用原子状态流转执行 `SHIPPED -> COMPLETED`
- 完成后执行销售归档
- 销售归档由 `SalesRecord.orderId` 唯一索引保证幂等
- 若并发下重复归档，允许捕获唯一索引冲突并视为“已归档”

### ProductionPlanServiceImpl

#### createPlan

现状：

- 先循环检查原料库存是否足够，再逐项扣减

改造后：

- 保留业务上的 BOM 校验，但真正扣减必须走原子扣减能力
- 任一物料扣减失败时，整个计划创建失败
- 错误提示明确指出“原料不足或已被其他计划占用”

说明：

- 不引入事务的前提下，多物料逐项扣减仍然存在部分成功的边界风险。
- 这是当前技术栈与最小改造范围下可接受的阶段性方案；若后续继续增强，可再引入 Mongo 事务或预占用模型。

#### approvePlan

改造后：

- 仅允许 `PENDING -> APPROVED` 或 `PENDING -> CANCELLED`
- 并发重复审批时，只能一个请求成功
- 取消计划时的原料返还沿用现有逻辑

#### startProduction

改造后：

- 计划状态更新改为原子 `APPROVED -> IN_PROGRESS`
- 自动创建任务只允许发生一次

为防重复建任务，新增一层最小保护：

- 为自动生成任务增加唯一标识约束，建议使用 `planId + taskName` 或显式 `autoCreated=true + planId` 组合保证唯一

效果：

- 即使两个请求几乎同时进入，只有一个请求能完成状态流转和任务创建主路径

#### completePlan

改造后：

- 保留“所有任务都完成后才能完工”的业务校验
- 最终状态更新改为原子 `IN_PROGRESS -> COMPLETED`

### ProductionTaskServiceImpl

#### updateProgress

改造后：

- 保留现有进度与完成数量联动逻辑
- `ProductionTask` 与 `ProductionPlan` 增加 `@Version` 后，保存时可感知并发覆盖
- 汇总 `plan.completedQuantity` 时，若发生乐观锁冲突，允许一次轻量重读后再保存

说明：

- 此处是编辑型并发问题，不像库存扣减那样高风险，因此用乐观锁更合适

## Error Handling Strategy

统一将失败分为两类：

### 1. Business Failure

适用于真实业务条件不满足：

- 库存不足
- 计划物料缺失
- 任务未全部完成

返回文案要明确告诉用户哪个条件不满足。

### 2. Concurrency Conflict

适用于数据在用户操作期间被他人修改：

- 状态已变化
- 版本冲突
- 条件更新未命中

统一提示风格：

- “数据已被其他操作更新，请刷新后重试”
- “订单状态已变化，请刷新后再操作”
- “库存已变化，请刷新后重新确认”

## Retry and Idempotency Policy

- 不对库存扣减类操作做自动重试，避免业务放大。
- 对普通编辑型乐观锁冲突，允许一次轻量重读后再保存。
- 对销售归档，依赖唯一索引保证幂等，不以循环重试为核心策略。

## Testing Strategy

本次改造至少补充以下测试：

1. 并发创建订单时，订单号保持唯一。
2. 同一订单被并发审批时，仅一个请求成功。
3. 同一订单被并发发货时，仅一个请求成功扣减并进入已发货状态。
4. 成品库存不足时，发货失败且库存不被错误扣减。
5. 同一生产计划被并发开工时，仅生成一个自动任务。
6. 同一订单被并发完结时，仅产生一条销售记录。
7. 任务进度汇总遇到版本冲突时，能返回明确并发错误或完成一次轻量重读更新。

测试优先级：

- 先补服务层测试与原子更新组件测试
- 暂不引入复杂压测框架

## File Impact

预计改动文件：

- `src/main/java/com/garment/model/RawMaterial.java`
- `src/main/java/com/garment/model/FinishedProduct.java`
- `src/main/java/com/garment/model/Order.java`
- `src/main/java/com/garment/model/ProductionPlan.java`
- `src/main/java/com/garment/model/ProductionTask.java`
- `src/main/java/com/garment/model/SalesRecord.java`
- `src/main/java/com/garment/service/impl/InventoryServiceImpl.java`
- `src/main/java/com/garment/service/impl/OrderServiceImpl.java`
- `src/main/java/com/garment/service/impl/ProductionPlanServiceImpl.java`
- `src/main/java/com/garment/service/impl/ProductionTaskServiceImpl.java`
- 新增并发支持组件及其测试

## Risks and Trade-Offs

### 1. Locations Still Use Document-Level Conflict Detection

库位数组 `locations` 本次不拆表，因此 FIFO 与移库仍是“读改写 + 版本检测”模型。它比当前实现安全得多，但还不是最细粒度的原子库位库存模型。

### 2. Multi-Document Atomicity Is Still Limited

当前系统未围绕 Mongo 多文档事务进行完整建模，因此像“先扣库存再改状态”这种跨集合流程，仍可能存在边界不一致场景。本次方案优先解决重复提交、超卖、撞号、重复归档等最常见高风险问题。

### 3. Some Existing Business Logic Will Need Small Refactors

为了接入原子更新与唯一序列，部分 Service 方法会从直接 `save` 改成“查询 + 原子调用 + 记录副作用”的结构，但接口签名保持不变。

## Success Criteria

完成本次改造后，应达到以下结果：

- 并发创建订单时不再出现订单号重复。
- 并发审批、发货、完结时，不再出现状态重复流转。
- 并发发货与扣料时，主路径不再因“先查后改”导致明显超扣或超卖。
- 同一订单不会重复归档为多条销售记录。
- 普通编辑场景遇到并发覆盖时能够被检测并明确反馈。

## Rollout Order

建议按以下顺序实施：

1. 新增并发支持组件与订单号序列能力。
2. 为关键模型补 `@Version` 与唯一索引。
3. 改造 `OrderServiceImpl` 中的订单号、审批、发货、完结。
4. 改造 `InventoryServiceImpl` 中的原子扣减与入库主路径。
5. 改造 `ProductionPlanServiceImpl` 中的扣料、审批、开工、完工。
6. 改造 `ProductionTaskServiceImpl` 中的进度汇总冲突处理。
7. 补充服务层并发相关测试。
