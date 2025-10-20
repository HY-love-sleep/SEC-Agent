package com.cubigdata.config;

import com.alibaba.cloud.ai.graph.node.KnowledgeRetrievalNode;
import com.cubigdata.service.RagDataService;
import com.cubigdata.workflow.nodes.CategoryValidationNode;
import com.cubigdata.workflow.nodes.ClassificationLLMNode;
import com.cubigdata.workflow.nodes.EnhancedRetrievalNode;
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
     * 相似度匹配节点配置
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
                        this.getClass().getResourceAsStream("/prompt/classification_prompt.txt")
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

    /**
     * 增强型检索节点（多模融合检索）
     * 结合关键词、正则、向量多种检索方式
     */
    @Bean("enhancedRetrievalNode")
    public EnhancedRetrievalNode enhancedRetrievalNode(
            @Qualifier("classificationVectorStore") VectorStore classificationVectorStore,
            RagDataService ragDataService) {
        return new EnhancedRetrievalNode(
            classificationVectorStore,
            ragDataService,
            objectMapper,
            "query",
            "retrievedDocs",
            5,    // vectorTopK: 向量检索返回前5个结果
            0.5   // vectorThreshold: 相似度阈值0.5
        );
    }

}
