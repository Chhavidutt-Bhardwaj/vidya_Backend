package com.vidya.tenantdata.assessment.bank;

import com.vidya.common.exception.BusinessException;
import com.vidya.tenantdata.assessment.enums.QuestionType;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Shapes of {@code options}, {@code answer} (the key) and a student's response for each question format, validation of
 * the key, and automatic marking.
 *
 * <pre>
 * format            options                                         answer key                               response
 * MCQ_SINGLE        {"choices":[{"key":"A","text":".."}, ..]}       {"keys":["B"]}                           {"keys":["B"]}
 * ASSERTION_REASON  same as MCQ_SINGLE                              {"keys":["A"]}                           {"keys":["A"]}
 * MCQ_MULTI         same                                            {"keys":["A","C"]}                       {"keys":["A"]}
 * TRUE_FALSE        –                                               {"value":true}                           {"value":false}
 * FILL_BLANK        –                                               {"blanks":[["mitochondria"],["ATP"]]}    {"blanks":["Mitochondria","atp"]}
 * NUMERICAL         –                                               {"value":9.8,"tolerance":0.05}           {"value":9.81}
 * MATCH_FOLLOWING   {"left":[{"key":"1",..}],"right":[{"key":"a",..}]} {"pairs":{"1":"c","2":"a"}}           {"pairs":{"1":"c"}}
 * SHORT/LONG_ANSWER –                                               {"modelAnswer":"..","rubric":".."} opt.  {"text":".."}
 * CASE_BASED        –  (passage; the child questions carry the marks)
 * </pre>
 *
 * Marking: single-answer formats get full marks or minus the negative marks. Multi-select gives proportional marks for
 * a subset of the right options and the negative marks if any wrong option is picked. Fill-in-the-blank and matching give
 * proportional marks per blank / pair. Blanks score zero. Written answers are marked by a teacher.
 */
public final class QuestionFormats {

    public static final Set<QuestionType> CHOICE = EnumSet.of(QuestionType.MCQ_SINGLE, QuestionType.MCQ_MULTI, QuestionType.ASSERTION_REASON);
    public static final Set<QuestionType> MANUAL = EnumSet.of(QuestionType.SHORT_ANSWER, QuestionType.LONG_ANSWER, QuestionType.CASE_BASED);
    /** Formats that can be bubbled on an OMR sheet. */
    public static final Set<QuestionType> OMR = EnumSet.of(QuestionType.MCQ_SINGLE, QuestionType.MCQ_MULTI, QuestionType.ASSERTION_REASON,
            QuestionType.TRUE_FALSE, QuestionType.NUMERICAL);

    private QuestionFormats() {
    }

    public static boolean autoMarked(QuestionType type) {
        return !MANUAL.contains(type);
    }

    /** @param correct null when not decided (blank or manual) */
    public record Grade(Boolean correct, BigDecimal marks, boolean manual, boolean blank) {
    }

    // ───────────────────────────── validation ─────────────────────────────

    public static void validate(QuestionType type, Map<String, Object> options, Map<String, Object> answer) {
        List<String> errors = new ArrayList<>();
        switch (type) {
            case MCQ_SINGLE, ASSERTION_REASON, MCQ_MULTI -> {
                List<String> keys = keys(options, "choices", errors);
                if (keys.size() < 2) {
                    errors.add("give at least two choices");
                }
                if (keys.size() > 8) {
                    errors.add("give at most eight choices");
                }
                List<String> correct = strings(answer == null ? null : answer.get("keys"));
                if (correct.isEmpty()) {
                    errors.add("answer.keys must name the correct choice(s)");
                } else if (!new HashSet<>(keys).containsAll(correct)) {
                    errors.add("answer.keys must be keys of the choices");
                } else if (type != QuestionType.MCQ_MULTI && correct.size() != 1) {
                    errors.add("exactly one correct choice is allowed");
                }
            }
            case TRUE_FALSE -> {
                if (answer == null || !(answer.get("value") instanceof Boolean)) {
                    errors.add("answer.value must be true or false");
                }
            }
            case FILL_BLANK -> {
                Object blanks = answer == null ? null : answer.get("blanks");
                if (!(blanks instanceof List<?> list) || list.isEmpty()) {
                    errors.add("answer.blanks must list the accepted answers of each blank");
                } else {
                    for (Object b : list) {
                        if (strings(b).stream().noneMatch(s -> !s.isBlank())) {
                            errors.add("every blank needs at least one accepted answer");
                            break;
                        }
                    }
                }
            }
            case NUMERICAL -> {
                if (number(answer == null ? null : answer.get("value")) == null) {
                    errors.add("answer.value must be a number");
                }
                Object tol = answer == null ? null : answer.get("tolerance");
                if (tol != null && (number(tol) == null || number(tol).signum() < 0)) {
                    errors.add("answer.tolerance must be a non-negative number");
                }
            }
            case MATCH_FOLLOWING -> {
                List<String> left = keys(options, "left", errors);
                List<String> right = keys(options, "right", errors);
                Map<String, String> pairs = pairs(answer == null ? null : answer.get("pairs"));
                if (left.size() < 2) {
                    errors.add("give at least two items to match");
                }
                if (!pairs.keySet().equals(new LinkedHashSet<>(left))) {
                    errors.add("answer.pairs must map every left key");
                }
                if (!new HashSet<>(right).containsAll(pairs.values())) {
                    errors.add("answer.pairs values must be right keys");
                }
            }
            case SHORT_ANSWER, LONG_ANSWER, CASE_BASED -> {
            }
        }
        if (!errors.isEmpty()) {
            throw new BusinessException("INVALID_QUESTION", type + ": " + String.join("; ", errors));
        }
    }

    // ───────────────────────────── marking ─────────────────────────────

    public static Grade grade(QuestionType type, Map<String, Object> key, Map<String, Object> response, BigDecimal marks, BigDecimal negative) {
        BigDecimal minus = negative == null || negative.signum() <= 0 ? BigDecimal.ZERO : negative.negate();
        if (MANUAL.contains(type)) {
            return new Grade(null, null, true, isBlank(type, response));
        }
        if (isBlank(type, response)) {
            return new Grade(null, BigDecimal.ZERO, false, true);
        }
        return switch (type) {
            case MCQ_SINGLE, ASSERTION_REASON -> {
                boolean ok = new HashSet<>(upper(strings(response.get("keys")))).equals(new HashSet<>(upper(strings(key.get("keys")))));
                yield new Grade(ok, ok ? marks : minus, false, false);
            }
            case MCQ_MULTI -> {
                Set<String> correct = new HashSet<>(upper(strings(key.get("keys"))));
                Set<String> chosen = new HashSet<>(upper(strings(response.get("keys"))));
                if (!correct.containsAll(chosen)) {
                    yield new Grade(false, minus, false, false);
                }
                if (chosen.equals(correct)) {
                    yield new Grade(true, marks, false, false);
                }
                yield new Grade(false, share(marks, chosen.size(), correct.size()), false, false);
            }
            case TRUE_FALSE -> {
                boolean ok = Boolean.valueOf(String.valueOf(response.get("value"))).equals(key.get("value"));
                yield new Grade(ok, ok ? marks : minus, false, false);
            }
            case NUMERICAL -> {
                BigDecimal given = number(response.get("value"));
                BigDecimal expected = number(key.get("value"));
                BigDecimal tol = number(key.get("tolerance"));
                boolean ok = given != null && expected != null
                        && given.subtract(expected).abs().compareTo(tol == null ? BigDecimal.ZERO : tol) <= 0;
                yield new Grade(ok, ok ? marks : minus, false, false);
            }
            case FILL_BLANK -> {
                List<?> accepted = (List<?>) key.get("blanks");
                List<String> given = strings(response.get("blanks"));
                int right = 0;
                for (int i = 0; i < accepted.size(); i++) {
                    String g = i < given.size() ? normal(given.get(i)) : "";
                    if (!g.isEmpty() && strings(accepted.get(i)).stream().map(QuestionFormats::normal).anyMatch(g::equals)) {
                        right++;
                    }
                }
                yield new Grade(right == accepted.size(), share(marks, right, accepted.size()), false, false);
            }
            case MATCH_FOLLOWING -> {
                Map<String, String> expected = pairs(key.get("pairs"));
                Map<String, String> given = pairs(response.get("pairs"));
                int right = (int) expected.entrySet().stream().filter(e -> e.getValue().equalsIgnoreCase(given.getOrDefault(e.getKey(), ""))).count();
                yield new Grade(right == expected.size(), share(marks, right, expected.size()), false, false);
            }
            default -> new Grade(null, null, true, false);
        };
    }

    public static boolean isBlank(QuestionType type, Map<String, Object> r) {
        if (r == null || r.isEmpty()) {
            return true;
        }
        return switch (type) {
            case MCQ_SINGLE, MCQ_MULTI, ASSERTION_REASON -> strings(r.get("keys")).isEmpty();
            case TRUE_FALSE -> !(r.get("value") instanceof Boolean) && !"true".equalsIgnoreCase(String.valueOf(r.get("value")))
                    && !"false".equalsIgnoreCase(String.valueOf(r.get("value")));
            case NUMERICAL -> number(r.get("value")) == null;
            case FILL_BLANK -> strings(r.get("blanks")).stream().allMatch(String::isBlank);
            case MATCH_FOLLOWING -> pairs(r.get("pairs")).isEmpty();
            default -> r.get("text") == null || String.valueOf(r.get("text")).isBlank();
        };
    }

    /** Choice keys in the order they are listed (the order of bubbles on an OMR sheet). */
    public static List<String> choiceKeys(Map<String, Object> options) {
        return keys(options, "choices", new ArrayList<>());
    }

    // ───────────────────────────── helpers ─────────────────────────────

    private static BigDecimal share(BigDecimal marks, int part, int whole) {
        if (whole == 0) {
            return BigDecimal.ZERO;
        }
        return marks.multiply(BigDecimal.valueOf(part)).divide(BigDecimal.valueOf(whole), 2, RoundingMode.DOWN);
    }

    private static List<String> keys(Map<String, Object> options, String field, List<String> errors) {
        Object raw = options == null ? null : options.get(field);
        List<String> keys = new ArrayList<>();
        if (!(raw instanceof List<?> list)) {
            errors.add("options." + field + " must be a list of {key, text}");
            return keys;
        }
        for (Object o : list) {
            if (!(o instanceof Map<?, ?> m) || m.get("key") == null || String.valueOf(m.get("key")).isBlank()) {
                errors.add("every entry of options." + field + " needs a key");
                return keys;
            }
            keys.add(String.valueOf(m.get("key")).trim());
        }
        if (new HashSet<>(keys).size() != keys.size()) {
            errors.add("options." + field + " keys must be unique");
        }
        return keys;
    }

    static List<String> strings(Object o) {
        List<String> out = new ArrayList<>();
        if (o instanceof Collection<?> c) {
            c.stream().filter(java.util.Objects::nonNull).forEach(v -> out.add(String.valueOf(v).trim()));
        } else if (o != null && !String.valueOf(o).isBlank()) {
            out.add(String.valueOf(o).trim());
        }
        return out;
    }

    private static List<String> upper(List<String> values) {
        return values.stream().map(v -> v.toUpperCase(Locale.ROOT)).toList();
    }

    static Map<String, String> pairs(Object o) {
        Map<String, String> out = new LinkedHashMap<>();
        if (o instanceof Map<?, ?> m) {
            m.forEach((k, v) -> {
                if (k != null && v != null && !String.valueOf(v).isBlank()) {
                    out.put(String.valueOf(k).trim(), String.valueOf(v).trim());
                }
            });
        }
        return out;
    }

    static BigDecimal number(Object o) {
        if (o instanceof BigDecimal b) {
            return b;
        }
        if (o instanceof Number n) {
            return new BigDecimal(n.toString());
        }
        if (o instanceof String s && !s.isBlank()) {
            try {
                return new BigDecimal(s.trim());
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    private static String normal(String s) {
        return s == null ? "" : s.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }
}
