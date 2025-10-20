package com.cubigdata;

import com.cubigdata.service.RagDataService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.Resource;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author yHong
 * @since 2025/9/15 17:33
 * @version 1.0
 */
@SpringBootApplication
@Slf4j
public class Application {

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }

    @Bean
    CommandLineRunner vectorIngestRunner(
            @Value("${rag.source:classpath:rag/rag_new.txt}") Resource ragSource,
            EmbeddingModel embeddingModel,
            @Qualifier("classificationVectorStore") VectorStore classificationVectorStore,
            RagDataService ragDataService,
            ObjectMapper objectMapper
    ) {
        return args -> {
            log.info("🔄 正在加载 RAG 知识库, EmbeddingModel: {}", embeddingModel.getClass().getName());
            
            // 加载结构化数据（用于关键词和正则匹配）
            ragDataService.loadRagData(ragSource);
            
            // 加载向量数据（用于语义检索）
            List<Document> documents = new ArrayList<>();
            
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(ragSource.getInputStream(), StandardCharsets.UTF_8))) {
                
                String line;
                int lineNum = 0;
                while ((line = reader.readLine()) != null) {
                    lineNum++;
                    if (!line.trim().isEmpty()) {
                        try {
                            JsonNode node = objectMapper.readTree(line);
                            
                            String text = node.path("text").asText();
                            String id = node.path("id").asText();
                            
                            if (text != null && !text.isEmpty()) {
                                Map<String, Object> metadata = new HashMap<>();
                                metadata.put("id", id);
                                metadata.put("field_name", node.path("field").path("name").asText());
                                metadata.put("category", node.path("taxonomy").path("target_category").asText());
                                metadata.put("level", node.path("level").path("default").asInt());
                                
                                documents.add(new Document(text, metadata));
                            }
                        } catch (Exception e) {
                            log.warn("解析第 {} 行向量数据失败: {}", lineNum, e.getMessage());
                        }
                    }
                }
            }

            int BATCH_SIZE = 200;
            int SLEEP_MILLIS = 65_000;
            List<Document> batch = new ArrayList<>();

            int total = 0;
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(ragSource.getInputStream(), StandardCharsets.UTF_8))) {

                String line;
                int lineNum = 0;
                while ((line = reader.readLine()) != null) {
                    lineNum++;
                    if (!line.trim().isEmpty()) {
                        try {
                            JsonNode node = objectMapper.readTree(line);
                            String text = node.path("text").asText();
                            if (text == null || text.isEmpty()) continue;

                            Map<String, Object> metadata = new HashMap<>();
                            metadata.put("id", node.path("id").asText());
                            metadata.put("field_name", node.path("field").path("name").asText());
                            metadata.put("category", node.path("taxonomy").path("target_category").asText());
                            metadata.put("level", node.path("level").path("default").asInt());
                            batch.add(new Document(text, metadata));

                            // 每200条批量写入一次
                            if (batch.size() >= BATCH_SIZE) {
                                log.info("🧠 正在写入第 {}~{} 条文档到向量库...", total + 1, total + batch.size());
                                classificationVectorStore.write(batch);
                                total += batch.size();
                                batch.clear();
                                log.info("💤 已写入 {} 条，休眠 60 秒以避免限流...", total);
                                Thread.sleep(SLEEP_MILLIS);
                            }
                        } catch (Exception e) {
                            log.warn("解析第 {} 行失败: {}", lineNum, e.getMessage());
                        }
                    }
                }
                // 写入剩余未满200条的部分
                if (!batch.isEmpty()) {
                    classificationVectorStore.write(batch);
                    total += batch.size();
                }
            }
            log.info("✅ 向量化完成，共写入 {} 条文档", total);

//
//            log.info("🔄 正在向量化 {} 条文档...", documents.size());
//            classificationVectorStore.write(documents);
//            log.info("✅ RAG 知识库加载完成");
        };
    }

    /**
     * 分类分级向量存储，用于后续 RAG 检索
     */
    @Bean
    public VectorStore classificationVectorStore(EmbeddingModel embeddingModel) {
        return SimpleVectorStore
                .builder(embeddingModel).build();
    }

    /**
     * 多轮对话记忆容器（基于内存）
     */
    @Bean
    public ChatMemory chatMemory() {
        return MessageWindowChatMemory.builder().build();
    }
}

