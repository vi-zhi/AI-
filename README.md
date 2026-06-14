# AI 语音画图工具

一个基于 Spring AI 的智能语音/文本画图工具，支持语音输入识别和 AI 生成流程图。

## 项目特性

- 🎤 **语音输入**：支持语音识别，自动转换为文本
- 💬 **文本输入**：支持直接文本描述需求
- 🤖 **AI 画图**：基于 Spring AI 智能生成流程图
- 🎨 **可视化展示**：实时展示 AI 生成的图表

## 技术栈

### 后端
- **Java 17**
- **Spring Boot 3.2.5**
- **Spring AI 1.1.0-M3**
- **MyBatis**
- **百度语音识别 API**

### 前端
- **Vue 3**
- **TypeScript**
- **Vite**
- **Pinia**
- **Axios**

## 项目结构

```
Ai-voice-draw-tool/
├── Ai-voice-draw-tool-api/          # API 模块
├── Ai-voice-draw-tool-app/          # 应用启动模块
├── Ai-voice-draw-tool-domain/       # 领域模块
├── Ai-voice-draw-tool-infrastructure/ # 基础设施模块
├── Ai-voice-draw-tool-trigger/      # 触发器模块（HTTP 接口）
├── Ai-voice-draw-tool-types/        # 公共类型模块
├── front/                           # 前端项目
├── docs/                            # 文档
└── data/                            # 数据目录
```

## 快速开始

### 前置条件

- JDK 17+
- Node.js 16+
- Maven 3.6+
- 百度智能云账号（语音识别 API）

### 后端启动

1. 配置百度 API 密钥
   编辑 `Ai-voice-draw-tool-app/src/main/resources/application.yml`，配置：
   ```yaml
   baidu:
     app-id: your-app-id
     api-key: your-api-key
     secret-key: your-secret-key
   ```

2. 启动后端
   ```bash
   cd Ai-voice-draw-tool-app
   mvn spring-boot:run
   ```

后端服务将在 `http://localhost:8091` 启动

### 前端启动

```bash
cd front
npm install
npm run dev
```

前端服务将在 `http://localhost:5173` 启动

## API 接口

### 语音画图
```
POST /api/v1/draw/voice
Content-Type: multipart/form-data

参数：
- file: 语音文件
- agentId: AI Agent ID（可选）
- userId: 用户 ID（可选）
```

### 文本画图
```
POST /api/v1/draw/text
Content-Type: application/json

参数：
- text: 描述文本
- agentId: AI Agent ID（可选）
- userId: 用户 ID（可选）
```

## 开发者

- **vizhi** - 2720561950@qq.com

## 许可证

Apache License 2.0
