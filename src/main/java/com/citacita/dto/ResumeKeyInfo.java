package com.citacita.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ResumeKeyInfo {
    private List<String> emails;
    private List<String> phones;
    private List<String> skills;
    private int yearsOfExperience;
    private String educationLevel;
}
