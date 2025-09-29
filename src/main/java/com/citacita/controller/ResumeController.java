package com.citacita.controller;

import com.citacita.dto.ChatRequest;
import com.citacita.dto.ResumeAnalysisResult;
import com.citacita.service.ResumeAnalyzerService;
import com.citacita.service.ResumeChatService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.http.codec.multipart.FormFieldPart;
import org.springframework.http.codec.multipart.Part;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
@Slf4j
@RequiredArgsConstructor
public class ResumeController {
    
    private final ResumeAnalyzerService resumeAnalyzerService;
    private final ResumeChatService resumeChatService;
    private final ObjectMapper objectMapper;
    
    @PostMapping(value = "/resume-analyze", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Mono<Map<String, Object>> analyzeResume(ServerWebExchange exchange) {
        return exchange.getMultipartData()
            .flatMap(multipartData -> {
                try {
                    log.info("开始处理简历分析请求");
                    
                    // 获取文件部分
                    Part filePart = multipartData.getFirst("file");
                    if (filePart == null || !(filePart instanceof FilePart)) {
                        log.error("没有找到文件部分或文件类型错误");
                        return Mono.just(createErrorResponse("没有上传文件或文件格式错误"));
                    }
                    
                    FilePart file = (FilePart) filePart;
                    log.info("接收到文件: {}", file.filename());
                    
                    // 获取语言参数
                    final String language;
                    Part languagePart = multipartData.getFirst("language");
                    if (languagePart instanceof FormFieldPart) {
                        language = ((FormFieldPart) languagePart).value();
                        log.info("语言参数: {}", language);
                    } else {
                        language = "en";
                        log.info("使用默认语言: {}", language);
                    }
                    
                    // 基本文件检查（让Service做详细验证）
                    if (file.filename() == null || file.filename().trim().isEmpty()) {
                        log.error("文件名为空");
                        return Mono.just(createErrorResponse("文件名不能为空"));
                    }
                    
                    log.info("开始调用分析服务");
                    // 直接调用分析服务，让Service处理所有验证
                    return resumeAnalyzerService.analyzeResumeWebFlux(file, language)
                        .doOnNext(result -> log.info("分析完成: {}", result.getFileName()))
                        .map(result -> createSuccessResponse(result, language))
                        .doOnError(error -> log.error("Service分析失败", error))
                        .onErrorResume(error -> {
                            log.error("处理Service错误", error);
                            return Mono.just(createErrorResponse("分析失败: " + error.getMessage()));
                        });
                        
                } catch (Exception e) {
                    log.error("简历分析请求处理失败", e);
                    return Mono.just(createErrorResponse("请求处理失败: " + e.getMessage()));
                }
            })
            .doOnError(error -> log.error("简历分析过程中发生错误", error))
            .onErrorResume(error -> {
                log.error("最终错误处理", error);
                return Mono.just(createErrorResponse("简历分析失败: " + error.getMessage()));
            });
    }
    
    @PostMapping(value = "/resume-chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> resumeChat(@RequestBody ChatRequest request) {
        if (request.getMessages() == null || request.getMessages().isEmpty()) {
            return Flux.just("data: {\"error\":\"消息列表不能为空\"}\n\n");
        }
        
        // 直接返回service结果，不做任何额外处理
        return resumeChatService.streamChat(request)
                .onErrorResume(e -> {
                    log.error("简历聊天失败", e);
                    return Flux.just("data: {\"error\":\"简历聊天失败\"}\n\ndata: [DONE]\n\n");
                });
    }
        
    @GetMapping("/health")
    public Mono<Map<String, Object>> health() {
        return Mono.just(Map.of(
            "status", "ok",
            "timestamp", LocalDateTime.now(),
            "service", "CitaCita Resume Checker",
            "version", "1.0.0"
        ));
    }
    
    // 移除原来的validateFile方法，让Service处理验证
    
    // 创建成功响应
    private Map<String, Object> createSuccessResponse(ResumeAnalysisResult result, String language) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("fileName", result.getFileName());
        response.put("fileId", result.getFileId());
        response.put("analysis", result);
        response.put("message", getAnalysisCompleteMessage(language));
        response.put("timestamp", LocalDateTime.now());
        return response;
    }
    
    // 创建错误响应
    private Map<String, Object> createErrorResponse(String errorMessage) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", false);
        response.put("error", errorMessage);
        response.put("timestamp", LocalDateTime.now());
        return response;
    }
    
    private String getAnalysisCompleteMessage(String language) {
        Map<String, String> messages = Map.of(
            "zh-CN", "简历分析完成！您可以开始询问关于简历改进的任何问题。",
            "en", "Resume analysis complete! You can start asking any questions about resume improvement.",
            "ms", "Analisis resume selesai! Anda boleh mula bertanya sebarang soalan tentang penambahbaikan resume."
        );
        return messages.getOrDefault(language, messages.get("en"));
    }
}