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

        log.info("开始进行相似度匹配搜索...");
        String queryStr = state.value("query").orElse("").toString();

        Map<String, Object> inputs = new HashMap<>();
        inputs.put("query", queryStr);
        inputs.put("threshold", 0.6);
        inputs.put("topN", 2);

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("inputs", inputs);

        CompletableFuture<Map> future = webClient.post()
                .uri(url)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(Map.class)
                .toFuture();

        Map<String, Object> updated = new HashMap<>();
        updated.put("similarityMatchResult", future.get());
        log.info("相似度搜索完成！");
        return updated;
    }
}
