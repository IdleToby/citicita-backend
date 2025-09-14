package com.citacita.service;

import com.citacita.entity.MascoJob;
import com.citacita.mapper.MascoJobMapper;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class MascoJobDatabaseService implements InitializingBean {

    @Autowired
    private MascoJobMapper mascoJobMapper;

    // 缓存和索引
    private Map<String, MascoJob> jobCache;
    private Map<String, Set<String>> searchIndex;
    private Map<String, Set<String>> majorGroupIndex;
    private boolean cacheInitialized = false;

    // 语言检测
    private final Pattern chinesePattern = Pattern.compile("[\\u4e00-\\u9fff]+");
    private final Pattern englishPattern = Pattern.compile("[a-zA-Z]+");

    @Override
    public void afterPropertiesSet() throws Exception {
        try {
            System.out.println("开始初始化MASCO工作数据缓存...");
            loadAllJobsToCache();
            buildSearchIndexes();
            System.out.println("MASCO工作数据缓存初始化完成，共 " + jobCache.size() + " 条记录");
            cacheInitialized = true;
        } catch (Exception e) {
            System.err.println("初始化MASCO工作数据缓存失败: " + e.getMessage());
            e.printStackTrace();
            cacheInitialized = false;
        }
    }

    /**
     * 加载所有工作数据到缓存
     */
    private void loadAllJobsToCache() {
        try {
            // 使用你现有的selectAll方法
            List<MascoJob> allJobs = mascoJobMapper.selectAll();
            
            jobCache = new ConcurrentHashMap<>();
            for (MascoJob job : allJobs) {
                if (job.getUnitGroupCode() != null && !job.getUnitGroupCode().trim().isEmpty()) {
                    jobCache.put(job.getUnitGroupCode(), job);
                }
            }
            
            System.out.println("成功加载 " + jobCache.size() + " 条工作记录到缓存");
        } catch (Exception e) {
            System.err.println("加载工作数据到缓存失败: " + e.getMessage());
            jobCache = new ConcurrentHashMap<>();
            throw e;
        }
    }

    /**
     * 构建搜索索引
     */
    private void buildSearchIndexes() {
        searchIndex = new ConcurrentHashMap<>();
        majorGroupIndex = new ConcurrentHashMap<>();

        for (MascoJob job : jobCache.values()) {
            String jobCode = job.getUnitGroupCode();
            
            // 构建多语言搜索索引
            buildJobSearchIndex(job, jobCode);
            
            // 构建专业组索引
            if (job.getMajorGroupCode() != null) {
                majorGroupIndex.computeIfAbsent(job.getMajorGroupCode(), k -> new HashSet<>())
                    .add(jobCode);
            }
        }
        
        System.out.println("搜索索引构建完成，索引词条: " + searchIndex.size());
    }

    /**
     * 构建工作搜索索引
     */
    private void buildJobSearchIndex(MascoJob job, String jobCode) {
        // 英文索引
        addToSearchIndex(job.getUnitGroupTitle(), jobCode);
        addToSearchIndex(job.getUnitGroupDescription(), jobCode);
        addToSearchIndex(job.getTasksInclude(), jobCode);
        addToSearchIndex(job.getExamples(), jobCode);
        
        // 中文索引
        addToSearchIndex(job.getUnitGroupTitleChinese(), jobCode);
        addToSearchIndex(job.getUnitGroupDescriptionChinese(), jobCode);
        addToSearchIndex(job.getTasksIncludeChinese(), jobCode);
        addToSearchIndex(job.getExamplesChinese(), jobCode);
        
        // 马来文索引
        addToSearchIndex(job.getUnitGroupTitleMalay(), jobCode);
        addToSearchIndex(job.getUnitGroupDescriptionMalay(), jobCode);
        addToSearchIndex(job.getTasksIncludeMalay(), jobCode);
        addToSearchIndex(job.getExamplesMalay(), jobCode);
        
        // 层级标题索引
        addToSearchIndex(job.getMajorGroupTitle(), jobCode);
        addToSearchIndex(job.getSubMajorGroupTitle(), jobCode);
        addToSearchIndex(job.getMinorGroupTitle(), jobCode);
        
        // 工作代码索引
        addToSearchIndex(job.getUnitGroupCode(), jobCode);
        addToSearchIndex(job.getMajorGroupCode(), jobCode);
        addToSearchIndex(job.getSubMajorGroupCode(), jobCode);
        addToSearchIndex(job.getMinorGroupCode(), jobCode);
    }

    private void addToSearchIndex(String text, String jobCode) {
        if (text != null && !text.trim().isEmpty()) {
            // 整词索引
            String lowerText = text.toLowerCase().trim();
            searchIndex.computeIfAbsent(lowerText, k -> new HashSet<>()).add(jobCode);
            
            // 分词索引
            String[] words = lowerText
                .replaceAll("[^a-zA-Z\\u4e00-\\u9fff\\s]", " ")
                .split("\\s+");
            
            for (String word : words) {
                if (word.length() > 2) {
                    searchIndex.computeIfAbsent(word, k -> new HashSet<>()).add(jobCode);
                }
            }
        }
    }

    /**
     * 智能工作搜索
     */
    public Mono<List<MascoJob>> searchJobs(String query, String language, int limit) {
        return Mono.fromCallable(() -> {
            if (query == null || query.trim().isEmpty()) {
                return Collections.emptyList();
            }

            // 如果缓存未初始化，直接查询数据库
            if (!cacheInitialized) {
                return searchJobsInDatabase(query, language, limit);
            }

            String detectedLanguage = language != null ? language : detectLanguage(query);
            String lowerQuery = query.toLowerCase().trim();
            
            Map<String, Integer> jobScores = new HashMap<>();
            
            // 1. 精确匹配搜索
            performExactSearch(lowerQuery, jobScores);
            
            // 2. 部分匹配搜索
            performPartialSearch(lowerQuery, jobScores);
            
            // 3. 模糊匹配搜索
            performFuzzySearch(lowerQuery, jobScores);
            
            // 4. 按分数排序并返回结果
            return jobScores.entrySet().stream()
                .filter(entry -> entry.getValue() > 0)
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(limit)
                .map(entry -> jobCache.get(entry.getKey()))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        });
    }

    /**
     * 数据库直接搜索（当缓存未初始化时的降级方案）
     */
    private List<MascoJob> searchJobsInDatabase(String query, String language, int limit) {
        try {
            String detectedLanguage = language != null ? language : detectLanguage(query);
            String langCode = mapToDbLanguageCode(detectedLanguage);
            
            // 优先使用关键词搜索
            List<MascoJob> results = mascoJobMapper.searchJobsByKeywords(query, langCode, limit);
            
            // 如果结果不够，尝试标题描述搜索
            if (results.size() < limit) {
                List<MascoJob> additionalResults = mascoJobMapper.searchJobsByTitleAndDescription(query, langCode, limit - results.size());
                
                // 去重并合并结果
                Set<String> existingCodes = results.stream()
                    .map(MascoJob::getUnitGroupCode)
                    .collect(Collectors.toSet());
                
                additionalResults.stream()
                    .filter(job -> !existingCodes.contains(job.getUnitGroupCode()))
                    .forEach(results::add);
            }
            
            return results;
                
        } catch (Exception e) {
            System.err.println("数据库搜索失败: " + e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * 映射语言代码到数据库语言代码
     */
    private String mapToDbLanguageCode(String language) {
        if (language == null) return "en";
        
        switch (language.toLowerCase()) {
            case "chinese":
            case "zh-cn":
            case "zh":
                return "zh-CN";
            case "malay":
            case "ms":
                return "ms";
            default:
                return "en";
        }
    }

    private void performExactSearch(String query, Map<String, Integer> jobScores) {
        Set<String> exactMatches = searchIndex.get(query);
        if (exactMatches != null) {
            for (String jobCode : exactMatches) {
                jobScores.put(jobCode, jobScores.getOrDefault(jobCode, 0) + 10);
            }
        }
    }

    private void performPartialSearch(String query, Map<String, Integer> jobScores) {
        String[] queryWords = query.split("\\s+");
        for (String word : queryWords) {
            if (word.length() > 2) {
                Set<String> matches = searchIndex.get(word);
                if (matches != null) {
                    for (String jobCode : matches) {
                        jobScores.put(jobCode, jobScores.getOrDefault(jobCode, 0) + 5);
                    }
                }
            }
        }
    }

    private void performFuzzySearch(String query, Map<String, Integer> jobScores) {
        for (Map.Entry<String, Set<String>> entry : searchIndex.entrySet()) {
            String keyword = entry.getKey();
            if (keyword.contains(query) || query.contains(keyword)) {
                for (String jobCode : entry.getValue()) {
                    jobScores.put(jobCode, jobScores.getOrDefault(jobCode, 0) + 2);
                }
            }
        }
    }

    /**
     * 检测查询语言
     */
    private String detectLanguage(String query) {
        int chineseChars = 0;
        int englishChars = 0;
        
        if (chinesePattern.matcher(query).find()) {
            chineseChars = query.replaceAll("[^\\u4e00-\\u9fff]", "").length();
        }
        
        if (englishPattern.matcher(query).find()) {
            englishChars = query.replaceAll("[^a-zA-Z]", "").length();
        }
        
        if (chineseChars > englishChars) {
            return "chinese";
        } else if (englishChars > chineseChars) {
            return "en";
        } else {
            return chinesePattern.matcher(query).find() ? "chinese" : "en";
        }
    }

    /**
     * 根据工作代码获取工作详情
     */
    public Mono<Optional<MascoJob>> getJobByCode(String unitGroupCode, String language) {
        return Mono.fromCallable(() -> {
            try {
                // 优先从缓存获取
                if (cacheInitialized && jobCache.containsKey(unitGroupCode)) {
                    return Optional.of(jobCache.get(unitGroupCode));
                }
                
                // 从数据库获取
                String langCode = mapToDbLanguageCode(language);
                MascoJob job = mascoJobMapper.selectByUnitGroupCodeAndLang(langCode, unitGroupCode);
                return Optional.ofNullable(job);
            } catch (Exception e) {
                System.err.println("获取工作详情失败: " + e.getMessage());
                return Optional.empty();
            }
        });
    }

    /**
     * 根据专业组代码获取工作列表
     */
    public Mono<List<MascoJob>> getJobsByMajorGroupCode(String majorGroupCode, String language) {
        return Mono.fromCallable(() -> {
            try {
                String langCode = mapToDbLanguageCode(language);
                
                // 优先从缓存获取
                if (cacheInitialized && majorGroupIndex.containsKey(majorGroupCode)) {
                    Set<String> jobCodes = majorGroupIndex.get(majorGroupCode);
                    return jobCodes.stream()
                        .map(jobCache::get)
                        .filter(Objects::nonNull)
                        .collect(Collectors.toList());
                }
                
                // 从数据库获取
                return mascoJobMapper.selectByMajorGroupCodeAndLang(langCode, majorGroupCode);
            } catch (Exception e) {
                System.err.println("获取专业组工作失败: " + e.getMessage());
                return Collections.emptyList();
            }
        });
    }

    /**
     * 获取统计信息
     */
    public Mono<JobStatistics> getStatistics() {
        return Mono.fromCallable(() -> {
            if (cacheInitialized) {
                long totalJobs = jobCache.size();
                long majorGroupCount = jobCache.values().stream()
                    .map(MascoJob::getMajorGroupCode)
                    .filter(Objects::nonNull)
                    .distinct()
                    .count();
                
                long subMajorGroupCount = jobCache.values().stream()
                    .map(MascoJob::getSubMajorGroupCode)
                    .filter(Objects::nonNull)
                    .distinct()
                    .count();
                    
                return new JobStatistics(totalJobs, majorGroupCount, subMajorGroupCount);
            } else {
                // 从数据库统计
                try {
                    Long totalJobs = mascoJobMapper.countAllJobs();
                    List<String> majorGroups = mascoJobMapper.selectAllMajorGroupCodes();
                    return new JobStatistics(totalJobs != null ? totalJobs : 0, majorGroups.size(), 0);
                } catch (Exception e) {
                    return new JobStatistics(0, 0, 0);
                }
            }
        });
    }

    /**
     * 格式化工作信息为RAG响应
     */
    public String formatJobsForRAG(List<MascoJob> jobs, String language) {
        if (jobs.isEmpty()) {
            return language.equals("chinese") ? "未找到相关工作信息。" : "No relevant job information found.";
        }

        StringBuilder response = new StringBuilder();
        
        if (language.equals("chinese")) {
            response.append("根据您的查询，以下是相关的工作职位信息：\n\n");
        } else {
            response.append("Based on your query, here are the relevant job positions:\n\n");
        }

        for (int i = 0; i < jobs.size(); i++) {
            MascoJob job = jobs.get(i);
            
            if (language.equals("chinese")) {
                response.append(formatJobInChinese(job));
            } else {
                response.append(formatJobInEnglish(job));
            }
            
            if (i < jobs.size() - 1) {
                response.append("\n\n---\n\n");
            }
        }

        return response.toString();
    }

    private String formatJobInChinese(MascoJob job) {
        return String.format(
            "**%s** (代码: %s)\n\n" +
            "**职位描述：** %s\n\n" +
            "**主要任务：** %s\n\n" +
            "**职业示例：** %s\n\n" +
            "**技能等级：** %s\n\n" +
            "**所属类别：** %s > %s > %s",
            getValueOrDefault(job.getUnitGroupTitleChinese(), job.getUnitGroupTitle()),
            job.getUnitGroupCode(),
            getValueOrDefault(job.getUnitGroupDescriptionChinese(), job.getUnitGroupDescription()),
            getValueOrDefault(job.getTasksIncludeChinese(), job.getTasksInclude()),
            getValueOrDefault(job.getExamplesChinese(), job.getExamples()),
            getValueOrDefault(job.getSkillLevelChinese(), job.getSkillLevel()),
            getValueOrDefault(job.getMajorGroupTitleChinese(), job.getMajorGroupTitle()),
            getValueOrDefault(job.getSubMajorGroupTitleChinese(), job.getSubMajorGroupTitle()),
            getValueOrDefault(job.getMinorGroupTitleChinese(), job.getMinorGroupTitle())
        );
    }

    private String formatJobInEnglish(MascoJob job) {
        return String.format(
            "**%s** (Code: %s)\n\n" +
            "**Job Description:** %s\n\n" +
            "**Main Tasks:** %s\n\n" +
            "**Job Examples:** %s\n\n" +
            "**Skill Level:** %s\n\n" +
            "**Category:** %s > %s > %s",
            job.getUnitGroupTitle(),
            job.getUnitGroupCode(),
            job.getUnitGroupDescription(),
            job.getTasksInclude(),
            job.getExamples(),
            job.getSkillLevel(),
            job.getMajorGroupTitle(),
            job.getSubMajorGroupTitle(),
            job.getMinorGroupTitle()
        );
    }

    private String getValueOrDefault(String preferred, String fallback) {
        return (preferred != null && !preferred.trim().isEmpty()) ? preferred : 
               (fallback != null ? fallback : "");
    }

    /**
     * 刷新缓存
     */
    public Mono<String> refreshCache() {
        return Mono.fromCallable(() -> {
            try {
                loadAllJobsToCache();
                buildSearchIndexes();
                cacheInitialized = true;
                return "缓存刷新成功，共 " + jobCache.size() + " 条记录";
            } catch (Exception e) {
                System.err.println("刷新缓存失败: " + e.getMessage());
                return "缓存刷新失败: " + e.getMessage();
            }
        });
    }

    /**
     * 统计信息类
     */
    public static class JobStatistics {
        private final long totalJobs;
        private final long majorGroupCount;
        private final long subMajorGroupCount;

        public JobStatistics(long totalJobs, long majorGroupCount, long subMajorGroupCount) {
            this.totalJobs = totalJobs;
            this.majorGroupCount = majorGroupCount;
            this.subMajorGroupCount = subMajorGroupCount;
        }

        public long getTotalJobs() { return totalJobs; }
        public long getMajorGroupCount() { return majorGroupCount; }
        public long getSubMajorGroupCount() { return subMajorGroupCount; }

        @Override
        public String toString() {
            return String.format("JobStatistics{jobs=%d, majorGroups=%d, subMajorGroups=%d}", 
                totalJobs, majorGroupCount, subMajorGroupCount);
        }
    }
}