package com.talentlens.ai;

import java.util.List;

/**
 * The skills a job requires, as extracted by the language model.
 */
public record RequiredSkills(List<String> skills) {
}
