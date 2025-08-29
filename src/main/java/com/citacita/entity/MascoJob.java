package com.citacita.entity;

import java.io.Serializable;
import lombok.Data;

/**
 * @TableName masco_job
 */
@Data
public class MascoJob implements Serializable {
    private String unitGroupCode;

    private String majorGroupCode;

    private String majorGroupTitle;

    private String majorGroupTitleMalay;

    private String majorGroupTitleChinese;

    private String subMajorGroupCode;

    private String subMajorGroupTitle;

    private String subMajorGroupTitleMalay;

    private String subMajorGroupTitleChinese;

    private String minorGroupCode;

    private String minorGroupTitle;

    private String minorGroupTitleMalay;

    private String minorGroupTitleChinese;

    private String unitGroupTitle;

    private String unitGroupTitleMalay;

    private String unitGroupTitleChinese;

    private String unitGroupDescription;

    private String unitGroupDescriptionMalay;

    private String unitGroupDescriptionChinese;

    private String tasksInclude;

    private String tasksIncludeMalay;

    private String tasksIncludeChinese;

    private String examples;

    private String examplesMalay;

    private String examplesChinese;

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
        MascoJob other = (MascoJob) that;
        return (this.getUnitGroupCode() == null ? other.getUnitGroupCode() == null : this.getUnitGroupCode().equals(other.getUnitGroupCode()))
            && (this.getMajorGroupCode() == null ? other.getMajorGroupCode() == null : this.getMajorGroupCode().equals(other.getMajorGroupCode()))
            && (this.getMajorGroupTitle() == null ? other.getMajorGroupTitle() == null : this.getMajorGroupTitle().equals(other.getMajorGroupTitle()))
            && (this.getMajorGroupTitleMalay() == null ? other.getMajorGroupTitleMalay() == null : this.getMajorGroupTitleMalay().equals(other.getMajorGroupTitleMalay()))
            && (this.getMajorGroupTitleChinese() == null ? other.getMajorGroupTitleChinese() == null : this.getMajorGroupTitleChinese().equals(other.getMajorGroupTitleChinese()))
            && (this.getSubMajorGroupCode() == null ? other.getSubMajorGroupCode() == null : this.getSubMajorGroupCode().equals(other.getSubMajorGroupCode()))
            && (this.getSubMajorGroupTitle() == null ? other.getSubMajorGroupTitle() == null : this.getSubMajorGroupTitle().equals(other.getSubMajorGroupTitle()))
            && (this.getSubMajorGroupTitleMalay() == null ? other.getSubMajorGroupTitleMalay() == null : this.getSubMajorGroupTitleMalay().equals(other.getSubMajorGroupTitleMalay()))
            && (this.getSubMajorGroupTitleChinese() == null ? other.getSubMajorGroupTitleChinese() == null : this.getSubMajorGroupTitleChinese().equals(other.getSubMajorGroupTitleChinese()))
            && (this.getMinorGroupCode() == null ? other.getMinorGroupCode() == null : this.getMinorGroupCode().equals(other.getMinorGroupCode()))
            && (this.getMinorGroupTitle() == null ? other.getMinorGroupTitle() == null : this.getMinorGroupTitle().equals(other.getMinorGroupTitle()))
            && (this.getMinorGroupTitleMalay() == null ? other.getMinorGroupTitleMalay() == null : this.getMinorGroupTitleMalay().equals(other.getMinorGroupTitleMalay()))
            && (this.getMinorGroupTitleChinese() == null ? other.getMinorGroupTitleChinese() == null : this.getMinorGroupTitleChinese().equals(other.getMinorGroupTitleChinese()))
            && (this.getUnitGroupTitle() == null ? other.getUnitGroupTitle() == null : this.getUnitGroupTitle().equals(other.getUnitGroupTitle()))
            && (this.getUnitGroupTitleMalay() == null ? other.getUnitGroupTitleMalay() == null : this.getUnitGroupTitleMalay().equals(other.getUnitGroupTitleMalay()))
            && (this.getUnitGroupTitleChinese() == null ? other.getUnitGroupTitleChinese() == null : this.getUnitGroupTitleChinese().equals(other.getUnitGroupTitleChinese()))
            && (this.getUnitGroupDescription() == null ? other.getUnitGroupDescription() == null : this.getUnitGroupDescription().equals(other.getUnitGroupDescription()))
            && (this.getUnitGroupDescriptionMalay() == null ? other.getUnitGroupDescriptionMalay() == null : this.getUnitGroupDescriptionMalay().equals(other.getUnitGroupDescriptionMalay()))
            && (this.getUnitGroupDescriptionChinese() == null ? other.getUnitGroupDescriptionChinese() == null : this.getUnitGroupDescriptionChinese().equals(other.getUnitGroupDescriptionChinese()))
            && (this.getTasksInclude() == null ? other.getTasksInclude() == null : this.getTasksInclude().equals(other.getTasksInclude()))
            && (this.getTasksIncludeMalay() == null ? other.getTasksIncludeMalay() == null : this.getTasksIncludeMalay().equals(other.getTasksIncludeMalay()))
            && (this.getTasksIncludeChinese() == null ? other.getTasksIncludeChinese() == null : this.getTasksIncludeChinese().equals(other.getTasksIncludeChinese()))
            && (this.getExamples() == null ? other.getExamples() == null : this.getExamples().equals(other.getExamples()))
            && (this.getExamplesMalay() == null ? other.getExamplesMalay() == null : this.getExamplesMalay().equals(other.getExamplesMalay()))
            && (this.getExamplesChinese() == null ? other.getExamplesChinese() == null : this.getExamplesChinese().equals(other.getExamplesChinese()))
            && (this.getSkillLevel() == null ? other.getSkillLevel() == null : this.getSkillLevel().equals(other.getSkillLevel()))
            && (this.getSkillLevelMalay() == null ? other.getSkillLevelMalay() == null : this.getSkillLevelMalay().equals(other.getSkillLevelMalay()))
            && (this.getSkillLevelChinese() == null ? other.getSkillLevelChinese() == null : this.getSkillLevelChinese().equals(other.getSkillLevelChinese()));
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((getUnitGroupCode() == null) ? 0 : getUnitGroupCode().hashCode());
        result = prime * result + ((getMajorGroupCode() == null) ? 0 : getMajorGroupCode().hashCode());
        result = prime * result + ((getMajorGroupTitle() == null) ? 0 : getMajorGroupTitle().hashCode());
        result = prime * result + ((getMajorGroupTitleMalay() == null) ? 0 : getMajorGroupTitleMalay().hashCode());
        result = prime * result + ((getMajorGroupTitleChinese() == null) ? 0 : getMajorGroupTitleChinese().hashCode());
        result = prime * result + ((getSubMajorGroupCode() == null) ? 0 : getSubMajorGroupCode().hashCode());
        result = prime * result + ((getSubMajorGroupTitle() == null) ? 0 : getSubMajorGroupTitle().hashCode());
        result = prime * result + ((getSubMajorGroupTitleMalay() == null) ? 0 : getSubMajorGroupTitleMalay().hashCode());
        result = prime * result + ((getSubMajorGroupTitleChinese() == null) ? 0 : getSubMajorGroupTitleChinese().hashCode());
        result = prime * result + ((getMinorGroupCode() == null) ? 0 : getMinorGroupCode().hashCode());
        result = prime * result + ((getMinorGroupTitle() == null) ? 0 : getMinorGroupTitle().hashCode());
        result = prime * result + ((getMinorGroupTitleMalay() == null) ? 0 : getMinorGroupTitleMalay().hashCode());
        result = prime * result + ((getMinorGroupTitleChinese() == null) ? 0 : getMinorGroupTitleChinese().hashCode());
        result = prime * result + ((getUnitGroupTitle() == null) ? 0 : getUnitGroupTitle().hashCode());
        result = prime * result + ((getUnitGroupTitleMalay() == null) ? 0 : getUnitGroupTitleMalay().hashCode());
        result = prime * result + ((getUnitGroupTitleChinese() == null) ? 0 : getUnitGroupTitleChinese().hashCode());
        result = prime * result + ((getUnitGroupDescription() == null) ? 0 : getUnitGroupDescription().hashCode());
        result = prime * result + ((getUnitGroupDescriptionMalay() == null) ? 0 : getUnitGroupDescriptionMalay().hashCode());
        result = prime * result + ((getUnitGroupDescriptionChinese() == null) ? 0 : getUnitGroupDescriptionChinese().hashCode());
        result = prime * result + ((getTasksInclude() == null) ? 0 : getTasksInclude().hashCode());
        result = prime * result + ((getTasksIncludeMalay() == null) ? 0 : getTasksIncludeMalay().hashCode());
        result = prime * result + ((getTasksIncludeChinese() == null) ? 0 : getTasksIncludeChinese().hashCode());
        result = prime * result + ((getExamples() == null) ? 0 : getExamples().hashCode());
        result = prime * result + ((getExamplesMalay() == null) ? 0 : getExamplesMalay().hashCode());
        result = prime * result + ((getExamplesChinese() == null) ? 0 : getExamplesChinese().hashCode());
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
        sb.append(", unitGroupCode=").append(unitGroupCode);
        sb.append(", majorGroupCode=").append(majorGroupCode);
        sb.append(", majorGroupTitle=").append(majorGroupTitle);
        sb.append(", majorGroupTitleMalay=").append(majorGroupTitleMalay);
        sb.append(", majorGroupTitleChinese=").append(majorGroupTitleChinese);
        sb.append(", subMajorGroupCode=").append(subMajorGroupCode);
        sb.append(", subMajorGroupTitle=").append(subMajorGroupTitle);
        sb.append(", subMajorGroupTitleMalay=").append(subMajorGroupTitleMalay);
        sb.append(", subMajorGroupTitleChinese=").append(subMajorGroupTitleChinese);
        sb.append(", minorGroupCode=").append(minorGroupCode);
        sb.append(", minorGroupTitle=").append(minorGroupTitle);
        sb.append(", minorGroupTitleMalay=").append(minorGroupTitleMalay);
        sb.append(", minorGroupTitleChinese=").append(minorGroupTitleChinese);
        sb.append(", unitGroupTitle=").append(unitGroupTitle);
        sb.append(", unitGroupTitleMalay=").append(unitGroupTitleMalay);
        sb.append(", unitGroupTitleChinese=").append(unitGroupTitleChinese);
        sb.append(", unitGroupDescription=").append(unitGroupDescription);
        sb.append(", unitGroupDescriptionMalay=").append(unitGroupDescriptionMalay);
        sb.append(", unitGroupDescriptionChinese=").append(unitGroupDescriptionChinese);
        sb.append(", tasksInclude=").append(tasksInclude);
        sb.append(", tasksIncludeMalay=").append(tasksIncludeMalay);
        sb.append(", tasksIncludeChinese=").append(tasksIncludeChinese);
        sb.append(", examples=").append(examples);
        sb.append(", examplesMalay=").append(examplesMalay);
        sb.append(", examplesChinese=").append(examplesChinese);
        sb.append(", skillLevel=").append(skillLevel);
        sb.append(", skillLevelMalay=").append(skillLevelMalay);
        sb.append(", skillLevelChinese=").append(skillLevelChinese);
        sb.append(", serialVersionUID=").append(serialVersionUID);
        sb.append("]");
        return sb.toString();
    }
}