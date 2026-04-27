package org.example.controller;

import com.alibaba.cloud.ai.dashscope.api.DashScopeApi;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import com.alibaba.cloud.ai.graph.NodeOutput;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.streaming.OutputType;
import com.alibaba.cloud.ai.graph.streaming.StreamingOutput;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpSession;
import org.example.agent.Intent;
import org.example.model.SessionInfo;
import org.example.agent.intent.IntentRecognizer;
import org.example.model.dto.*;
import org.example.service.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 统一 API 控制器
 * 适配前端接口需求
 */
@RestController
@RequestMapping("/api")
public class ChatController {

    private static final Logger logger = LoggerFactory.getLogger(ChatController.class);

    @Autowired
    private AiOpsService aiOpsService;
    
    @Autowired
    private ChatService chatService;

    @Autowired
    private AuthService authService;

    @Autowired
    private UserProfileService userProfileService;

    @Autowired
    private ConversationPersistenceService conversationPersistenceService;

    @Autowired
    private ToolCallback[] toolCallback;

    @Resource
    private IntentRecognizer intentRecognizer;

    @Resource
    @Lazy
    private IntentService intentService;

    @Resource
    private ReWriteQueryService reWriteQueryService;

    private final ExecutorService executor = Executors.newCachedThreadPool();

    // 存储会话信息
    private final Map<String, SessionInfo> sessions = new ConcurrentHashMap<>();

    @Resource
    private SessionInfoService sessionInfoService;

    /**
     * 普通对话接口（支持工具调用）
     * 与 /chat_react 逻辑一致，但直接返回完整结果而非流式输出
     */
    @PostMapping("/chat")
    public ResponseEntity<ApiResponse<ChatResponse>> chat(@RequestBody ChatRequest request, HttpSession httpSession) {
        try {
            Long userId = authService.requireUserId(httpSession);
            logger.info("收到对话请求 - UserId: {}, SessionId: {}, Question: {}", userId, request.getId(), request.getQuestion());

            // 参数校验
            if (request.getQuestion() == null || request.getQuestion().trim().isEmpty()) {
                logger.warn("问题内容为空");
                return ResponseEntity.ok(ApiResponse.success(ChatResponse.error("问题内容不能为空")));
            }


            // 获取或创建会话
            SessionInfo session = getOrCreateSession(request.getId());
            
            // 获取历史消息
            List<Map<String, String>> history = session.getHistory();
            logger.info("会话历史消息对数: {}", history.size() / 2);

            // 创建 DashScope API 和 ChatModel
            DashScopeApi dashScopeApi = chatService.createDashScopeApi();
            DashScopeChatModel chatModel = chatService.createStandardChatModel(dashScopeApi);

            String query = request.getQuestion();
            String reWriteQuery = "";

            // 进行意图识别
            Intent intent = intentRecognizer.recognize(query);
            logger.info("意图识别结果: {}", intent.name());
            String fullAnswer = "";
            if (intent == Intent.GENERAL) {
                fullAnswer = intentService.GeneralIntent(history, query);
            } else if (intent == Intent.RAG) {
                // 查询改写
                reWriteQuery = reWriteQueryService.reWrite(query);
                logger.info("改写后的问题: {}", reWriteQuery);
                fullAnswer = intentService.RagIntent(reWriteQuery);
            } else {
                reWriteQuery = reWriteQueryService.reWrite(query);
                // 记录可用工具
                chatService.logAvailableTools();

                logger.info("开始 ReactAgent 流式对话（支持自动工具调用）");

                // 构建系统提示词（包含当前轮的历史消息、用户资料和长期记忆）
                String structuredUserContext = userProfileService.buildStructuredContext(userId);
                String longTermMemoryContext = conversationPersistenceService.buildLongTermMemoryContext(userId, query);
                String systemPrompt = chatService.buildSystemPrompt(history, structuredUserContext, longTermMemoryContext);

                // 创建 ReactAgent
                ReactAgent agent = chatService.createReactAgent(chatModel, systemPrompt);
                // 执行对话
                fullAnswer = chatService.executeChat(agent, reWriteQuery);
            }


            // 更新会话历史
            sessionInfoService.addMessage(session,query, fullAnswer, httpSession);
            conversationPersistenceService.saveRound(userId, request.getId(), query, fullAnswer);
            logger.info("已更新会话历史 - SessionId: {}, 当前消息对数: {}", 
                request.getId(), session.getMessagePairCount());
            
            return ResponseEntity.ok(ApiResponse.success(ChatResponse.success(fullAnswer)));

        } catch (Exception e) {
            logger.error("对话失败", e);
            return ResponseEntity.ok(ApiResponse.success(ChatResponse.error(e.getMessage())));
        }
    }

    /**
     * 删除会话历史
     */
    @PostMapping("/chat/clear")
    public ResponseEntity<ApiResponse<String>> clearChatHistory(@RequestBody ClearRequest request, HttpSession httpSession) {
        try {
            Long userId = authService.requireUserId(httpSession);
            logger.info("收到删除会话历史请求 - UserId: {}, SessionId: {}", userId, request.getId());

            if (request.getId() == null || request.getId().isEmpty()) {
                return ResponseEntity.ok(ApiResponse.error("会话ID不能为空"));
            }

            boolean deleted = conversationPersistenceService.deleteSession(userId, request.getId());
            sessions.remove(request.getId());
            if (!deleted) {
                return ResponseEntity.ok(ApiResponse.error("会话不存在"));
            }

            return ResponseEntity.ok(ApiResponse.success("会话历史已删除"));

        } catch (Exception e) {
            logger.error("删除会话历史失败", e);
            return ResponseEntity.ok(ApiResponse.error(e.getMessage()));
        }
    }

    /**
     * ReactAgent 对话接口（SSE 流式模式，支持多轮对话，支持自动工具调用，例如获取当前时间，查询日志，告警等）
     * 支持 session 管理，保留对话历史
     */
    @PostMapping(value = "/chat_stream", produces = "text/event-stream;charset=UTF-8")
    public SseEmitter chatStream(@RequestBody ChatRequest request, HttpSession httpSession) {
        SseEmitter emitter = new SseEmitter(300000L); // 5分钟超时

        Long userId;
        try {
            userId = authService.requireUserId(httpSession);
        } catch (Exception e) {
            try {
                emitter.send(SseEmitter.event().name("message").data(SseMessage.error(e.getMessage()), MediaType.APPLICATION_JSON));
                emitter.complete();
            } catch (IOException ioException) {
                emitter.completeWithError(ioException);
            }
            return emitter;
        }

        // 参数校验
        if (request.getQuestion() == null || request.getQuestion().trim().isEmpty()) {
            logger.warn("问题内容为空");
            try {
                emitter.send(SseEmitter.event().name("message").data(SseMessage.error("问题内容不能为空"), MediaType.APPLICATION_JSON));
                emitter.complete();
            } catch (IOException e) {
                emitter.completeWithError(e);
            }
            return emitter;
        }

        executor.execute(() -> {
            try {
                logger.info("收到 ReactAgent 对话请求 - UserId: {}, SessionId: {}, Question: {}", userId, request.getId(), request.getQuestion());

                // 获取或创建会话
                SessionInfo session = getOrCreateSession(request.getId());


                // 获取历史消息
                List<Map<String, String>> history = session.getHistory();
                logger.info("ReactAgent 会话历史消息对数: {}", history.size() / 2);


                // 创建 DashScope API 和 ChatModel
                DashScopeApi dashScopeApi = chatService.createDashScopeApi();
                DashScopeChatModel chatModel = chatService.createStandardChatModel(dashScopeApi);

                String query = request.getQuestion();
                String reWriteQuery = "";

                // 进行意图识别
                Intent intent = intentRecognizer.recognize(query);
                logger.info("意图识别结果: {}", intent.name());

                Flux<Object> stream = null;
                if (intent == Intent.GENERAL) {
                    stream = intentService.GeneralIntentStream(history, query);
                } else if (intent == Intent.RAG) {
                    // 查询改写
                    reWriteQuery = reWriteQueryService.reWrite(query);
                    stream = intentService.RagIntentStream(reWriteQuery);
                } else {
                    reWriteQuery = reWriteQueryService.reWrite(query);
                    // 记录可用工具
                    chatService.logAvailableTools();

                    logger.info("开始 ReactAgent 流式对话（支持自动工具调用）");

                    // 构建系统提示词（包含当前轮的历史消息、用户资料和长期记忆）
                    String structuredUserContext = userProfileService.buildStructuredContext(userId);
                    String longTermMemoryContext = conversationPersistenceService.buildLongTermMemoryContext(userId, query);
                    String systemPrompt = chatService.buildSystemPrompt(history, structuredUserContext, longTermMemoryContext);

                    // 创建 ReactAgent
                    ReactAgent agent = chatService.createReactAgent(chatModel, systemPrompt);
                    // 使用 agent.stream() 进行流式对话
                    stream = agent.stream(reWriteQuery).map(s -> s);
                }


                // 用于累积完整答案
                StringBuilder fullAnswerBuilder = new StringBuilder();
                

                
                stream.subscribe(
                        obj -> {
                        try {
                            if (obj instanceof NodeOutput output) {
                                // 检查是否为 StreamingOutput 类型
                                if (output instanceof StreamingOutput streamingOutput) {
                                    OutputType type = streamingOutput.getOutputType();

                                    // 处理模型推理的流式输出
                                    if (type == OutputType.AGENT_MODEL_STREAMING) {
                                        // 流式增量内容，逐步显示
                                        String chunk = streamingOutput.message().getText();
                                        if (chunk != null && !chunk.isEmpty()) {
                                            fullAnswerBuilder.append(chunk);

                                            // 实时发送到前端
                                            emitter.send(SseEmitter.event()
                                                    .name("message")
                                                    .data(SseMessage.content(chunk), MediaType.APPLICATION_JSON));

                                            logger.info("发送流式内容: {}", chunk);
                                        }
                                    } else if (type == OutputType.AGENT_MODEL_FINISHED) {
                                        // 模型推理完成
                                        logger.info("模型输出完成");
                                    } else if (type == OutputType.AGENT_TOOL_FINISHED) {
                                        // 工具调用完成
                                        logger.info("工具调用完成: {}", output.node());
                                    } else if (type == OutputType.AGENT_HOOK_FINISHED) {
                                        // Hook 执行完成
                                        logger.debug("Hook 执行完成: {}", output.node());
                                    }
                                }
                            } else if (obj instanceof String chunk) {

                                if (chunk != null && !chunk.isEmpty()) {
                                    fullAnswerBuilder.append(chunk);

                                    emitter.send(SseEmitter.event()
                                            .name("message")
                                            .data(SseMessage.content(chunk), MediaType.APPLICATION_JSON));
                                }
                            }
                        } catch (IOException e) {
                            logger.error("发送流式消息失败", e);
                            throw new RuntimeException(e);
                        }
                    },
                    error -> {
                        // 错误处理
                        logger.error("ReactAgent 流式对话失败", error);
                        try {
                            emitter.send(SseEmitter.event()
                                    .name("message")
                                    .data(SseMessage.error(error.getMessage()), MediaType.APPLICATION_JSON));
                        } catch (IOException ex) {
                            logger.error("发送错误消息失败", ex);
                        }
                        emitter.completeWithError(error);
                    },
                    () -> {
                        // 完成处理
                        try {
                            String fullAnswer = fullAnswerBuilder.toString();
                            logger.info("ReactAgent 流式对话完成 - SessionId: {}, 答案长度: {}", 
                                request.getId(), fullAnswer.length());
                            
                            // 更新会话历史
                            sessionInfoService.addMessage(session,query, fullAnswer, httpSession);
                            conversationPersistenceService.saveRound(userId, request.getId(), query, fullAnswer);
                            logger.info("已更新会话历史 - SessionId: {}, 当前消息对数: {}", 
                                request.getId(), session.getMessagePairCount());
                            
                            // 发送完成标记
                            emitter.send(SseEmitter.event()
                                    .name("message")
                                    .data(SseMessage.done(), MediaType.APPLICATION_JSON));
                            emitter.complete();
                        } catch (IOException e) {
                            logger.error("发送完成消息失败", e);
                            emitter.completeWithError(e);
                        }
                    }
                );

            } catch (Exception e) {
                logger.error("ReactAgent 对话初始化失败", e);
                try {
                    emitter.send(SseEmitter.event()
                            .name("message")
                            .data(SseMessage.error(e.getMessage()), MediaType.APPLICATION_JSON));
                } catch (IOException ex) {
                    logger.error("发送错误消息失败", ex);
                }
                emitter.completeWithError(e);
            }
        });

        return emitter;
    }

    /**
     * AI 智能运维接口（SSE 流式模式）- 自动分析告警并生成运维报告
     * 无需用户输入，自动执行告警分析流程
     */
    @PostMapping(value = "/ai_ops", produces = "text/event-stream;charset=UTF-8")
    public SseEmitter aiOps() {
        SseEmitter emitter = new SseEmitter(600000L); // 10分钟超时（告警分析可能较慢）

        executor.execute(() -> {
            try {
                logger.info("收到 AI 智能运维请求 - 启动多 Agent 协作流程");

                DashScopeApi dashScopeApi = chatService.createDashScopeApi();
                DashScopeChatModel chatModel = DashScopeChatModel.builder()
                        .dashScopeApi(dashScopeApi)
                        .defaultOptions(DashScopeChatOptions.builder()
                                .withModel(DashScopeChatModel.DEFAULT_MODEL_NAME)
                                .withTemperature(0.3)
                                .withMaxToken(8000)
                                .withTopP(0.9)
                                .build())
                        .build();


                emitter.send(SseEmitter.event().name("message").data(SseMessage.content("正在读取告警并拆解任务...\n")));
                
                // 调用 AiOpsService 执行分析流程
                Optional<OverAllState> overAllStateOptional = aiOpsService.executeAiOpsAnalysis(chatModel, toolCallback);

                if (overAllStateOptional.isEmpty()) {
                    emitter.send(SseEmitter.event().name("message")
                            .data(SseMessage.error("多 Agent 编排未获取到有效结果"), MediaType.APPLICATION_JSON));
                    emitter.complete();
                    return;
                }

                OverAllState state = overAllStateOptional.get();
                logger.info("AI Ops 编排完成，开始提取最终报告...");

                // 提取最终报告
                Optional<String> finalReportOptional = aiOpsService.extractFinalReport(state);

                // 输出最终报告
                if (finalReportOptional.isPresent()) {
                    String finalReportText = finalReportOptional.get();
                    logger.info("提取到 Planner 最终报告，长度: {}", finalReportText.length());
                    
                    // 发送分隔线
                    emitter.send(SseEmitter.event().name("message")
                            .data(SseMessage.content("\n\n" + "=".repeat(60) + "\n"), MediaType.APPLICATION_JSON));
                    
                    // 发送完整的告警分析报告
                    emitter.send(SseEmitter.event().name("message")
                            .data(SseMessage.content("📋 **告警分析报告**\n\n"), MediaType.APPLICATION_JSON));
                    
                    int chunkSize = 50;
                    for (int i = 0; i < finalReportText.length(); i += chunkSize) {
                        int end = Math.min(i + chunkSize, finalReportText.length());
                        String chunk = finalReportText.substring(i, end);
                        
                        emitter.send(SseEmitter.event().name("message")
                                .data(SseMessage.content(chunk), MediaType.APPLICATION_JSON));
                    }
                    
                    // 发送结束分隔线
                    emitter.send(SseEmitter.event().name("message")
                            .data(SseMessage.content("\n" + "=".repeat(60) + "\n\n"), MediaType.APPLICATION_JSON));
                    
                    logger.info("最终报告已完整输出");
                } else {
                    logger.warn("未能提取到 Planner 最终报告");
                    emitter.send(SseEmitter.event().name("message")
                            .data(SseMessage.content("⚠️ 多 Agent 流程已完成，但未能生成最终报告。"), MediaType.APPLICATION_JSON));
                }

                emitter.send(SseEmitter.event().name("message").data(SseMessage.done(), MediaType.APPLICATION_JSON));
                emitter.complete();
                logger.info("AI Ops 多 Agent 编排完成");

            } catch (Exception e) {
                logger.error("AI Ops 多 Agent 协作失败", e);
                try {
                    emitter.send(SseEmitter.event().name("message")
                            .data(SseMessage.error("AI Ops 流程失败: " + e.getMessage()), MediaType.APPLICATION_JSON));
                } catch (IOException ex) {
                    logger.error("发送错误消息失败", ex);
                }
                emitter.completeWithError(e);
            }
        });

        return emitter;
    }


    /**
     * 获取当前用户会话列表
     */
    @GetMapping("/chat/sessions")
    public ResponseEntity<ApiResponse<List<SessionInfoResponse.SessionSummary>>> getSessionSummaries(HttpSession httpSession) {
        try {
            Long userId = authService.requireUserId(httpSession);
            logger.info("收到获取会话列表请求 - UserId: {}", userId);
            List<SessionInfoResponse.SessionSummary> response = conversationPersistenceService.getSessionSummaries(userId);
            return ResponseEntity.ok(ApiResponse.success(response));
        } catch (Exception e) {
            logger.error("获取会话列表失败", e);
            return ResponseEntity.ok(ApiResponse.error(e.getMessage()));
        }
    }

    /**
     * 获取会话信息
     */
    @GetMapping("/chat/session/{sessionId}")
    public ResponseEntity<ApiResponse<SessionInfoResponse>> getSessionInfo(@PathVariable String sessionId, HttpSession httpSession) {
        try {
            Long userId = authService.requireUserId(httpSession);
            logger.info("收到获取会话信息请求 - UserId: {}, SessionId: {}", userId, sessionId);

            SessionInfoResponse response = conversationPersistenceService.getSessionInfo(userId, sessionId);
            if (response == null) {
                return ResponseEntity.ok(ApiResponse.error("会话不存在"));
            }
            return ResponseEntity.ok(ApiResponse.success(response));

        } catch (Exception e) {
            logger.error("获取会话信息失败", e);
            return ResponseEntity.ok(ApiResponse.error(e.getMessage()));
        }
    }

    // ==================== 辅助方法 ====================

    private SessionInfo getOrCreateSession(String sessionId) {
        if (sessionId == null || sessionId.isEmpty()) {
            sessionId = UUID.randomUUID().toString();
        }
        return sessions.computeIfAbsent(sessionId, id -> sessionInfoService.createSessionInfo(id));
    }




}
