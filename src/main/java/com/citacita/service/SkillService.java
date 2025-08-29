package com.citacita.service;

import com.citacita.dto.SkillLevelDTO;

import java.util.List;

public interface SkillService {
    List<?> getAllSkills();
    List<SkillLevelDTO> getSkillsByLang(String lang);

    /**
     * Gets a single skill level by its code and language.
     */
    SkillLevelDTO getSkillLevelByLangAndId(String lang, String majorGroupCode);
}
