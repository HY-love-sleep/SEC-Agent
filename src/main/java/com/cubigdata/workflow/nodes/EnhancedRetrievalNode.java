package com.cubigdata.workflow.nodes;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.cubigdata.model.RagRecord;
import com.cubigdata.service.RagDataService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 增强型多模融合检索节点
 * 结合关键词、正则、向量多种检索方式，提供更准确的字段匹配
 * 
 * 检索策略：
 * 1. 关键词精确匹配（最快，基于倒排索引）
 * 2. 正则表达式验证（准确，基于样例数据）
 * 3. 向量语义检索（兜底，基于embedding）
 * 
 * @author yHong
 * @since 2025/10/20
 */
@Slf4j
public class EnhancedRetrievalNode implements NodeAction {
    
    private final VectorStore vectorStore;
    private final RagDataService ragDataService;
    private final ObjectMapper objectMapper;
    
    private final String inputKey;
    private final String outputKey;
    
    /**
     * 向量检索的 top-k 数量
     */
    private final int vectorTopK;
    
    /**
     * 向量检索的相似度阈值
     */
    private final double vectorThreshold;
    
    public EnhancedRetrievalNode(VectorStore vectorStore,
                                 RagDataService ragDataService,
                                 ObjectMapper objectMapper,
                                 String inputKey,
                                 String outputKey,
                                 int vectorTopK,
                                 double vectorThreshold) {
        this.vectorStore = vectorStore;
        this.ragDataService = ragDataService;
        this.objectMapper = objectMapper;
        this.inputKey = inputKey;
        this.outputKey = outputKey;
        this.vectorTopK = vectorTopK;
        this.vectorThreshold = vectorThreshold;
    }
    
    @Override
    public Map<String, Object> apply(OverAllState state) throws Exception {
        log.info("开始多模融合检索...");

        Object queryValue = state.value(inputKey).orElse(new HashMap<>());
        Map<String, Object> queryData;
        
        if (queryValue instanceof String) {
            queryData = objectMapper.readValue((String) queryValue, Map.class);
        } else {
            queryData = objectMapper.convertValue(queryValue, Map.class);
        }
        
        List<Map<String, Object>> columnInfoList = 
            (List<Map<String, Object>>) queryData.get("columnInfoList");
        
        if (columnInfoList == null || columnInfoList.isEmpty()) {
            log.warn("未找到字段信息，返回空结果");
            return Map.of(outputKey, "");
        }

        List<String> allColumnNames = columnInfoList.stream()
                .map(col -> (String) col.get("columnName"))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        List<EnhancedResult> enhancedResults = new ArrayList<>();
        
        for (Map<String, Object> columnInfo : columnInfoList) {
            String columnName = (String) columnInfo.get("columnName");
            String columnComment = (String) columnInfo.get("columnComment");
            Object exampleDataObj = columnInfo.get("exampleData");
            
            List<String> sampleData = new ArrayList<>();
            if (exampleDataObj instanceof List) {
                sampleData = ((List<?>) exampleDataObj).stream()
                        .filter(Objects::nonNull)
                        .map(Object::toString)
                        .collect(Collectors.toList());
            }
            
            log.debug("处理字段: {} ({})", columnName, columnComment);
            
            EnhancedResult result = multiModalSearch(
                columnName, 
                columnComment, 
                sampleData,
                allColumnNames
            );
            enhancedResults.add(result);
        }

        String formattedResults = formatResults(enhancedResults);
        
        Map<String, Object> output = new HashMap<>();
        output.put(outputKey, formattedResults);
        
        log.info("✅ 多模融合检索完成，共处理 {} 个字段", enhancedResults.size());
        
        return output;
    }
    
    /**
     * 多模态检索：关键词 -> 正则 -> 向量
     */
    private EnhancedResult multiModalSearch(String columnName, 
                                           String columnComment, 
                                           List<String> sampleData,
                                           List<String> tableColumns) {
        EnhancedResult result = new EnhancedResult();
        result.setColumnName(columnName);
        result.setColumnComment(columnComment);
        
        // 字段名精确匹配（优先级最高）
        RagRecord exactMatch = ragDataService.matchByFieldName(columnName);
        if (exactMatch != null) {
            log.debug("  ✓ 字段名精确匹配: {}", exactMatch.getField().getName());
            result.setExactMatch(exactMatch);
            result.setBestMatch(exactMatch);
            result.setMatchMethod("exact");
            return result;
        }
        
        // 关键词模糊匹配
        List<RagRecord> keywordMatches = new ArrayList<>();
        if (columnName != null && !columnName.isEmpty()) {
            keywordMatches.addAll(ragDataService.searchByKeyword(columnName));
        }
        if (columnComment != null && !columnComment.isEmpty()) {
            keywordMatches.addAll(ragDataService.searchByKeyword(columnComment));
        }
        
        // 去重
        keywordMatches = keywordMatches.stream().distinct().collect(Collectors.toList());
        result.setKeywordMatches(keywordMatches);
        
        if (!keywordMatches.isEmpty()) {
            log.debug("  ✓ 关键词匹配: {} 条", keywordMatches.size());
        }
        
        // 正则验证（如果有样例数据）
        List<RagRecord> regexMatches = new ArrayList<>();
        if (sampleData != null && !sampleData.isEmpty()) {
            regexMatches = ragDataService.validateBySampleData(sampleData);
            result.setRegexMatches(regexMatches);
            
            if (!regexMatches.isEmpty()) {
                log.debug("  ✓ 正则匹配: {} 条 (样例数据验证)", regexMatches.size());
            }
        }
        
        // 向量语义检索（兜底）
        String searchQuery = buildSearchQuery(columnName, columnComment);
        SearchRequest searchRequest = SearchRequest.builder()
                .query(searchQuery)
                .topK(vectorTopK)
                .similarityThreshold(vectorThreshold)
                .build();
        List<Document> vectorMatches = vectorStore.similaritySearch(searchRequest);
        result.setVectorMatches(vectorMatches);
        
        if (!vectorMatches.isEmpty()) {
            log.debug("  ✓ 向量匹配: {} 条", vectorMatches.size());
        }
        
        // 优先级 正则 > 关键词 > 向量
        RagRecord bestMatch = selectBestMatch(keywordMatches, regexMatches, columnName, columnComment);
        result.setBestMatch(bestMatch);
        
        if (bestMatch != null) {
            // 判断匹配方法
            if (regexMatches.contains(bestMatch)) {
                result.setMatchMethod("regex");
            } else if (keywordMatches.contains(bestMatch)) {
                result.setMatchMethod("keyword");
            } else {
                result.setMatchMethod("vector");
            }
            
            // 应用条件规则判断级别
            Map<String, Object> context = new HashMap<>();
            context.put("tableColumns", tableColumns);
            Integer determinedLevel = ragDataService.determineLevel(bestMatch, context);
            result.setDeterminedLevel(determinedLevel);
        }
        
        return result;
    }
    
    /**
     * 构建搜索查询
     */
    private String buildSearchQuery(String columnName, String columnComment) {
        StringBuilder query = new StringBuilder();
        query.append("字段名: ").append(columnName != null ? columnName : "");
        if (columnComment != null && !columnComment.isEmpty()) {
            query.append(", 注释: ").append(columnComment);
        }
        return query.toString();
    }
    
    /**
     * 选择最佳匹配
     */
    private RagRecord selectBestMatch(List<RagRecord> keywordMatches, 
                                     List<RagRecord> regexMatches,
                                     String columnName,
                                     String columnComment) {
        // 优先使用正则匹配的结果（最准确）
        if (!regexMatches.isEmpty()) {
            return regexMatches.get(0);
        }
        
        // 其次使用关键词匹配，并进行综合评分
        if (!keywordMatches.isEmpty()) {
            List<RagRecord> refinedMatches = ragDataService.matchFieldName(columnName, columnComment);
            if (!refinedMatches.isEmpty()) {
                return refinedMatches.get(0);
            }
            return keywordMatches.get(0);
        }
        
        return null;
    }
    
    /**
     * 格式化输出结果
     */
    private String formatResults(List<EnhancedResult> results) {
        StringBuilder formatted = new StringBuilder();
        formatted.append("=== RAG 检索结果 ===\n\n");
        
        int matchedCount = 0;
        
        for (EnhancedResult result : results) {
            formatted.append("字段: ").append(result.getColumnName());
            if (result.getColumnComment() != null && !result.getColumnComment().isEmpty()) {
                formatted.append(" (").append(result.getColumnComment()).append(")");
            }
            formatted.append("\n");
            
            if (result.getBestMatch() != null) {
                matchedCount++;
                RagRecord match = result.getBestMatch();
                
                formatted.append("- 匹配方式: ").append(getMatchMethodDesc(result.getMatchMethod())).append("\n");
                formatted.append("- 匹配定义: ").append(match.getField().getName()).append("\n");
                formatted.append("- 分类路径: ").append(String.join(" > ", match.getTaxonomy().getPath())).append("\n");
                formatted.append("- 目标类别: ").append(match.getTaxonomy().getTargetCategory()).append("\n");
                
                // 显示确定的级别
                if (result.getDeterminedLevel() != null) {
                    formatted.append("- 数据级别: ").append(result.getDeterminedLevel()).append("级");
                    
                    // 如果级别与默认级别不同，说明应用了条件规则
                    if (!result.getDeterminedLevel().equals(match.getLevel().getDefaultLevel())) {
                        formatted.append(" (条件规则)");
                    }
                    formatted.append("\n");
                } else {
                    formatted.append("- 默认级别: ").append(match.getLevel().getDefaultLevel()).append("级\n");
                }
                
                // 添加条件规则
                if (match.getLevel().getConditions() != null && !match.getLevel().getConditions().isEmpty()) {
                    formatted.append("- 条件规则:\n");
                    for (RagRecord.Condition condition : match.getLevel().getConditions()) {
                        formatted.append("  * ").append(condition.getRationale()).append("\n");
                    }
                }
                
                // 添加规则
                if (match.getRules() != null && !match.getRules().isEmpty()) {
                    formatted.append("- 分级规则: ");
                    // 只显示前2条规则，避免过长
                    int ruleCount = Math.min(2, match.getRules().size());
                    for (int i = 0; i < ruleCount; i++) {
                        if (i > 0) formatted.append("; ");
                        formatted.append(match.getRules().get(i));
                    }
                    if (match.getRules().size() > 2) {
                        formatted.append("...");
                    }
                    formatted.append("\n");
                }
                
                // PII标签
                if (match.getPiiTags() != null && !match.getPiiTags().isEmpty()) {
                    formatted.append("- PII标签: ").append(String.join(", ", match.getPiiTags())).append("\n");
                }
                
                // 完整描述（用于LLM参考）
                formatted.append("- 详细描述: ").append(match.getText()).append("\n");
                
            } else {
                formatted.append("- ⚠️ 未找到匹配（建议人工复核）\n");
            }
            
            formatted.append("\n");
        }
        
        formatted.insert(0, String.format("检索统计: 共 %d 个字段，成功匹配 %d 个，未匹配 %d 个\n\n", 
            results.size(), matchedCount, results.size() - matchedCount));
        
        return formatted.toString();
    }
    
    /**
     * 获取匹配方法描述
     */
    private String getMatchMethodDesc(String method) {
        switch (method) {
            case "exact": return "字段名精确匹配 ✓✓✓";
            case "regex": return "正则表达式验证 ✓✓";
            case "keyword": return "关键词匹配 ✓";
            case "vector": return "向量语义检索";
            default: return "未知";
        }
    }
    
    /**
     * 增强检索结果
     */
    @Data
    private static class EnhancedResult {
        private String columnName;
        private String columnComment;
        
        /**
         * 字段名精确匹配
         */
        private RagRecord exactMatch;
        
        /**
         * 关键词匹配结果
         */
        private List<RagRecord> keywordMatches;
        
        /**
         * 正则匹配结果
         */
        private List<RagRecord> regexMatches;
        
        /**
         * 向量匹配结果
         */
        private List<Document> vectorMatches;
        
        /**
         * 最佳匹配
         */
        private RagRecord bestMatch;
        
        /**
         * 匹配方法：exact/regex/keyword/vector
         */
        private String matchMethod;
        
        /**
         * 确定的级别（应用条件规则后）
         */
        private Integer determinedLevel;
    }
}

