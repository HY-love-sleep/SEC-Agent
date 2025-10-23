package com.cubigdata;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.ollama.OllamaEmbeddingModel;
import org.springframework.ai.reader.TextReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.FileSystemResource;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * @author yHong
 * @since 2025/9/15 17:33
 * @version 1.0
 */
@SpringBootApplication(
        exclude = { org.springframework.ai.model.ollama.autoconfigure.OllamaChatAutoConfiguration.class }
)

@Slf4j
public class Application {

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }

    @Bean
    CommandLineRunner vectorIngestRunner(
            @Value("${rag.source:classpath:rag/rag_friendly_classification.txt}") Resource ragSource,
            @Value("${rag.vector-file-path:classpath:vectors/classification_vectors.json}") String vectorFilePath,
            @Qualifier("ollamaEmbeddingModel") OllamaEmbeddingModel embeddingModel,
            @Qualifier("classificationVectorStore") VectorStore classificationVectorStore
    ) {
        return args -> {
            try {
                // 支持 classpath: 和 file: 两种协议
                Resource vectorFile;
                String actualPath;
                File fileToLoad = null;
                boolean isTemporaryFile = false;

                if (vectorFilePath.startsWith("file:")) {
                    // 外部文件系统路径（Docker部署场景）
                    actualPath = vectorFilePath.replace("file:", "");
                    vectorFile = new FileSystemResource(actualPath);
                    log.info("📂 使用外部文件系统路径: {}", actualPath);

                    if (vectorFile.exists()) {
                        fileToLoad = vectorFile.getFile();
                    }
                } else {
                    // classpath路径（开发环境或JAR内部）
                    actualPath = vectorFilePath.replace("classpath:", "");
                    vectorFile = new ClassPathResource(actualPath);
                    log.info("📦 使用classpath路径: {}", actualPath);

                    if (vectorFile.exists()) {
                        // 从JAR中读取资源，复制到临时文件
                        try (InputStream inputStream = vectorFile.getInputStream()) {
                            Path tempFile = Files.createTempFile("classification_vectors_", ".json");
                            Files.copy(inputStream, tempFile, StandardCopyOption.REPLACE_EXISTING);
                            fileToLoad = tempFile.toFile();
                            isTemporaryFile = true;
                            log.info("📋 已将classpath资源复制到临时文件: {}", tempFile);
                        }
                    }
                }

                if (fileToLoad != null && fileToLoad.exists()) {
                    log.info("🔄 从预计算文件加载向量数据: {}", vectorFilePath);
                    // 从文件加载向量数据
                    if (classificationVectorStore instanceof SimpleVectorStore) {
                        SimpleVectorStore simpleStore = (SimpleVectorStore) classificationVectorStore;
                        simpleStore.load(fileToLoad);
                        log.info("✅ 向量数据加载完成，文件大小: {} KB", fileToLoad.length() / 1024);
                    }

                    // 如果是临时文件，加载完成后可以删除（可选）
                    // if (isTemporaryFile) {
                    //     fileToLoad.deleteOnExit();
                    // }
                } else {
                    log.warn("⚠️ 向量文件不存在: {}", vectorFilePath);
                    log.info("🔄 正在向量化加载分类分级知识库, EmbeddingModel:{}", embeddingModel.getClass().getName());
                    var chunks = new TokenTextSplitter().transform(new TextReader(ragSource).read());
                    classificationVectorStore.write(chunks);
                    log.info("✅ 向量化完成，共处理 {} 个文档块", chunks.size());

                    // 保存向量数据到文件（用于后续部署）
                    if (classificationVectorStore instanceof SimpleVectorStore) {
                        SimpleVectorStore simpleStore = (SimpleVectorStore) classificationVectorStore;
                        File saveFile;

                        if (vectorFilePath.startsWith("file:")) {
                            // 外部文件系统路径
                            saveFile = new File(actualPath);
                        } else {
                            // classpath路径，保存到项目resources目录
                            saveFile = new File("src/main/resources/" + actualPath);
                        }

                        // 确保目录存在
                        if (saveFile.getParentFile() != null) {
                            saveFile.getParentFile().mkdirs();
                        }

                        simpleStore.save(saveFile);
                        log.info("💾 向量数据已保存到: {}, 文件大小: {} KB",
                                saveFile.getAbsolutePath(), saveFile.length() / 1024);
                    }
                }
            } catch (Exception e) {
                log.error("❌ 向量数据处理失败: {}", e.getMessage(), e);
                throw new RuntimeException("向量数据处理失败", e);
            }
        };
    }

    /**
     * 分类分级向量存储，用于后续 RAG 检索
     */
    @Bean
    public VectorStore classificationVectorStore(@Qualifier("ollamaEmbeddingModel") EmbeddingModel embeddingModel) {
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

