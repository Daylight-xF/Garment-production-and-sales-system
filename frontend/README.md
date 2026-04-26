# 服装生产销售管理系统前端

这是服装生产销售管理系统的前端项目，基于 Vue 3、Vite、Pinia、Vue Router、Element Plus、Axios 和 ECharts 构建。

## 开发环境

- Node.js 20.19 或以上，或 Node.js 22.12 或以上
- npm
- 后端服务默认运行在 `http://localhost:8088`

## 启动

```bash
npm install
npm run dev
```

开发服务器默认地址：

```text
http://localhost:5173
```

## 常用命令

```bash
# 启动开发服务器
npm run dev

# 类型检查并构建生产包
npm run build

# 本地预览生产构建结果
npm run preview
```

## API 代理

开发环境下，`vite.config.ts` 会把 `/api` 请求代理到后端：

```text
http://localhost:8088
```

如果需要连接其他后端地址，可以在环境变量中配置 `VITE_API_BASE_URL`。

## 目录说明

```text
src/
├── api/          # 各业务模块 API 封装
├── layout/       # 管理后台主布局
├── router/       # 路由、登录拦截和角色访问控制
├── store/        # Pinia 用户状态
├── utils/        # 请求、表单和业务辅助函数
└── views/        # 页面模块
```

## 页面模块

- 登录与注册
- 首页仪表盘
- 生产计划、生产任务、生产进度
- 原材料库存、成品库存、库存预警、待入库
- 订单创建、订单列表、订单详情
- 销售记录、销售报表、客户管理
- 数据统计
- 用户管理、产品定义、个人信息
