# T00 基建与目录规整

## 目标目录结构

```
agritime/
├── frontend/          # 前端页面与交互
│   ├── pages/         # 业务页面
│   ├── components/    # 公共组件
│   └── assets/        # 静态资源
├── backend/           # 后端服务
│   ├── api/           # HTTP 接口层
│   ├── service/       # 业务逻辑层
│   ├── model/         # 数据模型
│   └── middleware/    # 限流、安全中间件
├── data/              # 公共数据与规则库
│   ├── crop-base/     # 作物基础库
│   ├── guidance/      # 农事指导规则库
│   ├── region/        # 地理与环境数据
│   └── industries/    # 农林渔牧扩展品类（暂缓）
├── shared/            # 多端共享逻辑
│   ├── utils/         # 通用工具函数
│   └── constants/     # 全局常量与数据协议
└── docs/              # 项目文档
```

## 验收标准

1. 所有代码按上述目录结构存放，无散落文件
2. 原有4个核心接口完全兼容，历史功能不受影响
3. 新增接口统一遵循 REST 规范，错误码与返回格式一致
