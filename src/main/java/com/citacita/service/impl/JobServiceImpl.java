package com.citacita.service.impl;

import com.citacita.dto.JobDTO;
import com.citacita.entity.MascoJob;
import com.citacita.mapper.MascoJobMapper;
import com.citacita.service.JobService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class JobServiceImpl implements JobService {
    @Resource
    private MascoJobMapper mascoJobMapper;

    @Override
    public List<JobDTO> getJobListByLangAndId(String lang, String majorGroupCode) {
        List<MascoJob> entities = mascoJobMapper.selectByMajorGroupCodeAndLang(lang, majorGroupCode);
        return entities.stream()
                .map(this::convertToDto)
                .toList();
    }

    @Override
    public JobDTO getDetailJobByLangAndUnitGroupCode(String lang, String unitGroupCode) {
        MascoJob entity = mascoJobMapper.selectByUnitGroupCodeAndLang(lang, unitGroupCode);
        return (entity != null) ? convertToDto(entity) : null;
    }

    @Override
    public List<JobDTO> getJobListByLangAndUnitGroupTitle(String lang, String unitGroupTitle) {
        List<MascoJob> entities = mascoJobMapper.selectByLangAndUnitGroupTitle(lang, unitGroupTitle);
        return entities.stream()
                .map(this::convertToDto)
                .toList();
    }

    private JobDTO convertToDto(MascoJob entity) {
        JobDTO dto = new JobDTO();
        dto.setUnitGroupCode(entity.getUnitGroupCode());
        dto.setMajorGroupCode(entity.getMajorGroupCode());
        dto.setMajorGroupTitle(entity.getMajorGroupTitle());
        dto.setSubMajorGroupCode(entity.getSubMajorGroupCode());
        dto.setSubMajorGroupTitle(entity.getSubMajorGroupTitle());
        dto.setMinorGroupCode(entity.getMinorGroupCode());
        dto.setMinorGroupTitle(entity.getMinorGroupTitle());
        dto.setUnitGroupTitle(entity.getUnitGroupTitle());
        dto.setUnitGroupDescription(entity.getUnitGroupDescription());
        dto.setTasksInclude(entity.getTasksInclude());
        dto.setExamples(entity.getExamples());
        dto.setSkillLevel(entity.getSkillLevel());
        return dto;
    }
}
