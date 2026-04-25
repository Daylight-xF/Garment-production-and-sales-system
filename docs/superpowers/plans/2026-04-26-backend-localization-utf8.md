# Backend Localization and UTF-8 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 修复后端服务层中的中文乱码与英文提示，统一对外提示为中文，并补齐 Maven UTF-8 编码配置。

**Architecture:** 先以测试锁定关键用户可见提示，再修复服务实现类中的乱码字符串、英文状态提示和系统生成备注，最后在构建层显式声明 UTF-8，避免后续再次出现编码漂移。改动范围优先收敛在服务实现、相关单测和 `pom.xml`。

**Tech Stack:** Java 8, Spring Boot 2.7, Maven, JUnit 5, Mockito, AssertJ

---

### Task 1: 锁定受影响的测试断言

**Files:**
- Modify: `src/test/java/com/garment/service/impl/InventoryServiceImplTest.java`
- Modify: `src/test/java/com/garment/service/impl/OrderServiceImplTest.java`
- Modify: `src/test/java/com/garment/service/impl/UserServiceImplTest.java`
- Modify: `src/test/java/com/garment/service/impl/AuthServiceImplTest.java`
- Modify: `src/test/java/com/garment/service/impl/ProductionPlanServiceImplTest.java`

- [ ] 写出或修正关键业务提示的失败断言，覆盖库存、订单、用户、认证、生产计划至少一条中文提示
- [ ] 运行对应单测，确认当前因为乱码或英文提示而失败

### Task 2: 修复服务层中文提示

**Files:**
- Modify: `src/main/java/com/garment/service/impl/InventoryServiceImpl.java`
- Modify: `src/main/java/com/garment/service/impl/ProductionPlanServiceImpl.java`
- Modify: `src/main/java/com/garment/service/impl/OrderServiceImpl.java`
- Modify: `src/main/java/com/garment/service/impl/UserServiceImpl.java`
- Modify: `src/main/java/com/garment/service/impl/AuthServiceImpl.java`

- [ ] 将业务异常、系统自动备注、库存操作原因中的乱码统一修复为中文 UTF-8 文本
- [ ] 将直接暴露给前端的英文状态值提示改为中文描述
- [ ] 保持现有并发控制和业务逻辑不变，只修复文本与可见提示

### Task 3: 补齐构建编码配置

**Files:**
- Modify: `pom.xml`

- [ ] 增加 Maven 源码与报告编码属性，显式使用 UTF-8

### Task 4: 验证与复查

**Files:**
- Verify: `src/main/java/com/garment/service/impl/*.java`
- Verify: `src/test/java/com/garment/service/impl/*.java`
- Verify: `pom.xml`

- [ ] 运行受影响单测，确认中文提示断言通过
- [ ] 复查服务实现类中是否仍有典型乱码片段或对外英文提示残留
