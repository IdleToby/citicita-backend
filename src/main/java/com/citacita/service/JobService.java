package com.citacita.service;

import com.citacita.dto.JobDTO;
import com.citacita.entity.MascoJob;

import java.util.List;

public interface JobService {
    List<JobDTO> getJobListByLangAndId(String lang, String majorGroupCode);

    List<MascoJob> getAllJobs();

    JobDTO getDetailJobByLangAndUnitGroupCode(String lang, String unitGroupCode);

    List<JobDTO> getJobListByLangAndUnitGroupTitle(String lang, String unitGroupTitle);
}
