package com.cvats;

import java.util.List;

public record AnalysisResult(
        List<CriterionResult> criteria,
        boolean linkedInFound,
        boolean githubFound,
        boolean measurableResultsFound,
        boolean ocrUsed,
        List<String> ocrWarnings,
        List<String> recommendations
) {

    public int foundCount() {
        return (int) criteria.stream().filter(CriterionResult::found).count();
    }

    public int totalCriteria() {
        return criteria.size();
    }

    public double structuralScore() {
        if (criteria.isEmpty()) {
            return 0.0;
        }
        return (foundCount() / (double) totalCriteria()) * 100.0;
    }
}
