package com.vidya.tenantdata.engagement.service;

import com.vidya.tenantdata.calendar.enums.DayStatus;
import com.vidya.tenantdata.engagement.dto.EngagementDtos.DailyActivityReport;
import com.vidya.tenantdata.engagement.dto.EngagementDtos.HomeworkItem;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Turns daily reports into message text: a detailed version (email, in-app) and a compact one (SMS, WhatsApp). */
public final class DailyReportFormatter {

    public static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH);
    private static final DateTimeFormatter SHORT_DATE = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH);

    private DailyReportFormatter() {
    }

    /** True when there is anything to tell the family. */
    public static boolean hasContent(DailyActivityReport r) {
        if (!r.enrolled()) {
            return false;
        }
        boolean attendance = r.attendance() != null && !"NOT_MARKED".equals(r.attendance().status());
        return attendance || !r.homeworkAssigned().isEmpty() || !r.homeworkDueTomorrow().isEmpty() || !r.lessons().isEmpty()
                || !r.activities().isEmpty() || !r.announcements().isEmpty() || !r.remarks().isEmpty();
    }

    public static String attendanceLabel(String status) {
        if (status == null) {
            return null;
        }
        return switch (status) {
            case "PRESENT" -> "Present";
            case "ABSENT" -> "Absent";
            case "LATE" -> "Late";
            case "HALF_DAY" -> "Half day";
            case "EXCUSED" -> "On leave (excused)";
            case "NOT_MARKED" -> "Not marked yet";
            default -> status;
        };
    }

    public static String classLabel(DailyActivityReport r) {
        if (r.className() == null) {
            return "";
        }
        return r.sectionName() == null ? r.className() : r.className() + " " + r.sectionName();
    }

    /** Several lines per child, children separated by a blank line. */
    public static String detailed(List<DailyActivityReport> reports) {
        List<String> blocks = new ArrayList<>();
        for (DailyActivityReport r : reports) {
            blocks.add(detailed(r));
        }
        return String.join("\n\n", blocks);
    }

    public static String detailed(DailyActivityReport r) {
        StringBuilder b = new StringBuilder(r.studentName());
        String cls = classLabel(r);
        if (!cls.isEmpty()) {
            b.append(" (").append(cls).append(')');
        }
        if (r.dayStatus() != null && r.dayStatus() != DayStatus.WORKING) {
            b.append("\n• No school today").append(r.holidayName() != null ? " – " + r.holidayName() : "");
        }
        if (r.attendance() != null) {
            b.append("\n• Attendance: ").append(attendanceLabel(r.attendance().status()));
            if (r.attendance().remarks() != null && !r.attendance().remarks().isBlank()) {
                b.append(" (").append(r.attendance().remarks().trim()).append(')');
            }
        }
        if (!r.homeworkAssigned().isEmpty()) {
            b.append("\n• Homework: ").append(homework(r.homeworkAssigned()));
        }
        if (!r.homeworkDueTomorrow().isEmpty()) {
            b.append("\n• Due tomorrow: ").append(homework(r.homeworkDueTomorrow()));
        }
        if (!r.lessons().isEmpty()) {
            b.append("\n• Lessons: ").append(String.join("; ", r.lessons().stream()
                    .map(l -> l.subjectName() + " – " + (l.topic() != null ? l.topic() : l.title())).toList()));
        }
        if (!r.activities().isEmpty()) {
            b.append("\n• Class activities: ").append(String.join("; ", r.activities().stream()
                    .map(a -> (a.subjectName() != null ? a.subjectName() + " – " : "") + a.title()).toList()));
        }
        if (!r.announcements().isEmpty()) {
            b.append("\n• Announcements: ").append(String.join("; ", r.announcements().stream().map(a -> a.title()).toList()));
        }
        if (!r.remarks().isEmpty()) {
            b.append("\n• Teacher remarks: ").append(String.join("; ", r.remarks().stream()
                    .map(m -> "\"" + m.remark() + "\"" + (m.teacherName() != null ? " – " + m.teacherName() : "")).toList()));
        }
        return b.toString();
    }

    /** One short line per child, for SMS and WhatsApp. */
    public static String compact(List<DailyActivityReport> reports) {
        List<String> lines = new ArrayList<>();
        for (DailyActivityReport r : reports) {
            List<String> parts = new ArrayList<>();
            if (r.dayStatus() != null && r.dayStatus() != DayStatus.WORKING) {
                parts.add("no school");
            }
            if (r.attendance() != null) {
                parts.add(attendanceLabel(r.attendance().status()));
            }
            if (!r.homeworkAssigned().isEmpty()) {
                parts.add("HW " + r.homeworkAssigned().size());
            }
            if (!r.homeworkDueTomorrow().isEmpty()) {
                parts.add(r.homeworkDueTomorrow().size() + " due tmrw");
            }
            if (!r.remarks().isEmpty()) {
                parts.add(r.remarks().size() + (r.remarks().size() == 1 ? " remark" : " remarks"));
            }
            if (!r.announcements().isEmpty()) {
                parts.add(r.announcements().size() + (r.announcements().size() == 1 ? " notice" : " notices"));
            }
            lines.add(firstName(r.studentName()) + ": " + String.join(", ", parts));
        }
        return String.join("; ", lines) + ". Details in the app.";
    }

    public static String childNames(List<DailyActivityReport> reports) {
        return String.join(", ", reports.stream().map(r -> firstName(r.studentName())).toList());
    }

    private static String homework(List<HomeworkItem> items) {
        return String.join("; ", items.stream().map(h -> h.subjectName() + " – " + h.title() + " (due " + shortDate(h.dueOn()) + ")").toList());
    }

    private static String shortDate(LocalDate d) {
        return d == null ? "" : SHORT_DATE.format(d);
    }

    private static String firstName(String name) {
        if (name == null || name.isBlank()) {
            return "Your child";
        }
        int space = name.trim().indexOf(' ');
        return space < 0 ? name.trim() : name.trim().substring(0, space);
    }
}
