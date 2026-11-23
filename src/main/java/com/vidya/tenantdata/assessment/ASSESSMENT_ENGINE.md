# Assessment engine (multi-board)

Examinations, continuous assessment, results and analytics for schools on any board: CBSE, ICSE, ISC, State boards,
IB (MYP / DP), Cambridge IGCSE, or a custom scheme. Schema: tenant `V27__advanced_assessment_engine.sql`;
permissions: master `V38__assessment_engine_permissions.sql`.

## How it fits together

```
Assessment scheme (board rules) ──► assigned to classes for a year
   │  components (FORMATIVE / SUMMATIVE, weight, aggregation, source = assessments | exam paper)
   │  subject rules (SCHOLASTIC / ADDITIONAL / GRADE_ONLY / EXCLUDED, own pass %)
   │  co-scholastic areas, competency levels, grace / compartment / best-of / rounding / ranks
   ▼
Marks come from: formative assessments (scores) · exam papers (existing marks entry)
                 · online exams and OMR (posted into either) · approved adjustments (grace, moderation, re-evaluation)
   ▼
Report card engine ──► per subject: component breakdown, grace, pass, grade; overall result, ranks, cumulative
   ▼
Holistic progress card · analytics (subject, year-over-year, student progress, outcome attainment, item analysis)
```

Classes without an active scheme keep working as before: report cards use the exam paper marks as entered.

## Boards (presets)

`GET /api/v1/assessment-schemes/presets` lists them; a scheme is created from a preset and can then be changed.

| Preset | Board | Summary |
|---|---|---|
| CBSE_FOUNDATIONAL | CBSE | Balvatika–II, competency ratings only (Stream / Mountain / Sky), no pass/fail |
| CBSE_PREPARATORY, CBSE_MIDDLE | CBSE | PT 10 (best of 2) + notebook 5 + enrichment 5 + term exam 80; co-scholastic A–C |
| CBSE_SECONDARY | CBSE | IX–X: internal 20 + board 80, 33 % pass, best 5 + additional subject, compartment |
| CBSE_SENIOR | CBSE | XI–XII: theory / practical with separate pass |
| ICSE_JUNIOR, ICSE_SECONDARY | ICSE | Internal 20 + exam 80, best 5 incl. English |
| ISC_SENIOR | ISC | Theory 70–80 + project / practical, separate pass |
| STATE_CCE | State | FA / SA CCE pattern; adjust weights to the state board |
| IB_MYP | IB | Criteria A–D (0–8), 1–7 grades |
| IB_DP | IB | Internal + external assessment, 1–7 grades, no ranks |
| IGCSE | Cambridge | Coursework + papers, A*–G |
| CUSTOM | any | Blank scheme |

Preset numbers follow published board patterns; **check them against the board's current circular** before using
them for a session — every number is editable on the scheme.

## Results

`ResultCalculator` (pure, unit-tested) applies the scheme:

- component % by aggregation (SUM, AVERAGE, BEST_OF n, LATEST, MAX) × weight; not-conducted components drop out,
  exempt marks are excluded;
- rounding (NONE / HALF_UP / UP / DOWN), subject pass %, optional separate pass in marked components;
- **grace**: smallest deficits first, within per-subject / total / subject-count limits;
- a passed **additional** subject replaces the weakest failed main subject; **best-of N** subjects for the total;
- result: PASSED / COMPARTMENT (up to N failed subjects) / FAILED / WITHHELD; overall pass %;
- cumulative over terms (WEIGHTED by term weight, AVERAGE, LATEST); ranks via SQL `RANK()` when enabled.

## Endpoints (all `/api/v1`)

| Area | Endpoints | Permission |
|---|---|---|
| Schemes | `assessment-schemes` CRUD, `/presets`, `/for-class`, `/{id}/components`, `/{id}/co-scholastic-areas`, `/{id}/subjects`, `/{id}/classes`, `/{id}/activate`, `/{id}/archive`, `/{id}/copy` | ASSESSMENT_SCHEME_READ / _MANAGE |
| Competencies | `competencies`, `learning-outcomes`, `learning-outcomes/{id}/syllabus-topics` | COMPETENCY_READ / _MANAGE |
| Ratings | `competency-ratings`, `co-scholastic-grades` | ASSESSMENT_READ / _MANAGE |
| Formative | `assessments` CRUD, `/{id}/scores`, `/lock`, `/unlock` (MARKS_MODERATE) | ASSESSMENT_READ / _MANAGE |
| Grace & moderation | `mark-adjustments` (propose → approve by another user, reject, revert), `moderation/preview`, `moderation` (apply / list), `moderation/{id}/revert` | MARKS_ENTRY, MARKS_MODERATE |
| Re-evaluation | `reevaluation-requests` (request, mine, assign, decide, withdraw) | REEVALUATION_REQUEST / _MANAGE |
| Report cards | `report-cards/generate`, search, `/{id}`, `/{id}/holistic`, `/students/{id}`, `/{id}/remarks`, `/publish`, `/{id}/revoke` | REPORT_CARD_* |
| Question bank | `questions` (search, pool, bulk, submit, review, retire) | QUESTION_BANK_READ / _MANAGE / _REVIEW |
| Papers | `blueprints` (+ `/check`, `/generate`), `question-papers` (manual, swap item, finalise, answer key) | QUESTION_PAPER_MANAGE |
| Online exams | `online-exams` (+ schedule, cancel, attempts, answers, submit, manual-grading, publish-results, post-marks) | ONLINE_EXAM_MANAGE / _TAKE |
| OMR | `omr/batches` (JSON or CSV upload), `/{id}`, `/{id}/post` | OMR_MANAGE (+ MARKS_ENTRY to post) |
| Analytics | `assessment-analytics/classes/{c}/terms/{t}`, `/classes/{c}/years`, `/classes/{c}/terms/{t}/outcomes`, `/students/{s}/progress`, `/students/{s}/outcomes`, `/question-papers/{p}/item-analysis` | ASSESSMENT_ANALYTICS_READ |

Row access follows the marks rules: teachers work on subjects and classes they teach; parents and students see their
own records and only **published** report cards.

## Question formats

| Format | `options` | `answer` (key) | Student response |
|---|---|---|---|
| MCQ_SINGLE / ASSERTION_REASON | `{"choices":[{"key":"A","text":".."}]}` | `{"keys":["B"]}` | `{"keys":["B"]}` |
| MCQ_MULTI | same | `{"keys":["A","C"]}` | `{"keys":["A"]}` (proportional; any wrong pick = negative) |
| TRUE_FALSE | – | `{"value":true}` | `{"value":false}` |
| FILL_BLANK | – | `{"blanks":[["mitochondria"],["ATP"]]}` | `{"blanks":["..",".."]}` (per blank) |
| NUMERICAL | – | `{"value":9.8,"tolerance":0.05}` | `{"value":9.81}` |
| MATCH_FOLLOWING | `{"left":[..],"right":[..]}` | `{"pairs":{"1":"c"}}` | `{"pairs":{..}}` (per pair) |
| SHORT_ANSWER / LONG_ANSWER | – | `{"modelAnswer":"..","rubric":".."}` | `{"text":".."}` (teacher marks) |
| CASE_BASED | – | – | passage; its child questions carry the marks |

Questions are DRAFT → SUBMITTED → APPROVED (by someone other than the author) → RETIRED. Only approved questions are
used in generated papers. A question in a finalised paper can no longer be edited (retire it and add a new version).

## Papers

- **Blueprint**: sections with type, marks per question, count, optional "attempt any N", easy / medium / hard split,
  Bloom levels, units and outcomes. Σ marks × (N or count) must equal the total.
- **Generate**: seeded (same seed → same paper), fills difficulty quotas, spreads units, prefers least-used and least
  recently used questions, can avoid questions used in the last N days. Up to 6 sets: different questions while the
  bank allows, else the same questions reordered (noted in the response). `/check` is a dry run listing shortfalls.
- **Finalise** freezes all sets and records usage; **item analysis** after the exam stores facility and
  discrimination (upper / lower 27 %) on each question and flags problems (negative discrimination = check the key).

## Online exams

Students start inside the window; each gets their own question order (shuffled within sections, case-study parts kept
together) and choice order. Deadline = start + duration, never after the window closes; autosave is accepted for 30 s
after it. Objective questions are marked on submission; written answers wait in the manual-grading queue.
Result visibility: IMMEDIATE, AFTER_CLOSE or AFTER_PUBLISH. **Post marks** takes each student's best attempt, scales it
to the linked exam paper / assessment maximum and marks eligible students without an attempt absent.

Abandoned attempts are submitted by `AssessmentScheduler` every minute (`vidya.assessment.scheduler.enabled`,
env `VIDYA_ASSESSMENT_SCHEDULER`), and also lazily whenever the attempt is touched.

## OMR

CSV header: `student_code` | `admission_number` | `roll_number`, optional `set`, then `answers` (one character per
question, or tokens separated by space / `|`) or `q1..qN` columns. Bubble *n* is the *n*-th auto-marked question of the
set — the paper's `answer-key` endpoint lists them in that order. Tokens: letter or number of the choice, several
letters for multi-select, T/F, a number for numerical; `-` `.` `_` or empty = blank; `*` = multiple marks (scores 0).
Each row is matched to an enrolled student (roll numbers only within the chosen section) and scored; the batch is
kept for review and posted separately.

## Known limits

- No notifications yet on result publication or re-evaluation decisions (hook into the engagement outbox).
- OMR batches can be posted only for papers made entirely of bubble-able questions (otherwise the OMR score is the
  objective part and the written part is entered as marks).
- Elective choice is inferred from which subjects have marks; there is no explicit student-subject enrolment.
- Maker-checker: an adjustment cannot be approved by the user who proposed it.
- Schema and native SQL were checked against PostgreSQL 17; the Spring context / Testcontainers tests need Docker.
