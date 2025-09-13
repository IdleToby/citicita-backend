package com.citacita.service;

import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
public class RAGService {

    /**
     * 模拟 RAG 检索 - 你需要替换成实际的检索逻辑
     */
    public Mono<String> retrieveRelevantContent(String query) {
        return Mono.fromCallable(() -> {
            try {
                // 模拟检索延迟
                Thread.sleep(100);
                
                // 简单的关键词匹配示例
                String lowerQuery = query.toLowerCase();
                
                if (lowerQuery.contains("job") || lowerQuery.contains("工作") || lowerQuery.contains("职位")) {
                    return """
                        根据我们的数据库，以下是相关的职位信息：
                        
                        1. 软件工程师 - 需要 Java、Spring Boot 技能
                        2. 数据分析师 - 需要 Python、SQL 技能  
                        3. 前端开发工程师 - 需要 Vue.js、React 技能
                        
                        这些职位的平均薪资在 8000-15000 之间。
                        """;
                }
                
                if (lowerQuery.contains("skill") || lowerQuery.contains("技能")) {
                    return """
                        热门技能排行：
                        1. Java/Spring Boot - 后端开发必备
                        2. JavaScript/Vue.js - 前端开发主流
                        3. Python/SQL - 数据分析核心
                        4. Docker/Kubernetes - 容器化部署
                        """;
                }
                
                if (lowerQuery.contains("salary") || lowerQuery.contains("薪资") || lowerQuery.contains("工资")) {
                    return """
                        薪资参考数据：
                        - 初级开发者：6000-8000
                        - 中级开发者：8000-12000  
                        - 高级开发者：12000-18000
                        - 技术专家：18000+
                        """;
                }
                
                // 默认返回通用信息
                return """
                    CitaCita 平台致力于为求职者和雇主提供精准的职位匹配服务。
                    我们有丰富的职位数据库和专业的职业规划建议。
                    """;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return "检索服务暂时不可用";
            }
        });
    }
}