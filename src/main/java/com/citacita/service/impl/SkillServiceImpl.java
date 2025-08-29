package com.citacita.service.impl;

import com.citacita.dto.SkillLevelDTO;
import com.citacita.entity.MajorGroupSkillLevels;
import com.citacita.mapper.MajorGroupSkillLevelsMapper;
import com.citacita.service.SkillService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class SkillServiceImpl implements SkillService {
    @Resource
    private MajorGroupSkillLevelsMapper majorGroupSkillLevelsMapper;


    @Override
    public List<?> getAllSkills() {
        return majorGroupSkillLevelsMapper.selectAll();
    }

    @Override
    public List<SkillLevelDTO> getSkillsByLang(String lang) {
        // 1. Fetch the list of entities from the database.
        List<MajorGroupSkillLevels> entities = majorGroupSkillLevelsMapper.selectByLang(lang);

        // Handle the case where the database returns nothing.
        if (entities == null || entities.isEmpty()) {
            return Collections.emptyList();
        }

        // 2. Convert the list of entities into a list of DTOs.
        return entities.stream()
                .map(this::convertToDto) // Use the helper method for conversion
                .collect(Collectors.toList());
    }

    @Override
    public SkillLevelDTO getSkillLevelByLangAndId(String lang, String majorGroupCode) {
        // 1. Fetch the single entity from the database.
        MajorGroupSkillLevels entity = majorGroupSkillLevelsMapper.selectByLangAndId(lang, majorGroupCode);

        // 2. If the entity is found, convert it to a DTO; otherwise, return null.
        return (entity != null) ? convertToDto(entity) : null;
    }

    private SkillLevelDTO convertToDto(MajorGroupSkillLevels entity) {
        SkillLevelDTO dto = new SkillLevelDTO();
        dto.setMajorGroupCode(entity.getMajorGroupCode());
        dto.setMajorGroupTitle(entity.getMajorGroupTitle());
        dto.setEducationLevel(entity.getEducationLevel());
        dto.setSkillLevel(entity.getSkillLevel());
        return dto;
    }
}
