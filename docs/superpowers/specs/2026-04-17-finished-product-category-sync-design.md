# 成品库存分类自动带出设计文档

## 背景

当前“产品定义”与“成品库存”都使用 `category` 字段，但成品库存中的分类并不会在后续生产计划入库时自动继承产品定义中的分类。

这导致用户在产品定义页已维护好的产品分类，无法稳定地体现在之后新生成的新成品库存记录中。

## 目标

- 让以后新创建的生产计划保存当时产品定义的 `category`
- 让以后通过生产计划自动创建的成品库存记录自动带出对应 `category`
- 保持现有历史成品库存数据不做迁移、不做补写

## 非目标

- 不回填数据库中已有的成品库存分类
- 不修改成品库存页现有筛选和展示结构
- 不新增独立的分类同步任务或数据修复脚本

## 方案对比

### 方案一：在生产计划中保存分类快照，并在成品自动建档时透传

- 创建生产计划时，把产品定义中的 `category` 写入 `ProductionPlan.category`
- 后续生产计划入库触发自动建档时，把 `plan.category` 写入 `FinishedProduct.category`

优点：

- 分类来源稳定，能保留“创建计划当时”的业务快照
- 后续即使产品定义分类发生变更，旧计划入库仍保持一致
- 成品自动建档逻辑无需额外查一次产品定义

缺点：

- 需要给 `ProductionPlan` 增加一个字段

### 方案二：成品自动建档时按 `productCode` 实时查询产品定义分类

- 不修改 `ProductionPlan`
- 在自动创建 `FinishedProduct` 时通过 `productCode` 查询 `ProductDefinition.category`

优点：

- 改动更小

缺点：

- 分类结果依赖“当前产品定义”，不能保留历史快照
- 同一计划在不同时间入库，可能因为产品定义被修改而拿到不同分类

## 结论

采用方案一。

## 技术设计

### 数据模型

文件：`src/main/java/com/garment/model/ProductionPlan.java`

- 新增字段 `private String category;`
- 仅作为计划快照字段使用，不改变现有计划主键和查询条件

### 创建生产计划

文件：`src/main/java/com/garment/service/impl/ProductionPlanServiceImpl.java`

- 在 `createPlan(...)` 中读取已选产品定义的 `category`
- 保存到 `ProductionPlan.category`

### 成品自动创建

文件：`src/main/java/com/garment/service/impl/InventoryServiceImpl.java`

- 在 `findOrCreateFinishedProduct(ProductionPlan plan)` 中
- 当系统因生产计划入库而自动新建 `FinishedProduct` 时，将 `plan.category` 写入 `newProduct.category`
- 若命中已有成品记录，则继续复用已有记录，不额外修正历史空分类

## 测试设计

优先补充两类回归测试：

1. 创建生产计划时，计划对象会保存产品定义分类
2. 生产计划入库自动新建成品时，成品对象会带出计划分类

## 风险与兼容性

- 本次只影响 2026 年 4 月 17 日之后新创建计划和新生成成品的链路
- 历史计划如果本身没有 `category`，仍不会自动补齐到历史成品
- 现有手工新增成品流程保持不变

## 验收标准

- 在产品定义页将某产品分类设置为任意有效值
- 基于该产品新建生产计划
- 该计划后续入库自动生成新的成品库存记录时，列表中的 `category` 与产品定义页一致
- 不要求历史已存在成品库存记录自动补齐分类
