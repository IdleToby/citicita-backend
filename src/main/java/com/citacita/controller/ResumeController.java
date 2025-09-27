package com.citacita.controller;

import com.citacita.dto.ChatRequest;
import com.citacita.dto.ResumeAnalysisResult;
import com.citacita.service.ResumeAnalyzerService;
import com.citacita.service.ResumeChatService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
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
                        Map<String, Object> errorResponse = new HashMap<>();
                        errorResponse.put("error", "没有上传文件或文件格式错误");
                        return Mono.just(errorResponse);
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
                    
                    // 验证文件
                    return validateFile(file)
                        .doOnNext(valid -> log.info("文件验证结果: {}", valid))
                        .flatMap(valid -> {
                            if (!valid) {
                                log.error("文件验证失败");
                                Map<String, Object> errorResponse = new HashMap<>();
                                errorResponse.put("error", "文件验证失败");
                                return Mono.just(errorResponse);
                            }
                            
                            log.info("开始调用分析服务");
                            // 调用分析服务
                            return resumeAnalyzerService.analyzeResumeWebFlux(file, language)
                                .doOnNext(result -> log.info("分析完成: {}", result.getFileName()))
                                .map(result -> {
                                    Map<String, Object> response = new HashMap<>();
                                    response.put("success", true);
                                    response.put("fileName", result.getFileName());
                                    response.put("fileId", result.getFileId());
                                    response.put("analysis", result);
                                    response.put("message", getAnalysisCompleteMessage(language));
                                    return response;
                                });
                        });
                        
                } catch (Exception e) {
                    log.error("简历分析请求处理失败", e);
                    Map<String, Object> errorResponse = new HashMap<>();
                    errorResponse.put("error", "请求处理失败: " + e.getMessage());
                    return Mono.just(errorResponse);
                }
            })
            .doOnError(error -> log.error("简历分析过程中发生错误", error))
            .onErrorResume(error -> {
                log.error("最终错误处理", error);
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("error", "简历分析失败: " + error.getMessage());
                return Mono.just(errorResponse);
            });
    }
    
    // @PostMapping(value = "/resume-chat", produces = MediaType.TEXT_PLAIN_VALUE)
    // public Flux<String> resumeChat(@RequestBody ChatRequest request) {
    //     try {
    //         // 验证请求
    //         if (request.getMessages() == null || request.getMessages().isEmpty()) {
    //             return Flux.just("data: " + createErrorResponse("消息列表不能为空") + "\n\n");
    //         }
            
    //         return resumeChatService.streamChat(request);
            
    //     } catch (Exception e) {
    //         log.error("简历聊天失败", e);
    //         return Flux.just("data: " + createErrorResponse("简历聊天失败: " + e.getMessage()) + "\n\n");
    //     }
    // }
    @PostMapping(value = "/resume-chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> resumeChat(@RequestBody ChatRequest request) {
        if (request.getMessages() == null || request.getMessages().isEmpty()) {
            return Flux.just("data: {\"error\":\"消息列表不能为空\"}\n\n");
        }
        
        // 🔥 关键修复：直接返回service结果，不做任何额外处理
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
    
    private Mono<Boolean> validateFile(FilePart file) {
        return Mono.fromCallable(() -> {
            // 检查文件名
            String filename = file.filename();
            if (filename == null || filename.trim().isEmpty()) {
                log.error("文件名为空");
                return false;
            }
            
            // 检查文件类型
            String contentType = file.headers().getContentType() != null 
                ? file.headers().getContentType().toString() 
                : "";
            
            boolean validType = contentType.equals("application/pdf") ||
                               contentType.equals("application/msword") ||
                               contentType.equals("application/vnd.openxmlformats-officedocument.wordprocessingml.document") ||
                               contentType.equals("text/plain") ||
                               filename.toLowerCase().endsWith(".pdf") ||
                               filename.toLowerCase().endsWith(".doc") ||
                               filename.toLowerCase().endsWith(".docx") ||
                               filename.toLowerCase().endsWith(".txt");
            
            if (!validType) {
                log.error("不支持的文件类型: {} for file: {}", contentType, filename);
                return false;
            }
            
            return true;
        });
    }
    
    private String getAnalysisCompleteMessage(String language) {
        Map<String, String> messages = Map.of(
            "zh-CN", "简历分析完成！您可以开始询问关于简历改进的任何问题。",
            "en", "Resume analysis complete! You can start asking any questions about resume improvement.",
            "ms", "Analisis resume selesai! Anda boleh mula bertanya sebarang soalan tentang penambahbaikan resume."
        );
        return messages.getOrDefault(language, messages.get("en"));
    }
    
    private String createErrorResponse(String errorMessage) {
        try {
            return objectMapper.writeValueAsString(Map.of("error", errorMessage));
        } catch (Exception e) {
            return "{\"error\":\"" + errorMessage + "\"}";
        }
    }
}