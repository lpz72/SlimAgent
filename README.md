# SlimAgent

基于 Spring Boot、Spring AI Alibaba、DashScope、Milvus、Elasticsearch、MySQL 和 Redis 构建的智能减脂 Agent。系统围绕用户减脂目标提供个性化问答、健康画像管理、减脂知识库检索、长期记忆和热量计算能力，帮助用户获得更连续、更贴合自身情况的减脂建议。

## 项目简介

SlimAgent 是一个面向减脂场景的对话式智能体应用。用户登录后可以维护身高、体重、目标体重、运动水平、饮食偏好和健康备注等结构化信息；对话过程中，系统会结合用户画像、历史会话记忆和减脂知识库，生成更个性化的回答。

系统支持普通问答、RAG 知识库问答和工具增强问答。对于减脂相关问题，Agent 可检索知识库、读取用户画像、参考长期记忆，并调用 BMI、每日热量目标、运动消耗等计算工具。

## 核心能力

- 用户账号：支持注册、登录、退出和当前用户信息查询，登录态通过 Spring Session + Redis 保存。
- 减脂画像：记录性别、年龄、身高、体重、目标体重、活动水平、饮食偏好和健康备注。
- 智能对话：支持普通响应和 SSE 流式响应，适合前端逐字展示。
- 意图识别：根据问题类型选择通用对话、RAG 检索或工具增强对话。
- RAG 检索：基于 Milvus 向量检索、Elasticsearch 关键词检索和 DashScope 重排序模型组合召回减脂知识。
- 长期记忆：保存用户会话，构建历史摘要和记忆上下文，让后续建议更连贯。
- 减脂工具：支持 BMI 计算、每日能量需求与减脂热量估算、运动消耗估算。
- 文档上传：管理员可上传 `txt`、`md` 文档并自动建立向量索引。
- 前端界面：提供 Vue 前端和 Spring Boot 静态页面两种交互入口。

## 技术栈

| 模块 | 技术 |
| --- | --- |
| 后端框架 | Java 17、Spring Boot 3.2.0 |
| AI 能力 | Spring AI、Spring AI Alibaba、DashScope |
| Agent 框架 | Spring AI Alibaba Agent Framework、ReAct Agent |
| 向量检索 | Milvus 2.6.10、DashScope Embedding |
| 关键词检索 | Elasticsearch Java Client |
| 重排序 | DashScope `gte-rerank-v2` |
| 数据存储 | MySQL、MyBatis Plus |
| 会话状态 | Redis、Spring Session |
| 前端 | Vue 3、Vite、TypeScript |
| 接口文档 | Knife4j、Springdoc OpenAPI |

## 目录结构

```text
SlimAgent/
├── frontend/                         # Vue 前端工程
├── sql/                              # 数据库建表脚本
├── src/main/java/org/example/
│   ├── agent/
│   │   ├── intent/                   # 意图识别
│   │   └── tool/                     # Agent 工具
│   │       └── FatLossCalculatorTools.java
│   ├── controller/                   # REST API 控制器
│   │   ├── AuthController.java
│   │   ├── ChatController.java
│   │   ├── FileUploadController.java
│   │   └── UserProfileController.java
│   ├── mapper/                       # MyBatis Plus Mapper
│   ├── model/                        # DTO、实体和检索模型
│   └── service/                      # 业务服务、RAG、记忆和向量索引
├── src/main/resources/
│   ├── static/                       # Spring Boot 静态页面
│   └── application.yml               # 应用配置
├── uploads/                          # 上传文档目录
├── vector-database.yml               # Milvus 向量数据库编排
├── pom.xml
└── README.md
```

## 核心接口

### 用户认证

```http
POST /api/auth/register
POST /api/auth/login
POST /api/auth/logout
GET  /api/auth/me
```

注册或登录请求示例：

```json
{
  "username": "demo",
  "password": "123456",
  "nickname": "减脂用户"
}
```

### 用户减脂画像

```http
GET  /api/profile
POST /api/profile
```

保存画像请求示例：

```json
{
  "gender": "MALE",
  "age": 28,
  "heightCm": 175,
  "weightKg": 78,
  "targetWeightKg": 70,
  "activityLevel": "MODERATE",
  "dietPreference": "高蛋白，少油",
  "healthNotes": "膝盖不适，避免高冲击运动"
}
```

### 智能对话

普通对话：

```http
POST /api/chat
Content-Type: application/json
```

```json
{
  "Id": "session-001",
  "Question": "我现在 78kg，想减到 70kg，每天应该吃多少热量？"
}
```

流式对话：

```http
POST /api/chat_stream
Content-Type: application/json
Accept: text/event-stream
```

### 会话管理

```http
GET  /api/chat/sessions
GET  /api/chat/session/{sessionId}
POST /api/chat/clear
```

清空会话请求示例：

```json
{
  "Id": "session-001"
}
```

### 知识库文档上传

```http
POST /api/upload
Content-Type: multipart/form-data
```

仅管理员可上传文档。默认支持 `txt`、`md` 文件，上传成功后会自动创建向量索引。

### Milvus 健康检查

```http
GET /milvus/health
```

## 快速开始

### 1. 准备环境

请先准备以下服务：

- JDK 17
- Maven 3.8+
- MySQL
- Redis
- Milvus
- Elasticsearch
- DashScope API Key

启动本地 Milvus：

```bash
docker compose -f vector-database.yml up -d
```

### 2. 修改配置

在 `src/main/resources/application.yml` 中配置数据库、Redis、Milvus、Elasticsearch 和 DashScope。

建议将敏感配置改为环境变量或本地私有配置文件，例如：

```yaml
spring:
  ai:
    dashscope:
      api-key: ${DASHSCOPE_API_KEY}
```

### 3. 初始化数据库

执行 `sql/create_table.sql` 创建用户、画像、会话等业务表。

### 4. 启动后端

```bash
mvn clean spring-boot:run
```

后端默认运行在：

```text
http://localhost:9900
```

接口文档：

```text
http://localhost:9900/swagger-ui.html
```

### 5. 启动前端

```bash
cd frontend
npm install
npm run dev
```

前端默认运行在 Vite 输出的本地地址，通常为：

```text
http://localhost:5173
```

## 使用示例

登录：

```bash
curl -X POST http://localhost:9900/api/auth/login \
  -H "Content-Type: application/json" \
  -d "{\"username\":\"demo\",\"password\":\"123456\"}" \
  -c cookie.txt
```

保存减脂画像：

```bash
curl -X POST http://localhost:9900/api/profile \
  -H "Content-Type: application/json" \
  -b cookie.txt \
  -d "{\"gender\":\"MALE\",\"age\":28,\"heightCm\":175,\"weightKg\":78,\"targetWeightKg\":70,\"activityLevel\":\"MODERATE\",\"dietPreference\":\"高蛋白\",\"healthNotes\":\"无\"}"
```

发起对话：

```bash
curl -X POST http://localhost:9900/api/chat \
  -H "Content-Type: application/json" \
  -b cookie.txt \
  -d "{\"Id\":\"session-001\",\"Question\":\"请根据我的画像制定一周减脂建议\"}"
```

上传知识库文档：

```bash
curl -X POST http://localhost:9900/api/upload \
  -b cookie.txt \
  -F "file=@fat-loss-guide.md"
```

## 配置说明

| 配置项 | 说明 |
| --- | --- |
| `server.port` | 后端服务端口，默认 `9900` |
| `spring.datasource` | MySQL 连接配置 |
| `spring.data.redis` | Redis 与 Session 配置 |
| `spring.ai.dashscope` | DashScope 对话、向量化和重试配置 |
| `milvus` | Milvus 地址、认证和超时配置 |
| `rag.top-k` | 最终进入生成阶段的知识片段数量 |
| `rag.hybrid` | 向量检索与关键词检索融合配置 |
| `rag.es` | Elasticsearch 检索配置 |
| `rag.rerank` | DashScope 重排序配置 |
| `document.chunk` | 文档分片大小与重叠长度 |
| `file.upload` | 上传目录和允许的文件扩展名 |

## 版本信息

- 当前版本：`1.0-SNAPSHOT`
- 许可证：MIT
