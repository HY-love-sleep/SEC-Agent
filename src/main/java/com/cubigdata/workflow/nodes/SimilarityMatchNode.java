package com.cubigdata.workflow.nodes;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

@Slf4j
public class SimilarityMatchNode implements NodeAction {

    private final WebClient webClient;
    private final String url;

    public SimilarityMatchNode(WebClient webClient, String url) {
        this.webClient = webClient;
        this.url = url;
    }

    @Override
    public Map<String, Object> apply(OverAllState state) throws ExecutionException, InterruptedException {

        String queryStr = state.value("query").orElse("").toString();
        log.info("接收到的query字符串: {}", queryStr);

        Map<String, Object> inputs = new HashMap<>();
        inputs.put("query", queryStr);
        inputs.put("threshold", 0.6);
        inputs.put("topN", 2);

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("inputs", inputs);

        log.info("最终请求体: {}", WebClientUtils.toJsonString(requestBody));

        CompletableFuture<Map> future = webClient.post()
                .uri(url)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(Map.class)
                .toFuture();

        Map<String, Object> updated = new HashMap<>();
        updated.put("similarityMatchResult", future.get());
        return updated;
    }

    static class WebClientUtils {
        private static final com.fasterxml.jackson.databind.ObjectMapper MAPPER = new com.fasterxml.jackson.databind.ObjectMapper();

        public static String toJsonString(Object obj) {
            try {
                return MAPPER.writeValueAsString(obj);
            } catch (Exception e) {
                throw new RuntimeException("serialize failed", e);
            }
        }
    }
}
