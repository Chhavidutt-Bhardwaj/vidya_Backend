package com.vidya.tenantdata.assessment.omr;

import com.vidya.common.exception.BusinessException;
import com.vidya.tenantdata.assessment.enums.QuestionType;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Reads the export of an OMR scanner.
 *
 * <p>CSV with a header row. One student column: {@code student_code}, {@code admission_number} or {@code roll_number}
 * ({@code roll} works too); optional {@code set}; the answers either in one {@code answers} column or in
 * {@code q1, q2, ...} columns. Question n is the n-th auto-marked question of the paper (set) in paper order.</p>
 *
 * <p>Answer tokens: a choice letter (A = first choice) or its number (1 = first); several letters for multi-select
 * ({@code AC}); T/F (or A/B) for true-false; a number for numerical questions; {@code -}, {@code .}, {@code _} or
 * nothing = left blank; {@code *} = several bubbles where one was allowed (scored as wrong). An {@code answers} string
 * is one character per question ({@code ABD-C*}) unless it contains spaces or {@code |}, which then separate tokens.</p>
 */
public final class OmrParser {

    public static final int MAX_ROWS = 5000;

    private OmrParser() {
    }

    public enum StudentKey { STUDENT_CODE, ADMISSION_NUMBER, ROLL_NUMBER }

    public record Row(int rowNo, StudentKey keyType, String key, String setLabel, List<String> tokens, String raw) {
    }

    public record Token(Map<String, Object> response, boolean blank, boolean invalid) {
    }

    public static List<Row> parseCsv(String csv) {
        if (csv == null || csv.isBlank()) {
            throw new BusinessException("OMR_EMPTY", "The OMR file is empty");
        }
        List<String> lines = csv.lines().filter(l -> !l.isBlank()).toList();
        if (lines.size() < 2) {
            throw new BusinessException("OMR_EMPTY", "The OMR file has a header but no sheets");
        }
        if (lines.size() - 1 > MAX_ROWS) {
            throw new BusinessException("OMR_TOO_LARGE", "At most " + MAX_ROWS + " sheets per file");
        }
        List<String> header = Arrays.stream(split(stripBom(lines.get(0)))).map(h -> h.trim().toLowerCase(Locale.ROOT)).toList();
        int student = -1;
        StudentKey keyType = null;
        for (String[] alias : new String[][]{{"student_code", "STUDENT_CODE"}, {"student", "STUDENT_CODE"},
                {"admission_number", "ADMISSION_NUMBER"}, {"admission_no", "ADMISSION_NUMBER"}, {"roll_number", "ROLL_NUMBER"},
                {"roll", "ROLL_NUMBER"}, {"roll_no", "ROLL_NUMBER"}}) {
            if (student < 0 && header.contains(alias[0])) {
                student = header.indexOf(alias[0]);
                keyType = StudentKey.valueOf(alias[1]);
            }
        }
        if (student < 0) {
            throw new BusinessException("OMR_HEADER", "Header needs a student_code, admission_number or roll_number column");
        }
        int set = header.indexOf("set");
        int answers = header.indexOf("answers");
        List<Integer> questionCols = new ArrayList<>();
        for (int q = 1; header.contains("q" + q); q++) {
            questionCols.add(header.indexOf("q" + q));
        }
        if (answers < 0 && questionCols.isEmpty()) {
            throw new BusinessException("OMR_HEADER", "Header needs an answers column or q1, q2, ... columns");
        }
        List<Row> rows = new ArrayList<>();
        for (int i = 1; i < lines.size(); i++) {
            String[] cells = split(lines.get(i));
            String key = cell(cells, student);
            String setLabel = set >= 0 ? cell(cells, set) : null;
            List<String> tokens;
            if (answers >= 0) {
                tokens = tokens(cell(cells, answers));
            } else {
                tokens = questionCols.stream().map(c -> cell(cells, c)).toList();
            }
            rows.add(new Row(i, keyType, key, setLabel == null || setLabel.isBlank() ? null : setLabel.trim().toUpperCase(Locale.ROOT),
                    tokens, lines.get(i)));
        }
        return rows;
    }

    /** One character per question, or tokens separated by spaces or {@code |}. */
    public static List<String> tokens(String answers) {
        if (answers == null) {
            return List.of();
        }
        String s = answers.trim();
        if (s.contains("|") || s.contains(" ")) {
            return Arrays.stream(s.split("[|\\s]", -1)).map(String::trim).toList();
        }
        return s.chars().mapToObj(c -> String.valueOf((char) c)).toList();
    }

    /** A token as a response the question grader understands. */
    public static Token toResponse(QuestionType type, List<String> choiceKeys, String token) {
        String t = token == null ? "" : token.trim().toUpperCase(Locale.ROOT);
        if (t.isEmpty() || t.equals("-") || t.equals(".") || t.equals("_")) {
            return new Token(Map.of(), true, false);
        }
        if (t.equals("*")) {
            return new Token(Map.of(), false, true);
        }
        switch (type) {
            case MCQ_SINGLE, ASSERTION_REASON -> {
                String key = t.length() == 1 ? choice(choiceKeys, t.charAt(0)) : null;
                return key == null ? new Token(Map.of(), false, true) : new Token(Map.of("keys", List.of(key)), false, false);
            }
            case MCQ_MULTI -> {
                Set<String> keys = new LinkedHashSet<>();
                for (char c : t.toCharArray()) {
                    String key = choice(choiceKeys, c);
                    if (key == null) {
                        return new Token(Map.of(), false, true);
                    }
                    keys.add(key);
                }
                return new Token(Map.of("keys", List.copyOf(keys)), false, false);
            }
            case TRUE_FALSE -> {
                if (t.equals("T") || t.equals("A") || t.equals("1") || t.equals("TRUE")) {
                    return new Token(Map.of("value", true), false, false);
                }
                if (t.equals("F") || t.equals("B") || t.equals("2") || t.equals("FALSE")) {
                    return new Token(Map.of("value", false), false, false);
                }
                return new Token(Map.of(), false, true);
            }
            case NUMERICAL -> {
                try {
                    return new Token(Map.of("value", new BigDecimal(t)), false, false);
                } catch (NumberFormatException e) {
                    return new Token(Map.of(), false, true);
                }
            }
            default -> {
                return new Token(Map.of(), false, true);
            }
        }
    }

    private static String choice(List<String> keys, char c) {
        int idx = Character.isDigit(c) ? c - '1' : (c >= 'A' && c <= 'Z' ? c - 'A' : -1);
        return idx >= 0 && idx < keys.size() ? keys.get(idx) : null;
    }

    private static String[] split(String line) {
        return line.split(",", -1);
    }

    private static String cell(String[] cells, int i) {
        if (i < 0 || i >= cells.length) {
            return "";
        }
        String c = cells[i].trim();
        if (c.length() >= 2 && c.startsWith("\"") && c.endsWith("\"")) {
            c = c.substring(1, c.length() - 1).trim();
        }
        return c;
    }

    private static String stripBom(String s) {
        return s.startsWith("﻿") ? s.substring(1) : s;
    }
}
