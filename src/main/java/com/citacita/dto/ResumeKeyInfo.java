package com.citacita.dto;

import lombok.Builder;
import lombok.Data;
import java.util.List;

@Data
@Builder
public class ResumeKeyInfo {
    private List<String> emails;
    private List<String> phones;
    private List<String> skills;
    private int yearsOfExperience;
    private String educationLevel;
    
    // 新增字段
    private String workExperienceType;    // 工作经验类型说明
    private int internshipYears;          // 实习年数
}
