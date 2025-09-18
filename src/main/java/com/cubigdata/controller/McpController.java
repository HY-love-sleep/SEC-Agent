package com.cubigdata.controller;

import com.cubigdata.controller.qry.ColtParam;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.mcp.SyncMcpToolCallbackProvider;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
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
    public String getClftResult(@RequestParam String dbName, @RequestParam String tbName) {
        try {
            ChatClient chatClient = chatClientBuilder
                    .defaultSystem("""
                    你是一个数据分类分级专家，负责处理用户对库表的分类分级查询请求。
                    你需要：
                    1. 从用户侧获取查询的参数
                    2. 调用相关工具进行分类分级结果查询
                    3. 查询资产列表结果即可， 对应的position=0
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

    @PostMapping("/api/colt/add")
    public String addCollectionTask(@RequestBody ColtParam addParam) {
        ChatClient chatClient = chatClientBuilder
                .defaultSystem("""
                        你是数据采集专家， 负责处理用户对采集程序服务的相关请求， 并调用对应工具进行处理
                        你需要：
                        1. 从用户侧获取查询的参数
                        2. 调用相关工具进行采集任务的新增
                        """)
                .defaultToolCallbacks(toolCallbackProvider.getToolCallbacks())
                .build();
        return chatClient.prompt()
                .user("帮我根据请求参数:" + addParam.toString() + "新增一个采集任务")
                .call()
                .content();
    }

    @PostMapping("/api/colt/open")
    public String openCollectionTask(@RequestBody ColtParam openParam) {
        ChatClient chatClient = chatClientBuilder
                .defaultSystem("""
                        你是数据采集专家， 负责处理用户对采集程序服务的相关请求， 并调用对应工具进行处理
                        你需要：
                        1. 从用户侧获取要开启的采集任务的id
                        2. 调用相关工具进行采集任务的新增
                        """)
                .defaultToolCallbacks(toolCallbackProvider.getToolCallbacks())
                .build();
        return chatClient.prompt()
                .user("帮我开启一个采集任务， 采集任务id=" + openParam.collectTaskId())
                .call()
                .content();
    }

    @PostMapping("/api/colt/exec")
    public String execCollectionTask(@RequestBody ColtParam openParam) {
        ChatClient chatClient = chatClientBuilder
                .defaultSystem("""
                        你是数据采集专家， 负责处理用户对采集程序服务的相关请求， 并调用对应工具进行处理
                        你需要：
                        1. 从用户侧获取要执行的采集任务的id
                        2. 调用相关工具进行执行采集任务
                        """)
                .defaultToolCallbacks(toolCallbackProvider.getToolCallbacks())
                .build();
        return chatClient.prompt()
                .user("帮我执行一个采集任务， 采集任务id=" + openParam.collectTaskId())
                .call()
                .content();
    }

    @GetMapping("/api/colt/queryPage")
    public String queryPage(@RequestParam String dbName) {
        ChatClient chatClient = chatClientBuilder
                .defaultSystem("""
                        你是数据采集专家， 负责处理用户对采集程序服务的相关请求， 并调用对应工具进行处理
                        你需要：
                        1. 从用户侧获取数据库名称
                        2. 调用相关工具获取对应的采集任务ID
                        """)
                .defaultToolCallbacks(toolCallbackProvider.getToolCallbacks())
                .build();
        return chatClient.prompt()
                .user("帮我查找数据库名" + dbName + "对应的采集任务ID")
                .call()
                .content();
    }

    @GetMapping("/api/clft/getDbId")
    public String getDbId(@RequestParam String dbName) {
        ChatClient chatClient = chatClientBuilder
                .defaultSystem("""
                        你是分类分级专家， 负责处理用户对分类分级服务的相关请求， 并调用对应工具进行处理
                        你需要：
                        1. 调用相关工具进行元数据列表的全量查询
                        2. 根据用户提供的数据库名称， 筛选出对应的datasourceId
                        """)
                .defaultToolCallbacks(toolCallbackProvider.getToolCallbacks())
                .build();
        return chatClient.prompt()
                .user("帮我查找数据库名" + dbName + "对应的数据库id")
                .call()
                .content();
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
