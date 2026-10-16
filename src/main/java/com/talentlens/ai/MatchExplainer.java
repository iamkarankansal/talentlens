package com.talentlens.ai;

public interface MatchExplainer {

    MatchExplanation explain(String jobText, String candidateText);
}
