package com.citacita.dto;

import lombok.Builder;
import lombok.Data;
import java.util.List;

@Data
@Builder
public class ResumeStructure {
    private boolean hasContactInfo;
    private boolean hasSummary;
    private boolean hasExperience;
    private boolean hasEducation;
    private boolean hasSkills;
    private boolean hasAchievements;
    private boolean hasProjects;
    private boolean hasLanguages;
    private double completeness;
    private int length;
    private int wordCount;
    private List<String> completenessDetails;  
}
