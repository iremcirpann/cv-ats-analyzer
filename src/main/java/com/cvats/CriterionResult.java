package com.cvats;

public record CriterionResult(
        String name,
        boolean found,
        String evidence,
        String recommendation,
        String ocrRecommendation
) {
}
