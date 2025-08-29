package com.citacita.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class JobDTO {
    private String unitGroupCode;
    private String majorGroupCode;
    private String majorGroupTitle;
    private String subMajorGroupCode;
    private String subMajorGroupTitle;
    private String minorGroupCode;
    private String minorGroupTitle;
    private String unitGroupTitle;
    private String unitGroupDescription;
    private String tasksInclude;
    private String examples;
    private String skillLevel;
}
