package com.citacita.service;

import com.citacita.dto.ResumeAnalysisResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

@Service
@Slf4j
public class ResumeRagStore {
    
    // 生产环境建议用Redis替代
    private final Map<String, ResumeAnalysisResult> analysisResults = new ConcurrentHashMap<>();
    
    public void storeAnalysisResult(ResumeAnalysisResult result) {
        if (result == null || result.getFileName() == null) {
            log.warn("无效的分析结果");
            return;
        }
        
        analysisResults.put(result.getFileName(), result);
        log.info("✅ RAG存储: 文件={}, 评分={}", result.getFileName(), result.getQualityScore());
    }
    
    public ResumeAnalysisResult getAnalysisResult(String fileName) {
        return analysisResults.get(fileName);
    }
    
    public boolean hasAnalysisResult(String fileName) {
        return analysisResults.containsKey(fileName);
    }
}