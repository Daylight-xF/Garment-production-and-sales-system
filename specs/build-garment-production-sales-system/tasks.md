# Tasks

* [x] Task 1: 搭建后端Spring Boot项目基础架构

  * [x] SubTask 1.1: 初始化Spring Boot项目，配置Maven依赖（Spring Web、Spring Data MongoDB、Spring Security、JWT等）

  * [x] SubTask 1.2: 配置MongoDB数据库连接和应用基础配置（application.yml）

  * [x] SubTask 1.3: 搭建项目分层结构（controller、service、repository、model、dto、config、util）

  * [x] SubTask 1.4: 实现统一响应封装（统一返回格式、全局异常处理）

  * [x] SubTask 1.5: 实现JWT工具类和Spring Security安全配置

* [x] Task 2: 搭建前端Vue项目基础架构

  * [x] SubTask 2.1: 初始化Vue项目，配置依赖（Vue Router、Pinia、Axios、Element Plus、ECharts）

  * [x] SubTask 2.2: 搭建项目目录结构（views、components、store、router、api、utils）

  * [x] SubTask 2.3: 配置Axios请求拦截器（JWT令牌自动附加、响应错误统一处理）

  * [x] SubTask 2.4: 配置Vue Router路由守卫（权限控制）

  * [x] SubTask 2.5: 配置Pinia状态管理（用户信息、权限状态）

* [x] Task 3: 实现用户与权限管理模块

  * [x] SubTask 3.1: 后端 - 设计并实现用户（User）、角色（Role）、权限（Permission）数据模型

  * [x] SubTask 3.2: 后端 - 实现用户注册、登录、登出API接口

  * [x] SubTask 3.3: 后端 - 实现基于RBAC的权限控制，配置四种角色（系统管理员、生产管理人员、仓库操作人员、销售人员）的权限矩阵

  * [x] SubTask 3.4: 后端 - 实现用户CRUD管理接口（创建、查询、更新、删除、角色分配）

  * [x] SubTask 3.5: 前端 - 实现登录页面和登录流程

  * [x] SubTask 3.6: 前端 - 实现用户管理页面（用户列表、创建用户、编辑用户、角色分配）

  * [x] SubTask 3.7: 前端 - 实现主布局框架（侧边栏菜单、顶部导航、内容区域），菜单根据角色权限动态显示

* [x] Task 4: 实现生产管理模块

  * [x] SubTask 4.1: 后端 - 设计并实现生产计划（ProductionPlan）、生产任务（ProductionTask）数据模型

  * [x] SubTask 4.2: 后端 - 实现生产计划CRUD接口（创建、查询、更新、删除、审批）

  * [x] SubTask 4.3: 后端 - 实现生产任务接口（任务分配、进度更新、状态变更）

  * [x] SubTask 4.4: 前端 - 实现生产计划管理页面（计划列表、创建计划、编辑计划、审批计划）

  * [x] SubTask 4.5: 前端 - 实现生产任务管理页面（任务列表、任务分配、进度更新）

  * [x] SubTask 4.6: 前端 - 实现生产进度跟踪页面（甘特图或进度条展示）

* [x] Task 5: 实现库存管理模块

  * [x] SubTask 5.1: 后端 - 设计并实现原材料（RawMaterial）、成品（FinishedProduct）、库存记录（InventoryRecord）、库存预警（InventoryAlert）数据模型

  * [x] SubTask 5.2: 后端 - 实现库存查询接口（原材料库存、成品库存、分页、筛选）

  * [x] SubTask 5.3: 后端 - 实现出入库操作接口（入库、出库、库存数量更新、出入库日志记录）

  * [x] SubTask 5.4: 后端 - 实现库存预警接口（预警阈值设置、预警查询、预警通知）

  * [x] SubTask 5.5: 前端 - 实现原材料库存管理页面（库存列表、入库、出库操作）

  * [x] SubTask 5.6: 前端 - 实现成品库存管理页面（库存列表、入库、出库操作）

  * [x] SubTask 5.7: 前端 - 实现库存预警页面（预警列表、阈值设置、预警处理）

* [x] Task 6: 实现订单管理模块

  * [x] SubTask 6.1: 后端 - 设计并实现订单（Order）、订单明细（OrderItem）、订单日志（OrderLog）数据模型

  * [x] SubTask 6.2: 后端 - 实现订单CRUD接口（创建、查询、更新、取消）

  * [x] SubTask 6.3: 后端 - 实现订单审核接口（审核通过、审核拒绝、审核意见记录）

  * [x] SubTask 6.4: 后端 - 实现订单跟踪接口（状态变更记录查询）

  * [x] SubTask 6.5: 前端 - 实现订单列表页面（订单查询、筛选、分页）

  * [x] SubTask 6.6: 前端 - 实现订单创建页面（客户选择、产品选择、数量填写）

  * [x] SubTask 6.7: 前端 - 实现订单详情页面（订单信息、审核操作、状态跟踪）

* [x] Task 7: 实现销售管理模块

  * [x] SubTask 7.1: 后端 - 设计并实现销售记录（SalesRecord）、客户（Customer）数据模型

  * [x] SubTask 7.2: 后端 - 实现销售数据录入和查询接口

  * [x] SubTask 7.3: 后端 - 实现销售报表接口（按时间、产品类别等维度统计）

  * [x] SubTask 7.4: 后端 - 实现客户CRUD管理接口

  * [x] SubTask 7.5: 前端 - 实现销售数据录入页面

  * [x] SubTask 7.6: 前端 - 实现销售报表页面（报表条件筛选、数据展示）

  * [x] SubTask 7.7: 前端 - 实现客户管理页面（客户列表、添加客户、编辑客户）

* [x] Task 8: 实现数据统计分析模块

  * [x] SubTask 8.1: 后端 - 实现生产统计数据接口（计划完成率、各产品生产进度）

  * [x] SubTask 8.2: 后端 - 实现销售统计数据接口（销售额趋势、产品销量排行）

  * [x] SubTask 8.3: 后端 - 实现库存统计数据接口（库存分布、预警统计）

  * [x] SubTask 8.4: 前端 - 实现数据统计仪表盘页面，集成ECharts

  * [x] SubTask 8.5: 前端 - 实现生产数据可视化图表（柱状图、饼图等）

  * [x] SubTask 8.6: 前端 - 实现销售数据可视化图表（折线图、柱状图等）

  * [x] SubTask 8.7: 前端 - 实现库存数据可视化图表（饼图、仪表盘等）

* [x] Task 9: 系统集成测试与优化

  * [x] SubTask 9.1: 后端接口测试（单元测试、集成测试）

  * [x] SubTask 9.2: 前端功能测试（各模块页面功能验证）

  * [x] SubTask 9.3: 前后端联调测试（接口对接、数据流转验证）

  * [x] SubTask 9.4: 性能优化（接口响应时间、前端加载速度）

  * [x] SubTask 9.5: 安全检查（权限控制验证、SQL注入防护、XSS防护）

# Task Dependencies

* \[Task 2] depends on \[Task 1]

* \[Task 3] depends on \[Task 1, Task 2]

* \[Task 4] depends on \[Task 3]

* \[Task 5] depends on \[Task 3]

* \[Task 6] depends on \[Task 3]

* \[Task 7] depends on \[Task 3]

* \[Task 8] depends on \[Task 4, Task 5, Task 6, Task 7]

* \[Task 9] depends on \[Task 8]

