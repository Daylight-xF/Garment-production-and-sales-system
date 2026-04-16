# 个人信息功能设计文档

**日期：** 2026-04-16
**状态：** 已批准
**方案选择：** 方案1 - 纯前端实现

---

## 1. 功能概述

为服装生产销售管理系统开发个人信息功能，允许当前登录用户查看和编辑个人基本信息、修改密码、查看角色权限信息。

### 1.1 核心功能

- **基本信息管理：** 查看和编辑真实姓名、手机号、邮箱
- **密码修改：** 安全地修改登录密码（需验证旧密码）
- **角色权限展示：** 只读查看当前用户的角色和权限列表

### 1.2 用户故事

**作为** 系统用户，
**我希望** 能够在系统中查看和修改我的个人信息，
**以便于** 保持信息的准确性并管理账户安全。

---

## 2. 技术架构

### 2.1 技术栈

- **前端框架：** Vue 3 + Composition API
- **UI组件库：** Element Plus
- **状态管理：** Pinia（复用现有 user store）
- **路由：** Vue Router 4
- **HTTP客户端：** Axios（封装在 utils/request.js）
- **后端API：** 复用现有 Spring Boot 接口

### 2.2 文件结构

```
frontend/src/
├── views/
│   └── system/
│       └── Profile.vue              # 新增：个人信息页面组件
├── router/
│   └── index.js                     # 修改：添加 /profile 路由
├── layout/
│   └── MainLayout.vue               # 修改：点击"个人信息"跳转到 /profile
├── api/
│   └── user.js                      # 已存在：复用 getCurrentUser() 和 updateUser()
└── store/
    └── user.js                      # 已存在：复用 getUserInfoAction()
```

### 2.3 数据流

```
用户点击"个人信息"
    ↓
router.push('/profile')
    ↓
Profile.vue 组件加载（onMounted）
    ↓
调用 userStore.getUserInfoAction()
    ↓
GET /api/users/info → 返回 UserVO
    ↓
填充表单数据 → 用户查看/编辑
    ↓
用户提交修改
    ↓
表单验证（前端规则）
    ↓
PUT /api/users/{id} + {realName, phone, email} 或 {password}
    ↓
成功 → ElMessage.success() + 刷新store
失败 → ElMessage.error() + 显示错误详情
```

---

## 3. 页面设计

### 3.1 路由配置

```javascript
// 在 MainLayout 的 children 数组中添加
{
  path: 'profile',
  name: 'Profile',
  component: () => import('../views/system/Profile.vue'),
  meta: {
    title: '个人信息',
    requiresAuth: true
  }
}
```

**访问权限：** 所有已登录用户均可访问（无需特定角色）

### 3.2 整体布局

采用 **Element Plus Tabs 组件** 实现三标签页切换：

```
┌─────────────────────────────────────────────────────┐
│  个人信息                                            │
│  ┌───────────────────────────────────────────────┐  │
│  │ [基本信息] [修改密码] [角色权限]                │  │
│  ├───────────────────────────────────────────────┤  │
│  │                                               │  │
│  │  （各Tab内容区域）                              │  │
│  │                                               │  │
│  └───────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────┘
```

### 3.3 视觉风格规范

遵循项目现有设计语言：

| 元素 | 样式值 | 用途 |
|------|--------|------|
| 主色调 | `#409EFF` | 按钮、链接、激活状态 |
| 页面背景 | `#f0f2f5` | 主内容区背景 |
| 卡片背景 | `#ffffff` | 表单容器背景 |
| 文字主色 | `#333333` | 标题、重要文字 |
| 文字次色 | `#666666` | 一般说明文字 |
| 边框颜色 | `#DCDFE6` | 表单边框、分割线 |
| 圆角 | `8px` | 卡片、按钮圆角 |
| 阴影 | `0 2px 12px rgba(0,0,0,0.1)` | 卡片阴影 |

### 3.4 Tab1：基本信息

**布局：** 左右分栏（响应式）

```
┌──────────────────────────────────────────────────┐
│  ┌──────────┐  ┌────────────────────────────┐    │
│  │          │  │  用户名：system_admin       │    │
│  │  用户头像 │  │  真实姓名：[输入框________] │    │
│  │ 120x120  │  │  手机号：[输入框__________] │    │
│  │          │  │  邮箱：[输入框____________] │    │
│  │          │  │                            │    │
│  │          │  │  创建时间：2024-01-01 10:30 │    │
│  │          │  │  更新时间：2024-01-15 14:20 │    │
│  └──────────┘  │                            │    │
│                │     [保存]     [重置]        │    │
│                └────────────────────────────┘    │
└──────────────────────────────────────────────────┘
```

**字段说明：**

| 字段 | 类型 | 是否可编辑 | 验证规则 |
|------|------|-----------|---------|
| 用户名 | Input | ❌ 只读 | - |
| 真实姓名 | Input | ✅ 可编辑 | 必填，2-20字符 |
| 手机号 | Input | ✅ 可编辑 | 可选，11位手机号格式 |
| 邮箱 | Input | ✅ 可编辑 | 可选，邮箱格式 |
| 创建时间 | Display | ❌ 只读 | - |
| 更新时间 | Display | ❌ 只读 | - |

**头像区域：**
- 使用 Element Plus `<el-avatar>` 组件
- 尺寸：120x120px
- 默认显示用户名首字母或图标
- 后续可扩展上传功能

### 3.5 Tab2：修改密码

**布局：** 垂直表单

```
┌──────────────────────────────────────────────────┐
│                                                  │
│  ⚠️  密码修改成功后需要重新登录                    │
│                                                  │
│  旧密码：[••••••••________]  👁️                  │
│                                                  │
│  新密码：[••••••••________]  👁️                  │
│          [██████░░░░] 强度：中                   │
│          (6-20位，需包含字母和数字)                 │
│                                                  │
│  确认密码：[••••••••_______]  👁️                 │
│                                                  │
│           [修改密码]    [重置]                     │
│                                                  │
└──────────────────────────────────────────────────┘
```

**字段说明：**

| 字段 | 类型 | 验证规则 |
|------|------|---------|
| 旧密码 | Password | 必填 |
| 新密码 | Password | 必填，6-20位，包含字母+数字 |
| 确认密码 | Password | 必填，与新密码一致 |

**特殊功能：**
- 密码可见性切换图标（👁️）
- 新密码强度指示器（弱/中/强）
- 安全提示文字（顶部警告样式）
- 提交前二次确认弹窗

### 3.6 Tab3：角色权限（只读）

**布局：** 信息卡片 + 表格

```
┌──────────────────────────────────────────────────┐
│                                                  │
│  📋 当前角色                                     │
│  ┌─────────┐ ┌─────────┐ ┌─────────┐           │
│  │ 管理员  │ │ 仓库员  │         │           │
│  └─────────┘ └─────────┘ └─────────┘           │
│                                                  │
│  🔐 权限列表                                     │
│  ┌──────────────────────────────────────────┐   │
│  │ 模块      │ 权限名称     │ 权限编码       │   │
│  ├───────────┼─────────────┼─────────────────┤   │
│  │ 用户管理  │ 查看用户     │ USER_READ       │   │
│  │ 用户管理  │ 创建用户     │ USER_CREATE     │   │
│  │ 订单管理  │ 查看订单     │ ORDER_READ      │   │
│  │ ...       │ ...         │ ...             │   │
│  └──────────────────────────────────────────┘   │
│                                                  │
│  💡 如需调整角色权限，请联系系统管理员            │
│                                                  │
└──────────────────────────────────────────────────┘
```

**展示方式：**
- 角色：使用 `<el-tag>` 组件，不同角色不同颜色
- 权限：使用 `<el-table>` 表格展示，支持分页
- 说明：底部提示文字（info样式）

---

## 4. 数据验证

### 4.1 基本信息验证规则

```javascript
const basicInfoRules = {
  realName: [
    { required: true, message: '请输入真实姓名', trigger: 'blur' },
    { min: 2, max: 20, message: '长度在2到20个字符', trigger: 'blur' }
  ],
  phone: [
    {
      pattern: /^1[3-9]\d{9}$/,
      message: '请输入正确的手机号码',
      trigger: 'blur'
    }
  ],
  email: [
    { type: 'email', message: '请输入正确的邮箱地址', trigger: 'blur' }
  ]
}
```

### 4.2 密码验证规则

```javascript
const passwordRules = {
  oldPassword: [
    { required: true, message: '请输入旧密码', trigger: 'blur' }
  ],
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { min: 6, max: 20, message: '长度在6到20个字符', trigger: 'blur' },
    {
      validator: (rule, value, callback) => {
        const hasLetter = /[a-zA-Z]/.test(value)
        const hasNumber = /\d/.test(value)
        if (!hasLetter || !hasNumber) {
          callback(new Error('密码必须包含字母和数字'))
        } else {
          callback()
        }
      },
      trigger: 'blur'
    }
  ],
  confirmPassword: [
    { required: true, message: '请确认新密码', trigger: 'blur' },
    {
      validator: (rule, value, callback) => {
        if (value !== passwordForm.newPassword) {
          callback(new Error('两次输入的密码不一致'))
        } else {
          callback()
        }
      },
      trigger: 'blur'
    }
  ]
}
```

### 4.3 密码强度计算

```javascript
function getPasswordStrength(password) {
  let strength = 0
  if (password.length >= 8) strength++
  if (/[a-z]/.test(password)) strength++
  if (/[A-Z]/.test(password)) strength++
  if (/\d/.test(password)) strength++
  if (/[^a-zA-Z0-9]/.test(password)) strength++

  if (strength <= 2) return 'weak'      // 弱
  if (strength <= 3) return 'medium'    // 中
  return 'strong'                        // 强
}
```

---

## 5. 交互逻辑

### 5.1 页面加载流程

```javascript
onMounted(async () => {
  loading.value = true
  try {
    await userStore.getUserInfoAction()
    // 初始化表单数据
    initFormData()
  } catch (error) {
    ElMessage.error('获取用户信息失败')
    console.error(error)
  } finally {
    loading.value = false
  }
})
```

### 5.2 保存基本信息流程

```javascript
async function handleSaveBasicInfo() {
  // 1. 表单验证
  const valid = await basicFormRef.value.validate().catch(() => false)
  if (!valid) return

  // 2. 提交数据
  try {
    const userId = userStore.userInfo.id
    await updateUser(userId, {
      realName: basicForm.realName,
      phone: basicForm.phone,
      email: basicForm.email
    })

    // 3. 成功处理
    ElMessage.success('个人信息保存成功')
    await userStore.getUserInfoAction() // 刷新store
  } catch (error) {
    ElMessage.error(error.response?.data?.message || '保存失败')
  }
}
```

### 5.3 修改密码流程

```javascript
async function handleChangePassword() {
  // 1. 表单验证
  const valid = await passwordFormRef.value.validate().catch(() => false)
  if (!valid) return

  // 2. 二次确认
  try {
    await ElMessageBox.confirm(
      '确定要修改密码吗？修改后需要重新登录',
      '提示',
      { confirmButtonText: '确定', cancelButtonText: '取消', type: 'warning' }
    )
  } catch {
    return // 用户取消
  }

  // 3. 提交修改
  try {
    const userId = userStore.userInfo.id
    await updateUser(userId, {
      password: passwordForm.newPassword
    })

    // 4. 成功处理
    ElMessage.success('密码修改成功，即将跳转到登录页')

    // 5. 延迟退出登录
    setTimeout(() => {
      userStore.logout()
      router.push('/login')
    }, 2000)
  } catch (error) {
    ElMessage.error(error.response?.data?.message || '密码修改失败')
  }
}
```

### 5.4 重置表单逻辑

```javascript
function handleReset(formRef, originalData) {
  ElMessageBox.confirm('确定要重置吗？未保存的修改将丢失', '提示')
    .then(() => {
      Object.assign(formRef, JSON.parse(JSON.stringify(originalData)))
      ElMessage.info('表单已重置')
    })
    .catch(() => {})
}
```

---

## 6. 错误处理

### 6.1 HTTP错误码处理

| 错误码 | 处理策略 | 用户提示 |
|-------|---------|---------|
| 400 | 参数验证失败 | 显示具体字段错误 |
| 401 | 未授权/Token过期 | 清除token，跳转登录页 |
| 403 | 无权限 | "您没有权限执行此操作" |
| 404 | 用户不存在 | "用户信息不存在" |
| 422 | 业务逻辑错误 | 显示后端返回消息 |
| 500 | 服务器内部错误 | "服务器错误，请联系管理员" |
| 网络 | 断网/超时 | "网络连接失败，请检查网络" |

### 6.2 异常捕获机制

```javascript
try {
  // API调用
} catch (error) {
  if (error.response) {
    // 服务器返回了错误状态码
    const status = error.response.status
    const message = error.response.data?.message

    switch (status) {
      case 401:
        handleUnauthorized()
        break
      case 403:
        ElMessage.warning(message || '无权限')
        break
      default:
        ElMessage.error(message || '操作失败')
    }
  } else if (error.request) {
    // 请求已发出但没有响应
    ElMessage.error('网络连接失败，请检查网络')
  } else {
    // 其他错误
    ElMessage.error('发生未知错误')
  }
}
```

---

## 7. 安全性考虑

### 7.1 前端安全保障

1. **自我限制：** 只能修改当前登录用户的信息
   ```javascript
   const userId = userStore.userInfo.id // 始终使用当前用户ID
   ```

2. **敏感数据脱敏：**
   - 手机号显示：138****1234
   - 邮箱显示：user***@email.com

3. **操作确认：** 敏感操作（修改密码）需要二次确认

4. **防重复提交：** 按钮loading状态防止重复点击

### 7.2 密码安全

1. **传输加密：** 使用HTTPS协议（生产环境）
2. **前端不存储明文密码：** 仅在内存中持有
3. **密码强度要求：** 强制复杂度规则
4. **修改后强制重登：** 清除所有会话信息

---

## 8. 用户体验优化

### 8.1 加载状态

- 页面初次加载：显示骨架屏（Skeleton）或Loading动画
- API请求期间：禁用操作按钮并显示loading图标

### 8.2 反馈机制

- **成功：** 绿色成功提示（ElMessage.success）
- **失败：** 红色错误提示（ElMessage.error）
- **警告：** 橙色警告提示（ElMessage.warning）
- **信息：** 蓝色信息提示（ElMessage.info）

### 8.3 操作引导

- 必填项标记红色星号（*）
- 输入框placeholder提示示例格式
- 密码框下方显示格式要求
- Tab3底部显示联系管理员提示

### 8.4 响应式适配

- **≥992px：** 左右分栏布局（头像左，表单右）
- **<992px：** 上下堆叠布局（头像上，表单下）
- 移动端优化：表单宽度100%，适当增大触摸区域

---

## 9. 测试要点

### 9.1 功能测试

- [ ] 页面正常加载，数据显示正确
- [ ] 基本信息编辑后保存成功
- [ ] 表单验证规则生效（必填、格式、长度）
- [ ] 密码修改完整流程（含二次确认）
- [ ] 密码强度指示器正确显示
- [ ] 角色权限信息只读展示
- [ ] 重置按钮恢复原始数据

### 9.2 异常测试

- [ ] 未登录状态访问被拦截
- [ ] 网络断开时显示友好提示
- [ ] 服务器500错误时提示清晰
- [ ] Token过期时自动跳转登录

### 9.3 兼容性测试

- [ ] Chrome/Firefox/Safari/Edge最新版
- [ ] 1920x1080 / 1366x768 分辨率
- [ ] 移动端浏览器基本可用

---

## 10. 实现计划

### 10.1 开发步骤

1. **创建 Profile.vue 组件**
   - 基础页面结构
   - Tabs标签页框架
   - 响应式数据定义

2. **实现基本信息Tab**
   - 表单布局（左右分栏）
   - 数据绑定与回显
   - 表单验证规则
   - 保存/重置功能

3. **实现修改密码Tab**
   - 密码表单
   - 强度计算器
   - 修改流程（含确认弹窗）
   - 自动退出登录

4. **实现角色权限Tab**
   - 角色Tag展示
   - 权限表格渲染
   - 提示信息

5. **集成到路由系统**
   - 添加路由配置
   - 修改MainLayout跳转逻辑

6. **完善细节**
   - 错误处理
   - 加载状态
   - 响应式适配
   - UI微调

### 10.2 预估工作量

- **核心功能开发：** 2-3小时
- **UI美化与细节优化：** 1小时
- **测试与调试：** 1小时
- **总计：** 4-5小时

---

## 11. 后续扩展方向（可选）

本设计采用方案1（纯前端实现），后续可根据需求升级：

1. **头像上传功能**
   - 扩展User模型添加avatarUrl字段
   - 实现文件上传接口
   - 图片裁剪与压缩

2. **专用API端点**
   - `PUT /api/users/profile` - 仅允许修改自己
   - `PUT /api/users/password` - 专用改密接口（需验证旧密码）

3. **操作日志**
   - 记录每次个人信息修改
   - 包含修改人、修改时间、修改字段、新旧值

4. **多因素认证**
   - 修改密码时需短信/邮箱验证码
   - 敏感操作二次验证

---

## 附录：相关代码参考

### A. 现有API接口

```javascript
// src/api/user.js
export function getCurrentUser() {
  return request({ url: '/users/info', method: 'get' })
}

export function updateUser(id, data) {
  return request({ url: `/users/${id}`, method: 'put', data })
}
```

### B. 现有Store方法

```javascript
// src/store/user.js
async function getUserInfoAction() {
  const res = await getCurrentUser()
  const data = res.data || res
  setUserInfo(data)
  return data
}

function logout() {
  token.value = ''
  userInfo.value = {}
  localStorage.removeItem('token')
  localStorage.removeItem('userInfo')
}
```

### C. MainLayout现有代码位置

```vue
<!-- src/layout/MainLayout.vue 第207-209行 -->
else if (command === 'profile') {
  ElMessage.info('个人信息功能开发中')  // ← 需改为路由跳转
}
```

---

**文档版本：** v1.0
**最后更新：** 2026-04-16
**作者：** AI Assistant
**审批状态：** ✅ 已通过
