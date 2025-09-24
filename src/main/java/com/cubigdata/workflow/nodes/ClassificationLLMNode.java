package com.cubigdata.workflow.nodes;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.vectorstore.VectorStore;

import java.util.HashMap;
import java.util.Map;

public class ClassificationLLMNode implements NodeAction {

    private final ChatClient chatClient;
    private final ObjectMapper mapper = new ObjectMapper();

    private final String queryKey;
    private final String categoryKey;
    private final String docsKey;
    private final String simMatchKey;
    private final String outputKey;

    private final String promptTemplate;

    public ClassificationLLMNode(ChatClient.Builder modelBuilder,
                                 VectorStore classificationVectorStore,
                                 String queryKey,
                                 String categoryKey,
                                 String docsKey,
                                 String simMatchKey,
                                 String outputKey,
                                 String promptTemplate) {
        this.chatClient = modelBuilder
                .defaultSystem(promptTemplate)
                .defaultAdvisors(
//                        RetrievalAugmentationAdvisor.builder()
//                                .documentRetriever(VectorStoreDocumentRetriever.builder()
//                                        .vectorStore(classificationVectorStore)
//                                        .topK(2)
//                                        .similarityThreshold(0.6)
//                                        .build())
//                                .build(),
                        new SimpleLoggerAdvisor())
                .build();
        this.queryKey = queryKey;
        this.categoryKey = categoryKey;
        this.docsKey = docsKey;
        this.simMatchKey = simMatchKey;
        this.outputKey = outputKey;
        this.promptTemplate = promptTemplate;
    }

    @Override
    public Map<String, Object> apply(OverAllState state) throws Exception {
        String query = mapper.writeValueAsString(state.value(queryKey).orElse(""));
        String category = mapper.writeValueAsString(state.value(categoryKey).orElse(""));
        String retrievedDocs = mapper.writeValueAsString(state.value(docsKey).orElse(""));
        String simMatch = mapper.writeValueAsString(state.value(simMatchKey).orElse(""));

        // 替换占位符
        String finalPrompt = promptTemplate
                .replace("{{#1752826684738.query#}}", query)
                .replace("{{#1752826684738.category#}}", category)
                .replace("{{#context#}}", retrievedDocs + "\n" + simMatch);

        ChatResponse response = chatClient.prompt(finalPrompt)
                .options(ChatOptions.builder()
                        .temperature(0.5)
                        .build())
                .call()
                .chatResponse();

        String raw = response.getResult().getOutput().getText();

        String json = extractJsonPayload(raw);

        if (json == null) {
            throw new RuntimeException("LLM 输出中未找到 JSON 内容: " + raw);
        }

        Map<String, Object> result;
        try {
            result = mapper.readValue(json, Map.class);
        } catch (Exception e) {
            throw new RuntimeException("LLM 输出不是合法 JSON: " + json, e);
        }

        Map<String, Object> updated = new HashMap<>();
        updated.put(outputKey, result);
        return updated;
    }

    private String extractJsonPayload(String text) {
        if (text == null) return null;
        String s = text.trim();

        if (s.startsWith("```")) {
            int firstNl = s.indexOf('\n');
            int lastFence = s.lastIndexOf("```");
            if (firstNl >= 0 && lastFence > firstNl) {
                s = s.substring(firstNl + 1, lastFence).trim();
            }
        }

        int objStart = s.indexOf('{');
        int arrStart = s.indexOf('[');
        int start = -1;
        char open = 0, close = 0;

        if (objStart >= 0 && (arrStart < 0 || objStart < arrStart)) {
            start = objStart; open = '{'; close = '}';
        } else if (arrStart >= 0) {
            start = arrStart; open = '['; close = ']';
        } else {
            return null;
        }

        int depth = 0;
        boolean inStr = false;
        char prev = 0;
        for (int i = start; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '"' && prev != '\\') inStr = !inStr;
            if (!inStr) {
                if (c == open) depth++;
                else if (c == close) {
                    depth--;
                    if (depth == 0) {
                        return s.substring(start, i + 1).trim();
                    }
                }
            }
            prev = c;
        }

        return null;
    }
}
