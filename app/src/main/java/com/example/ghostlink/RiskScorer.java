package com.example.ghostlink;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Rule-based scam scoring engine.
 */
public class RiskScorer {

    private static final Pattern HAS_LINK  = Pattern.compile("https?://|www\\.");
    private static final Pattern HAS_PHONE = Pattern.compile("\\b\\+?\\d[\\d\\-() ]{7,}\\d\\b");
    private static final Pattern HAS_MONEY = Pattern.compile("\\$\\s?\\d+|\\b\\d+\\s?(usd|dollars)\\b");

    // Improved regex-based matching for more flexibility
    private static final Pattern URGENCY_REGEX = Pattern.compile(
            "\\b(urgent|immediately|act now|final notice|24 hours|48 hours|expires? soon|respond now|deadline)\\b",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern THREAT_REGEX = Pattern.compile(
            "\\b(suspended|closed|locked|compromised|legal action|arrest|lawsuit|unauthorized access|restricted|termination)\\b",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern VERIFICATION_REGEX = Pattern.compile(
            "\\b(verify|confirm|update|security check|log in|secure portal)\\b.*\\b(account|information|identity|login|access)\\b",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern IMPERSONATION_REGEX = Pattern.compile(
            "\\b(chase|wells fargo|bank of america|paypal|apple|microsoft|google|amazon|netflix|usps|fedex|irs|ssa)\\b",
            Pattern.CASE_INSENSITIVE
    );

    private static final String[] DIRECT_MONEY_PHRASES = {
            "send money", "send me money", "send cash", "send funds",
            "wire money", "wire transfer", "transfer money", "transfer funds",
            "pay me", "pay now", "pay immediately", "send $"
    };

    private static final String[] MONEY_WORDS = {
            "payment required", "wire", "invoice", "gift card", "itunes card",
            "google play card", "steam card", "crypto", "bitcoin",
            "cashapp", "cash app", "venmo", "zelle", "balance due",
            "western union", "money gram"
    };

    private static final String[] OTP_WORDS = {
            "otp", "one-time password", "one time password",
            "verification code", "2fa code", "do not share this code"
    };

    // ── Screen-type detection ─────────────────────────────────────────────────

    private static final Pattern MULTI_DATE_PATTERN = Pattern.compile(
            "(\\d{1,2}:\\d{2}\\s?(am|pm)|yesterday|today|" +
            "jan |feb |mar |apr |may |jun |jul |aug |sep |oct |nov |dec |" +
            "\\bmon\\b|\\btue\\b|\\bwed\\b|\\bthu\\b|\\bfri\\b|\\bsat\\b|\\bsun\\b)",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern MULTI_EMAIL_ROW_PATTERN = Pattern.compile(
            "([a-z0-9._%+\\-]+@[a-z0-9.\\-]+\\.[a-z]{2,})" +
            ".{0,600}" +
            "([a-z0-9._%+\\-]+@[a-z0-9.\\-]+\\.[a-z]{2,})" +
            ".{0,600}" +
            "([a-z0-9._%+\\-]+@[a-z0-9.\\-]+\\.[a-z]{2,})",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL
    );

    private static final String[] SAFE_SCREEN_MARKERS = {
            "google maps", "navigate to", "play store", "install app",
            "settings", "wi-fi", "bluetooth", "battery saver",
            "weather forecast", "news feed", "ghostlink"
    };

    // ─────────────────────────────────────────────────────────────────────────

    public RiskResult score(String cleanedText) {
        String text = (cleanedText == null) ? "" : cleanedText.trim();

        if (text.length() < 20) {
            return moreInfo("Not enough text to analyze");
        }

        if (containsAny(text, SAFE_SCREEN_MARKERS)) {
            return moreInfo("Open a message or email to scan");
        }

        if (looksLikeInboxList(text)) {
            return moreInfo("Open a specific message to scan it");
        }

        int score = 0;
        Set<String> reasons = new LinkedHashSet<>();

        // 1. Links - VERY high signal for phishing
        if (HAS_LINK.matcher(text).find()) {
            score += 20;
            reasons.add("Contains a link to an external website");
        }

        // 2. Urgency
        if (URGENCY_REGEX.matcher(text).find()) {
            score += 25;
            reasons.add("Uses urgent or time-sensitive language");
        }

        // 3. Threats
        if (THREAT_REGEX.matcher(text).find()) {
            score += 30;
            reasons.add("Threatens account closure or consequences");
        }

        // 4. Verification requests
        if (VERIFICATION_REGEX.matcher(text).find()) {
            score += 25;
            reasons.add("Asks to verify or confirm account details");
        }

        // 5. Impersonation
        if (IMPERSONATION_REGEX.matcher(text).find()) {
            score += 20;
            reasons.add("Impersonates a known company or service");
        }

        // 6. Direct Money / OTP (Critical)
        if (containsAny(text, DIRECT_MONEY_PHRASES)) {
            score += 60;
            reasons.add("Requests direct money transfer");
        }
        if (containsAny(text, OTP_WORDS)) {
            score += 45;
            reasons.add("Requests a security or verification code");
        }

        // 7. General Money Words
        if (containsAny(text, MONEY_WORDS)) {
            score += 20;
            reasons.add("Mentions suspicious payment methods");
        }

        if (score > 100) score = 100;

        RiskLabel label;
        if (score >= 60) {
            label = RiskLabel.NOT_SAFE;
        } else if (score >= 30) {
            label = RiskLabel.RISKY;
        } else if (score <= 15) {
            label = RiskLabel.SAFE;
            if (reasons.isEmpty()) reasons.add("No scam signals detected");
        } else {
            label = RiskLabel.MORE_INFO_NEEDED;
            if (reasons.isEmpty()) reasons.add("Weak signals — keep reading");
        }

        return new RiskResult(score, label, top3(reasons));
    }

    private static RiskResult moreInfo(String reason) {
        List<String> r = new ArrayList<>();
        r.add(reason);
        return new RiskResult(0, RiskLabel.MORE_INFO_NEEDED, r);
    }

    private static boolean containsAny(String text, String[] needles) {
        if (text == null || needles == null) return false;
        String t = text.toLowerCase();
        for (String n : needles) {
            if (n != null && !n.isEmpty() && t.contains(n.toLowerCase())) return true;
        }
        return false;
    }

    private static List<String> top3(Set<String> reasons) {
        List<String> out = new ArrayList<>(3);
        if (reasons == null) return out;
        for (String r : reasons) {
            out.add(r);
            if (out.size() == 3) break;
        }
        return out;
    }

    private boolean looksLikeInboxList(String text) {
        if (text == null) return false;
        if (MULTI_EMAIL_ROW_PATTERN.matcher(text).find()) return true;
        Matcher m = MULTI_DATE_PATTERN.matcher(text);
        int count = 0;
        while (m.find()) count++;
        return count >= 5; // Increased threshold
    }
}
