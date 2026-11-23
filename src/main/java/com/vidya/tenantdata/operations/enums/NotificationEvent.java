package com.vidya.tenantdata.operations.enums;

import java.util.List;

/**
 * Things that happen in the school that people are told about. Each has built-in wording, used until the
 * school writes its own; {@code placeholders} are the {{names}} the wording may use (besides the common ones).
 */
public enum NotificationEvent {

    STUDENT_ABSENT("Student absent", "Sent to guardians when their child is marked absent", "GUARDIANS",
            List.of("student_name", "class_section", "date"),
            "{{student_name}} was absent on {{date}}",
            "Dear Parent, {{student_name}} of {{class_section}} was marked absent on {{date}}. Please contact the school if this is unexpected. - {{school_name}}"),
    ATTENDANCE_SHORTFALL("Low attendance", "Sent to guardians when attendance falls below the school's minimum", "GUARDIANS",
            List.of("student_name", "class_section", "attendance_percent", "minimum_percent"),
            "Low attendance: {{student_name}}",
            "Dear Parent, {{student_name}} of {{class_section}} has {{attendance_percent}}% attendance; the minimum is {{minimum_percent}}%. - {{school_name}}"),
    FEE_DUE_REMINDER("Fee due reminder", "Sent to guardians before an instalment falls due", "GUARDIANS",
            List.of("student_name", "installment_name", "amount", "due_date"),
            "Fee reminder: {{installment_name}} due on {{due_date}}",
            "Dear Parent, {{installment_name}} of {{amount}} for {{student_name}} is due on {{due_date}}. - {{school_name}}"),
    FEE_OVERDUE("Fee overdue", "Sent to guardians after the due date has passed", "GUARDIANS",
            List.of("student_name", "installment_name", "amount", "due_date", "fine_amount"),
            "Fee overdue: {{installment_name}}",
            "Dear Parent, {{installment_name}} of {{amount}} for {{student_name}} was due on {{due_date}} and is unpaid. Late fine so far: {{fine_amount}}. - {{school_name}}"),
    FEE_PAYMENT_RECEIVED("Fee payment received", "Sent to guardians when a payment is recorded", "GUARDIANS",
            List.of("student_name", "amount", "receipt_no", "date"),
            "Payment received: receipt {{receipt_no}}",
            "Dear Parent, we received {{amount}} for {{student_name}} on {{date}}. Receipt no. {{receipt_no}}. - {{school_name}}"),
    FEE_MANDATE_INVITE("Auto-debit invitation", "Sent to guardians without an auto-debit mandate before the monthly debit day", "GUARDIANS",
            List.of("student_name", "amount", "debit_date", "setup_link"),
            "Set up auto-debit for school fees",
            "Dear Parent, fees of {{amount}} for {{student_name}} fall due by {{debit_date}}. Set up auto-debit (eNACH / UPI Autopay) once and "
                    + "fees are paid automatically every month: {{setup_link}}. You can also pay online or at the school office. - {{school_name}}"),
    FEE_MANDATE_REQUEST("Auto-debit authorisation", "Sent to the payer with the link to authorise a new auto-debit mandate", "GUARDIANS",
            List.of("payer_name", "student_name", "auth_link", "max_amount"),
            "Authorise auto-debit for school fees",
            "Dear {{payer_name}}, please authorise the auto-debit mandate (up to {{max_amount}} per debit) for the fees of {{student_name}}: "
                    + "{{auth_link}} - {{school_name}}"),
    FEE_AUTO_DEBIT_NOTICE("Auto-debit notice", "Sent to the payer before a fee auto-debit (pre-debit notification)", "GUARDIANS",
            List.of("student_name", "amount", "debit_date", "method"),
            "Fee auto-debit on {{debit_date}}: {{amount}}",
            "Dear Parent, {{amount}} for the fees of {{student_name}} will be auto-debited through your {{method}} mandate on {{debit_date}}. "
                    + "If you would rather pay another way, pay before {{debit_date}} or contact the school office. - {{school_name}}"),
    FEE_AUTO_DEBIT_FAILED("Auto-debit failed", "Sent to the payer when a fee auto-debit bounces", "GUARDIANS",
            List.of("student_name", "amount", "debit_date", "reason", "bounce_charge", "next_attempt"),
            "Fee auto-debit failed for {{student_name}}",
            "Dear Parent, the auto-debit of {{amount}} on {{debit_date}} for {{student_name}} failed ({{reason}}). A bounce charge of {{bounce_charge}} "
                    + "(incl. GST) has been added to the fees. {{next_attempt}} - {{school_name}}"),
    FEE_PAYMENT_LINK("Fee payment link", "Sent to the payer with a link to pay fees online", "GUARDIANS",
            List.of("student_name", "amount", "link", "expiry_date"),
            "Pay school fees online: {{amount}}",
            "Dear Parent, pay {{amount}} for the fees of {{student_name}} online: {{link}} (valid till {{expiry_date}}). - {{school_name}}"),
    EXAM_SCHEDULE_PUBLISHED("Exam schedule published", "Sent to guardians and students when an exam schedule is published", "GUARDIANS_AND_STUDENTS",
            List.of("exam_name", "class_section", "start_date"),
            "{{exam_name}} schedule published",
            "The schedule of {{exam_name}} for {{class_section}} is out. Exams start on {{start_date}}. - {{school_name}}"),
    RESULT_PUBLISHED("Result published", "Sent to guardians and students when a report card is published", "GUARDIANS_AND_STUDENTS",
            List.of("student_name", "exam_name"),
            "{{exam_name}} result published",
            "The {{exam_name}} result of {{student_name}} is now available. - {{school_name}}"),
    TIMETABLE_CHANGED("Timetable changed", "Sent to teachers and the class when a timetable is published or changed", "STAFF_AND_STUDENTS",
            List.of("class_section", "effective_date"),
            "Timetable updated for {{class_section}}",
            "The timetable of {{class_section}} has changed from {{effective_date}}. - {{school_name}}"),
    HOLIDAY_ANNOUNCED("Holiday announced", "Sent to everyone when a holiday is added to the calendar", "EVERYONE",
            List.of("holiday_name", "from_date", "to_date"),
            "Holiday: {{holiday_name}}",
            "The school will remain closed from {{from_date}} to {{to_date}} for {{holiday_name}}. - {{school_name}}"),
    TRANSPORT_DELAY("Transport delay", "Sent to guardians of a route when its vehicle is running late", "GUARDIANS",
            List.of("route_name", "delay_minutes"),
            "{{route_name}} is running late",
            "Dear Parent, {{route_name}} is running about {{delay_minutes}} minutes late today. - {{school_name}}"),
    LIBRARY_BOOK_OVERDUE("Library book overdue", "Sent to the borrower (and guardians of a student) when a book is overdue", "BORROWER",
            List.of("member_name", "book_title", "due_date", "fine_amount"),
            "Library book overdue: {{book_title}}",
            "{{book_title}} issued to {{member_name}} was due on {{due_date}}. Fine so far: {{fine_amount}}. - {{school_name}}"),
    DAILY_ACTIVITY_REPORT("Daily activity report", "Sent to guardians every school day: attendance, homework, class activities, announcements and teacher remarks (one message for all their children)", "GUARDIANS",
            List.of("date", "children", "summary"),
            "Daily report for {{date}}: {{children}}",
            "Dear Parent, here is today's update ({{date}}).\n\n{{summary}}\n\n- {{school_name}}"),
    EMERGENCY_ALERT("Emergency alert", "Sent at once (ignoring quiet hours) when an EMERGENCY announcement is published", "CHOSEN_WHEN_SENT",
            List.of("title", "message"),
            "URGENT: {{title}}",
            "URGENT: {{message}} - {{school_name}}"),
    GENERAL_NOTICE("General notice", "Circulars and notices the school sends by hand", "CHOSEN_WHEN_SENT",
            List.of("title", "message"),
            "{{title}}",
            "{{message}} - {{school_name}}");

    /** Usable in every template. */
    public static final List<String> COMMON_PLACEHOLDERS = List.of("school_name", "today");

    private final String label;
    private final String description;
    private final String audience;
    private final List<String> placeholders;
    private final String defaultSubject;
    private final String defaultBody;

    NotificationEvent(String label, String description, String audience, List<String> placeholders, String defaultSubject, String defaultBody) {
        this.label = label;
        this.description = description;
        this.audience = audience;
        this.placeholders = placeholders;
        this.defaultSubject = defaultSubject;
        this.defaultBody = defaultBody;
    }

    public String label() {
        return label;
    }

    public String description() {
        return description;
    }

    public String audience() {
        return audience;
    }

    public List<String> placeholders() {
        return placeholders;
    }

    public String defaultSubject() {
        return defaultSubject;
    }

    public String defaultBody() {
        return defaultBody;
    }
}
