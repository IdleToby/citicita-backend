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

    // ========== 新增方法 - 为RAG功能添加 ==========
    
    /**
     * 分页获取工作记录（推荐用于大数据量缓存初始化）
     */
    List<MascoJob> selectJobsWithPagination(@Param("offset") int offset, @Param("limit") int limit);
    
    /**
     * 根据关键词搜索工作（多语言支持）
     */
    List<MascoJob> searchJobsByKeywords(@Param("keywords") String keywords, @Param("lang") String lang, @Param("limit") int limit);
    
    /**
     * 获取所有不同的major_group_code
     */
    @Select("SELECT DISTINCT major_group_code FROM masco_job WHERE major_group_code IS NOT NULL ORDER BY major_group_code")
    List<String> selectAllMajorGroupCodes();
    
    /**
     * 统计总记录数
     */
    @Select("SELECT COUNT(*) FROM masco_job")
    Long countAllJobs();
    
    /**
     * 根据多个unit_group_code批量查询
     */
    List<MascoJob> selectByUnitGroupCodes(@Param("unitGroupCodes") List<String> unitGroupCodes, @Param("lang") String lang);
    
    /**
     * 模糊搜索工作标题和描述
     */
    List<MascoJob> searchJobsByTitleAndDescription(@Param("searchTerm") String searchTerm, @Param("lang") String lang, @Param("limit") int limit);
    
    /**
     * 根据unit_group_code获取单个记录（用于快速查找）
     */
    MascoJob selectByUnitGroupCode(@Param("unitGroupCode") String unitGroupCode);
}
