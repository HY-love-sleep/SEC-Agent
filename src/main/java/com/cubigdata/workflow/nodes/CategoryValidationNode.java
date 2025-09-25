package com.cubigdata.workflow.nodes;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.util.*;

/**
 * 类别验证节点 - 验证并修正分类名称
 * @author yHong
 * @version 1.0
 * @since 2025/9/23 19:35
 */
@Slf4j
public class CategoryValidationNode implements NodeAction {

    private final ObjectMapper objectMapper;

    public CategoryValidationNode(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public Map<String, Object> apply(OverAllState state) throws Exception {
        Object llmResultObj = state.value("llmResult").orElse(null);
        Object categoryObj = state.value("category").orElse(null);

        log.info("开始验证分类名称有效性...");

        Map<String, Object> result = new HashMap<>();

        try {
            List<String> validClassifications = parseCategoryList(categoryObj);

            JsonNode jsonNode;

            if (llmResultObj instanceof String) {
                String jsonString = (String) llmResultObj;
                log.info("LLM结果类型: 字符串");

                try {
                    jsonNode = objectMapper.readTree(jsonString);
                } catch (Exception e) {
                    log.warn("字符串不是有效JSON，尝试作为Java对象处理");
                    jsonNode = objectMapper.valueToTree(jsonString);
                }
            } else {
                log.info("LLM结果类型: 对象");
                jsonNode = objectMapper.valueToTree(llmResultObj);
            }

            String correctedJson = validateAndCorrectClassifications(jsonNode, validClassifications);

            result.put("corrected_result", correctedJson);

            log.info("分类验证完成");

        } catch (Exception e) {
            log.error("分类验证失败", e);
            result.put("incorrected_result", llmResultObj != null ? llmResultObj.toString() : "{}");
        }

        return result;
    }

    private List<String> parseCategoryList(Object categoryObj) {
        try {
            List<String> categories = new ArrayList<>();

            if (categoryObj instanceof List<?> categoryList) {
                for (Object item : categoryList) {
                    categories.add(item.toString());
                }
            } else if (categoryObj instanceof String categoryStr) {
                JsonNode categoryNode = objectMapper.readTree(categoryStr);
                if (categoryNode.isArray()) {
                    for (JsonNode item : categoryNode) {
                        categories.add(item.asText());
                    }
                }
            }

            return categories;
        } catch (Exception e) {
            log.warn("解析category失败，使用默认列表", e);
            // todo: 和产品确认， 补充默认分类别表or根据行业场景， 区分默认分类列表
            return Arrays.asList(
                "用户相关数据", "企业自身数据", "网络身份标识", "用户基本资料",
                "用户使用习惯和行为分析数据", "用户上网行为相关统计分析数据"
            );
        }
    }

    private String validateAndCorrectClassifications(JsonNode jsonNode, List<String> validClassifications) throws JsonProcessingException {
        Map<String, Object> dataMap = objectMapper.convertValue(jsonNode, Map.class);
        
        StringBuilder message = new StringBuilder();

        if (dataMap.containsKey("tableClassifications")) {
            String tableClass = (String) dataMap.get("tableClassifications");
            if (!validClassifications.contains(tableClass)) {
                String correctedClass = findMostSimilar(tableClass, validClassifications);
                dataMap.put("tableClassifications", correctedClass);
                message.append("表分类已修正: ").append(tableClass).append(" -> ").append(correctedClass).append("; ");
            }
        }

        if (dataMap.containsKey("columnInfoList")) {
            List<Map<String, Object>> columnInfoList = (List<Map<String, Object>>) dataMap.get("columnInfoList");
            for (Map<String, Object> columnInfo : columnInfoList) {
                if (columnInfo.containsKey("columnClassifications")) {
                    String columnClass = (String) columnInfo.get("columnClassifications");
                    if (!validClassifications.contains(columnClass)) {
                        String correctedClass = findMostSimilar(columnClass, validClassifications);
                        columnInfo.put("columnClassifications", correctedClass);
                        message.append("字段分类已修正: ").append(columnClass).append(" -> ").append(correctedClass).append("; ");
                    }
                }
            }
        }

        wrapClassificationsInList(dataMap);
        
        return objectMapper.writeValueAsString(dataMap);
    }

    // todo: 简单的相似度匹配 - 可以后续优化为向量相似度 (考虑相似度匹配接口， 新增字段级别的相似度结果)
    private String findMostSimilar(String target, List<String> validClassifications) {

        String bestMatch = validClassifications.get(0);
        int maxSimilarity = 0;
        
        for (String valid : validClassifications) {
            int similarity = calculateSimilarity(target, valid);
            if (similarity > maxSimilarity) {
                maxSimilarity = similarity;
                bestMatch = valid;
            }
        }
        
        return bestMatch;
    }

    private int calculateSimilarity(String s1, String s2) {
        int matches = 0;
        int minLength = Math.min(s1.length(), s2.length());
        
        for (int i = 0; i < minLength; i++) {
            if (s1.charAt(i) == s2.charAt(i)) {
                matches++;
            }
        }
        
        return matches;
    }

    private void wrapClassificationsInList(Map<String, Object> dataMap) {
        if (dataMap.containsKey("tableClassifications")) {
            Object tableClass = dataMap.get("tableClassifications");
            if (!(tableClass instanceof List)) {
                dataMap.put("tableClassifications", Arrays.asList(tableClass));
            }
        }
        
        if (dataMap.containsKey("columnInfoList")) {
            List<Map<String, Object>> columnInfoList = (List<Map<String, Object>>) dataMap.get("columnInfoList");
            for (Map<String, Object> columnInfo : columnInfoList) {
                if (columnInfo.containsKey("columnClassifications")) {
                    Object columnClass = columnInfo.get("columnClassifications");
                    if (!(columnClass instanceof List)) {
                        columnInfo.put("columnClassifications", Arrays.asList(columnClass));
                    }
                }
            }
        }
    }
}
