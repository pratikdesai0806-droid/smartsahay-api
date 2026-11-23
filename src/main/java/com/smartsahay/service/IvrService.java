package com.smartsahay.service;

import com.smartsahay.model.EligibilityRequest;
import com.smartsahay.model.EligibilityResult;
import com.smartsahay.model.Scheme;

import java.util.List;

/**
 * Pure logic for the simulated IVR (keypad + voice demo).
 * No servlet, no JDBC: it only converts keypad codes into an EligibilityRequest
 * and turns results into short sentences that are easy to read aloud.
 */
public class IvrService {

    /** Menu digit 1..6 -> scheme slug (all six exist in db/02_seed.sql). */
    private static final String[] SCHEME_SLUGS = {
            "pm-kisan-income-support-scheme",
            "pradhan-mantri-fasal-bima-yojana",
            "kisan-credit-card-scheme",
            "soil-health-card-scheme",
            "siri-dhanya-promotion",
            "ganga-kalyana-scheme"
    };

    private static final String[] CATEGORIES = {"GENERAL", "SC", "ST", "OBC", "MINORITY"};

    /** Income bracket 1..5 -> upper bound in rupees (bracket 5 = "above 8 lakh", sent as 10 lakh). */
    private static final long[] INCOME_UPPER = {100_000, 300_000, 600_000, 800_000, 1_000_000};

    /** Crop key 1..4 -> crop text. 4 ("Other") sends no crop. */
    private static final String[] CROPS = {"KHARIF", "RABI", "MILLET", null};

    private static final int MAX_SPOKEN_CHARS = 450;

    public static final String BAD_INPUT =
            "Sorry, I could not understand your answers. Please end the call and try again.";

    /** Keypad answers as sent by the browser. fruits and bank are accepted but not used by this backend's rules. */
    public static class Answers {
        private Integer state;    // 1 Karnataka, 2 other
        private Integer age;      // years
        private Integer category; // 1 General, 2 SC, 3 ST, 4 OBC, 5 Minority
        private Integer acres;    // whole acres, 0 = no land
        private Integer income;   // bracket 1..5
        private Integer crop;     // 1 Kharif, 2 Rabi, 3 Millets, 4 Other

        public Integer getState() { return state; }
        public Integer getAge() { return age; }
        public Integer getCategory() { return category; }
        public Integer getAcres() { return acres; }
        public Integer getIncome() { return income; }
        public Integer getCrop() { return crop; }

        public Answers() { }

        public Answers(Integer state, Integer age, Integer category, Integer acres, Integer income, Integer crop) {
            this.state = state;
            this.age = age;
            this.category = category;
            this.acres = acres;
            this.income = income;
            this.crop = crop;
        }
    }

    // ── Mode 1: scheme information ──────────────────────────────────────────

    /** Returns the slug for "1".."6", or null if the digit is not on the menu. */
    public String slugForDigit(String digits) {
        if (digits == null || digits.length() != 1 || digits.charAt(0) < '1' || digits.charAt(0) > '6') {
            return null;
        }
        return SCHEME_SLUGS[digits.charAt(0) - '1'];
    }

    public String invalidDigitSpeech() {
        return "That key is not on the menu. Please press a number from 1 to 6.";
    }

    public String schemeNotFoundSpeech() {
        return "Sorry, I could not find that scheme right now. Please try another number.";
    }

    public String schemeSpeech(Scheme s) {
        StringBuilder sb = new StringBuilder();
        sb.append(s.getName()).append(". ");
        if (s.getBenefits() != null && !s.getBenefits().isEmpty()) {
            sb.append(endWithPeriod(s.getBenefits().get(0))).append(' ');
        } else if (s.getDescription() != null) {
            sb.append(endWithPeriod(s.getDescription())).append(' ');
        }
        if (s.getWhereToApply() != null && !s.getWhereToApply().isBlank()) {
            sb.append("You can apply at ").append(endWithPeriod(s.getWhereToApply()));
        }
        return limit(toSpeech(sb.toString().trim()));
    }

    // ── Mode 2: eligibility ─────────────────────────────────────────────────

    /** Returns null when all answers are usable, otherwise a reason (for logs/tests only, never spoken). */
    public String validate(Answers a) {
        if (a == null) return "empty";
        if (!in(a.state, 1, 2)) return "state";
        if (!in(a.age, 1, 120)) return "age";
        if (!in(a.category, 1, 5)) return "category";
        if (!in(a.acres, 0, 500)) return "acres";
        if (!in(a.income, 1, 5)) return "income";
        if (!in(a.crop, 1, 4)) return "crop";
        return null;
    }

    public EligibilityRequest toRequest(Answers a) {
        return EligibilityRequest.of(
                a.getAcres(),
                INCOME_UPPER[a.getIncome() - 1],
                a.getAge(),
                a.getState() == 1 ? "Karnataka" : "Other",
                CATEGORIES[a.getCategory() - 1],
                CROPS[a.getCrop() - 1]);
    }

    public String eligibilitySpeech(EligibilityResult result) {
        int n = result.getEligibleCount();
        if (n == 0) {
            return "Based on your answers, I could not find a matching scheme. "
                    + "Please visit your local agriculture office for more help.";
        }
        List<Scheme> eligible = result.getEligible();
        StringBuilder sb = new StringBuilder("Based on your answers, you may be eligible for ");
        sb.append(n).append(n == 1 ? " scheme" : " schemes").append(". ");
        int shown = Math.min(3, eligible.size());
        sb.append(n > shown ? "They include " : (shown == 1 ? "It is " : "They are "));
        for (int i = 0; i < shown; i++) {
            if (i > 0) sb.append(i == shown - 1 ? " and " : ", ");
            sb.append(eligible.get(i).getName());
        }
        sb.append(". This is only a guide. Please confirm with your local agriculture office.");
        return toSpeech(sb.toString());
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    static boolean in(Integer v, int min, int max) {
        return v != null && v >= min && v <= max;
    }

    private static String endWithPeriod(String s) {
        String t = s.trim();
        return (t.endsWith(".") || t.endsWith("!") || t.endsWith("?")) ? t : t + ".";
    }

    /** Make text friendlier for speech synthesis. */
    public static String toSpeech(String text) {
        return text.replace("\u20B9", "rupees ")
                .replace("%", " percent")
                .replace("&", " and ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    /** Cut at the last full sentence that fits in MAX_SPOKEN_CHARS. */
    static String limit(String text) {
        if (text.length() <= MAX_SPOKEN_CHARS) return text;
        String cut = text.substring(0, MAX_SPOKEN_CHARS);
        int dot = cut.lastIndexOf(". ");
        return dot > 80 ? cut.substring(0, dot + 1) : cut.trim();
    }
}
