package com.citacita.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ResumeAnalysisResult {
    private String fileName;
    private String fileId;
    private ResumeStructure structure;
    private ResumeKeyInfo keyInfo;
    private int qualityScore;
    private List<ResumeSuggestion> suggestions;
    private String textContent;
    private LocalDateTime analyzedAt;
}
