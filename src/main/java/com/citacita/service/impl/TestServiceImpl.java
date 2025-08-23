package com.citacita.service.impl;

import com.citacita.entity.TestTable;
import com.citacita.mapper.TestTableMapper;
import com.citacita.service.TestService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class TestServiceImpl implements TestService {
    private final TestTableMapper testTableMapper;

    @Autowired
    public TestServiceImpl(TestTableMapper testTableMapper) {
        this.testTableMapper = testTableMapper;
    }

    public List<TestTable> getAllRows() {
        return testTableMapper.selectAll();
    }
}
