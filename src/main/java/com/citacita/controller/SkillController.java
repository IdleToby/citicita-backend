package com.citacita.controller;

import com.citacita.dto.ResultDTO;
import com.citacita.dto.SkillLevelDTO;
import com.citacita.service.SkillService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/skill")
public class SkillController {
    @Resource
    private SkillService skillService;


    @GetMapping("/getSkillLevelByLang")
    public ResultDTO<List<SkillLevelDTO>> getSkillLevelByLang(@RequestParam(name = "lang", defaultValue = "en") String lang) {
        List<SkillLevelDTO> rows = skillService.getSkillsByLang(lang);
        return ResultDTO.success(rows);
    }

    @GetMapping("/getSkillLevelByLangAndId")
    public ResultDTO<SkillLevelDTO> getSkillLevelByLangAndId(@RequestParam(name = "lang", defaultValue = "en") String lang, @RequestParam(name = "majorGroupCode") String majorGroupCode) {
        SkillLevelDTO skillLevel = skillService.getSkillLevelByLangAndId(lang, majorGroupCode);
        return ResultDTO.success(skillLevel);
    }

}