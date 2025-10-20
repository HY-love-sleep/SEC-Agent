package com.cubigdata.service;

import com.cubigdata.model.RagRecord;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * RAG 数据加载和索引服务
 * 提供关键词索引、正则匹配、字段名匹配等功能
 * 
 * @author yHong
 * @since 2025/10/20
 */
@Service
@Slf4j
public class RagDataService {
    
    private final ObjectMapper objectMapper;
    private List<RagRecord> ragRecords;
    
    /**
     * 关键词倒排索引：keyword -> List<RagRecord>
     */
    private Map<String, List<RagRecord>> keywordIndex;
    
    /**
     * 字段名索引：fieldName -> RagRecord
     */
    private Map<String, RagRecord> fieldNameIndex;
    
    public RagDataService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.ragRecords = new ArrayList<>();
        this.keywordIndex = new HashMap<>();
        this.fieldNameIndex = new HashMap<>();
    }
    
    /**
     * 加载 RAG 数据
     */
    public void loadRagData(Resource ragResource) throws IOException {
        log.info("🔄 正在加载结构化 RAG 数据...");
        
        ragRecords = new ArrayList<>();
        
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(ragResource.getInputStream(), StandardCharsets.UTF_8))) {
            
            String line;
            int lineNum = 0;
            while ((line = reader.readLine()) != null) {
                lineNum++;
                if (!line.trim().isEmpty()) {
                    try {
                        RagRecord record = objectMapper.readValue(line, RagRecord.class);
                        ragRecords.add(record);
                    } catch (Exception e) {
                        log.warn("解析第 {} 行 RAG 记录失败: {}", lineNum, e.getMessage());
                    }
                }
            }
        }
        
        log.info("✅ 成功加载 {} 条 RAG 记录", ragRecords.size());
        
        // 构建索引
        buildIndexes();
    }
    
    /**
     * 构建所有索引
     */
    private void buildIndexes() {
        log.info("🔄 正在构建索引...");
        
        // 构建关键词索引
        buildKeywordIndex();
        
        // 构建字段名索引
        buildFieldNameIndex();
        
        log.info("✅ 索引构建完成");
    }
    
    /**
     * 构建关键词倒排索引
     */
    private void buildKeywordIndex() {
        keywordIndex = new HashMap<>();
        
        for (RagRecord record : ragRecords) {
            if (record.getDetection() != null && record.getDetection().getKeywords() != null) {
                for (String keyword : record.getDetection().getKeywords()) {
                    String normalizedKey = keyword.toLowerCase();
                    keywordIndex.computeIfAbsent(normalizedKey, k -> new ArrayList<>()).add(record);
                }
            }
        }
        
        log.info("  - 关键词索引: {} 个关键词", keywordIndex.size());
    }
    
    /**
     * 构建字段名索引
     */
    private void buildFieldNameIndex() {
        fieldNameIndex = new HashMap<>();
        
        for (RagRecord record : ragRecords) {
            if (record.getField() != null && record.getField().getName() != null) {
                String normalizedName = record.getField().getName().toLowerCase();
                fieldNameIndex.put(normalizedName, record);
                
                // 也索引别名
                if (record.getField().getAliases() != null) {
                    for (String alias : record.getField().getAliases()) {
                        fieldNameIndex.put(alias.toLowerCase(), record);
                    }
                }
            }
        }
        
        log.info("  - 字段名索引: {} 个字段/别名", fieldNameIndex.size());
    }
    
    /**
     * 通过关键词搜索（支持模糊匹配）
     */
    public List<RagRecord> searchByKeyword(String text) {
        if (text == null || text.isEmpty()) {
            return Collections.emptyList();
        }
        
        String normalizedText = text.toLowerCase();
        
        return keywordIndex.entrySet().stream()
                .filter(entry -> normalizedText.contains(entry.getKey()) || 
                               entry.getKey().contains(normalizedText))
                .flatMap(entry -> entry.getValue().stream())
                .distinct()
                .collect(Collectors.toList());
    }
    
    /**
     * 通过字段名精确匹配
     */
    public RagRecord matchByFieldName(String fieldName) {
        if (fieldName == null || fieldName.isEmpty()) {
            return null;
        }
        
        return fieldNameIndex.get(fieldName.toLowerCase());
    }
    
    /**
     * 通过正则验证样例数据
     */
    public List<RagRecord> validateBySampleData(List<String> sampleData) {
        if (sampleData == null || sampleData.isEmpty()) {
            return Collections.emptyList();
        }
        
        List<RagRecord> matches = new ArrayList<>();
        
        for (RagRecord record : ragRecords) {
            if (record.getDetection() == null || 
                record.getDetection().getRegexPatterns() == null) {
                continue;
            }
            
            for (RagRecord.RegexPattern pattern : record.getDetection().getRegexPatterns()) {
                try {
                    Pattern regex = Pattern.compile(pattern.getRegex());
                    
                    for (String sample : sampleData) {
                        if (sample != null && regex.matcher(sample).matches()) {
                            matches.add(record);
                            // 找到匹配就跳出，避免重复
                            break;
                        }
                    }
                } catch (Exception e) {
                    log.warn("正则表达式编译失败: {}", pattern.getRegex());
                }
            }
        }
        
        return matches;
    }
    
    /**
     * 字段名综合匹配（考虑字段名、别名、注释）
     */
    public List<RagRecord> matchFieldName(String fieldName, String columnComment) {
        List<MatchResult> candidates = new ArrayList<>();
        
        for (RagRecord record : ragRecords) {
            int score = 0;
            
            // 检查字段名
            if (record.getField() != null) {
                if (fieldName != null && fieldName.equalsIgnoreCase(record.getField().getName())) {
                    score += 100;
                }
                
                // 检查别名
                if (record.getField().getAliases() != null) {
                    for (String alias : record.getField().getAliases()) {
                        if (fieldName != null && fieldName.equalsIgnoreCase(alias)) {
                            score += 90;
                            break;
                        }
                    }
                }
                
                // 检查关键词
                if (record.getDetection() != null && record.getDetection().getKeywords() != null) {
                    for (String keyword : record.getDetection().getKeywords()) {
                        if (fieldName != null && 
                            fieldName.toLowerCase().contains(keyword.toLowerCase())) {
                            score += 50;
                            break;
                        }
                    }
                }
            }
            
            // 检查注释匹配
            if (columnComment != null && !columnComment.isEmpty()) {
                if (record.getDetection() != null && 
                    record.getDetection().getCommentPatterns() != null) {
                    for (String pattern : record.getDetection().getCommentPatterns()) {
                        try {
                            String regex = pattern.replace("*", ".*");
                            if (columnComment.matches(regex)) {
                                score += 60;
                                break;
                            }
                        } catch (Exception e) {
                            // 忽略正则错误
                        }
                    }
                }
                
                // 注释中包含字段名
                if (record.getField() != null && 
                    columnComment.contains(record.getField().getName())) {
                    score += 40;
                }
            }
            
            if (score > 0) {
                candidates.add(new MatchResult(record, score));
            }
        }
        
        // 按得分排序
        candidates.sort((a, b) -> Integer.compare(b.score, a.score));
        
        return candidates.stream()
                .map(mr -> mr.record)
                .collect(Collectors.toList());
    }
    
    /**
     * 应用条件规则判断级别
     */
    public Integer determineLevel(RagRecord record, Map<String, Object> context) {
        if (record == null || record.getLevel() == null) {
            return null;
        }
        
        // 获取默认级别
        Integer defaultLevel = record.getLevel().getDefaultLevel();
        
        // 检查条件
        if (record.getLevel().getConditions() != null) {
            for (RagRecord.Condition condition : record.getLevel().getConditions()) {
                if (evaluateCondition(condition, context)) {
                    log.debug("条件匹配: {}", condition.getRationale());
                    return condition.getResult();
                }
            }
        }
        
        return defaultLevel;
    }
    
    /**
     * 评估条件是否满足
     */
    private boolean evaluateCondition(RagRecord.Condition condition, Map<String, Object> context) {
        if (condition.getWhen() == null) {
            return false;
        }
        
        String operator = (String) condition.getWhen().get("operator");
        
        if ("co_occurs_with_any".equals(operator)) {
            // 检查是否与指定字段共现
            List<String> requiredFields = (List<String>) condition.getWhen().get("fields");
            List<String> tableColumns = (List<String>) context.get("tableColumns");
            
            if (requiredFields != null && tableColumns != null) {
                for (String field : requiredFields) {
                    if (tableColumns.stream().anyMatch(col -> 
                        col.toLowerCase().contains(field.toLowerCase()))) {
                        return true;
                    }
                }
            }
        } else if ("published".equals(operator)) {
            // 检查是否已发布
            Boolean isPublished = (Boolean) context.get("isPublished");
            return Boolean.TRUE.equals(isPublished);
        }
        
        return false;
    }
    
    /**
     * 获取所有记录
     */
    public List<RagRecord> getAllRecords() {
        return Collections.unmodifiableList(ragRecords);
    }
    
    /**
     * 获取记录总数
     */
    public int getRecordCount() {
        return ragRecords.size();
    }
    
    /**
     * 匹配结果（内部类）
     */
    private static class MatchResult {
        final RagRecord record;
        final int score;
        
        MatchResult(RagRecord record, int score) {
            this.record = record;
            this.score = score;
        }
    }
}

