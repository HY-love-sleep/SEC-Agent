package com.cubigdata.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import java.util.List;
import java.util.Map;

/**
 * RAG 知识库记录模型
 * 对应 rag_new.txt 中的结构化JSON数据
 * 
 * @author yHong
 * @since 2025/10/20
 */
@Data
public class RagRecord {
    private String id;
    private FieldInfo field;
    private Taxonomy taxonomy;
    
    @JsonProperty("pii_tags")
    private List<String> piiTags;
    
    private LevelInfo level;
    private List<String> rules;
    private Detection detection;
    private List<Example> examples;
    private Source source;
    
    /**
     * 向量化文本，用于语义检索
     */
    private String text;
    
    /**
     * 字段信息
     */
    @Data
    public static class FieldInfo {
        private String name;
        private List<String> aliases;
        private List<String> synonyms;
    }
    
    /**
     * 分类层次结构
     */
    @Data
    public static class Taxonomy {
        private List<String> path;
        
        @JsonProperty("macro_category")
        private String macroCategory;
        
        @JsonProperty("target_category")
        private String targetCategory;
    }
    
    /**
     * 级别信息
     */
    @Data
    public static class LevelInfo {
        @JsonProperty("default")
        private Integer defaultLevel;
        
        private List<Condition> conditions;
    }
    
    /**
     * 条件规则
     */
    @Data
    public static class Condition {
        /**
         * 条件：operator, fields, scope 等
         */
        private Map<String, Object> when;
        
        /**
         * 条件满足后的级别
         */
        private Integer result;
        
        /**
         * 人类可读的理由
         */
        private String rationale;
        
        /**
         * 置信度 (0-1)
         */
        private Double confidence;
    }
    
    /**
     * 检测规则
     */
    @Data
    public static class Detection {
        /**
         * 关键词列表
         */
        private List<String> keywords;
        
        /**
         * 正则表达式匹配模式
         */
        @JsonProperty("regex_patterns")
        private List<RegexPattern> regexPatterns;
        
        /**
         * 注释匹配模式
         */
        @JsonProperty("comment_patterns")
        private List<String> commentPatterns;
    }
    
    /**
     * 正则表达式模式
     */
    @Data
    public static class RegexPattern {
        /**
         * 正则表达式
         */
        private String regex;
        
        /**
         * 模式描述
         */
        private String desc;
        
        /**
         * 匹配权重 (0-1)
         */
        private Double weight;
    }
    
    /**
     * 示例数据
     */
    @Data
    public static class Example {
        private String scenario;
        
        @JsonProperty("column_name")
        private String columnName;
        
        @JsonProperty("column_comment")
        private String columnComment;
        
        @JsonProperty("sample_data")
        private List<String> sampleData;
        
        private String context;
        private Integer result;
        private String reasoning;
    }
    
    /**
     * 数据来源
     */
    @Data
    public static class Source {
        private String doc;
        private String section;
        
        @JsonProperty("last_updated")
        private String lastUpdated;
    }
}

