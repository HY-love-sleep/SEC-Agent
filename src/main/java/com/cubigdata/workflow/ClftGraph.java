package com.cubigdata.workflow;

import com.alibaba.cloud.ai.graph.GraphRepresentation;
import com.alibaba.cloud.ai.graph.KeyStrategy;
import com.alibaba.cloud.ai.graph.KeyStrategyFactory;
import com.alibaba.cloud.ai.graph.StateGraph;
import com.alibaba.cloud.ai.graph.action.AsyncEdgeAction;
import com.alibaba.cloud.ai.graph.exception.GraphStateException;
import com.alibaba.cloud.ai.graph.node.KnowledgeRetrievalNode;
import com.alibaba.cloud.ai.graph.state.strategy.ReplaceStrategy;
import com.cubigdata.workflow.dispatcher.StructuredValidationDispatcher;
import com.cubigdata.workflow.nodes.CategoryValidationNode;
import com.cubigdata.workflow.nodes.ClassificationLLMNode;
import com.cubigdata.workflow.nodes.SimilarityMatchNode;
import com.cubigdata.workflow.nodes.StructuredValidationNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

import static com.alibaba.cloud.ai.graph.StateGraph.END;
import static com.alibaba.cloud.ai.graph.StateGraph.START;
import static com.alibaba.cloud.ai.graph.action.AsyncNodeAction.node_async;

/**
 * @author yHong
 * @version 1.0
 * @since 2025/9/23 17:12
 */
@Configuration
@Slf4j
public class ClftGraph {
    private final ClassificationLLMNode classificationLLMNode;
    private final SimilarityMatchNode similarityMatchNode;
    private final KnowledgeRetrievalNode knowledgeRetrievalNode;
    private final CategoryValidationNode categoryValidationNode;
    private final StructuredValidationNode structuredValidationNode;

    public ClftGraph(ClassificationLLMNode classificationLLMNode, SimilarityMatchNode similarityMatchNode, KnowledgeRetrievalNode knowledgeRetrievalNode, CategoryValidationNode categoryValidationNode, StructuredValidationNode structuredValidationNode) {
        this.classificationLLMNode = classificationLLMNode;
        this.similarityMatchNode = similarityMatchNode;
        this.knowledgeRetrievalNode = knowledgeRetrievalNode;
        this.categoryValidationNode = categoryValidationNode;
        this.structuredValidationNode = structuredValidationNode;
    }

    @Bean
    public StateGraph secGraph(ChatClient.Builder chatClientBuilder, @Qualifier("classificationVectorStore") VectorStore classificationVectorStore) throws GraphStateException {
        KeyStrategyFactory keyStrategyFactory = () -> {
            HashMap<String, KeyStrategy> keyStrategyHashMap = new HashMap<>();
            keyStrategyHashMap.put("query", new ReplaceStrategy());
            keyStrategyHashMap.put("category", new ReplaceStrategy());
            keyStrategyHashMap.put("retrievedDocs", new ReplaceStrategy());
            keyStrategyHashMap.put("similarityMatchResult", new ReplaceStrategy());
            keyStrategyHashMap.put("llmResult", new ReplaceStrategy());
            keyStrategyHashMap.put("corrected_result", new ReplaceStrategy());
            keyStrategyHashMap.put("incorrected_result", new ReplaceStrategy());
            keyStrategyHashMap.put("is_validate", new ReplaceStrategy());
            return keyStrategyHashMap;
        };

        StateGraph stateGraph = new StateGraph(keyStrategyFactory)
                .addNode("similarityMatch", node_async(similarityMatchNode))
                .addNode("knowledgeRetrieval", node_async(knowledgeRetrievalNode))
                .addNode("classification", node_async(classificationLLMNode))
                .addNode("categoryValidation", node_async(categoryValidationNode))
                .addNode("structuredValidation", node_async(structuredValidationNode))
                .addEdge(START, "similarityMatch")
                .addEdge(START, "knowledgeRetrieval")
                .addEdge("similarityMatch", "classification")
                .addEdge("knowledgeRetrieval", "classification")
                .addEdge("classification", "structuredValidation")
                .addConditionalEdges("structuredValidation",  AsyncEdgeAction.edge_async(new StructuredValidationDispatcher()), Map.of("yes", "categoryValidation", "no", "classification"))
                .addEdge("categoryValidation", END);
        GraphRepresentation representation = stateGraph.getGraph(GraphRepresentation.Type.PLANTUML,
                "ClassifyLevel UML Flow");
        log.info("\n=== ClassifyLevel UML Flow ===");
        log.info(representation.content());
        log.info("==================================\n");

        return stateGraph;
    }
}
