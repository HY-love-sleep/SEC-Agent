package com.cubigdata.controller;

import com.alibaba.cloud.ai.graph.CompiledGraph;
import com.alibaba.cloud.ai.graph.NodeOutput;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.StateGraph;
import com.alibaba.cloud.ai.graph.exception.GraphStateException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import java.util.Map;

/**
 * @author yHong
 * @version 1.0
 * @since 2025/9/23 17:29
 */
@RestController
@RequestMapping("/sec/workflow")
@Slf4j
public class GraphController {
    private final ObjectMapper objectMapper;

    private final CompiledGraph compiledGraph;

    public GraphController(ObjectMapper objectMapper, @Qualifier("secGraph") StateGraph stateGraph) throws GraphStateException {
        this.objectMapper = objectMapper;
        this.compiledGraph = stateGraph.compile();
    }

    @PostMapping(value = "/chat", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> simpleChat(@RequestBody Map<String, Object> body,
                                                    @RequestParam(value = "thread_id", defaultValue = "yhong", required = false) String threadId) throws Exception {
        RunnableConfig runnableConfig = RunnableConfig.builder().threadId(threadId).build();
        Map<String,Object> query = (Map<String,Object>) body.get("query");
        String queryStr = objectMapper.writeValueAsString(query);
        Object category = body.get("category");
        Flux<NodeOutput> resultFuture = compiledGraph.fluxStream(Map.of("query", queryStr, "category", category), runnableConfig);

        return resultFuture
                .map(nodeOutput -> {
                    String nodeName = nodeOutput.node();
                    OverAllState state = nodeOutput.state();

                    String outputData = extractNodeOutput(nodeName, state);

                    String message = String.format("节点 %s 执行完成: %s", nodeName, outputData);

                    return ServerSentEvent.<String>builder()
                            .event("node_output")
                            .data(message)
                            .id(nodeName)
                            .build();
                })
                .concatWith(Flux.just(ServerSentEvent.<String>builder()
                        .event("complete")
                        .data("工作流执行完成")
                        .build()))
                .onErrorResume(throwable -> {
                    log.error("SSE流处理出错", throwable);
                    return Flux.just(ServerSentEvent.<String>builder()
                            .event("error")
                            .data("处理失败: " + throwable.getMessage())
                            .build());
                });
    }

    /**
     * 提取节点输出内容
     */
    private String extractNodeOutput(String nodeName, OverAllState state) {
        try {
            // 根据节点名称提取相应的输出数据
            switch (nodeName) {
                case "__START__":
                    return "开始执行工作流";

                case "__END__":
                    return "工作流执行结束";

                case "classification":
                    Object classificationResult = state.value("llmResult").orElse("无分类结果");
                    return String.format("分类分级结果: %s", classificationResult);

                case "similarity_match":
                    Object similarityResult = state.value("similarityMatchResult").orElse("无相似度匹配结果");
                    return String.format("相似度匹配结果: %s", similarityResult);

                default:
                    Map<String, Object> allData = state.data();
                    StringBuilder output = new StringBuilder();

                    for (Map.Entry<String, Object> entry : allData.entrySet()) {
                        String key = entry.getKey();
                        Object value = entry.getValue();

                        if (!key.startsWith("__") && !key.equals("query") && !key.equals("category")) {
                            if (output.length() > 0) {
                                output.append(", ");
                            }
                            output.append(String.format("%s: %s", key, value));
                        }
                    }

                    return output.length() > 0 ? output.toString() : "无具体输出";
            }
        } catch (Exception e) {
            log.error("提取节点输出失败: {}", e.getMessage());
            return "输出提取失败";
        }
    }
}
