package com.cubigdata.controller;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.mcp.SyncMcpToolCallbackProvider;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author yHong
 * @version 1.0
 * @since 2025/9/15 17:52
 */
@RestController
@RequestMapping("/mcp")
public class McpController {
    private final SyncMcpToolCallbackProvider toolCallbackProvider;

    private final ChatClient.Builder chatClientBuilder;

    public McpController(SyncMcpToolCallbackProvider toolCallbackProvider, ChatClient.Builder chatClientBuilder) {
        this.toolCallbackProvider = toolCallbackProvider;
        this.chatClientBuilder = chatClientBuilder;
    }

    @GetMapping("/api/clft/result")
    public String processDataAsset(@RequestParam String dbName, @RequestParam String tbName) {
        try {
            ChatClient chatClient = chatClientBuilder
                    .defaultSystem("""
                    你是一个数据分类分级专家，负责处理用户对库表的分类分级查询请求。
                    你需要：
                    1. 从用户侧获取查询的参数
                    2. 调用相关工具进行分类分级结果查询
                    """)
                    .defaultToolCallbacks(toolCallbackProvider.getToolCallbacks())
                    .build();

            return chatClient.prompt()
                    .user("帮我查询下tbName=" + tbName + ", dbName=" + dbName + "的数据库表分类分级结果")
                    .call()
                    .content();
        } catch (Exception e) {
            System.err.println("MCP查询失败: " + e.getMessage());
            e.printStackTrace();
            return "查询失败: " + e.getMessage() + "。请检查MCP服务器连接状态。";
        }
    }

    @GetMapping("/health")
    public String healthCheck() {
        try {
            var toolCallbacks = toolCallbackProvider.getToolCallbacks();
            if (toolCallbacks.length == 0) {
                return "MCP连接状态: 无可用工具回调";
            }
            return "MCP连接状态: 正常，可用工具数量: " + toolCallbacks.length;
        } catch (Exception e) {
            return "MCP连接状态: 异常 - " + e.getMessage();
        }
    }

}
