# 服装生产销售管理系统

一个面向服装企业的生产、库存、订单、销售与权限管理系统。项目采用前后端分离架构：后端提供 Spring Boot REST API，前端使用 Vue 3 构建管理后台，数据存储在 MongoDB 中。

## 功能概览

- 用户与权限：登录、注册、用户管理、角色分配、基于 JWT 的接口鉴权和基于角色的页面访问控制。
- 生产管理：生产计划创建、审批、启动、完成、生产任务分配与进度维护。
- 库存管理：原材料库存、成品库存、库存出入库、库位调整、库存阈值和预警处理。
- 订单管理：订单创建、审核、驳回、取消、发货、完成和状态日志跟踪。
- 销售管理：销售记录、客户资料、销售报表、产品排行和类别分布统计。
- 数据统计：生产、库存、销售等核心指标的可视化统计分析。
- 产品定义：统一维护产品名称、分类、颜色、尺码、成本价等基础资料。

## 技术栈

### 后端

- Java 8
- Spring Boot 2.7.18
- Spring Web
- Spring Security
- Spring Data MongoDB
- JWT
- Maven

### 前端

- Vue 3
- Vite
- TypeScript
- Vue Router
- Pinia
- Element Plus
- Axios
- ECharts

### 数据库

- MongoDB

## 目录结构

```text
.
├── src/                         # Spring Boot 后端源码
│   ├── main/java/com/garment
│   │   ├── config               # 安全配置、JWT 过滤器、初始化数据
│   │   ├── controller           # REST API 控制器
│   │   ├── dto                  # 请求与响应对象
│   │   ├── model                # MongoDB 文档模型
│   │   ├── repository           # MongoDB Repository
│   │   ├── service              # 业务接口与实现
│   │   └── util                 # 工具类与权限常量
│   ├── main/resources
│   │   └── application.yml      # 后端配置
│   └── test                     # 后端单元测试
├── frontend/                    # Vue 前端项目
│   ├── src
│   │   ├── api                  # 前端 API 封装
│   │   ├── layout               # 后台布局
│   │   ├── router               # 路由与访问控制
│   │   ├── store                # Pinia 状态
│   │   ├── utils                # 前端工具函数
│   │   └── views                # 页面模块
│   └── test                     # 前端工具与页面测试文件
├── docs/                        # 项目设计与实施文档
├── bug/                         # 问题记录
└── pom.xml                      # 后端 Maven 配置
```

## 环境要求

- JDK 8
- Maven 3.6 或以上
- Node.js 20.19 或以上，或 Node.js 22.12 或以上
- npm
- MongoDB 4.4 或以上

## 配置说明

后端默认配置位于 `src/main/resources/application.yml`：

```yaml
server:
  port: 8088

spring:
  data:
    mongodb:
      host: localhost
      port: 27017
      database: garment_db
      auto-index-creation: true
```

默认后端端口为 `8088`，默认 MongoDB 数据库为 `garment_db`。前端开发服务器默认运行在 `5173`，并通过 Vite 代理把 `/api` 请求转发到 `http://localhost:8088`。

## 快速启动

### 1. 启动 MongoDB

请先确认本机 MongoDB 已启动，并可通过 `localhost:27017` 访问。

### 2. 启动后端

在项目根目录执行：

```bash
mvn spring-boot:run
```

后端启动后访问地址：

```text
http://localhost:8088
```

### 3. 启动前端

进入前端目录并安装依赖：

```bash
cd frontend
npm install
npm run dev
```

前端开发地址：

```text
http://localhost:5173
```

## 默认账号

系统启动时会自动初始化角色和管理员账号：

| 用户名 | 密码 | 角色 |
| --- | --- | --- |
| `admin` | `admin123` | 系统管理员 |

新注册用户默认属于未激活状态，需要管理员在用户管理中分配角色并启用后才能访问业务页面。

## 角色权限

| 角色编码 | 角色名称 | 主要权限 |
| --- | --- | --- |
| `admin` | 系统管理员 | 拥有全部系统权限 |
| `production_manager` | 生产管理人员 | 生产计划、生产任务、生产统计、库存查看 |
| `warehouse_staff` | 仓库操作人员 | 原材料和成品库存、出入库、库存预警、库存统计 |
| `sales_staff` | 销售人员 | 订单、销售记录、销售报表、客户管理 |
| `inactive` | 未激活用户 | 无业务权限 |

## 常用命令

### 后端

```bash
# 启动后端
mvn spring-boot:run

# 运行后端测试
mvn test

# 打包后端
mvn package
```

### 前端

```bash
cd frontend

# 启动开发服务器
npm run dev

# 构建生产包
npm run build

# 本地预览构建结果
npm run preview
```

## API 模块

后端接口统一使用 `/api` 前缀，主要模块如下：

| 模块 | 路径前缀 | 说明 |
| --- | --- | --- |
| 认证 | `/api/auth` | 注册、登录、退出 |
| 用户 | `/api/users` | 用户信息、角色分配、状态和密码 |
| 角色 | `/api/roles` | 角色查询 |
| 产品定义 | `/api/product-definition` | 产品基础资料维护 |
| 生产计划 | `/api/production/plans` | 计划创建、审批、启动、完成 |
| 生产任务 | `/api/production/tasks` | 任务创建、分配和进度 |
| 库存 | `/api/inventory` | 原材料、成品、出入库、预警 |
| 订单 | `/api/orders` | 订单生命周期管理 |
| 销售 | `/api/sales` | 销售记录、客户、报表 |
| 统计 | `/api/statistics` | 生产、库存、销售统计 |

## 前端页面

- `/login`：登录
- `/register`：注册
- `/dashboard`：首页
- `/production/plan`：生产计划
- `/production/task`：生产任务
- `/production/progress`：生产进度
- `/inventory/raw-material`：原材料库存
- `/inventory/finished-product`：成品库存
- `/inventory/alert`：库存预警
- `/inventory/pending-stock-in`：待入库
- `/order/list`：订单列表
- `/order/create`：创建订单
- `/sales/record`：销售记录
- `/sales/report`：销售报表
- `/sales/customer`：客户管理
- `/statistics`：数据统计
- `/system/user`：用户管理
- `/system/product-definition`：产品定义
- `/profile`：个人信息

## 开发说明

- 后端响应统一使用 `Result<T>` 包装，成功响应默认 `code` 为 `200`。
- 前端请求封装在 `frontend/src/utils/request.js`，会自动携带本地保存的 JWT。
- 前端默认 API 地址为 `/api`，开发环境由 Vite 代理到后端 `8088` 端口。
- 后端接口默认需要认证，只有登录和注册接口允许匿名访问。
- 角色与默认管理员账号由 `DataInitializer` 在应用启动时自动初始化。

## 构建与部署

1. 后端执行 `mvn package` 生成可运行 jar。
2. 前端执行 `npm run build` 生成静态资源到 `frontend/dist`。
3. 部署时请确保后端可访问 MongoDB，并根据实际环境调整 `application.yml` 或外部配置。
4. 前端生产环境可通过 `VITE_API_BASE_URL` 指定后端 API 地址。

## 注意事项

- 请勿在生产环境继续使用默认管理员密码。
- `jwt.secret` 建议在生产环境通过环境变量或外部配置覆盖。
- MongoDB 数据库需要提前启动，否则后端无法完成连接和初始化。
- 前后端端口如被占用，请分别调整 `application.yml` 和 `frontend/vite.config.ts`。
