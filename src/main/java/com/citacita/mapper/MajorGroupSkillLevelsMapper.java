package com.citacita.mapper;

import com.citacita.entity.MajorGroupSkillLevels;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
* @author Toby
* @description 针对表【major_group_skill_levels】的数据库操作Mapper
* @createDate 2025-08-29 01:24:09
* @Entity com.citacita.entity.MajorGroupSkillLevels
*/
public interface MajorGroupSkillLevelsMapper {

    int deleteByPrimaryKey(Long id);

    int insert(MajorGroupSkillLevels record);

    int insertSelective(MajorGroupSkillLevels record);

    MajorGroupSkillLevels selectByPrimaryKey(Long id);

    int updateByPrimaryKeySelective(MajorGroupSkillLevels record);

    int updateByPrimaryKey(MajorGroupSkillLevels record);

    @Select("SELECT * FROM major_group_skill_levels")
    List<MajorGroupSkillLevels> selectAll();

    List<MajorGroupSkillLevels> selectByLang(@Param("lang") String lang);

    MajorGroupSkillLevels selectByLangAndId(@Param("lang") String lang, @Param("majorGroupCode") String majorGroupCode);
}
