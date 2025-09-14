package com.citacita.controller;

import com.citacita.entity.MascoJob;
import com.citacita.service.EnhancedFAQRAGService;
import com.citacita.service.MascoJobDatabaseService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Optional;

/**
 * 测试控制器 - 用于验证RAG和工作搜索功能
 * 这个控制器仅用于测试，生产环境可以删除
 */
@RestController
@RequestMapping("/api/test")
@CrossOrigin(origins = "*") // 开发环境允许跨域
public class JobRAGTestController {
    
    @Autowired
    private EnhancedFAQRAGService ragService;
    
    @Autowired
    private MascoJobDatabaseService jobService;
    
    /**
     * 测试完整的RAG查询（包括工作、FAQ、Grants）
     */
    @GetMapping("/rag")
    public Mono<String> testRAG(@RequestParam String query) {
        System.out.println("测试RAG查询: " + query);
        return ragService.retrieveRelevantContent(query)
            .doOnNext(result -> System.out.println("RAG结果长度: " + result.length()))
            .doOnError(error -> System.err.println("RAG查询错误: " + error.getMessage()));
    }
    
    /**
     * 测试直接工作搜索
     */
    @GetMapping("/jobs")
    public Mono<List<MascoJob>> testJobSearch(
            @RequestParam String query,
            @RequestParam(defaultValue = "en") String lang,
            @RequestParam(defaultValue = "5") int limit) {
        System.out.println("测试工作搜索: " + query + ", 语言: " + lang);
        return jobService.searchJobs(query, lang, limit)
            .doOnNext(jobs -> System.out.println("找到工作数量: " + jobs.size()))
            .doOnError(error -> System.err.println("工作搜索错误: " + error.getMessage()));
    }
    
    /**
     * 根据工作代码获取详情
     */
    @GetMapping("/job/{code}")
    public Mono<Optional<MascoJob>> getJobByCode(
            @PathVariable String code,
            @RequestParam(defaultValue = "en") String lang) {
        System.out.println("获取工作详情: " + code + ", 语言: " + lang);
        return jobService.getJobByCode(code, lang)
            .doOnNext(job -> System.out.println("工作详情存在: " + job.isPresent()))
            .doOnError(error -> System.err.println("获取工作详情错误: " + error.getMessage()));
    }
    
    /**
     * 获取专业组工作
     */
    @GetMapping("/major-group/{code}")
    public Mono<List<MascoJob>> getJobsByMajorGroup(
            @PathVariable String code,
            @RequestParam(defaultValue = "en") String lang) {
        System.out.println("获取专业组工作: " + code + ", 语言: " + lang);
        return jobService.getJobsByMajorGroupCode(code, lang)
            .doOnNext(jobs -> System.out.println("专业组工作数量: " + jobs.size()))
            .doOnError(error -> System.err.println("获取专业组工作错误: " + error.getMessage()));
    }
    
    /**
     * 获取统计信息
     */
    @GetMapping("/stats")
    public Mono<String> getStats() {
        System.out.println("获取统计信息");
        return jobService.getStatistics()
            .map(stats -> String.format("""
                数据库统计信息:
                - 总工作数量: %d
                - 主要组数量: %d
                - 子主要组数量: %d
                """, 
                stats.getTotalJobs(), 
                stats.getMajorGroupCount(), 
                stats.getSubMajorGroupCount()))
            .doOnNext(result -> System.out.println("统计信息获取成功"))
            .doOnError(error -> System.err.println("获取统计信息错误: " + error.getMessage()));
    }
    
    /**
     * 刷新缓存
     */
    @GetMapping("/refresh-cache")
    public Mono<String> refreshCache() {
        System.out.println("刷新缓存请求");
        return jobService.refreshCache()
            .doOnNext(result -> System.out.println("缓存刷新结果: " + result))
            .doOnError(error -> System.err.println("缓存刷新错误: " + error.getMessage()));
    }
    
    /**
     * 健康检查
     */
    @GetMapping("/health")
    public Mono<String> healthCheck() {
        return Mono.just("Job RAG服务运行正常 - " + java.time.LocalDateTime.now());
    }
    
    /**
     * 测试多语言查询
     */
    @GetMapping("/multilang")
    public Mono<String> testMultilanguage() {
        return Mono.just("""
            测试多语言查询建议：
            
            英文测试：
            /api/test/rag?query=software developer
            /api/test/rag?query=what jobs are available?
            
            中文测试：
            /api/test/rag?query=软件开发员
            /api/test/rag?query=有什么工作？
            
            工作代码测试：
            /api/test/rag?query=2111
            /api/test/job/2111?lang=zh-CN
            
            FAQ测试：
            /api/test/rag?query=AI简历检查器
            /api/test/rag?query=政府补助
            
            低相关性测试：
            /api/test/rag?query=今天天气怎么样
            """);
    }
}