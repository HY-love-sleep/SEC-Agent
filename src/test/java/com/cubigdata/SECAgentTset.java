//package com.cubigdata;
//
//import com.alibaba.cloud.ai.dashscope.api.DashScopeApi;
//import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
//import com.alibaba.cloud.ai.graph.KeyStrategy;
//import com.alibaba.cloud.ai.graph.KeyStrategyFactory;
//import com.alibaba.cloud.ai.graph.agent.ReactAgent;
//import com.alibaba.cloud.ai.graph.state.strategy.ReplaceStrategy;
//import org.junit.jupiter.api.BeforeEach;
//import org.junit.jupiter.api.Test;
//import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
//import org.springframework.ai.chat.model.ChatModel;
//import org.springframework.ai.mcp.SyncMcpToolCallbackProvider;
//import org.springframework.beans.factory.annotation.Autowired;
//
//import java.util.HashMap;
//
///**
// * @author yHong
// * @version 1.0
// * @since 2025/9/17 17:16
// */
//@EnabledIfEnvironmentVariable(named = "AI_DASHSCOPE_API_KEY", matches = ".+")
//public class SECAgentTset {
//    private ChatModel chatModel;
//
//    @Autowired
//    private SyncMcpToolCallbackProvider toolCallbackProvider;
//
//    @BeforeEach
//    void setUp() {
//        // Create DashScopeApi instance using the API key from environment variable
//        DashScopeApi dashScopeApi = DashScopeApi.builder().apiKey(System.getenv("AI_DASHSCOPE_API_KEY")).build();
//
//        // Create DashScope ChatModel instance
//        this.chatModel = DashScopeChatModel.builder().dashScopeApi(dashScopeApi).build();
//    }
//
//    @Test
//    public void testSecAgent() throws Exception {
//        KeyStrategyFactory stateFactory = () -> {
//            HashMap<String, KeyStrategy> keyStrategyHashMap = new HashMap<>();
//            keyStrategyHashMap.put("input", new ReplaceStrategy());
//            keyStrategyHashMap.put("dbName", new ReplaceStrategy());
//            keyStrategyHashMap.put("clft_res", new ReplaceStrategy());
//            return keyStrategyHashMap;
//        };
//
//        ReactAgent coltAgent = ReactAgent.builder()
//                .name("writer_agent")
//                .model(chatModel)
//                .description("处理采集程序服务相关的业务流程")
//                .instruction("")
//                .outputKey("dbName")
//                .tools()
//                .build();
//
//    }
//}
