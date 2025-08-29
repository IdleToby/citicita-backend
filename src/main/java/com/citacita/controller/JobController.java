package com.citacita.controller;

import com.citacita.dto.JobDTO;
import com.citacita.dto.ResultDTO;
import com.citacita.service.JobService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/job")
public class JobController {
    @Resource
    private JobService skillService;


    @GetMapping("/getJobListByLangAndMajorGroupCode")
    public ResultDTO<List<JobDTO>> getJobListByLangAndMajorGroupCode(@RequestParam(name = "lang", defaultValue = "en") String lang, String majorGroupCode) {
        List<JobDTO> rows = skillService.getJobListByLangAndId(lang, majorGroupCode);
        return ResultDTO.success(rows);
    }

    @GetMapping("/getDetailJobByLangAndUnitGroupCode")
    public ResultDTO<JobDTO> getDetailJobByLangAndUnitGroupCode(@RequestParam(name = "lang", defaultValue = "en") String lang, String unitGroupCode) {
        JobDTO data = skillService.getDetailJobByLangAndUnitGroupCode(lang, unitGroupCode);
        return ResultDTO.success(data);
    }

    @GetMapping("/autoCompleteJobByLangAndUnitGroupTitle")
    public ResultDTO<List<JobDTO>> getJobListByLangAndUnitGroupTitle(@RequestParam(name = "lang", defaultValue = "en") String lang, String unitGroupTitle) {
        List<JobDTO> rows = skillService.getJobListByLangAndUnitGroupTitle(lang, unitGroupTitle);
        return ResultDTO.success(rows);
    }


}