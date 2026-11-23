# Academic structure configuration

How a school's classes, sections, streams, subjects, curriculum and grading fit together, and which APIs set them up. For the yearly cycle (new year, holidays, timetable, promotion), see `../ACADEMIC_SETUP.md`.

```
Class (stage, grade level, hasStreams, grading scale)          ← reusable across years
 └─ Section per year (A, B …; stream for XI–XII)
Stream (Science, Commerce, Humanities …)                      ← reusable
Subject catalogue                                              ← reusable
Curriculum per year
 └─ curriculum subject = class × subject (× stream) + compulsory/elective + order
      └─ /academic-years/{id}/class-subjects/from-curriculum
           whole-class rows → class subject for every section
           stream rows      → class subject for that stream's sections only
Grading scale (tiers 0–100)
   marks are graded with: exam schedule's scale → class's scale → school default
```

All endpoints are under `/api/v1`. They use the existing class, section, subject, curriculum and grading permissions; no new permissions were added.

## 1. Fastest start: the Indian K–12 template

`POST /academic-structure/templates/indian-k12`, guarded by `ACADEMIC_CLASS_CREATE` + `SUBJECT_CREATE` + `GRADING_SCALE_MANAGE` (+ `CURRICULUM_CREATE` when `academicYearId` is sent).

```jsonc
{ "fromLevel": -2,          // -3 Pre-Nursery, -2 Nursery, -1 LKG, 0 UKG, 1 Class I … (default -2)
  "toLevel": 12,            // default 12
  "streams": ["SCIENCE", "COMMERCE", "HUMANITIES"],   // default all three; [] = XI–XII without streams
  "gradingScales": true,    // presets + one per class by stage (default true)
  "subjects": true,         // common subject catalogue (default true)
  "academicYearId": "…",    // optional: also create "Standard curriculum <year>"
  "dryRun": true }          // preview first
→ { "dryRun": true,
    "classesCreated": ["Nursery", "LKG", "UKG", "Class I", …],
    "classesUpdated": ["Class 5 (level 5, stage PRIMARY, grading scale)"],
    "streamsCreated": ["SCIENCE", "COMMERCE", "HUMANITIES"],
    "gradingScalesCreated": ["Descriptive scale (pre-primary)", "8-point scale (I–VIII)", "CBSE 9-point scale (IX–XII)"],
    "subjectsCreated": ["English", "Hindi", …],
    "curriculumCreated": "Standard curriculum AY2027-28", "curriculumSubjects": 142,
    "notes": ["Review subjects and compulsory / elective flags against your board's scheme of studies", …] }
```

- **Safe to re-run.** Existing classes are matched by grade level, code, or a name like "Class 5", "Class V", "Grade 5", "5th", "Std 5" or "Lower KG". They keep their name and only get missing stage, level, streams or scale values. Subjects, streams and scales are matched by name or code.
- **Stages:** Nursery–UKG pre-primary, I–V primary, VI–VIII middle, IX–X secondary, XI–XII senior secondary.
- **Grading:** pre-primary gets the descriptive scale, I–VIII the 8-point scale, IX–XII the CBSE 9-point scale. The 9-point scale becomes the school default if there is none.
- **Curriculum subjects:**

  | Stage | Subjects |
  |---|---|
  | Pre-primary | English, Hindi, Maths, EVS, Art |
  | Primary | adds Computer Science (elective), Art and PE; EVS from Class III |
  | Middle | English, Hindi, Sanskrit (elective), Maths, Science, Social Science, Computer Science, Art, PE |
  | Secondary | English, Hindi, Maths, Science, Social Science, IT (elective), PE |
  | Senior secondary | English, PE, then per stream: **Science** Physics and Chemistry (compulsory), Maths, Biology and CS (electives). **Commerce** Accountancy, Business Studies and Economics, with Maths and IT electives. **Humanities** History and Political Science, with Geography, Economics and Psychology electives. |

- The template gives a starting point. Edit the curriculum grid (§4) to match your board's scheme of studies.

## 2. Classes

- `POST /academic-classes` now also accepts `gradeLevel` (-3…12), `stage` (defaults from the level) and `hasStreams`.
- `PUT /academic-structure/classes/{classId}` (`ACADEMIC_CLASS_UPDATE`) sets all four structure settings at once. A null value clears that setting.

```jsonc
{ "stage": null, "gradeLevel": 11, "hasStreams": true, "gradingScaleId": "…" }
```

- A grade level belongs to one class only.
- Streams can't be turned off while sections in open years still have a stream.
- `AcademicClassResponse` now includes `stage`, `gradeLevel`, `hasStreams` and `gradingScaleId`.

## 3. Streams and sections

| | Endpoint | Permission |
|---|---|---|
| List (with usage counts) | `GET /academic-structure/streams` | `ACADEMIC_CLASS_READ` |
| Create | `POST /academic-structure/streams` `{ code, name, description, sortOrder }` | `ACADEMIC_CLASS_CREATE` |
| Update | `PUT /academic-structure/streams/{id}` (code fixed once used) | `ACADEMIC_CLASS_UPDATE` |
| Activate / deactivate | `PATCH /academic-structure/streams/{id}/status` `{ "status": "INACTIVE" }` | `ACADEMIC_CLASS_UPDATE` |
| Delete (unused only) | `DELETE /academic-structure/streams/{id}` | `ACADEMIC_CLASS_DELETE` |
| Stream of sections | `PUT /academic-structure/sections/streams` `{ "sections": [{ "sectionId", "streamId" }] }` | `SECTION_UPDATE` |

- `POST /sections` also accepts `streamId`.
- A section can only have a stream if its class has `hasStreams`, and the stream must be active.
- The bulk call is all or nothing; `streamId: null` clears a section's stream.
- Copying a year (`/academic-years/setup`) keeps each section's stream.

## 4. Subjects and curriculum

- Subjects: unchanged, `POST /subjects`.
- One subject: `POST /curricula/{id}/subjects` now accepts `streamId`. The same subject can be added once per stream (e.g. Mathematics for Science and for Commerce).
- **Grid save:** `PUT /academic-structure/curricula/{curriculumId}/subjects`. Requires `CURRICULUM_SUBJECT_CREATE`, `CURRICULUM_SUBJECT_UPDATE` and `CURRICULUM_SUBJECT_DELETE`.

```jsonc
{ "academicClassIds": ["<XI>"],
  "subjects": [
    { "academicClassId": "<XI>", "subjectId": "<English>",     "streamId": null,         "compulsory": true,  "displayOrder": 1 },
    { "academicClassId": "<XI>", "subjectId": "<Physics>",     "streamId": "<SCIENCE>",  "compulsory": true,  "displayOrder": 2 },
    { "academicClassId": "<XI>", "subjectId": "<Mathematics>", "streamId": "<SCIENCE>",  "compulsory": false, "displayOrder": 3 },
    { "academicClassId": "<XI>", "subjectId": "<Mathematics>", "streamId": "<COMMERCE>", "compulsory": false, "displayOrder": 3 } ] }
→ { "added": 3, "updated": 1, "removed": 2, "unchanged": 0 }
```

- For every class in `academicClassIds`, the subject list becomes exactly these rows. Classes not listed are not touched.
- One invalid row (unknown subject, stream on a class without streams, duplicate) changes nothing.
- `POST /academic-years/{id}/class-subjects/from-curriculum` then creates the subjects for each class:
  - whole-class rows become class subjects for every section;
  - stream rows become section subjects for that stream's sections only.

  The response's `details` shows `classLevel` and `streamSectionLevel`. Set section streams before running it.

## 5. Grading

| | Endpoint | Permission |
|---|---|---|
| Presets | `GET /exams/grading-scales/presets` | `GRADING_SCALE_READ` |
| Create from preset | `POST /exams/grading-scales/presets/{key}?name=&makeDefault=` | `GRADING_SCALE_MANAGE` |
| Edit name and tiers | `PUT /exams/grading-scales/{id}` (same body as create) | `GRADING_SCALE_MANAGE` |
| Scale that will be used | `GET /exams/grading-scales/effective?academicClassId=` or `?examScheduleId=` → `{ source, scale }` | `GRADING_SCALE_READ` |
| Class scale | `PUT /academic-structure/classes/{id}` `gradingScaleId` | `ACADEMIC_CLASS_UPDATE` |

**Presets** (`key`):

| Key | Bands | Passing |
|---|---|---|
| `CBSE_9_POINT` | A1 91–100 (10) … D 33–40 (4) | E below 33 fails |
| `CBSE_8_POINT` | Same bands with E1 21–32 and E2 0–20 | E1 and E2 are marked not passing; change this if your school follows no-detention |
| `PRE_PRIMARY_DESCRIPTIVE` | Outstanding … Needs support | All passing |
| `PERCENTAGE_DIVISION` | Distinction / I / II / III | Below 33 fails |

**Tier rules** (applied on create and update):
- Tiers must cover 0–100 with no overlaps.
- Gaps of up to 1 point are allowed: boards publish whole-number bands, so a mark of **90.5 % gets the lower band (A2)**. Before this change such marks got no grade.

**Which scale grades a mark:**
1. The exam schedule's scale.
2. Otherwise the class's scale.
3. Otherwise the school default.

Before this change, marks got no grade when the schedule had no scale. Creating a scale with `isDefault: true` now clears the old default; before, two defaults could break `/default`. Editing a scale doesn't regrade marks already saved; re-save them to regrade.

## 6. Overview and checks

`GET /academic-structure/overview?academicYearId=` (`ACADEMIC_CLASS_READ` + `CURRICULUM_READ`) returns, in one call:
- streams;
- every active class in level order, with its effective grading scale and where it comes from (`CLASS`, `SCHOOL_DEFAULT` or `NONE`);
- the class's sections (stream, student count);
- the class's curriculum subjects (stream, compulsory);
- `issues` to show as a to-do list.

| Code | Level | Meaning |
|---|---|---|
| `NO_GRADING_SCALE` | ERROR | Class has no scale and there is no default: marks get no grade |
| `NO_STREAMS` | ERROR | Classes use streams but no active stream exists |
| `NO_GRADE_LEVEL` | WARNING | Class has no level or stage |
| `CLASS_SCALE_DELETED` | WARNING | Class scale was deleted; the default is used |
| `NO_SUBJECTS` | WARNING | Class has sections this year but no curriculum subjects |
| `SECTION_WITHOUT_STREAM` | WARNING | Section of a class with streams has no stream |
| `STREAM_WITHOUT_SUBJECTS` | WARNING | Stream has sections but the curriculum has no subjects for it |
| `INACTIVE_STREAM` | WARNING | Section uses an inactive stream |
| `STREAM_ON_UNSTREAMED_CLASS` | WARNING | Class without streams has stream sections or subjects |

The year checklist (`/academic-years/{id}/setup-status`) has two new RECOMMENDED steps: `STREAMS` (only when a class uses streams) and `GRADING`.

## 7. Suggested screens

| Screen | Read | Write |
|---|---|---|
| Structure overview / to-do | `GET /academic-structure/overview` | links to the screens below |
| Quick setup wizard | template with `dryRun: true` | template |
| Classes | `GET /academic-classes` | `PUT /academic-structure/classes/{id}` |
| Streams | `GET /academic-structure/streams` | streams CRUD |
| Sections × stream | overview sections | `PUT /academic-structure/sections/streams` |
| Curriculum grid (class × subject × stream) | `GET /curricula/{id}` | `PUT /academic-structure/curricula/{id}/subjects` |
| Grading scales | `GET /exams/grading-scales`, `/presets` | presets, `PUT /{id}`, `PATCH /{id}/default` |

## 8. TypeScript types

```ts
type ClassStage = 'PRE_PRIMARY' | 'PRIMARY' | 'MIDDLE' | 'SECONDARY' | 'SENIOR_SECONDARY';
interface Stream { id: string; code: string; name: string; description: string | null; sortOrder: number;
  status: 'ACTIVE' | 'INACTIVE'; sections: number; curriculumSubjects: number; }
interface StructureOverview { academicYearId: string; academicYearCode: string; streams: Stream[];
  classes: ClassNode[]; defaultGradingScale: { id: string; name: string } | null; issues: Issue[]; }
interface ClassNode { id: string; code: string; name: string; stage: ClassStage | null; gradeLevel: number | null;
  hasStreams: boolean; gradingSource: 'EXAM_SCHEDULE' | 'CLASS' | 'SCHOOL_DEFAULT' | 'NONE';
  gradingScaleId: string | null; gradingScaleName: string | null; sections: SectionNode[]; subjects: SubjectNode[]; }
interface SectionNode { id: string; code: string; name: string; streamId: string | null; streamCode: string | null;
  capacity: number | null; students: number; }
interface SubjectNode { curriculumSubjectId: string; curriculumId: string; subjectId: string; subjectCode: string;
  name: string; type: string; compulsory: boolean; displayOrder: number; streamId: string | null; streamCode: string | null; }
interface Issue { level: 'ERROR' | 'WARNING'; code: string; academicClassId: string | null; message: string; }
interface CurriculumGridResult { added: number; updated: number; removed: number; unchanged: number; }
interface TemplateResult { dryRun: boolean; classesCreated: string[]; classesUpdated: string[]; streamsCreated: string[];
  gradingScalesCreated: string[]; subjectsCreated: string[]; curriculumCreated: string | null;
  curriculumSubjects: number; notes: string[]; }
```

## 9. Database (tenant V20)

- New `stream` table.
- `academic_class` gets `stage`, `grade_level`, `has_streams` and `grading_scale_id`.
- `section.stream_id` and `curriculum_subject.stream_id` added.
- The unique index on curriculum subjects now includes the stream.
- Existing data is unchanged: no levels, no streams, no class scales until set.
