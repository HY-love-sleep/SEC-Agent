package com.cubigdata.workflow.nodes;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;

/**
 * 结构化验证节点 - 验证LLM输出的JSON结构
 * @author yHong
 * @version 1.0
 * @since 2025/9/23 19:30
 */
@Slf4j
public class StructuredValidationNode implements NodeAction {

    private final ObjectMapper objectMapper;

    public StructuredValidationNode(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public Map<String, Object> apply(OverAllState state) throws Exception {
        Object llmResultObj = state.value("llmResult").orElse(null);
        if (null == llmResultObj) return Map.of("is_validate", 0);
        String query = state.value("query").orElse("").toString();
        log.info("开始验证LLM输出结构: {}", llmResultObj);

        Map<String, Object> result = new HashMap<>();
        
        try {
            JsonNode jsonNode;

            if (llmResultObj instanceof String llmResultStr) {
                log.info("开始验证LLM输出结构(字符串): {}", llmResultStr);

                try {
                    jsonNode = objectMapper.readTree(llmResultStr);
                } catch (Exception e) {
                    log.warn("字符串不是有效JSON，尝试作为Java对象处理");
                    jsonNode = objectMapper.valueToTree(llmResultStr);
                }
            } else {
                log.info("开始验证LLM输出结构(对象): {}", llmResultObj);
                jsonNode = objectMapper.valueToTree(llmResultObj);
            }

            boolean isValid = validateStructure(jsonNode, query);
            
            result.put("is_validate", isValid ? 1 : 0);
            
            log.info("结构化验证结果: isValid={}", isValid);
            
        } catch (Exception e) {
            log.error("JSON解析失败", e);
            result.put("is_validate", 0);
        }
        
        return result;
    }

    // todo: 根据验证结果， 修改分类分级节点的Prompt
    private boolean validateStructure(JsonNode jsonNode, String query) {
        // 检查顶层必需字段
        String[] requiredTopLevelKeys = {
            "columnInfoList", "tbName", "tableClassifications", 
            "tableLevel", "tableReasoning"
        };
        
        for (String key : requiredTopLevelKeys) {
            if (!jsonNode.has(key)) {
                log.warn("缺少顶层字段: {}", key);
                return false;
            }
        }
        
        // 检查columnInfoList结构
        JsonNode columnInfoList = jsonNode.get("columnInfoList");
        if (!columnInfoList.isArray()) {
            log.warn("columnInfoList必须是数组");
            return false;
        }
        
        // 检查每个字段的必需属性
        String[] requiredColumnKeys = {
            "columnName", "columnClassifications", "columnLevel", "columnReasoning"
        };
        
        for (JsonNode columnInfo : columnInfoList) {
            for (String key : requiredColumnKeys) {
                if (!columnInfo.has(key)) {
                    log.warn("字段缺少必需属性: {}", key);
                    return false;
                }
            }
        }
        
        return true;
    }
}
