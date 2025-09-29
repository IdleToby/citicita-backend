package com.citacita.service;

import com.citacita.dto.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class ResumeRagService {
    
    private final ResumeRagStore ragStore;
    
    public String buildResumeContext(String fileName, String userQuery, String language) {
        log.info("🔍 构建RAG上下文: 文件={}, 查询={}", fileName, userQuery);
        
        ResumeAnalysisResult result = ragStore.getAnalysisResult(fileName);
        if (result == null) {
            log.warn("⚠️ 未找到分析结果: {}", fileName);
            return buildDefaultContext(fileName, language);
        }
        
        // 分析用户问题类型
        QueryType queryType = analyzeQueryType(userQuery);
        log.info("📊 问题类型: {}", queryType);
        
        return buildContextByType(result, queryType, language);
    }
    
    private QueryType analyzeQueryType(String query) {
        if (query == null || query.trim().isEmpty()) {
            return QueryType.GENERAL;
        }
        
        String lower = query.toLowerCase();
        
        if (containsAny(lower, "skill", "技能", "kemahiran", "技术")) return QueryType.SKILLS;
        if (containsAny(lower, "experience", "工作", "实习", "pengalaman", "经历")) return QueryType.EXPERIENCE;
        if (containsAny(lower, "education", "教育", "学历", "pendidikan", "大学")) return QueryType.EDUCATION;
        if (containsAny(lower, "format", "格式", "structure", "结构")) return QueryType.FORMAT;
        if (containsAny(lower, "improve", "改进", "优化", "建议", "better")) return QueryType.IMPROVEMENT;
        if (containsAny(lower, "contact", "联系", "email", "phone", "邮箱")) return QueryType.CONTACT;
        
        return QueryType.GENERAL;
    }
    
    private String buildContextByType(ResumeAnalysisResult result, QueryType type, String language) {
        StringBuilder context = new StringBuilder();
        
        context.append(buildBasicInfo(result, language)).append("\n\n");
        
        switch (type) {
            case SKILLS:
                context.append(buildSkillsContext(result, language));
                break;
            case EXPERIENCE:
                context.append(buildExperienceContext(result, language));
                break;
            case EDUCATION:
                context.append(buildEducationContext(result, language));
                break;
            case FORMAT:
                context.append(buildFormatContext(result, language));
                break;
            case IMPROVEMENT:
                context.append(buildImprovementContext(result, language));
                break;
            case CONTACT:
                context.append(buildContactContext(result, language));
                break;
            default:
                context.append(buildGeneralContext(result, language));
        }
        
        context.append("\n\n").append(buildRelevantSuggestions(result, type, language));
        context.append("\n\n").append(buildFooter(language));
        
        return context.toString();
    }
    
    private String buildBasicInfo(ResumeAnalysisResult result, String language) {
        switch (language) {
            case "zh-CN":
                return String.format("=== 简历分析概览 ===\n文件: %s\n评分: %d/100\n完整度: %.0f%%\n技能数: %d",
                    result.getFileName(), result.getQualityScore(), 
                    result.getStructure().getCompleteness() * 100,
                    result.getKeyInfo().getSkills().size());
            case "ms":
                return String.format("=== Gambaran Analisis ===\nFail: %s\nSkor: %d/100\nKelengkapan: %.0f%%",
                    result.getFileName(), result.getQualityScore(), 
                    result.getStructure().getCompleteness() * 100);
            default:
                return String.format("=== Analysis Overview ===\nFile: %s\nScore: %d/100\nCompleteness: %.0f%%",
                    result.getFileName(), result.getQualityScore(), 
                    result.getStructure().getCompleteness() * 100);
        }
    }
    
    private String buildSkillsContext(ResumeAnalysisResult result, String language) {
        ResumeKeyInfo keyInfo = result.getKeyInfo();
        
        switch (language) {
            case "zh-CN":
                StringBuilder zh = new StringBuilder("=== 技能分析 ===\n");
                zh.append("状态: ").append(result.getStructure().isHasSkills() ? "✅ 有" : "❌ 无").append("\n");
                zh.append("数量: ").append(keyInfo.getSkills().size()).append("个\n");
                if (!keyInfo.getSkills().isEmpty()) {
                    zh.append("技能列表:\n");
                    keyInfo.getSkills().forEach(s -> zh.append("- ").append(s).append("\n"));
                }
                return zh.toString();
            default:
                StringBuilder en = new StringBuilder("=== Skills Analysis ===\n");
                en.append("Status: ").append(result.getStructure().isHasSkills() ? "✅ Present" : "❌ Missing").append("\n");
                en.append("Count: ").append(keyInfo.getSkills().size()).append("\n");
                return en.toString();
        }
    }
    
    private String buildExperienceContext(ResumeAnalysisResult result, String language) {
        ResumeKeyInfo keyInfo = result.getKeyInfo();
        
        switch (language) {
            case "zh-CN":
                return String.format("=== 工作经验 ===\n状态: %s\n年限: %d年\n类型: %s\n实习: %d年",
                    result.getStructure().isHasExperience() ? "✅ 有" : "❌ 无",
                    keyInfo.getYearsOfExperience(), keyInfo.getWorkExperienceType(),
                    keyInfo.getInternshipYears());
            default:
                return String.format("=== Experience ===\nStatus: %s\nYears: %d\nType: %s",
                    result.getStructure().isHasExperience() ? "✅ Present" : "❌ Missing",
                    keyInfo.getYearsOfExperience(), keyInfo.getWorkExperienceType());
        }
    }
    
    private String buildEducationContext(ResumeAnalysisResult result, String language) {
        switch (language) {
            case "zh-CN":
                return String.format("=== 教育背景 ===\n状态: %s\n学历: %s",
                    result.getStructure().isHasEducation() ? "✅ 有" : "❌ 无",
                    result.getKeyInfo().getEducationLevel());
            default:
                return String.format("=== Education ===\nStatus: %s\nLevel: %s",
                    result.getStructure().isHasEducation() ? "✅ Present" : "❌ Missing",
                    result.getKeyInfo().getEducationLevel());
        }
    }
    
    private String buildFormatContext(ResumeAnalysisResult result, String language) {
        ResumeStructure structure = result.getStructure();
        
        switch (language) {
            case "zh-CN":
                StringBuilder zh = new StringBuilder("=== 格式分析 ===\n完整度详情:\n");
                structure.getCompletenessDetails().forEach(d -> zh.append("- ").append(d).append("\n"));
                zh.append("字数: ").append(structure.getWordCount());
                return zh.toString();
            default:
                return "=== Format Analysis ===\nWord count: " + structure.getWordCount();
        }
    }
    
    private String buildImprovementContext(ResumeAnalysisResult result, String language) {
        List<ResumeSuggestion> suggestions = result.getSuggestions();
        
        switch (language) {
            case "zh-CN":
                StringBuilder zh = new StringBuilder("=== 改进建议 ===\n");
                suggestions.stream().filter(s -> "high".equals(s.getPriority()))
                    .forEach(s -> zh.append("🔴 高: ").append(s.getMessage()).append("\n"));
                suggestions.stream().filter(s -> "medium".equals(s.getPriority()))
                    .forEach(s -> zh.append("🟡 中: ").append(s.getMessage()).append("\n"));
                return zh.toString();
            default:
                StringBuilder en = new StringBuilder("=== Suggestions ===\n");
                suggestions.forEach(s -> en.append("- ").append(s.getMessage()).append("\n"));
                return en.toString();
        }
    }
    
    private String buildContactContext(ResumeAnalysisResult result, String language) {
        ResumeKeyInfo keyInfo = result.getKeyInfo();
        
        switch (language) {
            case "zh-CN":
                return String.format("=== 联系信息 ===\n状态: %s\n邮箱: %d个\n电话: %d个",
                    result.getStructure().isHasContactInfo() ? "✅ 完整" : "❌ 缺失",
                    keyInfo.getEmails().size(), keyInfo.getPhones().size());
            default:
                return String.format("=== Contact Info ===\nStatus: %s\nEmails: %d\nPhones: %d",
                    result.getStructure().isHasContactInfo() ? "✅ Complete" : "❌ Missing",
                    keyInfo.getEmails().size(), keyInfo.getPhones().size());
        }
    }
    
    private String buildGeneralContext(ResumeAnalysisResult result, String language) {
        return buildSkillsContext(result, language) + "\n\n" + buildExperienceContext(result, language);
    }
    
    private String buildRelevantSuggestions(ResumeAnalysisResult result, QueryType type, String language) {
        List<ResumeSuggestion> relevant = result.getSuggestions().stream()
            .filter(s -> isRelevant(s, type))
            .limit(3)
            .collect(Collectors.toList());
        
        if (relevant.isEmpty()) return "";
        
        switch (language) {
            case "zh-CN":
                StringBuilder zh = new StringBuilder("=== 相关建议 ===\n");
                relevant.forEach(s -> zh.append("- ").append(s.getMessage()).append("\n"));
                return zh.toString();
            default:
                StringBuilder en = new StringBuilder("=== Relevant Suggestions ===\n");
                relevant.forEach(s -> en.append("- ").append(s.getMessage()).append("\n"));
                return en.toString();
        }
    }
    
    private boolean isRelevant(ResumeSuggestion s, QueryType type) {
        String msg = s.getMessage().toLowerCase();
        switch (type) {
            case SKILLS: return msg.contains("skill") || msg.contains("技能");
            case EXPERIENCE: return msg.contains("experience") || msg.contains("工作");
            case CONTACT: return msg.contains("contact") || msg.contains("联系");
            default: return true;
        }
    }
    
    private String buildFooter(String language) {
        switch (language) {
            case "zh-CN": return "请仅基于以上分析结果回答,不要编造信息。";
            case "ms": return "Sila jawab berdasarkan analisis di atas sahaja.";
            default: return "Please answer based only on the above analysis.";
        }
    }
    
    private String buildDefaultContext(String fileName, String language) {
        switch (language) {
            case "zh-CN": return "文件: " + fileName + "\n分析结果未找到,请重新上传。";
            case "ms": return "Fail: " + fileName + "\nAnalisis tidak dijumpai.";
            default: return "File: " + fileName + "\nAnalysis not found.";
        }
    }
    
    private boolean containsAny(String text, String... keywords) {
        for (String k : keywords) {
            if (text.contains(k.toLowerCase())) return true;
        }
        return false;
    }
    
    private enum QueryType {
        SKILLS, EXPERIENCE, EDUCATION, FORMAT, IMPROVEMENT, CONTACT, GENERAL
    }
}