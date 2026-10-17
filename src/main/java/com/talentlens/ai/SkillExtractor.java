package com.talentlens.ai;

import java.util.List;

public interface SkillExtractor {

    /**
     * The skills, tools and technologies a job asks for, as short names.
     */
    List<String> requiredSkills(String jobText);
}
