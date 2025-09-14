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

    public EnhancedFAQRAGService() {
        this.faqDatabase = initializeFAQDatabase();
    }

    /**
     * 基于FAQ+Grants+Jobs的智能检索（修复阻塞问题）
     */
    public Mono<String> retrieveRelevantContent(String query) {
        try {
            String lowerQuery = query.toLowerCase();
            
            // 检测用户使用的语言
            String detectedLanguage = detectLanguage(query);
            
            // 1. 首先检查工作相关查询 - 优先级最高
            if (isJobRelatedQuery(lowerQuery)) {
                return processJobQuery(lowerQuery, detectedLanguage);
            }
            
            // 2. 检查FAQ和Grants
            List<FAQ> matchedFAQs = findMatchingFAQs(lowerQuery);
            if (!matchedFAQs.isEmpty()) {
                return Mono.just(formatFAQResponse(matchedFAQs, detectedLanguage));
            }
            
            // 3. 检查是否为低相关性查询（需要引导用户重新输入）
            if (isLowRelevanceQuery(lowerQuery)) {
                return Mono.just(generateLowRelevanceResponse(lowerQuery, detectedLanguage));
            }
            
            // 4. 如果没有直接匹配但不是完全无关，返回相关的通用信息
            return Mono.just(getRelatedGuidance(lowerQuery, detectedLanguage));
            
        } catch (Exception e) {
            System.err.println("Enhanced FAQ RAG检索错误: " + e.getMessage());
            return Mono.just(getDefaultGuidance("chinese")); // 默认中文
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
     * 为找不到工作时提供指导
     */
    private String getJobSearchGuidance(String query, String language) {
        if ("chinese".equals(language)) {
            return String.format("""
                很抱歉，我没有找到与"%s"直接匹配的工作信息。
                
                **建议您可以尝试：**
                • 使用更具体的职位名称，如"软件开发员"、"会计师"、"护士"
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
        } else {
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
                • "What jobs are suitable for me?" (try our career quiz first)
                • "What jobs are in major group 1?" (by MASCO classification)
                
                I can also help you learn about government grants, AI tools, and other information!
                """, query);
        }
    }

    /**
     * 检测用户使用的语言
     */
    private String detectLanguage(String query) {
        int chineseChars = 0;
        int englishChars = 0;
        
        // 统计中文字符数
        if (chinesePattern.matcher(query).find()) {
            chineseChars = query.replaceAll("[^\\u4e00-\\u9fff]", "").length();
        }
        
        // 统计英文字符数
        if (englishPattern.matcher(query).find()) {
            englishChars = query.replaceAll("[^a-zA-Z]", "").length();
        }
        
        // 根据字符数比例决定语言
        if (chineseChars > englishChars) {
            return "chinese";
        } else if (englishChars > chineseChars) {
            return "english";
        } else {
            // 如果相等或都为0，检查是否包含中文
            return chinesePattern.matcher(query).find() ? "chinese" : "english";
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
        if ("chinese".equals(language)) {
            return generateChineseLowRelevanceResponse(query);
        } else {
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
     * 根据查询内容提供相关建议
     */
    private String getSuggestionForQuery(String query, String language) {
        String lowerQuery = query.toLowerCase();
        
        if ("chinese".equals(language)) {
            if (containsKeywords(lowerQuery, "天气", "weather", "气温", "下雨")) {
                return "**建议：** 如果您想了解工作地点附近的设施，可以问我「地图功能怎么用？」";
            } else if (containsKeywords(lowerQuery, "吃饭", "餐厅", "食物", "restaurant", "food")) {
                return "**建议：** 如果您关心工作地点的生活设施，可以问我「怎么查看工作地点周边的支持服务？」";
            } else if (containsKeywords(lowerQuery, "学习", "课程", "培训", "education", "course", "training")) {
                return "**建议：** 我们有相关培训信息!您可以问我「政府有什么技能培训计划？」";
            } else if (containsKeywords(lowerQuery, "钱", "薪水", "收入", "money", "salary", "income")) {
                return "**建议：** 如果您想了解财政支持，可以问我「有什么补助金或财政援助？」";
            } else if (containsKeywords(lowerQuery, "专业", "技术", "职业")) {
                return "**建议：** 您可以问我「有什么技术类工作？」或「专业组2有什么职业？」";
            }
            return "**提示：** 请尝试问我关于具体工作、职业发展、AI工具使用或政府补助的问题。";
        } else {
            if (containsKeywords(lowerQuery, "weather", "temperature", "rain", "天气")) {
                return "**Suggestion:** If you want to know about facilities near workplaces, you can ask me 'How to use the map function?'";
            } else if (containsKeywords(lowerQuery, "restaurant", "food", "dining", "eat")) {
                return "**Suggestion:** If you're concerned about living facilities near work locations, ask me 'How to check support services around workplaces?'";
            } else if (containsKeywords(lowerQuery, "study", "course", "training", "education", "learn")) {
                return "**Suggestion:** We have training information! You can ask me 'What government skill training programs are available?'";
            } else if (containsKeywords(lowerQuery, "money", "salary", "income", "pay")) {
                return "**Suggestion:** If you want to know about financial support, ask me 'What grants or financial assistance are available?'";
            } else if (containsKeywords(lowerQuery, "professional", "technical", "career")) {
                return "**Suggestion:** You can ask me 'What technical jobs are available?' or 'What careers are in major group 2?'";
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
     * 格式化FAQ回复（支持多语言）
     */
    private String formatFAQResponse(List<FAQ> faqs, String language) {
        StringBuilder response = new StringBuilder();
        
        if ("chinese".equals(language)) {
            response.append("根据CitaCita平台的信息,以下资源可能对您有帮助:\n\n");
        } else {
            response.append("Based on CitaCita platform information, the following resources may help you:\n\n");
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

    /**
     * 获取相关指导信息（支持多语言）
     */
    private String getRelatedGuidance(String query, String language) {
        // 检查补助金相关关键词
        if (containsKeywords(query, "grant", "financial", "assistance", "funding", "support", "subsidy", 
                           "补助", "资助", "财政", "津贴", "支持", "补贴", "税务", "减免")) {
            if ("chinese".equals(language)) {
                return """
                    马来西亚为重返职场的女性提供多种财政支持和补助计划：
                    
                    **主要计划包括：**
                    • **Career Comeback Programme** - TalentCorp职业回归计划,提供12个月个人所得税减免
                    • **雇主税务激励** - 雇主聘用女性回归者可获得50%额外税务扣除
                    • **灵活工作安排支持** - FWA实施支持和税务优惠
                    • **MYFutureJobs女性倡议** - 重新技能培训和就业安置
                    • **创业融资计划** - DanaNITA、WinBiz等女性企业家专项融资
                    
                    请告诉我您具体需要哪种类型的支持，我可以提供更详细的信息。
                    """;
            } else {
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
        
        // 其他类别的指导信息也按语言返回...
        return getDefaultGuidance(language);
    }

    private boolean containsKeywords(String query, String... keywords) {
        for (String keyword : keywords) {
            if (query.contains(keyword.toLowerCase())) {
                return true;
            }
        }
        return false;
    }

    private String getDefaultGuidance(String language) {
        if ("chinese".equals(language)) {
            return """
                欢迎使用CitaCita职业匹配平台!我可以帮助您:
                
                **工作搜索** - 浏览MASCO职业分类中的详细工作信息和要求
                **职业测验** - 通过测验找到适合的工作建议  
                **AI工具** - 使用简历检查、面试练习等AI功能
                **支持服务** - 查找托儿所等工作支持设施
                **财政支持** - 了解政府补助金和财政援助计划
                
                请告诉我您具体想了解什么，我会为您提供更详细的信息!
                """;
        } else {
            return """
                Welcome to CitaCita career matching platform! I can help you with:
                
                **Job Search** - Browse detailed job information and requirements from MASCO occupation classification
                **Career Quiz** - Find suitable job suggestions through quizzes
                **AI Tools** - Use resume checking, interview practice and other AI features
                **Support Services** - Find childcare and other workplace support facilities
                **Financial Support** - Learn about government grants and financial assistance programs
                
                Please tell me what you'd like to know more about, and I'll provide detailed information!
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
            Jobs are displayed using the MASCO (Malaysian Standard Classification of Occupations) 2020 system:
            
            1. **By Major Groups** - 9 major occupational groups (1-9)
            2. **By Sub-Major Groups** - More specific occupational categories  
            3. **By Minor Groups** - Detailed occupational families
            4. **Unit Groups** - Specific job titles with comprehensive descriptions
            
            Each job includes detailed information about tasks, requirements, skill levels, examples, and multilingual support (English, Chinese, Malay).
            """,
            Arrays.asList("job", "information", "find", "industry", "masco", "occupation", "工作", "信息", "行业", "职业", "分类")
        ));
        
        // 2. 工作测验
        faqs.put("job_quiz", new FAQ(
            "What is the job quiz and how does it work?",
            """
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
            The platform provides AI-driven resume builders, interview coaching suggestions, and even role models' stories to inspire women re-entering the workforce. These tools are designed to boost confidence step by step.
            """,
            Arrays.asList("ai", "confidence", "career", "readiness", "resume", "interview", "智能", "信心", "职业", "简历", "面试")
        ));
        
        // 7. AI简历检查器
        faqs.put("resume_checker", new FAQ(
            "What is the AI Resume Checker?",
            """
            The Resume Checker reviews your uploaded resume and highlights strengths and areas for improvement. It checks for clarity, keywords that match job descriptions, and missing information. You'll receive practical suggestions to make your resume stronger and more competitive.
            """,
            Arrays.asList("resume", "checker", "upload", "review", "keywords", "简历", "检查", "上传", "关键词")
        ));
        
        // 8. AI模拟面试
        faqs.put("mock_interview", new FAQ(
            "How does the AI Mock Interview tool help?",
            """
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