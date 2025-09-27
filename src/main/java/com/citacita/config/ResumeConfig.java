package com.citacita.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import java.util.Arrays;
import java.util.List;

@Data
@Component
@ConfigurationProperties(prefix = "citacita.resume")
public class ResumeConfig {
    private String uploadPath = "uploads/resumes";
    private long maxFileSize = 10485760L; // 10MB
    private List<String> allowedTypes = Arrays.asList(
        "application/pdf",
        "application/msword",
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        "text/plain"
    );
}