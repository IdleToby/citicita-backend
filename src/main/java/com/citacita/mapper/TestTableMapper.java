package com.citacita.mapper;

import com.citacita.entity.TestTable;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
* @author Toby
* @description 针对表【test_table】的数据库操作Mapper
* @createDate 2025-08-23 22:15:26
* @Entity com.citacita.entity.TestTable
*/
@Mapper
public interface TestTableMapper {

    int deleteByPrimaryKey(Long id);

    int insert(TestTable record);

    int insertSelective(TestTable record);

    TestTable selectByPrimaryKey(Long id);

    int updateByPrimaryKeySelective(TestTable record);

    int updateByPrimaryKey(TestTable record);

    @Select("SELECT * FROM test_table")
    List<TestTable> selectAll();
}
