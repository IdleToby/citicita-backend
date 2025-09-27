package com.citacita.config;

import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Data
@Component
public class AzureOpenAIConfig {
    
    @Value("${azure.openai.endpoint}")
    private String endpoint;
    
    @Value("${azure.openai.apiKey}")
    private String apiKey;
    
    @Value("${azure.openai.resume.temperature:0.7}")
    private Double temperature;
    
    @Value("${azure.openai.resume.max-tokens:2000}")
    private Integer maxTokens;
}
