package com.citacita.controller;

import com.citacita.dto.ResultDTO;
import com.citacita.service.TestService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/test")
public class TestController {
    private final TestService testService;

    @Autowired
    public TestController(TestService testService) {
        this.testService = testService;
    }

    @GetMapping("/testTable")
    public ResultDTO testTable() {
        List<?> rows = testService.getAllRows();
        return ResultDTO.success(rows);
    }

}