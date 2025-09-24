package com.cubigdata.service;

import com.alibaba.cloud.ai.graph.CompiledGraph;
import com.alibaba.cloud.ai.graph.KeyStrategy;
import com.alibaba.cloud.ai.graph.KeyStrategyFactory;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.agent.flow.agent.SequentialAgent;
import com.alibaba.cloud.ai.graph.state.strategy.ReplaceStrategy;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @author yHong
 * @version 1.0
 * @since 2025/9/17 17:05
 */
@Component
public class SecAgent {
    public CompiledGraph buildCompiledGraph(ChatModel chatModel, List<ToolCallback> tools) throws Exception {
        KeyStrategyFactory stateFactory = () -> {
            HashMap<String, KeyStrategy> keyStrategyHashMap = new HashMap<>();
            keyStrategyHashMap.put("input", new ReplaceStrategy());
            keyStrategyHashMap.put("dbName", new ReplaceStrategy());
            keyStrategyHashMap.put("clft_res", new ReplaceStrategy());
            return keyStrategyHashMap;
        };

        ReactAgent coltAgent = ReactAgent.builder()
                .name("colt_agent")
                .model(chatModel)
                .description("处理采集程序服务相关的业务流程")
                .inputKey("input")
                .instruction("""
                        你是数据采集专家， 负责处理用户对采集程序服务的相关请求， 并调用对应工具进行处理。
                        你需要：
                        1. 对于用户给定输入（包含dataSourceId、dataSourceType、dataSourceName、databaseCodes）， 首先新增一个采集任务, 调用-addCollectionTask；
                        2. 采集任务新增成功后， 根据数据库名称， 获取上一步新增的采集任务对应的CollectTaskId，调用-getPageOfCollectionTask进行过滤;
                        3. 根据上一步得到的CollectTaskId， 开启采集任务， 调用-openCollectionTask；
                        4. 采集任务开启后， 根据CollectTaskId， 执行采集任务， 调用-executeCollectionTask；
                        5. 采集任务完成后， 返回dbName供分类, 注意， 这里的dbName从用户输入中获取， 只返回dbName!

                        重要提示：
                        - executeCollectionTask会立即返回200， 但是采集任务会在后台跑；
                        - 你可以等待10S后再返回dbName
                        """)
                .maxIterations(10)
                .outputKey("dbName")
//                .tools(toolFilter(tools, List.of("addCollectionTask", "getPageOfCollectionTask", "openCollectionTask", "executeCollectionTask")))
                .tools(tools)
                .build();

        ReactAgent clftAgent = ReactAgent.builder()
                .name("clft_agent")
                .model(chatModel)
                .description("处理分类分级服务相关的业务流程")
                .inputKey("dbName")
                .instruction("""
                        你是分类分级专家， 负责处理用户对分类分级服务的相关请求， 并调用对应工具进行处理。
                        你需要：
                        1. 对元数据列表进行全量查询， 根据dbName筛选出对应的dbId， 调用-getMetaDataAllList;
                        2. 得到dbId后， 对这个数据库进行分类分级打标， 调用-executeClassifyLevel；
                        3. 打标完成后，根据dbName和tbName, 查询分类分级结果， 调用-getClassifyLevelResult；

                        重要提示：
                        - executeClassifyLevel会立即返回200， 但是采集任务会在后台跑；
                        - 你需要等待15s后再去查询分类分级结果； 或者重复调用getClassifyLevelResult， 直到返回结果！
                        - 调用getClassifyLevelResult时， position填写0；
                        """)
                .maxIterations(15)
                .outputKey("clft_res")
//                .tools(toolFilter(tools, List.of("getMetaDataAllList", "executeClassifyLevel", "getClassifyLevelResult")))
                .tools(tools)
                .build();

        SequentialAgent secAgent = SequentialAgent.builder()
                .name("sec_agent")
                .state(stateFactory)
                .description("数据安全分类分级安全助手")
//                .inputKey("input")
//                .outputKey("clft_res")
                .subAgents(List.of(coltAgent, clftAgent))
                .build();

        return secAgent.getAndCompileGraph();

    }

    private List<ToolCallback> toolFilter(List<ToolCallback> allTools, List<String> toolNames) {
        Set<String> targetNames = new HashSet<>(toolNames);
        return allTools.stream()
                .filter(tool -> targetNames.contains(tool.getToolDefinition().name()))
                .collect(Collectors.toList());
    }

}
