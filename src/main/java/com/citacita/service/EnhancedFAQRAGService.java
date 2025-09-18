package com.citacita.service;

import com.citacita.entity.MascoJob;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class EnhancedFAQRAGService {

    @Autowired
    private MascoJobDatabaseService mascoJobService;

    // FAQ知识库 - 包含FAQ和Grants信息
    private final Map<String, FAQ> faqDatabase;
    
    // 语言检测模式
    private final Pattern chinesePattern = Pattern.compile("[\\u4e00-\\u9fff]+");
    private final Pattern englishPattern = Pattern.compile("[a-zA-Z]+");
    private final Pattern malayPattern = Pattern.compile("\\b(apa|bagaimana|di mana|kenapa|bila|boleh|tidak|ya|kerja|jawatan|pekerjaan|saya|anda|ini|itu|dengan|untuk|dari|ke|dan|atau)\\b", Pattern.CASE_INSENSITIVE);

    public EnhancedFAQRAGService() {
        this.faqDatabase = initializeFAQDatabase();
    }

    /**
     * 基于FAQ+Grants+Jobs的智能检索（修复阻塞问题）
     */
    public Mono<String> retrieveRelevantContent(String query) {
        try {
            String lowerQuery = query.toLowerCase();
            
            // 强制重新检测语言，不依赖任何会话状态
            String detectedLanguage = detectLanguage(query);
            System.out.println("=== 强制语言检测 ===");
            System.out.println("查询: " + query);
            System.out.println("检测到的语言: " + detectedLanguage);
            System.out.println("========================");
            
            // 1. 首先检查是否询问页面导航 - 强制使用检测到的语言
            String navigationResponse = getPageNavigation(query, detectedLanguage);
            if (navigationResponse != null) {
                System.out.println("返回页面导航，语言: " + detectedLanguage);
                return Mono.just(addLanguageHeader(navigationResponse, detectedLanguage));
            }
            
            // 2. 检查工作相关查询 - 强制使用检测到的语言
            if (isJobRelatedQuery(lowerQuery)) {
                return processJobQuery(lowerQuery, detectedLanguage)
                    .map(response -> addLanguageHeader(response, detectedLanguage));
            }
            
            // 3. 检查FAQ和Grants - 强制使用检测到的语言
            List<FAQ> matchedFAQs = findMatchingFAQs(lowerQuery);
            if (!matchedFAQs.isEmpty()) {
                String response = formatFAQResponse(matchedFAQs, detectedLanguage);
                return Mono.just(addLanguageHeader(response, detectedLanguage));
            }
            
            // 4. 检查是否为低相关性查询 - 强制使用检测到的语言
            if (isLowRelevanceQuery(lowerQuery)) {
                String response = generateLowRelevanceResponse(lowerQuery, detectedLanguage);
                return Mono.just(addLanguageHeader(response, detectedLanguage));
            }
            
            // 5. 返回相关指导信息 - 强制使用检测到的语言
            String response = getRelatedGuidance(lowerQuery, detectedLanguage);
            return Mono.just(addLanguageHeader(response, detectedLanguage));
            
        } catch (Exception e) {
            System.err.println("Enhanced FAQ RAG检索错误: " + e.getMessage());
            // 即使在错误情况下也要重新检测语言
            String detectedLanguage = detectLanguage(query);
            String response = getDefaultGuidance(detectedLanguage);
            return Mono.just(addLanguageHeader(response, detectedLanguage));
        }
    }

    /**
     * 添加语言标识头（用于调试和强制语言）
     */
    private String addLanguageHeader(String response, String detectedLanguage) {
        // 在开发阶段可以添加语言标识，生产环境可以移除
        String languageHeader = "";
        
        // 可选：添加不可见的语言标记用于调试
        switch (detectedLanguage) {
            case "chinese":
                languageHeader = "<!-- LANG: 中文 -->\n";
                break;
            case "malay":
                languageHeader = "<!-- LANG: Malay -->\n";
                break;
            default:
                languageHeader = "<!-- LANG: English -->\n";
                break;
        }
        
        return languageHeader + response;
    }

    /**
     * 更新映射数据库语言代码的方法
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

    /**
     * 处理工作相关查询（修复阻塞问题）
     */
    private Mono<String> processJobQuery(String query, String language) {
        return mascoJobService.searchJobs(query, language, 3)
            .map(jobs -> {
                if (!jobs.isEmpty()) {
                    return mascoJobService.formatJobsForRAG(jobs, language);
                } else {
                    // 如果没有找到工作，提供相关建议
                    return getJobSearchGuidance(query, language);
                }
            })
            .doOnError(error -> {
                System.err.println("处理工作查询错误: " + error.getMessage());
            })
            .onErrorReturn(getJobSearchGuidance(query, language));
    }

    /**
     * 判断是否为工作相关查询
     */
    private boolean isJobRelatedQuery(String query) {
        // 扩展工作相关关键词
        String[] jobKeywords = {
            // 英文关键词
            "job", "career", "work", "position", "role", "occupation", "employment",
            "developer", "engineer", "manager", "analyst", "consultant", "technician",
            "programmer", "designer", "administrator", "coordinator", "specialist",
            "accountant", "nurse", "teacher", "lawyer", "doctor", "chef", "mechanic",
            "salary", "skills", "requirement", "qualification", "experience",
            "what job", "job title", "job description", "career path", "job code",
            
            // 中文关键词
            "工作", "职业", "职位", "岗位", "就业", "求职", "招聘",
            "开发", "工程师", "经理", "分析师", "顾问", "技术员",
            "程序员", "设计师", "管理员", "协调员", "专家",
            "会计", "护士", "老师", "律师", "医生", "厨师", "机械师",
            "薪资", "薪水", "技能", "要求", "资格", "经验",
            "什么工作", "职位名称", "工作描述", "职业发展", "工作代码",
            
            // 马来语关键词
            "kerja", "kerjaya", "jawatan", "pekerjaan", "gaji", "kemahiran",
            
            // MASCO相关
            "masco", "职业分类", "occupation classification"
        };
        
        for (String keyword : jobKeywords) {
            if (query.contains(keyword.toLowerCase())) {
                return true;
            }
        }
        
        // 检查是否包含职位代码模式 (如: 2111, 1234)
        if (query.matches(".*\\b\\d{4}\\b.*")) {
            return true;
        }
        
        return false;
    }

    /**
     * 为找不到工作时提供指导（修复版本 - 支持三种语言）
     */
    private String getJobSearchGuidance(String query, String language) {
        switch (language) {
            case "chinese":
                return String.format("""
                    很抱歉，我没有找到与"%s"直接匹配的工作信息。
                    
                    **建议您可以尝试：**
                    • 使用更具体的职位名称，如"软件开发人员"、"会计师"、"护士"
                    • 搜索工作代码，如"2111"、"2421"
                    • 询问特定行业的工作，如"IT行业有什么工作？"
                    • 使用英文或马来文搜索，如"software developer"
                    
                    **或者您可以问我：**
                    • "有什么IT相关的工作？"
                    • "管理类工作有哪些？"
                    • "什么工作适合我？"（可以先做我们的职业测验）
                    • "专业组1有什么工作？"（按MASCO分类）
                    
                    我还可以帮您了解政府补助、AI工具使用等其他信息！
                    """, query);
                    
            case "malay":
                return String.format("""
                    Maaf, saya tidak dapat mencari maklumat kerja yang sepadan dengan "%s".
                    
                    **Anda boleh cuba:**
                    • Gunakan nama jawatan yang lebih spesifik seperti "software developer", "akauntan", "jururawat"
                    • Cari menggunakan kod kerja seperti "2111", "2421"
                    • Tanya tentang industri tertentu seperti "Apakah kerja IT yang tersedia?"
                    • Cuba cari dalam bahasa Inggeris atau Cina
                    
                    **Atau anda boleh tanya saya:**
                    • "Apakah kerja berkaitan IT yang ada?"
                    • "Apakah kerja pengurusan yang tersedia?"
                    • "Apakah kerja yang sesuai untuk saya?" (cuba kuiz kerjaya kami dahulu)
                    • "Apakah kerja dalam kumpulan utama 1?" (mengikut klasifikasi MASCO)
                    
                    Saya juga boleh membantu anda mengetahui tentang geran kerajaan, penggunaan alat AI dan maklumat lain!
                    """, query);
                    
            default: // english
                return String.format("""
                    Sorry, I couldn't find job information directly matching "%s".
                    
                    **You can try:**
                    • Use specific job titles like "software developer", "accountant", "nurse"
                    • Search by job codes like "2111", "2421"
                    • Ask about specific industries like "What IT jobs are available?"
                    • Try searching in Chinese or Malay
                    
                    **Or you can ask me:**
                    • "What IT-related jobs are there?"
                    • "What management jobs are available?"
                    • "What jobs are suitable for me?" (try our job quiz first)
                    • "What jobs are in major group 1?" (by MASCO classification)
                    
                    I can also help you learn about government grants, AI tools, and other information!
                    """, query);
        }
    }

    /*
     * 三语言强制检测（英文、中文、马来语）
     */
    private String detectLanguage(String query) {
        if (query == null || query.trim().isEmpty()) {
            return "english"; // 默认英文
        }
        
        String cleanQuery = query.trim().toLowerCase();
        
        // 1. 优先检查明显的语言标识词
        
        //中文
        String[] chineseIndicators = {
            "什么", "怎么", "如何", "哪里", "为什么", "是否", "能否", "可以", "谢谢", "你好", 
            "工作", "职业", "政府", "补助", "帮助", "页面", "链接", "在哪", "怎样", "如何",
            "的", "了", "和", "我", "你", "他", "她", "我们", "你们", "他们"
        };
        for (String indicator : chineseIndicators) {
            if (cleanQuery.contains(indicator)) {
                System.out.println("检测到中文标识词: " + indicator);
                return "chinese";
            }
        }
        
        // 马来语标识词（扩展列表）
        String[] malayIndicators = {
            "apa", "bagaimana", "di mana", "kenapa", "bila", "boleh", "tidak", "kerja", "jawatan", 
            "pekerjaan", "bantuan", "kerajaan", "saya", "anda", "kami", "mereka", "dengan", "untuk",
            "halaman", "pautan", "mana", "macam mana"
        };
        for (String indicator : malayIndicators) {
            if (cleanQuery.contains(indicator)) {
                System.out.println("检测到马来语标识词: " + indicator);
                return "malay";
            }
        }
        
        // 英文标识词（扩展列表）
        String[] englishIndicators = {
            "what", "how", "where", "why", "when", "can", "could", "should", "would", "hello", "hi", 
            "thank", "job", "work", "government", "grant", "page", "link", "access", "find", "show",
            "do", "does", "is", "are", "the", "and", "to", "of", "in", "for", "with"
        };
        for (String indicator : englishIndicators) {
            if (cleanQuery.contains(" " + indicator + " ") || cleanQuery.startsWith(indicator + " ") || 
                cleanQuery.endsWith(" " + indicator) || cleanQuery.equals(indicator)) {
                System.out.println("检测到英文标识词: " + indicator);
                return "english";
            }
        }
        
        // 2. 字符和模式检测
        int chineseChars = 0;
        int englishChars = 0;
        boolean hasMalayWords = malayPattern.matcher(cleanQuery).find();
        
        if (chinesePattern.matcher(query).find()) {
            chineseChars = query.replaceAll("[^\\u4e00-\\u9fff]", "").length();
        }
        
        if (englishPattern.matcher(query).find()) {
            englishChars = query.replaceAll("[^a-zA-Z]", "").length();
        }
        
        // 3. 决策逻辑（优先级：马来语 > 中文 > 英文）
        if (hasMalayWords) {
            return "malay";
        } else if (chineseChars > 0) {
            return "chinese";
        } else if (englishChars > 0) {
            return "english";
        } else {
            return "english"; // 默认英文
        }
    }
    

    /**
     * 检查是否为低相关性查询
     */
    private boolean isLowRelevanceQuery(String query) {
        // 获取所有FAQ的最高匹配分数
        int maxFAQScore = 0;
        for (FAQ faq : faqDatabase.values()) {
            int score = calculateMatchScore(query, faq);
            maxFAQScore = Math.max(maxFAQScore, score);
        }
        
        // 如果FAQ最高分数为0且不是工作相关查询，认为是完全无关的查询
        return maxFAQScore == 0 && !isJobRelatedQuery(query);
    }

    /**
     * 生成低相关性回复（引导用户重新输入）
     */
    private String generateLowRelevanceResponse(String query, String language) {
        switch (language) {
            case "chinese":
                return generateChineseLowRelevanceResponse(query);
            case "malay":
                return generateMalayLowRelevanceResponse(query);
            default:
                return generateEnglishLowRelevanceResponse(query);
        }
    }

    /**
     * 生成中文低相关性回复
     */
    private String generateChineseLowRelevanceResponse(String query) {
        // 根据查询内容提供相关建议
        String suggestion = getSuggestionForQuery(query, "chinese");
        
        return String.format("""
            你好！很高兴与你交流——你的提问和想法都很有价值。

            **重要提醒：我们的所有功能都不需要注册，完全免费使用！**
            
            我专门为CitaCita职业匹配平台提供支持,主要可以帮助您了解:
            
            **工作和职业相关：**
            • "有什么工作适合我？"
            • "软件开发员是做什么的？"
            • "工作代码2111是什么？"
            • "专业组1有哪些工作？"
            • "工作测验怎么使用？"
            • "如何查看职位要求？"
            
            **AI工具使用:**
            • "AI简历检查器怎么用?"
            • "AI模拟面试是什么?"
            • "聊天机器人能帮我做什么?"
            
            **政府补助和支持：**
            • "有什么补助金给重返职场的女性？"
            • "政府有什么创业支持计划？"
            • "税务减免政策有哪些？"
            
            **支持服务：**
            • "地图功能如何使用？"
            • "哪里可以找到托儿所？"
            
            %s
            
            请尝试问我以上相关的问题，我会很乐意为您详细解答!
            """, suggestion);
    }

    /**
     * 生成英文低相关性回复
     */
    private String generateEnglishLowRelevanceResponse(String query) {
        String suggestion = getSuggestionForQuery(query, "english");
        
        return String.format("""
            Hi there! We're so glad you reached out — your questions and ideas matter.

            **Important: All our features are free to use with no registration required!**
            
            I'm specifically designed to help with CitaCita career matching platform, and I can assist you with:
            
            **Jobs & Career:**
            • "What jobs are suitable for me?"
            • "What does a software developer do?"
            • "What is job code 2111?"
            • "What jobs are in major group 1?"
            • "How to use the job quiz?"
            • "How to check job requirements?"
            
            **AI Tools:**
            • "How does the AI Resume Checker work?"
            • "What is the AI Mock Interview?"
            • "What can the chatbot help me with?"
            
            **Government Grants & Support:**
            • "What grants are available for women returning to work?"
            • "What government entrepreneurship support programs exist?"
            • "What tax relief policies are available?"
            
            **Support Services:**
            • "How to use the map function?"
            • "Where can I find childcare centers?"
            
            %s
            
            Please try asking me questions related to the above topics, and I'll be happy to help in detail!
            """, suggestion);
    }

    /**
     * 马来语低相关性回复
     */
    private String generateMalayLowRelevanceResponse(String query) {
        String suggestion = getSuggestionForQuery(query, "malay");
        
        return String.format("""
            Hai! Kami sangat gembira anda menghubungi kami — soalan dan idea anda sangat berharga.

            Saya direka khusus untuk membantu platform CitaCita career matching, dan saya boleh membantu anda dengan:
            
            Saya direka khusus untuk membantu platform CitaCita career matching, dan saya boleh membantu anda dengan:
            
            **Pekerjaan & Kerjaya:**
            • "Apakah kerja yang sesuai untuk saya?"
            • "Apakah yang dilakukan oleh software developer?"
            • "Apakah kod kerja 2111?"
            • "Apakah kerja dalam kumpulan utama 1?"
            • "Bagaimana menggunakan kuiz pekerjaan?"
            • "Bagaimana memeriksa keperluan kerja?"
            
            **Alat AI:**
            • "Bagaimana AI Resume Checker berfungsi?"
            • "Apakah AI Mock Interview?"
            • "Apakah yang chatbot boleh bantu saya?"
            
            **Geran & Sokongan Kerajaan:**
            • "Apakah geran yang tersedia untuk wanita yang kembali bekerja?"
            • "Apakah program sokongan keusahawanan kerajaan yang wujud?"
            • "Apakah dasar pelepasan cukai yang tersedia?"
            
            **Perkhidmatan Sokongan:**
            • "Bagaimana menggunakan fungsi peta?"
            • "Di manakah saya boleh mencari pusat jagaan kanak-kanak?"
            
            %s
            
            Sila cuba tanya saya soalan berkaitan topik di atas, dan saya akan gembira membantu secara terperinci!
            """, suggestion);
        }
    

    /**
     * 根据查询内容提供相关建议
     */
    private String getSuggestionForQuery(String query, String language) {
        String lowerQuery = query.toLowerCase();
        
        switch (language) {
            case "chinese":
                if (containsKeywords(lowerQuery, "天气", "weather", "cuaca")) {
                    return "**建议：** 如果您想了解工作地点附近的设施，可以问我「地图功能怎么用？」";
                } else if (containsKeywords(lowerQuery, "学习", "课程", "培训")) {
                    return "**建议：** 我们有相关培训信息！您可以问我「政府有什么技能培训计划？」";
                }
                return "**提示：** 请尝试问我关于具体工作、职业发展、AI工具使用或政府补助的问题。";
                
            case "malay":
                if (containsKeywords(lowerQuery, "cuaca", "weather", "hujan")) {
                    return "**Cadangan:** Jika anda ingin tahu tentang kemudahan berhampiran tempat kerja, tanya saya 'Bagaimana menggunakan fungsi peta?'";
                } else if (containsKeywords(lowerQuery, "belajar", "kursus", "latihan")) {
                    return "**Cadangan:** Kami ada maklumat latihan! Tanya saya 'Apakah program latihan kemahiran kerajaan yang tersedia?'";
                }
                return "**Tip:** Sila cuba tanya saya soalan tentang kerja tertentu, pembangunan kerjaya, alat AI, atau geran kerajaan.";
                
            default: // english
                if (containsKeywords(lowerQuery, "weather", "temperature", "rain")) {
                    return "**Suggestion:** If you want to know about facilities near workplaces, ask me 'How to use the map function?'";
                } else if (containsKeywords(lowerQuery, "study", "course", "training")) {
                    return "**Suggestion:** We have training information! Ask me 'What government skill training programs are available?'";
                }
                return "**Tip:** Please try asking me questions about specific jobs, career development, AI tools, or government grants.";
        }
    }

    /**
     * 查找匹配的FAQ
     */
    private List<FAQ> findMatchingFAQs(String query) {
        List<FAQ> exactMatches = new ArrayList<>();
        List<FAQ> partialMatches = new ArrayList<>();
        
        for (FAQ faq : faqDatabase.values()) {
            int matchScore = calculateMatchScore(query, faq);
            
            if (matchScore >= 3) { // 高匹配度
                exactMatches.add(faq);
            } else if (matchScore >= 1) { // 部分匹配
                partialMatches.add(faq);
            }
        }
        
        // 优先返回精确匹配，否则返回部分匹配
        if (!exactMatches.isEmpty()) {
            return exactMatches.stream().limit(2).collect(Collectors.toList());
        }
        
        return partialMatches.stream().limit(3).collect(Collectors.toList());
    }

    /**
     * 计算匹配分数
     */
    private int calculateMatchScore(String query, FAQ faq) {
        int score = 0;
        
        // 检查问题关键词匹配
        for (String keyword : faq.keywords) {
            if (query.contains(keyword.toLowerCase())) {
                score += 2;
            }
        }
        
        // 排除常见的无意义词汇
        Set<String> commonWords = Set.of("what", "how", "when", "where", "why", "who", 
                                        "is", "are", "can", "could", "will", "would", 
                                        "the", "a", "an", "and", "or", "but", "in", "on", 
                                        "at", "to", "for", "of", "with", "by");
        
        String[] questionWords = faq.question.toLowerCase().split("\\s+");
        for (String word : questionWords) {
            if (query.contains(word) && word.length() > 2 && !commonWords.contains(word)) {
                score += 1;
            }
        }
        
        return score;
    }

    /**
     * 格式化FAQ回复（支持三种语言）
     */
    private String formatFAQResponse(List<FAQ> faqs, String language) {
        StringBuilder response = new StringBuilder();
        
        switch (language) {
            case "chinese":
                response.append("根据CitaCita平台的信息,以下资源可能对您有帮助:\n\n");
                break;
            case "malay":
                response.append("Berdasarkan maklumat platform CitaCita, sumber berikut mungkin membantu anda:\n\n");
                break;
            default: // english
                response.append("Based on CitaCita platform information, the following resources may help you:\n\n");
                break;
        }
        
        for (int i = 0; i < faqs.size(); i++) {
            FAQ faq = faqs.get(i);
            response.append(String.format("**%s**\n\n%s", faq.question, faq.answer));
            
            if (i < faqs.size() - 1) {
                response.append("\n\n---\n\n");
            }
        }
        
        return response.toString();
    }

    private boolean containsKeywords(String query, String... keywords) {
        for (String keyword : keywords) {
            if (query.contains(keyword.toLowerCase())) {
                return true;
            }
        }
        return false;
    }

    /**
     * 获取相关指导信息（支持三种语言）- 修复版
     */
    private String getRelatedGuidance(String query, String language) {
        // 检查补助金相关关键词 - 分组检查
        boolean isGrantRelated = containsKeywords(query, "grant", "financial", "assistance", "funding", "support", "subsidy") ||
                            containsKeywords(query, "补助", "资助", "财政", "津贴", "支持", "补贴", "税务", "减免") ||
                            containsKeywords(query, "geran", "kewangan", "bantuan", "pembiayaan", "sokongan", "subsidi", "cukai", "pelepasan");
        
        if (isGrantRelated) {
            switch (language) {
                case "chinese":
                    return """
                        马来西亚为重返职场的女性提供多种财政支持和补助计划：
                        
                        **主要计划包括：**
                        • **Career Comeback Programme** - TalentCorp职业回归计划，提供12个月个人所得税减免
                        • **雇主税务激励** - 雇主聘用女性回归者可获得50%额外税务扣除
                        • **灵活工作安排支持** - FWA实施支持和税务优惠
                        • **MYFutureJobs女性倡议** - 重新技能培训和就业安置
                        • **创业融资计划** - DanaNITA、WinBiz等女性企业家专项融资
                        
                        请告诉我您具体需要哪种类型的支持，我可以提供更详细的信息。
                        """;
                        
                case "malay":
                    return """
                        Malaysia menyediakan pelbagai sokongan kewangan dan program geran untuk wanita yang kembali bekerja:
                        
                        **Program utama termasuk:**
                        • **Career Comeback Programme** - Program kembali bekerja TalentCorp dengan pengecualian cukai pendapatan peribadi 12 bulan
                        • **Insentif Cukai Majikan** - 50% potongan cukai tambahan untuk majikan yang mengambil wanita yang kembali bekerja
                        • **Sokongan Pengaturan Kerja Fleksibel** - Sokongan pelaksanaan FWA dan faedah cukai
                        • **Inisiatif Wanita MYFutureJobs** - Latihan kemahiran semula dan penempatan kerja
                        • **Pembiayaan Keusahawanan** - DanaNITA, WinBiz dan skim pembiayaan khusus usahawan wanita
                        
                        Sila beritahu saya jenis sokongan khusus yang anda perlukan, dan saya boleh memberikan maklumat yang lebih terperinci.
                        """;
                        
                default: // english
                    return """
                        Malaysia provides various financial support and grant programs for women returning to work:
                        
                        **Main programs include:**
                        • **Career Comeback Programme** - TalentCorp career return program with 12-month personal income tax exemption
                        • **Employer Tax Incentives** - 50% additional tax deduction for employers hiring women returnees
                        • **Flexible Work Arrangement Support** - FWA implementation support and tax benefits
                        • **MYFutureJobs Women Initiative** - Re-skilling training and job placement
                        • **Entrepreneurship Financing** - DanaNITA, WinBiz and other women entrepreneur financing schemes
                        
                        Please tell me what specific type of support you need, and I can provide more detailed information.
                        """;
            }
        }
        
        // 检查AI工具相关关键词 - 分组检查
        boolean isAIRelated = containsKeywords(query, "ai", "artificial", "intelligence", "resume", "interview", "chatbot") ||
                            containsKeywords(query, "智能", "人工", "简历", "面试", "聊天机器人") ||
                            containsKeywords(query, "pintar", "buatan", "resume", "temuduga", "chatbot");
        
        if (isAIRelated) {
            switch (language) {
                case "chinese":
                    return """
                        CitaCita平台提供多种AI工具来帮助您的职业发展：
                        
                        **AI工具包括：**
                        • **AI简历检查器** - 分析和改进您的简历
                        • **AI模拟面试** - 练习面试技巧和获得反馈
                        • **AI聊天机器人** - 24/7职业指导和网站导航
                        
                        这些工具旨在提高您的就业竞争力和面试信心。请告诉我您想了解哪个AI工具的详细信息！
                        """;
                        
                case "malay":
                    return """
                        Platform CitaCita menyediakan pelbagai alat AI untuk membantu pembangunan kerjaya anda:
                        
                        **Alat AI termasuk:**
                        • **Pemeriksa Resume AI** - Menganalisis dan menambah baik resume anda
                        • **Temuduga Simulasi AI** - Berlatih kemahiran temuduga dan mendapat maklum balas
                        • **Chatbot AI** - Bimbingan kerjaya 24/7 dan navigasi laman web
                        
                        Alat-alat ini bertujuan untuk meningkatkan daya saing pekerjaan dan keyakinan temuduga anda. Sila beritahu saya alat AI mana yang anda ingin ketahui maklumat terperincinya!
                        """;
                        
                default: // english
                    return """
                        CitaCita platform provides various AI tools to help with your career development:
                        
                        **AI Tools include:**
                        • **AI Resume Checker** - Analyze and improve your resume
                        • **AI Mock Interview** - Practice interview skills and get feedback
                        • **AI Chatbot** - 24/7 career guidance and website navigation
                        
                        These tools are designed to enhance your employability and interview confidence. Please let me know which AI tool you'd like detailed information about!
                        """;
            }
        }
        
        // 默认返回通用指导
        return getDefaultGuidance(language);
    }

    /**
     * 三语言默认指导
     */
    private String getDefaultGuidance(String language) {
        switch (language) {
            case "chinese":
                return """
                    欢迎使用CitaCita职业匹配平台!我可以帮助您:
                    
                    **工作搜索** - 浏览MASCO职业分类中的详细工作信息和要求
                    **职业测验** - 通过测验找到适合的工作建议  
                    **AI工具** - 使用简历检查、面试练习等AI功能
                    **支持服务** - 查找托儿所等工作支持设施
                    **财政支持** - 了解政府补助金和财政援助计划
                    
                    请告诉我您具体想了解什么，我会为您提供更详细的信息!
                    """;
                    
            case "malay":
                return """
                    Selamat datang ke platform padanan kerjaya CitaCita! Saya boleh membantu anda dengan:
                    
                    **Carian Kerja** - Lihat maklumat kerja terperinci dan keperluan dari klasifikasi pekerjaan MASCO
                    **Kuiz Kerjaya** - Dapatkan cadangan kerja yang sesuai melalui kuiz
                    **Alat AI** - Gunakan semakan resume, latihan temuduga dan ciri AI lain
                    **Perkhidmatan Sokongan** - Cari jagaan kanak-kanak dan kemudahan sokongan tempat kerja lain
                    **Sokongan Kewangan** - Ketahui tentang geran kerajaan dan program bantuan kewangan
                    
                    Sila beritahu saya apa yang anda ingin ketahui lebih lanjut, dan saya akan memberikan maklumat terperinci!
                    """;
                    
            default: // english
                return """
                    Welcome to CitaCita career matching platform! I can help you with:
                    
                    **Job Search** - Browse detailed job information and requirements from MASCO occupation classification
                    **Job Quiz** - Find suitable job suggestions through quizzes
                    **AI Tools** - Use resume checking, interview practice and other AI features
                    **Support Services** - Find childcare and other workplace support facilities
                    **Financial Support** - Learn about government grants and financial assistance programs
                    
                    Please tell me what you'd like to know more about, and I'll provide detailed information!
                    """;
            }
        }
    
    /**
     * 根据查询内容提供相应的页面链接指导
     */
    private String getPageNavigation(String query, String language) {
        String lowerQuery = query.toLowerCase();
        
        // 检查是否询问主页
        if (containsKeywords(lowerQuery, "home", "homepage", "main page", "首页", "主页", "laman utama", "homepage")) {
            return getNavigationResponse("home", language);
        }
        
        // 检查是否询问工作/职业相关页面
        if (containsKeywords(lowerQuery, "jobs", "work", "career", "industry", "工作", "职业", "行业", "kerja", "kerjaya", "industri")) {
            return getNavigationResponse("jobs", language);
        }
        
        // 检查是否询问测验
        if (containsKeywords(lowerQuery, "quiz", "test", "assessment", "测验", "测试", "评估", "kuiz", "ujian", "penilaian")) {
            return getNavigationResponse("quiz", language);
        }
        
        // 检查是否询问地图功能
        if (containsKeywords(lowerQuery, "map", "location", "childcare", "nursery", "地图", "位置", "托儿所", "幼儿园", "peta", "lokasi", "jagaan kanak")) {
            return getNavigationResponse("map", language);
        }
        
        // 检查是否询问政府补助
        if (containsKeywords(lowerQuery, "grants", "funding", "financial support", "补助", "资助", "财政支持", "geran", "pembiayaan", "sokongan kewangan")) {
            return getNavigationResponse("grants", language);
        }
        
        // 检查是否询问FAQ
        if (containsKeywords(lowerQuery, "faq", "questions", "help", "support", "常见问题", "帮助", "支持", "soalan lazim", "bantuan", "sokongan")) {
            return getNavigationResponse("faq", language);
        }
        
        // 检查是否询问AI工具
        if (containsKeywords(lowerQuery, "ai", "artificial intelligence", "resume checker", "mock interview", "chatbot", 
                        "智能", "人工智能", "简历检查", "模拟面试", "聊天机器人", 
                        "pintar buatan", "pemeriksa resume", "temuduga simulasi")) {
            return getNavigationResponse("ai", language);
        }
        
        return null; // 没有找到特定页面相关的查询
    }

    /**
     * 生成导航响应
     */
    private String getNavigationResponse(String pageType, String language) {
        switch (pageType) {
            case "home":
                return getHomeNavigation(language);
            case "jobs":
                return getJobsNavigation(language);
            case "quiz":
                return getQuizNavigation(language);
            case "map":
                return getMapNavigation(language);
            case "grants":
                return getGrantsNavigation(language);
            case "faq":
                return getFAQNavigation(language);
            case "ai":
                return getAINavigation(language);
            default:
                return null;
        }
    }

    /**
     * 主页导航
     */
    private String getHomeNavigation(String language) {
        switch (language) {
            case "chinese":
                return """
                    您可以访问我们的主页了解CitaCita平台的全部功能：
                    
                    🏠 **主页链接：** https://citacita.work/
                    
                    在主页上，您可以快速访问所有功能模块，包括工作搜索、职业测验、AI工具、政府补助信息等。
                    """;
                    
            case "malay":
                return """
                    Anda boleh melawat laman utama kami untuk mengetahui semua fungsi platform CitaCita:
                    
                    🏠 **Pautan Laman Utama:** https://citacita.work/
                    
                    Di laman utama, anda boleh mengakses semua modul fungsi dengan pantas, termasuk carian kerja, kuiz kerjaya, alat AI, maklumat geran kerajaan dan lain-lain.
                    """;
                    
            default: // english
                return """
                    You can visit our homepage to explore all CitaCita platform features:
                    
                    🏠 **Homepage Link:** https://citacita.work/
                    
                    On the homepage, you can quickly access all functional modules, including job search, job quiz, AI tools, government grants information, and more.
                    """;
        }
    }

    /**
     * 工作页面导航
     */
    private String getJobsNavigation(String language) {
        switch (language) {
            case "chinese":
                return """
                    您可以在我们的工作页面探索各种职业机会：
                    
                    💼 **工作页面链接：** https://citacita.work/jobs
                    
                    在工作页面上，您可以：
                    • 浏览十个MASCO主要行业分类的工作信息
                    • 查看详细的职位描述和要求
                    • 使用职业测验功能（位于页面左下角）
                    """;
                    
            case "malay":
                return """
                    Anda boleh meneroka pelbagai peluang kerjaya di laman kerja kami:
                    
                    💼 **Pautan Laman Kerja:** https://citacita.work/jobs
                    
                    Di laman kerja, anda boleh:
                    • Lihat maklumat kerja dari sepuluh klasifikasi industri utama MASCO
                    • Lihat penerangan jawatan dan keperluan yang terperinci
                    • Gunakan fungsi kuiz kerjaya (terletak di sudut kiri bawah halaman)
                    """;
                    
            default: // english
                return """
                    You can explore various career opportunities on our jobs page:
                    
                    💼 **Jobs Page Link:** https://citacita.work/jobs
                    
                    On the jobs page, you can:
                    • Browse job information from ten MASCO major industry classifications
                    • View detailed job descriptions and requirements
                    • Use the job quiz feature (located in the bottom left corner of the page)
                    """;
        }
    }

    /**
     * 测验导航
     */
    private String getQuizNavigation(String language) {
        switch (language) {
            case "chinese":
                return """
                    我们的职业测验可以帮助您找到合适的工作建议：
                    
                    📝 **职业测验位置：** https://citacita.work/jobs
                    
                    **如何使用测验：**
                    1. 访问工作页面
                    2. 在页面左下角找到测验按钮
                    3. 点击开始测验，根据您的兴趣和技能回答问题
                    4. 获得个性化的工作建议
                    """;
                    
            case "malay":
                return """
                    Kuiz kerjaya kami boleh membantu anda mencari cadangan kerja yang sesuai:
                    
                    📝 **Lokasi Kuiz Kerjaya:** https://citacita.work/jobs
                    
                    **Cara menggunakan kuiz:**
                    1. Lawati laman kerja
                    2. Cari butang kuiz di sudut kiri bawah halaman
                    3. Klik untuk memulakan kuiz, jawab soalan berdasarkan minat dan kemahiran anda
                    4. Dapatkan cadangan kerja yang dipersonalisasi
                    """;
                    
            default: // english
                return """
                    Our job quiz can help you find suitable job recommendations:
                    
                    📝 **Job Quiz Location:** https://citacita.work/jobs
                    
                    **How to use the quiz:**
                    1. Visit the jobs page
                    2. Find the quiz button in the bottom left corner of the page
                    3. Click to start the quiz and answer questions based on your interests and skills
                    4. Get personalized job recommendations
                    """;
        }
    }

    /**
     * 地图导航
     */
    private String getMapNavigation(String language) {
        switch (language) {
            case "chinese":
                return """
                    我们的地图功能可以帮助您找到工作地点附近的支持服务：
                    
                    🗺️ **地图页面链接：** https://citacita.work/map
                    
                    在地图上，您可以找到：
                    • 托儿所和幼儿园位置
                    • 其他工作支持设施
                    • 便民服务场所
                    
                    注意：地图搜索结果无法保存，但您可以随时重新搜索。
                    """;
                    
            case "malay":
                return """
                    Fungsi peta kami boleh membantu anda mencari perkhidmatan sokongan berhampiran tempat kerja:
                    
                    🗺️ **Pautan Laman Peta:** https://citacita.work/map
                    
                    Di peta, anda boleh mencari:
                    • Lokasi pusat jagaan kanak-kanak dan tadika
                    • Kemudahan sokongan kerja lain
                    • Tempat perkhidmatan awam
                    
                    Nota: Hasil carian peta tidak boleh disimpan, tetapi anda boleh mencari semula pada bila-bila masa.
                    """;
                    
            default: // english
                return """
                    Our map feature can help you find support services near workplaces:
                    
                    🗺️ **Map Page Link:** https://citacita.work/map
                    
                    On the map, you can find:
                    • Childcare centers and kindergarten locations
                    • Other workplace support facilities
                    • Public service locations
                    
                    Note: Map search results cannot be saved, but you can search again anytime.
                    """;
        }
    }

    /**
     * 补助页面导航
     */
    private String getGrantsNavigation(String language) {
        switch (language) {
            case "chinese":
                return """
                    了解马来西亚政府为女性提供的各种补助和支持计划：
                    
                    💰 **政府补助页面链接：** https://citacita.work/grants
                    
                    在补助页面上，您可以了解：
                    • 重返职场女性的税务减免计划
                    • 创业融资和商业支持
                    • 技能培训和就业安置服务
                    • 灵活工作安排支持
                    """;
                    
            case "malay":
                return """
                    Ketahui pelbagai program geran dan sokongan yang disediakan kerajaan Malaysia untuk wanita:
                    
                    💰 **Pautan Laman Geran:** https://citacita.work/grants
                    
                    Di laman geran, anda boleh mengetahui:
                    • Pelan pelepasan cukai untuk wanita yang kembali bekerja
                    • Pembiayaan keusahawanan dan sokongan perniagaan
                    • Perkhidmatan latihan kemahiran dan penempatan kerja
                    • Sokongan pengaturan kerja fleksibel
                    """;
                    
            default: // english
                return """
                    Learn about various grant and support programs provided by the Malaysian government for women:
                    
                    💰 **Grants Page Link:** https://citacita.work/grants
                    
                    On the grants page, you can learn about:
                    • Tax exemption plans for women returning to work
                    • Entrepreneurship financing and business support
                    • Skills training and job placement services
                    • Flexible work arrangement support
                    """;
        }
    }

    /**
     * FAQ导航
     */
    private String getFAQNavigation(String language) {
        switch (language) {
            case "chinese":
                return """
                    查看我们的常见问题解答，获取平台使用指导：
                    
                    ❓ **FAQ页面链接：** https://citacita.work/faq
                    
                    FAQ页面包含：
                    • 平台功能使用指南
                    • 常见问题的详细解答
                    • 故障排除和技术支持
                    • 联系方式和进一步帮助
                    """;
                    
            case "malay":
                return """
                    Lihat soalan lazim kami untuk mendapatkan panduan penggunaan platform:
                    
                    ❓ **Pautan Laman FAQ:** https://citacita.work/faq
                    
                    Laman FAQ mengandungi:
                    • Panduan penggunaan fungsi platform
                    • Jawapan terperinci untuk soalan lazim
                    • Penyelesaian masalah dan sokongan teknikal
                    • Maklumat hubungan dan bantuan lanjut
                    """;
                    
            default: // english
                return """
                    Check our frequently asked questions for platform usage guidance:
                    
                    ❓ **FAQ Page Link:** https://citacita.work/faq
                    
                    The FAQ page contains:
                    • Platform feature usage guides
                    • Detailed answers to common questions
                    • Troubleshooting and technical support
                    • Contact information and further assistance
                    """;
        }
    }

    /**
     * AI工具导航
     */
    private String getAINavigation(String language) {
        switch (language) {
            case "chinese":
                return """
                    探索我们的AI工具，提升您的职业竞争力：
                    
                    🤖 **AI工具页面链接：** https://citacita.work/ai
                    
                    AI工具包括：
                    • **AI简历检查器** - 分析和优化您的简历
                    • **AI模拟面试** - 练习面试技巧并获得反馈
                    • **AI聊天机器人** - 24/7职业指导和网站导航
                    
                    这些工具旨在帮助您在求职过程中更加自信和准备充分。
                    """;
                    
            case "malay":
                return """
                    Terokai alat AI kami untuk meningkatkan daya saing kerjaya anda:
                    
                    🤖 **Pautan Laman Alat AI:** https://citacita.work/ai
                    
                    Alat AI termasuk:
                    • **Pemeriksa Resume AI** - Menganalisis dan mengoptimumkan resume anda
                    • **Temuduga Simulasi AI** - Berlatih kemahiran temuduga dan mendapat maklum balas
                    • **Chatbot AI** - Bimbingan kerjaya 24/7 dan navigasi laman web
                    
                    Alat-alat ini bertujuan membantu anda lebih yakin dan bersedia dalam proses mencari kerja.
                    """;
                    
            default: // english
                return """
                    Explore our AI tools to enhance your career competitiveness:
                    
                    🤖 **AI Tools Page Link:** https://citacita.work/ai
                    
                    AI tools include:
                    • **AI Resume Checker** - Analyze and optimize your resume
                    • **AI Mock Interview** - Practice interview skills and get feedback
                    • **AI Chatbot** - 24/7 career guidance and website navigation
                    
                    These tools are designed to help you be more confident and prepared in your job search process.
                    """;
        }
    }
    
    /**
     * 初始化FAQ+Grants数据库（保持你原有的所有数据）
     */
    private Map<String, FAQ> initializeFAQDatabase() {
        Map<String, FAQ> faqs = new HashMap<>();
        
        // 添加一个基本的工作信息FAQ - 你需要添加所有原有的FAQ数据
        faqs.put("job_info", new FAQ(
            "What kind of job information can I find?",
            """
            **CitaCita所有工作信息完全免费，无需注册即可查看！**

            Jobs are displayed using the MASCO (Malaysian Standard Classification of Occupations) 2020 system:
            
            1. **By Major Groups** - 9 major occupational groups (1-9)
            2. **By Sub-Major Groups** - More specific occupational categories  
            3. **By Minor Groups** - Detailed occupational families
            4. **Unit Groups** - Specific job titles with comprehensive descriptions
            
            Each job includes detailed information about tasks, requirements, skill levels, examples, and multilingual support (English, Chinese, Malay).

            **完全免费访问，无需创建账户或登录。**
            """,
            Arrays.asList("job", "information", "find", "industry", "masco", "occupation", "工作", "信息", "行业", "职业", "分类")
        ));
        
        // 2. 工作测验
        faqs.put("job_quiz", new FAQ(
            "What is the job quiz and how does it work?",
            """
            **职业测验完全免费，无需注册即可使用！**
            
            The job quiz is a multiple-choice tool that guides you step by step. It always suggests a job based on your choices, so you don't feel lost even if you're unsure about qualifications or skills.
            """,
            Arrays.asList("quiz", "test", "multiple-choice", "suggest", "测验", "测试", "建议")
        ));
        
        // 3. 书签功能
        faqs.put("bookmark", new FAQ(
            "Can I bookmark or save jobs for later?",
            """
            Currently, jobs cannot be bookmarked. However, each job page highlights related quizzes and suggestions to help you find the best-fit role.
            """,
            Arrays.asList("bookmark", "save", "later", "收藏", "保存", "稍后")
        ));
        
        // 4. 数据来源
        faqs.put("job_source", new FAQ(
            "Where do these job descriptions come from?",
            """
            These job descriptions come from MASCO 2020, prepared by the Ministry of Human Resources Malaysia. You can access it here: https://emasco.mohr.gov.my/
            """,
            Arrays.asList("source", "description", "masco", "ministry", "来源", "描述")
        ));
        
        // 5. 地图功能
        faqs.put("map_function", new FAQ(
            "How does the map function work?",
            """
            The map shows childcare centers, kindergartens, nurseries, and other supportive services near workplaces. Please note: the map is not saveable, but you can revisit it anytime to search again.
            """,
            Arrays.asList("map", "childcare", "kindergarten", "nursery", "地图", "托儿所", "幼儿园")
        ));
        
        // 6. AI工具总览
        faqs.put("ai_tools", new FAQ(
            "Is there AI for confidence and career readiness?",
            """
            **CitaCita的所有AI工具完全免费，无需注册即可使用！**
            The platform provides AI-driven resume builders, interview coaching suggestions, and even role models' stories to inspire women re-entering the workforce. These tools are designed to boost confidence step by step.
            """,
            Arrays.asList("ai", "confidence", "career", "readiness", "resume", "interview", "智能", "信心", "职业", "简历", "面试")
        ));
        
        // 7. AI简历检查器
        faqs.put("resume_checker", new FAQ(
            "What is the AI Resume Checker?",
            """
            **AI简历检查器完全免费，无需注册，直接上传即可使用！**
            The Resume Checker reviews your uploaded resume and highlights strengths and areas for improvement. It checks for clarity, keywords that match job descriptions, and missing information. You'll receive practical suggestions to make your resume stronger and more competitive.
            """,
            Arrays.asList("resume", "checker", "upload", "review", "keywords", "简历", "检查", "上传", "关键词")
        ));
        
        // 8. AI模拟面试
        faqs.put("mock_interview", new FAQ(
            "How does the AI Mock Interview tool help?",
            """
            **AI模拟面试完全免费，无需注册，直接开始练习！**
            The Mock Interview simulates common interview questions based on your chosen job role. The AI analyses your responses, tone, and structure, then provides constructive feedback. This allows you to practise in a safe environment, gain confidence, and improve before facing real interviews.
            """,
            Arrays.asList("mock", "interview", "simulate", "questions", "feedback", "practice", "模拟", "面试", "问题", "反馈", "练习")
        ));
        
        // 9. AI聊天机器人
        faqs.put("ai_chatbot", new FAQ(
            "What does the AI Chatbot do?",
            """
            The AI Chatbot is your 24/7 guide for the website. You can ask it questions about navigating pages, finding job suggestions, using the quizzes, or accessing childcare maps. It's like having a friendly assistant to walk you through the site whenever you need help.
            """,
            Arrays.asList("chatbot", "24/7", "guide", "navigate", "assistant", "help", "聊天机器人", "导航", "助手", "帮助")
        ));
        
        // 10. AI安全性
        faqs.put("ai_safety", new FAQ(
            "Are these AI tools safe and reliable?",
            """
            Yes. We follow ethical AI practices to ensure your data remains private and secure. The AI tools are not meant to replace human guidance but to extend support—helping you feel confident, prepared, and empowered in your career journey.
            """,
            Arrays.asList("safe", "reliable", "ethical", "private", "secure", "data", "安全", "可靠", "道德", "隐私", "数据")
        ));
        
        // ============= 新增GRANTS部分 =============
        
        // 11. Career Comeback Programme
        faqs.put("career_comeback", new FAQ(
            "Career Comeback Programme & Tax Benefits for Women Returning to Work",
            """
            **TalentCorp Career Comeback Programme** provides comprehensive support for women returning to workforce:
            
            **支持服务:**
            • 工作坊和职业指导
            • 雇主对接服务
            • 12个月个人所得税减免(有效期至2027年12月31日)
            
            **申请链接:**
            • 计划详情:https://www.talentcorp.com.my/ccp
            • 税务减免:https://www.talentcorp.com.my/careercomebacktax
            
            这个计划专门为重返职场的女性设计，提供全方位的支持。
            """,
            Arrays.asList("career", "comeback", "tax", "exemption", "talentcorp", "return", "work", "职业", "回归", "税务", "减免", "重返", "工作")
        ));
        
        // 12. 雇主税务激励
        faqs.put("employer_incentives", new FAQ(
            "Employer Tax Incentives for Hiring Women Returnees (Budget 2025)",
            """
            **雇主聘用女性回归者税务激励(2025年预算案)**
            
            **资格期间:** 2025年1月1日至2027年12月31日
            **税务优惠:** 雇佣首12个月薪酬的50%额外税务扣除
            **覆盖范围:** 符合条件的女性员工薪资和工资
            
            **官方链接:**
            https://www.investmalaysia.gov.my/media/k0dc3vme/budget-2025-tax-measures.pdf
            
            这项激励计划鼓励雇主积极聘用重返职场的女性。
            """,
            Arrays.asList("employer", "tax", "incentive", "budget", "2025", "hiring", "women", "雇主", "税务", "激励", "预算", "聘用", "女性")
        ));
        
        // 13. 灵活工作安排支持
        faqs.put("flexible_work", new FAQ(
            "Flexible Work Arrangement (FWA) Support & Incentives",
            """
            **灵活工作安排(FWA)支持和激励**
            
            **目标:** 支持实施家庭友善工作安排的雇主
            **法律框架:** 2022年劳工法修正案第60P和60Q条
            **政府支持:** KESUMA、JTKSM和TalentCorp提供实施指南
            
            **税务优惠:**
            • FWA能力建设和软件开支50%税务扣除
            • 上限RM500,000,有效期2025-2027年
            
            **详情链接:**
            https://www.talentcorp.com.my/resources/press-releases/launch-of-the-flexible-work-arrangement-fwa-guidelines/
            """,
            Arrays.asList("flexible", "work", "arrangement", "fwa", "support", "incentive", "灵活", "工作", "安排", "支持", "激励")
        ));
        
        // 14. 家庭照护支持和税务减免
        faqs.put("family_care_support", new FAQ(
            "Enhanced Family Care Support & Tax Relief",
            """
            **增强家庭照护支持和税务减免**
            
            **员工优惠:**
            • 托儿税务减免:6岁以下儿童每年RM3,000
            • 老人照护扩展:从2025年起,扩展至包括父母/祖父母照护津贴税务减免
            
            **雇主优惠:**
            • 提供托儿/老人照护津贴的税务扣除
            • 额外带薪照护假激励:50%税务扣除(最多12个月),有效期2025-2027年
            
            **税务减免信息:**
            https://www.hasil.gov.my/en/individual/individual-life-cycle/how-to-declare-income/tax-reliefs/
            """,
            Arrays.asList("family", "care", "support", "tax", "relief", "childcare", "elderly", "家庭", "照护", "支持", "税务", "减免", "托儿", "老人")
        ));
        
        // 15. MYFutureJobs女性倡议
        faqs.put("myfuturejobs_women", new FAQ(
            "MYFutureJobs Women Initiative",
            """
            **MYFutureJobs女性倡议**
            
            **目标群体:** 单亲妈妈、家庭主妇和暂时中断职业准备重返工作的女性
            
            **计划包括:**
            • 重新技能和提升技能培训计划
            • MYMidCareer40计划
            • MYNextChampion计划  
            • 职业博览会
            • 社会保障
            
            **官方链接:**
            https://myfuturejobs.gov.my/women/
            
            这个倡议专门为女性提供全面的就业支持和培训机会。
            """,
            Arrays.asList("myfuturejobs", "women", "initiative", "single", "mother", "housewife", "reskilling", "女性", "倡议", "单亲", "妈妈", "家庭主妇", "重新技能")
        ));
        
        // 16. 培训和就业安置
        faqs.put("training_placement", new FAQ(
            "MYFutureJobs Training and Job Placement Programs",
            """
            **MYFutureJobs培训和就业安置计划**
            
            **目标:** 提供重新技能和提升技能培训，解决技能差距，提高就业能力
            
            **培训课程包括:**
            • Microsoft Office课程(Word、Excel、PowerPoint)
            • 工业4.0数字营销证书（在线）
            • Facebook营销课程和销售页面开发
            • 项目管理证书(CIPM)
            • 中小企业数字营销培训
            • 更多专业课程...
            
            **详情链接:**
            https://myfuturejobs.gov.my/training-programmes/
            """,
            Arrays.asList("training", "job", "placement", "reskilling", "upskilling", "microsoft", "digital", "marketing", "培训", "就业", "安置", "重新技能", "数字营销")
        ));
        
        // 17. 创业融资计划
        faqs.put("business_financing", new FAQ(
            "Business Financing Schemes for Women Entrepreneurs",
            """
            **女性企业家商业融资计划**
            
            **主要计划:**
            
            **1. DanaNITA特殊商业融资计划**
            • 专为土著女性企业家提供特殊融资
            • 目标:增强女性创业参与，扩展业务，提高家庭收入
            • 链接:https://www.mara.gov.my/en/index/ent-menu/support-facilities/ent-business-finance/dananita/
            
            **2. Women in Business (BI WinBiz) - 伊斯兰银行**
            • 专为马来西亚女性企业家设计的融资产品
            • 覆盖中小企业的营运资金和资本开支
            • 链接:https://www.bankislam.com/business-banking/sme-banking/winbiz-financing/
            
            **3. MADANI WANITA-i (BSN)**
            • BSN为女性企业家提供的微型融资便利
            • 适用于有意扩展业务的女性
            • 链接:https://www.bsn.com.my/page/MadaniWanita-i
            """,
            Arrays.asList("business", "financing", "entrepreneur", "dananita", "winbiz", "madani", "wanita", "loan", "商业", "融资", "企业家", "贷款", "女性", "创业")
        ));
        
        // 18. 政府支持计划
        faqs.put("government_support", new FAQ(
            "Government Support Programs for Women",
            """
            **政府女性支持计划**
            
            **1. PERANTIS**
            • 通过指导支持女性领导力,提供RM50,000补助金
            • 链接:https://www.jpw.gov.my/index.php/ms/services-jpw/perantis
            
            **2. iJPW - 马来西亚妇女赋权部支持清单**
            • 妇女赋权部提供的综合支持服务列表
            • 链接:https://ijpw.jpw.gov.my/
            
            **3. 就业保险系统(LINDUNG KERJAYA)**
            • 为失业的受保人员提供收入替代
            • 链接:https://www.perkeso.gov.my/en/our-services/protection/employment-insurance.html
            
            **4. TalentCorp专业人士计划**
            • 为在马来西亚的专业人士、海外马来西亚人和希望重返工作的女性提供机会
            • 链接:https://www.talentcorp.com.my/our-initiatives/for-professionals/
            """,
            Arrays.asList("government", "support", "perantis", "ijpw", "employment", "insurance", "talentcorp", "professionals", "政府", "支持", "就业", "保险", "专业人士")
        ));

        // 19. 无需注册声明 (添加在 return faqs; 之前)
        faqs.put("no_registration", new FAQ(
            "Do I need to register or sign up to use CitaCita features?",
            """
            **不需要注册！CitaCita的所有功能都完全免费使用，无需注册账户。**
            
            **您可以直接使用：**
            • 🔍 **工作搜索** - 浏览所有MASCO职业信息
            • 📝 **职业测验** - 获取个性化工作建议  
            • 🤖 **AI工具** - 简历检查、模拟面试、聊天机器人
            • 🗺️ **地图功能** - 查找托儿所等支持服务
            • 💰 **政府补助信息** - 了解各种财政支持计划
            • ❓ **FAQ页面** - 获取详细使用指南
            
            **三种语言支持：** 中文、英文、马来文
            **24/7 可用：** 随时访问所有功能
            **完全免费：** 无隐藏费用，无需个人信息
            
            **No Registration Required! All CitaCita features are completely free to use without creating an account.**
            
            **You can directly access:**
            • 🔍 **Job Search** - Browse all MASCO occupation information
            • 📝 **Job Quiz** - Get personalized job recommendations
            • 🤖 **AI Tools** - Resume checker, mock interview, chatbot
            • 🗺️ **Map Function** - Find childcare and support services
            • 💰 **Government Grants** - Learn about financial support programs
            • ❓ **FAQ Page** - Get detailed usage guides
            
            **Three Language Support:** Chinese, English, Malay
            **24/7 Available:** Access all features anytime
            **Completely Free:** No hidden fees, no personal information required
            
            **Tiada Pendaftaran Diperlukan! Semua ciri CitaCita boleh digunakan secara percuma tanpa membuat akaun.**
            
            **Anda boleh terus mengakses:**
            • 🔍 **Carian Kerja** - Lihat semua maklumat pekerjaan MASCO
            • 📝 **Kuiz Kerja** - Dapatkan cadangan kerja yang dipersonalisasi
            • 🤖 **Alat AI** - Pemeriksa resume, temuduga simulasi, chatbot
            • 🗺️ **Fungsi Peta** - Cari jagaan kanak-kanak dan perkhidmatan sokongan
            • 💰 **Geran Kerajaan** - Ketahui program sokongan kewangan
            • ❓ **Halaman FAQ** - Dapatkan panduan penggunaan terperinci
            
            **Sokongan Tiga Bahasa:** Cina, Inggeris, Melayu
            **Tersedia 24/7:** Akses semua ciri pada bila-bila masa
            **Sepenuhnya Percuma:** Tiada bayaran tersembunyi, tiada maklumat peribadi diperlukan
            """,
            Arrays.asList(
                // 英文关键词
                "register", "registration", "sign up", "signup", "account", "login", "free", "no cost", "access", "use",
                "create account", "membership", "subscribe", "subscription", "required", "need", "must",
                
                // 中文关键词  
                "注册", "登记", "注册账户", "免费", "无需", "不需要", "创建账户", "登录", "会员", "订阅", "必须", "需要",
                "账号", "帐户", "收费", "付费", "使用", "访问", "免费使用",
                
                // 马来语关键词
                "daftar", "pendaftaran", "akaun", "log masuk", "percuma", "tidak perlu", "tiada", "guna", "akses",
                "cipta akaun", "keahlian", "langganan", "diperlukan", "mesti", "perlu"
            )
        ));
        
        return faqs;
    }

    /**
     * FAQ数据结构
     */
    private static class FAQ {
        final String question;
        final String answer;
        final List<String> keywords;

        FAQ(String question, String answer, List<String> keywords) {
            this.question = question;
            this.answer = answer;
            this.keywords = keywords;
        }
    }
}