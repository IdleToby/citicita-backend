package com.citacita.entity;

import java.io.Serializable;
import lombok.Data;

/**
 * @TableName major_group_skill_levels
 */
@Data
public class MajorGroupSkillLevels implements Serializable {
    private String majorGroupCode;

    private String majorGroupTitle;

    private String majorGroupTitleMalay;

    private String majorGroupTitleChinese;

    private String educationLevel;

    private String educationLevelMalay;

    private String educationLevelChinese;

    private String skillLevel;

    private String skillLevelMalay;

    private String skillLevelChinese;

    private static final long serialVersionUID = 1L;

    @Override
    public boolean equals(Object that) {
        if (this == that) {
            return true;
        }
        if (that == null) {
            return false;
        }
        if (getClass() != that.getClass()) {
            return false;
        }
        MajorGroupSkillLevels other = (MajorGroupSkillLevels) that;
        return (this.getMajorGroupCode() == null ? other.getMajorGroupCode() == null : this.getMajorGroupCode().equals(other.getMajorGroupCode()))
            && (this.getMajorGroupTitle() == null ? other.getMajorGroupTitle() == null : this.getMajorGroupTitle().equals(other.getMajorGroupTitle()))
            && (this.getMajorGroupTitleMalay() == null ? other.getMajorGroupTitleMalay() == null : this.getMajorGroupTitleMalay().equals(other.getMajorGroupTitleMalay()))
            && (this.getMajorGroupTitleChinese() == null ? other.getMajorGroupTitleChinese() == null : this.getMajorGroupTitleChinese().equals(other.getMajorGroupTitleChinese()))
            && (this.getEducationLevel() == null ? other.getEducationLevel() == null : this.getEducationLevel().equals(other.getEducationLevel()))
            && (this.getEducationLevelMalay() == null ? other.getEducationLevelMalay() == null : this.getEducationLevelMalay().equals(other.getEducationLevelMalay()))
            && (this.getEducationLevelChinese() == null ? other.getEducationLevelChinese() == null : this.getEducationLevelChinese().equals(other.getEducationLevelChinese()))
            && (this.getSkillLevel() == null ? other.getSkillLevel() == null : this.getSkillLevel().equals(other.getSkillLevel()))
            && (this.getSkillLevelMalay() == null ? other.getSkillLevelMalay() == null : this.getSkillLevelMalay().equals(other.getSkillLevelMalay()))
            && (this.getSkillLevelChinese() == null ? other.getSkillLevelChinese() == null : this.getSkillLevelChinese().equals(other.getSkillLevelChinese()));
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((getMajorGroupCode() == null) ? 0 : getMajorGroupCode().hashCode());
        result = prime * result + ((getMajorGroupTitle() == null) ? 0 : getMajorGroupTitle().hashCode());
        result = prime * result + ((getMajorGroupTitleMalay() == null) ? 0 : getMajorGroupTitleMalay().hashCode());
        result = prime * result + ((getMajorGroupTitleChinese() == null) ? 0 : getMajorGroupTitleChinese().hashCode());
        result = prime * result + ((getEducationLevel() == null) ? 0 : getEducationLevel().hashCode());
        result = prime * result + ((getEducationLevelMalay() == null) ? 0 : getEducationLevelMalay().hashCode());
        result = prime * result + ((getEducationLevelChinese() == null) ? 0 : getEducationLevelChinese().hashCode());
        result = prime * result + ((getSkillLevel() == null) ? 0 : getSkillLevel().hashCode());
        result = prime * result + ((getSkillLevelMalay() == null) ? 0 : getSkillLevelMalay().hashCode());
        result = prime * result + ((getSkillLevelChinese() == null) ? 0 : getSkillLevelChinese().hashCode());
        return result;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(getClass().getSimpleName());
        sb.append(" [");
        sb.append("Hash = ").append(hashCode());
        sb.append(", majorGroupCode=").append(majorGroupCode);
        sb.append(", majorGroupTitle=").append(majorGroupTitle);
        sb.append(", majorGroupTitleMalay=").append(majorGroupTitleMalay);
        sb.append(", majorGroupTitleChinese=").append(majorGroupTitleChinese);
        sb.append(", educationLevel=").append(educationLevel);
        sb.append(", educationLevelMalay=").append(educationLevelMalay);
        sb.append(", educationLevelChinese=").append(educationLevelChinese);
        sb.append(", skillLevel=").append(skillLevel);
        sb.append(", skillLevelMalay=").append(skillLevelMalay);
        sb.append(", skillLevelChinese=").append(skillLevelChinese);
        sb.append(", serialVersionUID=").append(serialVersionUID);
        sb.append("]");
        return sb.toString();
    }
}