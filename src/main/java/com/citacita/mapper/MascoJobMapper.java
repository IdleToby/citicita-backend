package com.citacita.mapper;

import com.citacita.entity.MascoJob;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
* @author Toby
* @description 针对表【masco_job】的数据库操作Mapper
* @createDate 2025-08-29 14:08:34
* @Entity com.citacita.entity.MascoJob
*/
public interface MascoJobMapper {

    int deleteByPrimaryKey(Long id);

    int insert(MascoJob record);

    int insertSelective(MascoJob record);

    MascoJob selectByPrimaryKey(Long id);

    int updateByPrimaryKeySelective(MascoJob record);

    int updateByPrimaryKey(MascoJob record);

    @Select("SELECT * FROM masco_job")
    List<MascoJob> selectAll();

    List<MascoJob> selectByMajorGroupCodeAndLang(@Param("lang") String lang, @Param("majorGroupCode") String majorGroupCode);

    MascoJob selectByUnitGroupCodeAndLang(@Param("lang") String lang, @Param("unitGroupCode") String unitGroupCode);

    List<MascoJob> selectByLangAndUnitGroupTitle(@Param("lang") String lang, @Param("unitGroupTitle") String unitGroupTitle);
}
