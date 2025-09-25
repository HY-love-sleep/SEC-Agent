package com.cubigdata.config;

import com.alibaba.cloud.ai.graph.node.KnowledgeRetrievalNode;
import com.cubigdata.workflow.nodes.CategoryValidationNode;
import com.cubigdata.workflow.nodes.ClassificationLLMNode;
import com.cubigdata.workflow.nodes.SimilarityMatchNode;
import com.cubigdata.workflow.nodes.StructuredValidationNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;

import javax.net.ssl.SSLException;
import java.io.IOException;
import reactor.netty.http.client.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

@Configuration
@Slf4j
public class NodeConfig {
    private final ObjectMapper objectMapper;

    public NodeConfig(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }


    /**
     * KnowledgeRetrievalNode 节点配置
     */
    @Bean("knowledgeRetrievalNode")
    public KnowledgeRetrievalNode knowledgeRetrievalNode(@Qualifier("classificationVectorStore") VectorStore classificationVectorStore) {
        return KnowledgeRetrievalNode.builder()
                .inputKey("query")
                .vectorStore(classificationVectorStore)
                .topK(10)
                .similarityThreshold(0.2)
                .outputKey("retrievedDocs")
                .build();
    }


    /**
     * HttpNode 节点配置
     */
    @Bean("similarityMatchNode")
    public SimilarityMatchNode similarityMatchNode() throws SSLException {
        HttpClient httpClient = HttpClient.create()
                .secure(ssl -> {
                    try {
                        ssl.sslContext(
                                SslContextBuilder.forClient()
                                        .trustManager(InsecureTrustManagerFactory.INSTANCE).build()
                        );
                    } catch (SSLException e) {
                        throw new RuntimeException(e);
                    }
                });

        WebClient webClient = WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();

        return new SimilarityMatchNode(webClient, "https://172.16.22.18:8901/py/match");
    }


    /**
     * 分类分级节点配置
     */
    @Bean("classificationLLMNode")
    public ClassificationLLMNode classificationLLMNode(ChatClient.Builder chatClientBuilder,
                                                       @Qualifier("classificationVectorStore") VectorStore classificationVectorStore) throws IOException {
        String promptTemplate = new String(
                Objects.requireNonNull(
                        this.getClass().getResourceAsStream("/classification_prompt.txt")
                ).readAllBytes(), StandardCharsets.UTF_8);

        return new ClassificationLLMNode(
                chatClientBuilder,
                classificationVectorStore,
                "query",
                "category",
                "retrievedDocs",
                "similarityMatchResult",
                "llmResult",
                promptTemplate
        );
    }

    /**
     * 类别验证节点
     */
    @Bean
    public CategoryValidationNode categoryValidationNode() {
        return new CategoryValidationNode(objectMapper);
    }

    /**
     * LLM返回结果， 结构验证节点
     */
    @Bean
    public StructuredValidationNode structuredValidationNode() {
        return new StructuredValidationNode(objectMapper);
    }


}
