package com.citacita.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ResumeStructure {
    private boolean hasContactInfo;
    private boolean hasSummary;
    private boolean hasExperience;
    private boolean hasEducation;
    private boolean hasSkills;
    private boolean hasAchievements;
    private double completeness;
    private int length;
    private int wordCount;
}
