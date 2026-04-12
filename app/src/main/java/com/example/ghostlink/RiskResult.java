package com.example.ghostlink;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Output of Phase 2 (OCR + scam scoring).
 *
 * Keep this object simple so Phase 3 can display it easily.
 */
public class RiskResult {
    private final int score; // 0-100
    private final RiskLabel label;
    private final List<String> reasons; // 1-3 short reasons

    public RiskResult(int score, RiskLabel label, List<String> reasons) {
        this.score = clamp(score, 0, 100);
        this.label = (label == null) ? RiskLabel.MORE_INFO_NEEDED : label;
        if (reasons == null) {
            this.reasons = Collections.emptyList();
        } else {
            this.reasons = Collections.unmodifiableList(new ArrayList<>(reasons));
        }
    }

    public int getScore() {
        return score;
    }

    public RiskLabel getLabel() {
        return label;
    }

    public List<String> getReasons() {
        return reasons;
    }

    public int getConfidencePercent() {
        // For MVP, confidence == score.
        return score;
    }

    public String getTopReasonOrEmpty() {
        return (reasons != null && !reasons.isEmpty()) ? reasons.get(0) : "";
    }

    private static int clamp(int v, int lo, int hi) {
        if (v < lo) return lo;
        if (v > hi) return hi;
        return v;
    }
}
