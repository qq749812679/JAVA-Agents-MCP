package com.enterprise.ai.core.langgraph;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * LangGraph - Agent执行流程图实现
 * 负责定义和执行基于图的Agent工作流
 */
public class LangGraph<S, A> {
    private static final Logger logger = LoggerFactory.getLogger(LangGraph.class);
    
    // 节点映射: 节点名 -> 节点函数
    private final Map<String, Function<S, A>> nodes = new HashMap<>();
    
    // 边映射: 源节点 -> (条件函数 -> 目标节点)
    private final Map<String, List<Edge<S>>> edges = new HashMap<>();
    
    // 入口节点
    private String entryNode;
    
    // 结束节点列表
    private final Set<String> exitNodes = new HashSet<>();
    
    // 运行时状态存储
    private final Map<String, Object> runtimeState = new ConcurrentHashMap<>();
    
    /**
     * 添加节点
     */
    public LangGraph<S, A> addNode(String nodeName, Function<S, A> nodeFunction) {
        nodes.put(nodeName, nodeFunction);
        logger.debug("Added node: {}", nodeName);
        return this;
    }
    
    /**
     * 设置入口节点
     */
    public LangGraph<S, A> setEntryPoint(String nodeName) {
        if (!nodes.containsKey(nodeName)) {
            throw new IllegalArgumentException("Entry node '" + nodeName + "' does not exist");
        }
        this.entryNode = nodeName;
        logger.debug("Set entry point to: {}", nodeName);
        return this;
    }
    
    /**
     * 添加结束节点
     */
    public LangGraph<S, A> addExitNode(String nodeName) {
        if (!nodes.containsKey(nodeName)) {
            throw new IllegalArgumentException("Exit node '" + nodeName + "' does not exist");
        }
        exitNodes.add(nodeName);
        logger.debug("Added exit node: {}", nodeName);
        return this;
    }
    
    /**
     * 添加边
     */
    public LangGraph<S, A> addEdge(String fromNode, String toNode) {
        return addConditionalEdge(fromNode, toNode, state -> true);
    }
    
    /**
     * 添加条件边
     */
    public LangGraph<S, A> addConditionalEdge(String fromNode, String toNode, Function<S, Boolean> condition) {
        validateNodes(fromNode, toNode);
        
        if (!edges.containsKey(fromNode)) {
            edges.put(fromNode, new ArrayList<>());
        }
        
        edges.get(fromNode).add(new Edge<>(toNode, condition));
        logger.debug("Added edge: {} -> {}", fromNode, toNode);
        return this;
    }
    
    /**
     * 验证节点是否存在
     */
    private void validateNodes(String fromNode, String toNode) {
        if (!nodes.containsKey(fromNode)) {
            throw new IllegalArgumentException("Source node '" + fromNode + "' does not exist");
        }
        if (!nodes.containsKey(toNode)) {
            throw new IllegalArgumentException("Target node '" + toNode + "' does not exist");
        }
    }
    
    /**
     * 执行图
     */
    public GraphResult<A> execute(S initialState) {
        if (entryNode == null) {
            throw new IllegalStateException("Entry point not set");
        }
        
        // 清除之前的运行时状态
        runtimeState.clear();
        
        String currentNode = entryNode;
        List<String> executionPath = new ArrayList<>();
        Map<String, A> nodeOutputs = new HashMap<>();
        
        try {
            // 执行节点，直到到达出口节点
            while (!exitNodes.contains(currentNode)) {
                logger.debug("Executing node: {}", currentNode);
                executionPath.add(currentNode);
                
                // 执行当前节点
                Function<S, A> nodeFunction = nodes.get(currentNode);
                A output = nodeFunction.apply(initialState);
                nodeOutputs.put(currentNode, output);
                
                // 确定下一个节点
                String nextNode = findNextNode(currentNode, initialState);
                
                if (nextNode == null) {
                    logger.warn("No valid transition from node: {}", currentNode);
                    break;
                }
                
                currentNode = nextNode;
            }
            
            // 如果到达了出口节点，执行它
            if (exitNodes.contains(currentNode)) {
                logger.debug("Executing exit node: {}", currentNode);
                executionPath.add(currentNode);
                
                Function<S, A> nodeFunction = nodes.get(currentNode);
                A output = nodeFunction.apply(initialState);
                nodeOutputs.put(currentNode, output);
            }
            
            // 成功完成
            return new GraphResult<>(
                    true,
                    executionPath,
                    nodeOutputs,
                    null
            );
        } catch (Exception e) {
            logger.error("Error executing graph: {}", e.getMessage(), e);
            
            // 执行失败
            return new GraphResult<>(
                    false,
                    executionPath,
                    nodeOutputs,
                    e.getMessage()
            );
        }
    }
    
    /**
     * 查找下一个要执行的节点
     */
    private String findNextNode(String currentNode, S state) {
        if (!edges.containsKey(currentNode)) {
            return null;
        }
        
        // 评估所有边的条件，选择第一个满足条件的目标节点
        for (Edge<S> edge : edges.get(currentNode)) {
            if (edge.condition.apply(state)) {
                return edge.targetNode;
            }
        }
        
        return null;
    }
    
    /**
     * 获取所有节点
     */
    public Set<String> getNodes() {
        return Collections.unmodifiableSet(nodes.keySet());
    }
    
    /**
     * 设置运行时状态
     */
    public void setState(String key, Object value) {
        runtimeState.put(key, value);
    }
    
    /**
     * 获取运行时状态
     */
    @SuppressWarnings("unchecked")
    public <T> T getState(String key) {
        return (T) runtimeState.get(key);
    }
    
    /**
     * 内部类: 边的表示
     */
    private static class Edge<S> {
        final String targetNode;
        final Function<S, Boolean> condition;
        
        Edge(String targetNode, Function<S, Boolean> condition) {
            this.targetNode = targetNode;
            this.condition = condition;
        }
    }
    
    /**
     * 图执行结果类
     */
    public static class GraphResult<A> {
        private final boolean success;
        private final List<String> executionPath;
        private final Map<String, A> nodeOutputs;
        private final String errorMessage;
        
        public GraphResult(boolean success, List<String> executionPath, Map<String, A> nodeOutputs, String errorMessage) {
            this.success = success;
            this.executionPath = executionPath;
            this.nodeOutputs = nodeOutputs;
            this.errorMessage = errorMessage;
        }
        
        public boolean isSuccess() {
            return success;
        }
        
        public List<String> getExecutionPath() {
            return Collections.unmodifiableList(executionPath);
        }
        
        public Map<String, A> getNodeOutputs() {
            return Collections.unmodifiableMap(nodeOutputs);
        }
        
        public String getErrorMessage() {
            return errorMessage;
        }
        
        public A getLastOutput() {
            if (executionPath.isEmpty()) {
                return null;
            }
            return nodeOutputs.get(executionPath.get(executionPath.size() - 1));
        }
    }
} 