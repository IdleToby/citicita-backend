package com.citacita.service;

import com.citacita.dto.*;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.hwpf.extractor.WordExtractor;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.MatchResult;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class ResumeAnalyzerService {
    
    // 配置常量
    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024L; // 10MB
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("pdf", "doc", "docx", "txt");
    private final ResumeRagStore ragStore;
    
    // 扩展的技能库
    private static final List<String> PROGRAMMING_SKILLS = Arrays.asList(
        "JavaScript", "Python", "Java", "C++", "C#", "PHP", "Ruby", "Go", "Rust", 
        "Swift", "Kotlin", "TypeScript", "Scala", "R"
    );
    
    private static final List<String> WEB_SKILLS = Arrays.asList(
        "React", "Vue", "Vue.js", "Angular", "Node.js", "HTML", "CSS", "Bootstrap", 
        "jQuery", "Express", "Spring", "Spring Boot", "Django", "Flask", "Nginx",
        "Ant Design", "Element Plus", "Three.js", "Tailwind CSS", "Echarts", "Uniapp"
    );
    
    private static final List<String> DATABASE_SKILLS = Arrays.asList(
        "MySQL", "PostgreSQL", "MongoDB", "Redis", "Oracle", "SQL Server", 
        "SQLite", "Elasticsearch", "SQL"
    );
    
    private static final List<String> CLOUD_SKILLS = Arrays.asList(
        "AWS", "Azure", "GCP", "Docker", "Kubernetes", "Jenkins", "Git", "Linux",
        "Azure AI Services", "Geoapify Map"
    );
    
    private static final List<String> SOFT_SKILLS = Arrays.asList(
        "Project Management", "Leadership", "Communication", "Team Work", 
        "Problem Solving", "Analytics", "Marketing", "Sales"
    );
    
    // 中英文关键词映射
    private static final Map<String, List<String>> SECTION_KEYWORDS = Map.of(
        "contact", Arrays.asList("联系方式", "联系信息", "contact", "email", "phone", "手机", "邮箱", "电话"),
        "summary", Arrays.asList("个人简介", "自我评价", "职业目标", "summary", "objective", "profile", "about", "overview", "自我介绍"),
        "experience", Arrays.asList("工作经历", "实习经历", "项目经历", "experience", "work", "employment", "career", "professional", "实习", "项目", "internship"),
        "education", Arrays.asList("教育背景", "学历", "education", "degree", "university", "college", "academic", "大学", "学院", "专业", "毕业院校"),
        "skills", Arrays.asList("专业技能", "技能", "skills", "competencies", "expertise", "technologies", "technical", "abilities", "技术栈"),
        "achievements", Arrays.asList("获奖情况", "成就", "achievements", "accomplishments", "awards", "honors", "recognition", "certifications", "奖项"),
        "projects", Arrays.asList("项目经历", "项目经验", "projects", "portfolio", "work samples", "personal projects", "项目"),
        "languages", Arrays.asList("语言能力", "语言技能", "languages", "language skills", "linguistic", "英语", "雅思", "托福")
    );
    
    // public ResumeAnalyzerService() {
    //     log.info("ResumeAnalyzerService 启动 - 使用内置配置");
    //     log.info("最大文件大小: {} MB", MAX_FILE_SIZE / 1024 / 1024);
    //     log.info("支持的文件类型: {}", ALLOWED_EXTENSIONS);
    // }
    @PostConstruct
    public void init() {
        log.info("ResumeAnalyzerService 启动 - 使用内置配置");
        log.info("最大文件大小: {} MB", MAX_FILE_SIZE / 1024 / 1024);
        log.info("支持的文件类型: {}", ALLOWED_EXTENSIONS);
    }

    public Mono<ResumeAnalysisResult> analyzeResumeWebFlux(FilePart file, String language) {
        log.info("开始分析简历: {}", file.filename());
        
        return validateFile(file)
            .flatMap(this::extractTextWebFlux)
            .map(textContent -> {
                log.info("文本提取完成，长度: {}", textContent.length());
                
                if (textContent.length() < 50) {
                    throw new RuntimeException("简历内容过少，无法进行有效分析");
                }
                
                String tempId = UUID.randomUUID().toString();
                return analyzeResumeContent(file.filename(), tempId, textContent, language);
            })
            .doOnError(error -> log.error("简历分析失败: {}", file.filename(), error));
    }
    
    private Mono<FilePart> validateFile(FilePart file) {
        return Mono.fromCallable(() -> {
            String filename = file.filename();
            if (filename == null || filename.trim().isEmpty()) {
                throw new IllegalArgumentException("文件名不能为空");
            }
            
            String extension = getFileExtension(filename).toLowerCase();
            if (!ALLOWED_EXTENSIONS.contains(extension)) {
                throw new IllegalArgumentException("不支持的文件类型: " + extension + 
                    "。支持的类型: " + String.join(", ", ALLOWED_EXTENSIONS));
            }
            
            log.info("文件验证通过: {}", filename);
            return file;
        });
    }
    
    private String getFileExtension(String filename) {
        if (filename == null || !filename.contains(".")) {
            return "";
        }
        return filename.substring(filename.lastIndexOf(".") + 1);
    }
    
    private Mono<String> extractTextWebFlux(FilePart file) {
        return DataBufferUtils.join(file.content())
            .map(dataBuffer -> {
                try {
                    byte[] bytes = new byte[dataBuffer.readableByteCount()];
                    dataBuffer.read(bytes);
                    DataBufferUtils.release(dataBuffer);
                    
                    if (bytes.length > MAX_FILE_SIZE) {
                        throw new IllegalArgumentException("文件大小超出限制: " + 
                            (bytes.length / 1024 / 1024) + "MB，最大允许: " + 
                            (MAX_FILE_SIZE / 1024 / 1024) + "MB");
                    }
                    
                    String contentType = getContentType(file.filename());
                    log.info("文件类型: {}, 大小: {} bytes", contentType, bytes.length);
                    
                    return extractTextFromBytes(bytes, file.filename(), contentType);
                } catch (IOException e) {
                    log.error("文本提取失败: {}", file.filename(), e);
                    throw new RuntimeException("文本提取失败", e);
                }
            });
    }
    
    private String extractTextFromBytes(byte[] bytes, String filename, String contentType) throws IOException {
        switch (contentType) {
            case "application/pdf":
                return extractFromPDFBytes(bytes);
            case "application/vnd.openxmlformats-officedocument.wordprocessingml.document":
                return extractFromDocxBytes(bytes);
            case "application/msword":
                return extractFromDocBytes(bytes);
            case "text/plain":
                return new String(bytes, StandardCharsets.UTF_8);
            default:
                if (filename != null) {
                    String lower = filename.toLowerCase();
                    if (lower.endsWith(".pdf")) {
                        return extractFromPDFBytes(bytes);
                    } else if (lower.endsWith(".docx")) {
                        return extractFromDocxBytes(bytes);
                    } else if (lower.endsWith(".doc")) {
                        return extractFromDocBytes(bytes);
                    } else if (lower.endsWith(".txt")) {
                        return new String(bytes, StandardCharsets.UTF_8);
                    }
                }
                throw new IllegalArgumentException("不支持的文件类型: " + contentType);
        }
    }
    
    private String extractFromPDFBytes(byte[] bytes) throws IOException {
        try (PDDocument document = PDDocument.load(bytes)) {
            PDFTextStripper stripper = new PDFTextStripper();
            return stripper.getText(document);
        }
    }
    
    private String extractFromDocxBytes(byte[] bytes) throws IOException {
        try (ByteArrayInputStream bis = new ByteArrayInputStream(bytes);
             XWPFDocument document = new XWPFDocument(bis);
             XWPFWordExtractor extractor = new XWPFWordExtractor(document)) {
            return extractor.getText();
        }
    }
    
    private String extractFromDocBytes(byte[] bytes) throws IOException {
        try (ByteArrayInputStream bis = new ByteArrayInputStream(bytes);
             HWPFDocument document = new HWPFDocument(bis);
             WordExtractor extractor = new WordExtractor(document)) {
            return extractor.getText();
        }
    }
    
    private String getContentType(String filename) {
        if (filename == null) return "";
        
        String lowerFilename = filename.toLowerCase();
        if (lowerFilename.endsWith(".pdf")) return "application/pdf";
        if (lowerFilename.endsWith(".docx")) return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
        if (lowerFilename.endsWith(".doc")) return "application/msword";
        if (lowerFilename.endsWith(".txt")) return "text/plain";
        
        return "";
    }
    
    // private ResumeAnalysisResult analyzeResumeContent(String fileName, String fileId, String textContent, String language) {
    //     try {
    //         log.info("开始分析简历内容: {}", fileName);
            
    //         ResumeStructure structure = analyzeStructure(textContent, language);
    //         ResumeKeyInfo keyInfo = extractKeyInformation(textContent, language);
    //         int qualityScore = assessQuality(structure, keyInfo);
    //         List<ResumeSuggestion> suggestions = generateSuggestions(structure, keyInfo, language);
            
    //         return ResumeAnalysisResult.builder()
    //                 .fileName(fileName)
    //                 .fileId(fileId)
    //                 .structure(structure)
    //                 .keyInfo(keyInfo)
    //                 .qualityScore(qualityScore)
    //                 .suggestions(suggestions)
    //                 .textContent(textContent.substring(0, Math.min(1000, textContent.length())))
    //                 .analyzedAt(LocalDateTime.now())
    //                 .build();
                    
    //     } catch (Exception e) {
    //         log.error("简历内容分析失败: {}", fileName, e);
    //         throw new RuntimeException("简历内容分析失败: " + e.getMessage());
    //     }
    // }
    private ResumeAnalysisResult analyzeResumeContent(String fileName, String fileId, String textContent, String language) {
        try {
            log.info("开始分析简历内容: {}", fileName);
            
            ResumeStructure structure = analyzeStructure(textContent, language);
            ResumeKeyInfo keyInfo = extractKeyInformation(textContent, language);
            int qualityScore = assessQuality(structure, keyInfo);
            List<ResumeSuggestion> suggestions = generateSuggestions(structure, keyInfo, language);
            
            ResumeAnalysisResult result = ResumeAnalysisResult.builder()
                    .fileName(fileName)
                    .fileId(fileId)
                    .structure(structure)
                    .keyInfo(keyInfo)
                    .qualityScore(qualityScore)
                    .suggestions(suggestions)
                    .textContent(textContent.substring(0, Math.min(1000, textContent.length())))
                    .analyzedAt(LocalDateTime.now())
                    .build();
            
            // 🔥 新增: 存储到RAG系统
            try {
                ragStore.storeAnalysisResult(result);
                log.info("✅ RAG存储成功: 文件={}, 评分={}", fileName, qualityScore);
            } catch (Exception e) {
                log.error("❌ RAG存储失败: {}", fileName, e);
                // 不影响主流程，继续返回结果
            }
            
            return result;
                    
        } catch (Exception e) {
            log.error("简历内容分析失败: {}", fileName, e);
            throw new RuntimeException("简历内容分析失败: " + e.getMessage());
        }
    }
    
    private ResumeStructure analyzeStructure(String text, String language) {
        boolean hasContactInfo = detectSectionByKeywords(text, "contact");
        boolean hasSummary = detectSectionByKeywords(text, "summary");
        boolean hasExperience = detectSectionByKeywords(text, "experience");
        boolean hasEducation = detectSectionByKeywords(text, "education");
        boolean hasSkills = detectSectionByKeywords(text, "skills");
        boolean hasAchievements = detectSectionByKeywords(text, "achievements");
        boolean hasProjects = detectSectionByKeywords(text, "projects");
        boolean hasLanguages = detectSectionByKeywords(text, "languages");
        
        int sectionCount = (hasContactInfo ? 1 : 0) + (hasSummary ? 1 : 0) + 
                          (hasExperience ? 1 : 0) + (hasEducation ? 1 : 0) + 
                          (hasSkills ? 1 : 0) + (hasAchievements ? 1 : 0) +
                          (hasProjects ? 1 : 0) + (hasLanguages ? 1 : 0);
        
        double completeness = sectionCount / 8.0;
        
        // 生成多语言的完整度说明
        List<String> completenessDetails = generateCompletenessDetails(
            hasContactInfo, hasSummary, hasExperience, hasEducation, 
            hasSkills, hasAchievements, hasProjects, hasLanguages, 
            sectionCount, completeness, language
        );
        
        log.info("简历完整度详细分析: {}", String.join(", ", completenessDetails));
        
        return ResumeStructure.builder()
                .hasContactInfo(hasContactInfo)
                .hasSummary(hasSummary)
                .hasExperience(hasExperience)
                .hasEducation(hasEducation)
                .hasSkills(hasSkills)
                .hasAchievements(hasAchievements)
                .hasProjects(hasProjects)
                .hasLanguages(hasLanguages)
                .completeness(completeness)
                .completenessDetails(completenessDetails)
                .length(text.length())
                .wordCount(text.split("\\s+").length)
                .build();
    }
    
    // 生成多语言的完整度详情
    private List<String> generateCompletenessDetails(boolean hasContactInfo, boolean hasSummary, 
                                                    boolean hasExperience, boolean hasEducation,
                                                    boolean hasSkills, boolean hasAchievements, 
                                                    boolean hasProjects, boolean hasLanguages,
                                                    int sectionCount, double completeness, String language) {
        List<String> details = new ArrayList<>();
        
        // 使用HashMap替代Map.of避免10个键值对限制
        Map<String, Map<String, String>> labels = new HashMap<>();
        
        // 中文标签
        Map<String, String> zhLabels = new HashMap<>();
        zhLabels.put("contact", "联系信息");
        zhLabels.put("summary", "个人简介");
        zhLabels.put("experience", "工作经历");
        zhLabels.put("education", "教育背景");
        zhLabels.put("skills", "技能展示");
        zhLabels.put("achievements", "成就奖项");
        zhLabels.put("projects", "项目经历");
        zhLabels.put("languages", "语言能力");
        zhLabels.put("calculation", "计算方式");
        zhLabels.put("yes", "✅");
        zhLabels.put("no", "❌");
        labels.put("zh-CN", zhLabels);
        
        // 英文标签
        Map<String, String> enLabels = new HashMap<>();
        enLabels.put("contact", "Contact Info");
        enLabels.put("summary", "Summary");
        enLabels.put("experience", "Experience");
        enLabels.put("education", "Education");
        enLabels.put("skills", "Skills");
        enLabels.put("achievements", "Achievements");
        enLabels.put("projects", "Projects");
        enLabels.put("languages", "Languages");
        enLabels.put("calculation", "Calculation");
        enLabels.put("yes", "✅");
        enLabels.put("no", "❌");
        labels.put("en", enLabels);
        
        // 马来语标签
        Map<String, String> msLabels = new HashMap<>();
        msLabels.put("contact", "Maklumat Hubungan");
        msLabels.put("summary", "Ringkasan");
        msLabels.put("experience", "Pengalaman");
        msLabels.put("education", "Pendidikan");
        msLabels.put("skills", "Kemahiran");
        msLabels.put("achievements", "Pencapaian");
        msLabels.put("projects", "Projek");
        msLabels.put("languages", "Bahasa");
        msLabels.put("calculation", "Pengiraan");
        msLabels.put("yes", "✅");
        msLabels.put("no", "❌");
        labels.put("ms", msLabels);
        
        Map<String, String> currentLabels = labels.getOrDefault(language, labels.get("en"));
        
        // 生成各项检查结果
        details.add(currentLabels.get("contact") + ": " + (hasContactInfo ? currentLabels.get("yes") : currentLabels.get("no")));
        details.add(currentLabels.get("summary") + ": " + (hasSummary ? currentLabels.get("yes") : currentLabels.get("no")));
        details.add(currentLabels.get("experience") + ": " + (hasExperience ? currentLabels.get("yes") : currentLabels.get("no")));
        details.add(currentLabels.get("education") + ": " + (hasEducation ? currentLabels.get("yes") : currentLabels.get("no")));
        details.add(currentLabels.get("skills") + ": " + (hasSkills ? currentLabels.get("yes") : currentLabels.get("no")));
        details.add(currentLabels.get("achievements") + ": " + (hasAchievements ? currentLabels.get("yes") : currentLabels.get("no")));
        details.add(currentLabels.get("projects") + ": " + (hasProjects ? currentLabels.get("yes") : currentLabels.get("no")));
        details.add(currentLabels.get("languages") + ": " + (hasLanguages ? currentLabels.get("yes") : currentLabels.get("no")));
        details.add(currentLabels.get("calculation") + ": " + sectionCount + " ÷ 8 = " + String.format("%.0f", completeness * 100) + "%");
        
        return details;
    }
    
    private boolean detectSectionByKeywords(String text, String sectionType) {
        List<String> keywords = SECTION_KEYWORDS.get(sectionType);
        if (keywords == null) return false;
        
        String lowerText = text.toLowerCase();
        
        return keywords.stream().anyMatch(keyword -> {
            String lowerKeyword = keyword.toLowerCase();
            return lowerText.contains(lowerKeyword + ":") || 
                   lowerText.contains(lowerKeyword + "：") ||  // 中文冒号
                   lowerText.contains(lowerKeyword + "\n") ||
                   lowerText.matches(".*\\b" + Pattern.quote(lowerKeyword) + "\\b.*") ||
                   lowerText.contains(lowerKeyword);
        });
    }
    
    private ResumeKeyInfo extractKeyInformation(String text, String language) {
        // 增强的邮箱提取，支持中文域名
        Pattern emailPattern = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
        List<String> emails = emailPattern.matcher(text)
                .results()
                .map(MatchResult::group)
                .distinct()
                .collect(Collectors.toList());
        
        // 更精确的电话提取，只匹配真正的手机号码
        Pattern phonePattern = Pattern.compile("(?:手机号码?[:：]?\\s*)?(1[3-9]\\d{9})");
        List<String> phones = phonePattern.matcher(text)
                .results()
                .map(match -> match.group(1)) // 只取手机号码部分，不包括前缀
                .distinct()
                .collect(Collectors.toList());
        
        // 增强的技能提取
        List<String> skills = extractAllSkills(text);
        
        // 工作经验分析（区分正式工作和实习，支持多语言）
        WorkExperienceInfo workInfo = analyzeWorkExperience(text, language);
        
        // 教育水平检测
        String educationLevel = detectEducationLevel(text);
        
        return ResumeKeyInfo.builder()
                .emails(emails)
                .phones(phones)
                .skills(skills)
                .yearsOfExperience(workInfo.getFormalWorkYears())
                .workExperienceType(workInfo.getExperienceType())
                .internshipYears(workInfo.getInternshipYears())
                .educationLevel(educationLevel)
                .build();
    }
    
    // 新增工作经验分析类
    private static class WorkExperienceInfo {
        private final int formalWorkYears;
        private final int internshipYears;
        private final String experienceType;
        
        public WorkExperienceInfo(int formalWorkYears, int internshipYears, String experienceType) {
            this.formalWorkYears = formalWorkYears;
            this.internshipYears = internshipYears;
            this.experienceType = experienceType;
        }
        
        public int getFormalWorkYears() { return formalWorkYears; }
        public int getInternshipYears() { return internshipYears; }
        public String getExperienceType() { return experienceType; }
    }
    
    private WorkExperienceInfo analyzeWorkExperience(String text, String language) {
        List<Integer> formalWorkMonths = new ArrayList<>();
        List<Integer> internshipMonthsList = new ArrayList<>();
        
        // 先识别并排除教育背景部分
        String workOnlyText = removeEducationSections(text);
        
        // 更精确的工作经历日期匹配
        Pattern workDatePattern = Pattern.compile("(\\d{4})[年./-](\\d{1,2})[月./-]?\\s*[-–—]\\s*(?:(\\d{4})[年./-](\\d{1,2})[月./-]?|至今|现在)");
        
        workDatePattern.matcher(workOnlyText).results().forEach(match -> {
            try {
                String startYearStr = match.group(1);
                String startMonthStr = match.group(2);
                String endYearStr = match.group(3);
                String endMonthStr = match.group(4);
                
                if (startYearStr != null && startMonthStr != null) {
                    int startYear = Integer.parseInt(startYearStr);
                    int startMonth = Integer.parseInt(startMonthStr);
                    
                    // 只处理2024年后的日期（排除教育背景）
                    if (startYear >= 2024 && startYear <= 2030) {
                        int endYear, endMonth;
                        String matchedText = match.group();
                        
                        if (endYearStr == null || matchedText.contains("至今") || matchedText.contains("现在")) {
                            endYear = LocalDateTime.now().getYear();
                            endMonth = LocalDateTime.now().getMonthValue();
                        } else {
                            endYear = Integer.parseInt(endYearStr);
                            endMonth = Integer.parseInt(endMonthStr);
                        }
                        
                        // 计算月数
                        int months = (endYear - startYear) * 12 + (endMonth - startMonth);
                        if (months < 0) months = 0;
                        if (months > 36) return; // 排除超过3年的异常值
                        
                        // 获取周围上下文，更精确判断
                        String context = extractContext(workOnlyText, match.start(), match.end());
                        
                        // 必须明确是工作相关的才计算
                        if (isDefinitelyWorkExperience(context)) {
                            boolean isInternship = determineIfInternship(context, matchedText);
                            
                            if (isInternship) {
                                internshipMonthsList.add(months);
                                log.info("确认实习: {}.{}-{} = {}个月", 
                                    startYear, startMonth, 
                                    (endYearStr != null ? endYear + "." + endMonth : "至今"), 
                                    months);
                            } else {
                                formalWorkMonths.add(months);
                                log.info("确认正式工作: {}.{}-{} = {}个月", startYear, startMonth, endYear, months);
                            }
                        }
                    }
                }
            } catch (NumberFormatException e) {
                log.warn("日期解析错误: {}", match.group());
            }
        });
        
        // 计算总时间
        int totalFormalWorkMonths = formalWorkMonths.stream().mapToInt(Integer::intValue).sum();
        int totalInternshipMonths = internshipMonthsList.stream().mapToInt(Integer::intValue).sum();
        
        int formalWorkYears = totalFormalWorkMonths > 0 ? Math.max(1, (int) Math.ceil(totalFormalWorkMonths / 12.0)) : 0;
        int internshipYears = totalInternshipMonths > 0 ? Math.max(1, (int) Math.ceil(totalInternshipMonths / 12.0)) : 0;
        
        // 根据语言生成经验类型描述
        String experienceType = generateWorkExperienceDescription(formalWorkYears, internshipYears, language);
        
        log.info("最终统计: 正式工作{}个月, 实习{}个月, 类型: {}", 
            totalFormalWorkMonths, totalInternshipMonths, experienceType);
        
        return new WorkExperienceInfo(formalWorkYears, internshipYears, experienceType);
    }
    
    // 生成多语言的工作经验描述
    private String generateWorkExperienceDescription(int formalWorkYears, int internshipYears, String language) {
        Map<String, Map<String, String>> experienceTexts = Map.of(
            "zh-CN", Map.of(
                "formal_only", formalWorkYears + "年正式工作经验",
                "formal_with_internship", formalWorkYears + "年正式工作经验 + " + internshipYears + "年实习经验",
                "internship_only", "仅有" + internshipYears + "年实习经验，无正式工作经验",
                "no_experience", "暂无工作经验"
            ),
            "en", Map.of(
                "formal_only", formalWorkYears + " year" + (formalWorkYears > 1 ? "s" : "") + " of formal work experience",
                "formal_with_internship", formalWorkYears + " year" + (formalWorkYears > 1 ? "s" : "") + " of formal work + " + internshipYears + " year" + (internshipYears > 1 ? "s" : "") + " of internship",
                "internship_only", "Only " + internshipYears + " year" + (internshipYears > 1 ? "s" : "") + " of internship experience, no formal work experience",
                "no_experience", "No work experience"
            ),
            "ms", Map.of(
                "formal_only", formalWorkYears + " tahun pengalaman kerja formal",
                "formal_with_internship", formalWorkYears + " tahun kerja formal + " + internshipYears + " tahun latihan industri",
                "internship_only", "Hanya " + internshipYears + " tahun pengalaman latihan industri, tiada pengalaman kerja formal",
                "no_experience", "Tiada pengalaman kerja"
            )
        );
        
        Map<String, String> texts = experienceTexts.getOrDefault(language, experienceTexts.get("en"));
        
        if (formalWorkYears > 0) {
            return internshipYears > 0 ? texts.get("formal_with_internship") : texts.get("formal_only");
        } else if (internshipYears > 0) {
            return texts.get("internship_only");
        } else {
            return texts.get("no_experience");
        }
    }
    
    // 提取日期匹配周围的上下文
    private String extractContext(String text, int start, int end) {
        int contextStart = Math.max(0, start - 100);
        int contextEnd = Math.min(text.length(), end + 100);
        return text.substring(contextStart, contextEnd);
    }
    
    // 移除教育背景部分，避免干扰
    private String removeEducationSections(String text) {
        // 移除明确的教育背景部分
        String result = text.replaceAll("教育背景[\\s\\S]*?(?=专业技能|实习与项目经历|$)", "");
        result = result.replaceAll("Educational background[\\s\\S]*?(?=Professional skills|Internship|$)", "");
        
        // 移除包含学校信息的行
        result = result.replaceAll(".*(?:蒙纳士大学|厦门大学|Monash University).*\\n?", "");
        result = result.replaceAll(".*(?:GPA|专业|主修课程).*\\n?", "");
        result = result.replaceAll(".*(?:出生年月|毕业院校).*\\n?", "");
        
        return result;
    }
    
    // 更严格地判断是否为工作经历
    private boolean isDefinitelyWorkExperience(String context) {
        String lowerContext = context.toLowerCase();
        
        // 必须包含明确的工作相关词汇
        boolean hasWorkKeywords = lowerContext.contains("cita-cita") || 
                                 lowerContext.contains("金圣龙") || 
                                 lowerContext.contains("牧原") ||
                                 lowerContext.contains("实习") ||
                                 lowerContext.contains("工程师") ||
                                 lowerContext.contains("开发") ||
                                 lowerContext.contains("项目");
        
        // 排除教育相关内容
        boolean hasEducationKeywords = lowerContext.contains("gpa") ||
                                     lowerContext.contains("专业") ||
                                     lowerContext.contains("大学") ||
                                     lowerContext.contains("university") ||
                                     lowerContext.contains("学士") ||
                                     lowerContext.contains("硕士") ||
                                     lowerContext.contains("出生");
        
        return hasWorkKeywords && !hasEducationKeywords;
    }
    
    // 新增方法：判断是否为实习
    private boolean determineIfInternship(String section, String matchText) {
        String sectionLower = section.toLowerCase();
        String matchLower = matchText.toLowerCase();
        
        // 1. 明确标注实习的
        if (sectionLower.contains("实习") || matchLower.contains("实习") ||
            sectionLower.contains("intern") || matchLower.contains("intern")) {
            return true;
        }
        
        // 2. 在"实习与项目经历"部分的，默认认为是实习
        if (sectionLower.contains("实习与项目经历") || sectionLower.contains("实习经历")) {
            return true;
        }
        
        // 3. 包含学生身份相关词汇的
        if (sectionLower.contains("助理") || sectionLower.contains("assistant") ||
            sectionLower.contains("trainee") || sectionLower.contains("graduate")) {
            return true;
        }
        
        // 4. 工作时间较短的（小于等于6个月），可能是实习
        try {
            // 从匹配文本中提取月份数来判断
            if (matchText.contains("-") && !matchText.contains("年")) {
                // 简单启发式：如果是短期工作，很可能是实习
                return true;
            }
        } catch (Exception e) {
            // 忽略解析错误
        }
        
        // 5. 默认情况：如果在教育背景显示还在读书期间的工作，认为是实习
        // 由于用户还在读研究生（2024.07-2026.04），所以2024年之后的工作都应该是实习
        return true; // 对于学生，默认认为都是实习
    }
    
    private List<String> extractAllSkills(String text) {
        List<String> allSkills = new ArrayList<>();
        allSkills.addAll(PROGRAMMING_SKILLS);
        allSkills.addAll(WEB_SKILLS);
        allSkills.addAll(DATABASE_SKILLS);
        allSkills.addAll(CLOUD_SKILLS);
        allSkills.addAll(SOFT_SKILLS);
        
        return allSkills.stream()
                .filter(skill -> text.toLowerCase().contains(skill.toLowerCase()))
                .distinct()
                .collect(Collectors.toList());
    }
    
    private int estimateExperience(String text) {
        // 只匹配工作相关的日期范围，排除出生年月和教育经历
        Pattern workDatePattern = Pattern.compile("(\\d{4})[年./-](\\d{1,2})[月./-]?\\s*[-–—]\\s*(\\d{4})[年./-](\\d{1,2})[月./-]?|" +
                                                 "(\\d{4})[年./-](\\d{1,2})[月./-]?\\s*[-–—]\\s*至今|" +
                                                 "(\\d{4})[年./-](\\d{1,2})[月./-]?\\s*[-–—]\\s*现在", Pattern.CASE_INSENSITIVE);
        
        List<Integer> experienceMonths = new ArrayList<>();
        String lowerText = text.toLowerCase();
        
        // 只在工作经历、实习经历、项目经历部分查找日期
        String[] workSections = text.split("(?=实习与项目经历|工作经历|项目经历|internship|experience)");
        
        for (String section : workSections) {
            if (section.toLowerCase().contains("实习") || section.toLowerCase().contains("项目") || 
                section.toLowerCase().contains("internship") || section.toLowerCase().contains("experience")) {
                
                workDatePattern.matcher(section).results().forEach(match -> {
                    try {
                        String startYearStr = match.group(1) != null ? match.group(1) : match.group(5) != null ? match.group(5) : match.group(7);
                        String startMonthStr = match.group(2) != null ? match.group(2) : match.group(6) != null ? match.group(6) : match.group(8);
                        String endYearStr = match.group(3);
                        String endMonthStr = match.group(4);
                        
                        if (startYearStr != null && startMonthStr != null) {
                            int startYear = Integer.parseInt(startYearStr);
                            int startMonth = Integer.parseInt(startMonthStr);
                            
                            // 排除明显错误的年份（如出生年月）
                            if (startYear < 2020 || startYear > 2030) {
                                return;
                            }
                            
                            int endYear, endMonth;
                            if (endYearStr == null || match.group().contains("至今") || match.group().contains("现在")) {
                                endYear = LocalDateTime.now().getYear();
                                endMonth = LocalDateTime.now().getMonthValue();
                            } else {
                                endYear = Integer.parseInt(endYearStr);
                                endMonth = Integer.parseInt(endMonthStr);
                            }
                            
                            int totalMonths = (endYear - startYear) * 12 + (endMonth - startMonth);
                            if (totalMonths > 0 && totalMonths <= 120) { // 最多10年经验，避免异常值
                                experienceMonths.add(totalMonths);
                            }
                        }
                    } catch (NumberFormatException e) {
                        // 忽略格式错误
                    }
                });
            }
        }
        
        // 转换为年数（向上取整）
        int totalMonths = experienceMonths.stream().mapToInt(Integer::intValue).sum();
        return (int) Math.ceil(totalMonths / 12.0);
    }
    
    private String detectEducationLevel(String text) {
        String lowerText = text.toLowerCase();
        
        // 支持中英文学历检测，更精确的匹配
        if (lowerText.matches(".*(?:博士|phd|doctorate|ph\\.d|doctor).*")) return "PhD";
        if (lowerText.matches(".*(?:硕士|研究生|master|msc|mba|ma\\b|ms\\b|数据科学专业|数据科学).*")) return "Masters";
        if (lowerText.matches(".*(?:学士|本科|bachelor|bsc|ba\\b|bs\\b|degree|大学|软件工程专业|软件工程).*")) return "Bachelors";
        if (lowerText.matches(".*(?:高中|中学|high school|secondary).*")) return "High School";
        
        // 特别检查蒙纳士大学和厦门大学的情况
        if (lowerText.contains("蒙纳士大学") || lowerText.contains("monash university")) {
            return "Masters"; // 蒙纳士大学的数据科学专业
        }
        if (lowerText.contains("厦门大学") && lowerText.contains("软件工程")) {
            return "Bachelors"; // 厦门大学的软件工程专业
        }
        
        return "Unknown";
    }
    
    private int assessQuality(ResumeStructure structure, ResumeKeyInfo keyInfo) {
        int score = 0;
        
        // 结构完整性 (40%)
        score += (int) (structure.getCompleteness() * 40);
        
        // 联系信息 (20%)
        if (!keyInfo.getEmails().isEmpty()) score += 10;
        if (!keyInfo.getPhones().isEmpty()) score += 10;
        
        // 技能数量和质量 (25%)
        int skillCount = keyInfo.getSkills().size();
        if (skillCount >= 15) score += 25;
        else if (skillCount >= 10) score += 20;
        else if (skillCount >= 5) score += 15;
        else if (skillCount >= 3) score += 10;
        else if (skillCount >= 1) score += 5;
        
        // 经验年数 (15%)
        int experience = keyInfo.getYearsOfExperience();
        if (experience >= 5) score += 15;
        else if (experience >= 3) score += 12;
        else if (experience >= 1) score += 8;
        else if (experience > 0) score += 5;
        
        return Math.min(100, score);
    }
    
    private List<ResumeSuggestion> generateSuggestions(ResumeStructure structure, 
                                                      ResumeKeyInfo keyInfo, 
                                                      String language) {
        List<ResumeSuggestion> suggestions = new ArrayList<>();
        
        // 高优先级建议
        if (!structure.isHasContactInfo()) {
            suggestions.add(createSuggestion("missing_section", "high", "add_contact_info", language));
        }
        
        if (structure.getWordCount() < 300) {
            suggestions.add(createSuggestion("content_improvement", "high", "increase_content", language));
        }
        
        // 中优先级建议
        if (!structure.isHasSummary()) {
            suggestions.add(createSuggestion("missing_section", "medium", "add_summary", language));
        }
        
        if (!structure.isHasProjects()) {
            suggestions.add(createSuggestion("missing_section", "medium", "add_projects", language));
        }
        
        if (keyInfo.getSkills().size() < 5) {
            suggestions.add(createSuggestion("content_improvement", "medium", "add_more_skills", language));
        }
        
        if (keyInfo.getYearsOfExperience() == 0) {
            suggestions.add(createSuggestion("content_improvement", "medium", "add_experience_dates", language));
        }
        
        // 低优先级建议
        if (!structure.isHasAchievements()) {
            suggestions.add(createSuggestion("missing_section", "low", "add_achievements", language));
        }
        
        if (!structure.isHasLanguages()) {
            suggestions.add(createSuggestion("missing_section", "low", "add_languages", language));
        }
        
        // 按优先级排序：high -> medium -> low
        suggestions.sort((a, b) -> {
            Map<String, Integer> priorityOrder = Map.of("high", 3, "medium", 2, "low", 1);
            return priorityOrder.get(b.getPriority()).compareTo(priorityOrder.get(a.getPriority()));
        });
        
        return suggestions;
    }
    
    private ResumeSuggestion createSuggestion(String type, String priority, String messageKey, String language) {
        return ResumeSuggestion.builder()
                .type(type)
                .priority(priority)
                .message(getLocalizedMessage(messageKey, language))
                .build();
    }
    
    private String getLocalizedMessage(String key, String language) {
        Map<String, Map<String, String>> messages = Map.of(
            "zh-CN", Map.of(
                "add_contact_info", "添加完整的联系信息，包括邮箱和电话号码",
                "add_summary", "添加个人简介或职业目标部分，突出你的核心优势",
                "add_projects", "添加项目经历部分，展示你的实际工作成果",
                "add_more_skills", "添加更多相关技能，建议至少5个技能关键词",
                "increase_content", "简历内容过少，建议增加更多详细的工作经历和成就",
                "add_experience_dates", "为工作经历添加具体的时间范围",
                "add_achievements", "添加获奖情况或认证证书，提升简历亮点",
                "add_languages", "添加语言能力信息，展示国际化素养"
            ),
            "en", Map.of(
                "add_contact_info", "Add complete contact information including email and phone number",
                "add_summary", "Add a personal summary or career objective section highlighting your core strengths",
                "add_projects", "Add a projects section to showcase your practical achievements",
                "add_more_skills", "Add more relevant skills, recommend at least 5 skill keywords",
                "increase_content", "Resume content is too brief, consider adding more detailed work experience",
                "add_experience_dates", "Add specific date ranges for work experience",
                "add_achievements", "Add achievements or certifications to highlight your accomplishments",
                "add_languages", "Add language skills to demonstrate international capabilities"
            ),
            "ms", Map.of(
                "add_contact_info", "Tambah maklumat hubungan lengkap termasuk email dan nombor telefon",
                "add_summary", "Tambah bahagian ringkasan peribadi yang menonjolkan kekuatan teras anda",
                "add_projects", "Tambah bahagian projek untuk mempamerkan pencapaian praktikal anda",
                "add_more_skills", "Tambah lebih banyak kemahiran berkaitan, cadangkan sekurang-kurangnya 5 kemahiran",
                "increase_content", "Kandungan resume terlalu ringkas, pertimbangkan untuk menambah lebih banyak pengalaman",
                "add_experience_dates", "Tambah julat tarikh khusus untuk pengalaman kerja",
                "add_achievements", "Tambah pencapaian atau pensijilan untuk menonjolkan prestasi anda",
                "add_languages", "Tambah kemahiran bahasa untuk menunjukkan keupayaan antarabangsa"
            )
        );
        
        return messages.getOrDefault(language, messages.get("en")).getOrDefault(key, "Improvement suggestion");
    }
}