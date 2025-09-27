package com.citacita.service;

import com.citacita.config.ResumeConfig;
import com.citacita.dto.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.hwpf.extractor.WordExtractor;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.MatchResult;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class ResumeAnalyzerService {
    
    private final ResumeConfig resumeConfig;
    
    public Mono<ResumeAnalysisResult> analyzeResumeWebFlux(FilePart file, String language) {
        return saveFileWebFlux(file)
            .flatMap(fileId -> 
                extractTextWebFlux(file)
                    .map(textContent -> {
                        log.info("文本提取完成，长度: {}", textContent.length());
                        return analyzeResumeContent(file.filename(), fileId, textContent, language);
                    })
            )
            .doOnError(error -> log.error("简历分析失败: {}", file.filename(), error));
    }
    
    // 修正文件保存方法
    private Mono<String> saveFileWebFlux(FilePart file) {
        String fileId = UUID.randomUUID().toString();
        String fileName = fileId + getFileExtension(file.filename());
        
        return Mono.fromCallable(() -> {
            Path uploadPath = Paths.get(resumeConfig.getUploadPath());
            try {
                Files.createDirectories(uploadPath);
                log.info("创建上传目录: {}", uploadPath);
                return uploadPath.resolve(fileName);
            } catch (IOException e) {
                log.error("无法创建上传目录: {}", uploadPath, e);
                throw new RuntimeException("无法创建上传目录", e);
            }
        })
        .flatMap(filePath -> {
            log.info("保存文件到: {}", filePath);
            
            // 完全响应式的文件保存，不使用block()
            return file.transferTo(filePath)
                .then(Mono.just(fileId))
                .doOnSuccess(savedFileId -> log.info("文件保存成功: {}", savedFileId))
                .doOnError(error -> log.error("文件保存失败", error));
        });
    }
    
    private Mono<String> extractTextWebFlux(FilePart file) {
        return DataBufferUtils.join(file.content())
            .map(dataBuffer -> {
                try {
                    byte[] bytes = new byte[dataBuffer.readableByteCount()];
                    dataBuffer.read(bytes);
                    DataBufferUtils.release(dataBuffer);
                    
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
                // 根据文件扩展名推断
                if (filename.toLowerCase().endsWith(".pdf")) {
                    return extractFromPDFBytes(bytes);
                } else if (filename.toLowerCase().endsWith(".docx")) {
                    return extractFromDocxBytes(bytes);
                } else if (filename.toLowerCase().endsWith(".doc")) {
                    return extractFromDocBytes(bytes);
                } else if (filename.toLowerCase().endsWith(".txt")) {
                    return new String(bytes, StandardCharsets.UTF_8);
                }
                throw new IllegalArgumentException("不支持的文件类型: " + contentType + " for file: " + filename);
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
    
    private String getFileExtension(String filename) {
        return filename != null && filename.contains(".") 
            ? filename.substring(filename.lastIndexOf("."))
            : "";
    }
    
    private ResumeAnalysisResult analyzeResumeContent(String fileName, String fileId, String textContent, String language) {
        try {
            // 分析简历结构
            ResumeStructure structure = analyzeStructure(textContent);
            
            // 提取关键信息
            ResumeKeyInfo keyInfo = extractKeyInformation(textContent);
            
            // 评估简历质量
            int qualityScore = assessQuality(structure, keyInfo);
            
            // 生成建议
            List<ResumeSuggestion> suggestions = generateSuggestions(structure, keyInfo, language);
            
            return ResumeAnalysisResult.builder()
                    .fileName(fileName)
                    .fileId(fileId)
                    .structure(structure)
                    .keyInfo(keyInfo)
                    .qualityScore(qualityScore)
                    .suggestions(suggestions)
                    .textContent(textContent.substring(0, Math.min(1000, textContent.length())))
                    .analyzedAt(LocalDateTime.now())
                    .build();
                    
        } catch (Exception e) {
            log.error("简历内容分析失败: {}", fileName, e);
            throw new RuntimeException("简历内容分析失败: " + e.getMessage());
        }
    }
    
    // 以下方法与之前相同，保持不变
    private ResumeStructure analyzeStructure(String text) {
        String lowerText = text.toLowerCase();
        
        boolean hasContactInfo = lowerText.matches(".*(?:email|phone|address|linkedin).*");
        boolean hasSummary = lowerText.matches(".*(?:summary|objective|profile|about).*");
        boolean hasExperience = lowerText.matches(".*(?:experience|work|employment|career).*");
        boolean hasEducation = lowerText.matches(".*(?:education|degree|university|college).*");
        boolean hasSkills = lowerText.matches(".*(?:skills|competencies|expertise).*");
        boolean hasAchievements = lowerText.matches(".*(?:achievements|accomplishments|awards).*");
        
        int sectionCount = (hasContactInfo ? 1 : 0) + (hasSummary ? 1 : 0) + 
                          (hasExperience ? 1 : 0) + (hasEducation ? 1 : 0) + 
                          (hasSkills ? 1 : 0) + (hasAchievements ? 1 : 0);
        
        double completeness = sectionCount / 6.0;
        
        return ResumeStructure.builder()
                .hasContactInfo(hasContactInfo)
                .hasSummary(hasSummary)
                .hasExperience(hasExperience)
                .hasEducation(hasEducation)
                .hasSkills(hasSkills)
                .hasAchievements(hasAchievements)
                .completeness(completeness)
                .length(text.length())
                .wordCount(text.split("\\s+").length)
                .build();
    }
    
    private ResumeKeyInfo extractKeyInformation(String text) {
        // Email提取
        Pattern emailPattern = Pattern.compile("\\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Z|a-z]{2,}\\b");
        List<String> emails = emailPattern.matcher(text)
                .results()
                .map(MatchResult::group)
                .collect(Collectors.toList());
        
        // 电话号码提取
        Pattern phonePattern = Pattern.compile("(?:\\+?1[-\\.\\s]?)?\\(?[0-9]{3}\\)?[-\\.\\s]?[0-9]{3}[-\\.\\s]?[0-9]{4}");
        List<String> phones = phonePattern.matcher(text)
                .results()
                .map(MatchResult::group)
                .collect(Collectors.toList());
        
        // 技能提取
        List<String> skills = extractSkills(text);
        
        // 经验年数估算
        int yearsOfExperience = estimateExperience(text);
        
        // 教育水平检测
        String educationLevel = detectEducationLevel(text);
        
        return ResumeKeyInfo.builder()
                .emails(emails)
                .phones(phones)
                .skills(skills)
                .yearsOfExperience(yearsOfExperience)
                .educationLevel(educationLevel)
                .build();
    }
    
    private List<String> extractSkills(String text) {
        List<String> commonSkills = Arrays.asList(
            "JavaScript", "Python", "Java", "React", "Node.js", "SQL", "HTML", "CSS",
            "Project Management", "Leadership", "Communication", "Analytics", "Marketing",
            "Sales", "Customer Service", "Data Analysis", "Microsoft Office"
        );
        
        return commonSkills.stream()
                .filter(skill -> text.toLowerCase().contains(skill.toLowerCase()))
                .collect(Collectors.toList());
    }
    
    private int estimateExperience(String text) {
        Pattern yearPattern = Pattern.compile("(?:19|20)\\d{2}[-\\s]*(?:present|(?:19|20)\\d{2})");
        List<String> yearRanges = yearPattern.matcher(text)
                .results()
                .map(MatchResult::group)
                .collect(Collectors.toList());
        
        if (yearRanges.isEmpty()) return 0;
        
        List<Integer> years = yearRanges.stream()
                .flatMap(range -> {
                    Pattern singleYearPattern = Pattern.compile("(?:19|20)\\d{2}");
                    return singleYearPattern.matcher(range)
                            .results()
                            .map(m -> Integer.parseInt(m.group()));
                })
                .collect(Collectors.toList());
        
        if (years.isEmpty()) return 0;
        
        int minYear = years.stream().mapToInt(Integer::intValue).min().orElse(0);
        int maxYear = Math.max(years.stream().mapToInt(Integer::intValue).max().orElse(0), 
                              LocalDateTime.now().getYear());
        
        return maxYear - minYear;
    }
    
    private String detectEducationLevel(String text) {
        String lowerText = text.toLowerCase();
        
        if (lowerText.matches(".*(?:phd|doctorate|ph\\.d).*")) return "PhD";
        if (lowerText.matches(".*(?:master|msc|mba|ma\\b).*")) return "Masters";
        if (lowerText.matches(".*(?:bachelor|bsc|ba\\b|degree).*")) return "Bachelors";
        if (lowerText.matches(".*(?:high school|secondary).*")) return "High School";
        
        return "Unknown";
    }
    
    private int assessQuality(ResumeStructure structure, ResumeKeyInfo keyInfo) {
        int score = 0;
        
        // 结构完整性 (40%)
        score += (int) (structure.getCompleteness() * 40);
        
        // 联系信息 (20%)
        if (!keyInfo.getEmails().isEmpty()) score += 10;
        if (!keyInfo.getPhones().isEmpty()) score += 10;
        
        // 技能数量 (20%)
        score += Math.min(keyInfo.getSkills().size() * 2, 20);
        
        // 经验年数 (20%)
        if (keyInfo.getYearsOfExperience() > 0) {
            score += Math.min(keyInfo.getYearsOfExperience() * 2, 20);
        }
        
        return score;
    }
    
    private List<ResumeSuggestion> generateSuggestions(ResumeStructure structure, 
                                                      ResumeKeyInfo keyInfo, 
                                                      String language) {
        List<ResumeSuggestion> suggestions = new ArrayList<>();
        
        if (!structure.isHasContactInfo()) {
            suggestions.add(ResumeSuggestion.builder()
                    .type("missing_section")
                    .priority("high")
                    .message(getLocalizedMessage("add_contact_info", language))
                    .build());
        }
        
        if (!structure.isHasSummary()) {
            suggestions.add(ResumeSuggestion.builder()
                    .type("missing_section")
                    .priority("medium")
                    .message(getLocalizedMessage("add_summary", language))
                    .build());
        }
        
        if (keyInfo.getSkills().size() < 5) {
            suggestions.add(ResumeSuggestion.builder()
                    .type("content_improvement")
                    .priority("medium")
                    .message(getLocalizedMessage("add_more_skills", language))
                    .build());
        }
        
        if (structure.getWordCount() < 200) {
            suggestions.add(ResumeSuggestion.builder()
                    .type("content_improvement")
                    .priority("high")
                    .message(getLocalizedMessage("increase_content", language))
                    .build());
        }
        
        return suggestions;
    }
    
    private String getLocalizedMessage(String key, String language) {
        Map<String, Map<String, String>> messages = Map.of(
            "zh-CN", Map.of(
                "add_contact_info", "添加完整的联系信息，包括邮箱和电话号码",
                "add_summary", "添加个人简介或职业目标部分",
                "add_more_skills", "添加更多相关技能以提高匹配度",
                "increase_content", "简历内容过少，建议增加更多详细信息"
            ),
            "en", Map.of(
                "add_contact_info", "Add complete contact information including email and phone number",
                "add_summary", "Add a personal summary or career objective section",
                "add_more_skills", "Add more relevant skills to improve matching",
                "increase_content", "Resume content is too brief, consider adding more details"
            ),
            "ms", Map.of(
                "add_contact_info", "Tambah maklumat hubungan lengkap termasuk email dan nombor telefon",
                "add_summary", "Tambah bahagian ringkasan peribadi atau objektif kerjaya",
                "add_more_skills", "Tambah lebih banyak kemahiran yang berkaitan untuk meningkatkan padanan",
                "increase_content", "Kandungan resume terlalu ringkas, pertimbangkan untuk menambah lebih banyak butiran"
            )
        );
        
        return messages.getOrDefault(language, messages.get("en")).get(key);
    }
}