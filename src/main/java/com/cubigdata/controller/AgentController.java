package com.cubigdata.controller;

import com.alibaba.cloud.ai.dashscope.api.DashScopeApi;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.alibaba.cloud.ai.graph.CompiledGraph;
import com.cubigdata.service.SecAgent;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.mcp.SyncMcpToolCallbackProvider;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.Map;

/**
 * @author yHong
 * @version 1.0
 * @since 2025/9/17 18:37
 */

@RestController
@RequestMapping("/sec")
public class AgentController {
    private final SyncMcpToolCallbackProvider toolCallbackProvider;

    private final ChatModel chatModel;

    private final SecAgent agent;

    public AgentController(SyncMcpToolCallbackProvider toolCallbackProvider, ChatModel chatModel, SecAgent agent) {
        this.toolCallbackProvider = toolCallbackProvider;
        DashScopeApi dashScopeApi = DashScopeApi.builder().apiKey(System.getenv("AI_DASHSCOPE_API_KEY")).build();
        this.chatModel = DashScopeChatModel.builder().dashScopeApi(dashScopeApi).build();
        this.agent = agent;
    }

    @PostMapping(value = "/demo")
    public Map<String, Object> startInvoke(@RequestBody Map<String, Object> inputs) throws Exception {
        CompiledGraph compiledGraph = agent.buildCompiledGraph(chatModel, Arrays.asList(toolCallbackProvider.getToolCallbacks()));
        return compiledGraph.call(inputs).get().data();
    }

}
