package com.citacita.service;

import com.citacita.dto.JobDTO;

import java.util.List;

public interface JobService {
    List<JobDTO> getJobListByLangAndId(String lang, String majorGroupCode);

    JobDTO getDetailJobByLangAndUnitGroupCode(String lang, String unitGroupCode);

    List<JobDTO> getJobListByLangAndUnitGroupTitle(String lang, String unitGroupTitle);
}
