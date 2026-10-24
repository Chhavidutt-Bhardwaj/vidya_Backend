# 10. Examination & Assessment API

The Examination & Assessment module provides full lifecycle management for school exams: grading scales (letter grades, grade points, percentage bands), exam terms (terms, semesters, cycles), class-level exam schedules, subject exam configurations (dates, times, max marks, passing marks, theory/practical breakdown, room assignments), and student marks evaluation (single & bulk marks entry, grade auto-computation, and evaluation locking).

---

## 1. Scope & Security

- **Database Scope**: Tenant DB (`school_xxx_db`)
- **Required Headers**:
  - `Authorization: Bearer <accessToken>`
  - `X-Tenant-ID: <schoolTenantId>`
- **Required Roles / Permissions**:
  - Viewing Exams/Marks: `EXAM_READ`, `MARKS_READ` (`SCHOOL_ADMIN`, `TEACHER`, `STUDENT`, `PARENT`)
  - Managing Scales/Terms/Schedules: `GRADING_SCALE_MANAGE`, `EXAM_CREATE`, `EXAM_UPDATE`, `EXAM_PUBLISH` (`SCHOOL_ADMIN`, `SCHOOL_OWNER`)
  - Submitting Marks: `MARKS_ENTRY`, `MARKS_LOCK` (`SCHOOL_ADMIN`, `TEACHER`)

---

## 2. TypeScript Data Interfaces

```typescript
export type ExamTermStatus = 'PLANNED' | 'SCHEDULED' | 'IN_PROGRESS' | 'EVALUATING' | 'COMPLETED' | 'CANCELLED';
export type ExamScheduleStatus = 'DRAFT' | 'PUBLISHED' | 'ONGOING' | 'EVALUATION' | 'CONCLUDED' | 'LOCKED' | 'CANCELLED';
export type MarkEntryStatus = 'DRAFT' | 'SUBMITTED' | 'VERIFIED' | 'LOCKED';

// Grading Scale
export interface GradingScaleTierDto {
  id?: string;
  grade: string;               // e.g. "A1", "A+", "B"
  minPercentage: number;       // e.g. 91.00
  maxPercentage: number;       // e.g. 100.00
  gradePoint: number;          // e.g. 10.00
  description?: string;        // e.g. "Outstanding"
  isPassing: boolean;
  sortOrder: number;
}

export interface GradingScaleResponse {
  id: string;
  scaleCode: string;
  name: string;
  description?: string;
  isDefault: boolean;
  tiers: GradingScaleTierDto[];
  createdAt: string;
  updatedAt: string;
}

export interface CreateGradingScaleRequest {
  name: string;
  description?: string;
  isDefault?: boolean;
  tiers: Omit<GradingScaleTierDto, 'id'>[];
}

// Exam Term
export interface ExamTermResponse {
  id: string;
  termCode: string;
  academicYearId: string;
  academicYearName: string;
  name: string;
  startDate: string;           // YYYY-MM-DD
  endDate: string;             // YYYY-MM-DD
  weightagePercentage: number; // e.g. 30.00
  status: ExamTermStatus;
  createdAt: string;
  updatedAt: string;
}

export interface CreateExamTermRequest {
  academicYearId: string;
  name: string;
  startDate: string;
  endDate: string;
  weightagePercentage?: number;
}

export interface UpdateExamTermRequest {
  name?: string;
  startDate?: string;
  endDate?: string;
  weightagePercentage?: number;
}

// Exam Schedule & Subject Configs
export interface ExamSubjectConfigResponse {
  id: string;
  configCode: string;
  examScheduleId: string;
  subjectId: string;
  subjectCode: string;
  subjectName: string;
  examDate: string;            // YYYY-MM-DD
  startTime: string;           // HH:mm:ss
  endTime: string;             // HH:mm:ss
  maxMarks: number;
  passMarks: number;
  theoryMaxMarks?: number;
  practicalMaxMarks?: number;
  roomId?: string;
  roomNumber?: string;
  createdAt: string;
  updatedAt: string;
}

export interface ExamScheduleResponse {
  id: string;
  scheduleCode: string;
  examTermId: string;
  examTermName: string;
  academicClassId: string;
  academicClassName: string;
  gradingScaleId?: string;
  gradingScaleName?: string;
  name: string;
  status: ExamScheduleStatus;
  subjectConfigs: ExamSubjectConfigResponse[];
  createdAt: string;
  updatedAt: string;
}

export interface CreateExamScheduleRequest {
  examTermId: string;
  academicClassId: string;
  gradingScaleId?: string;
  name: string;
}

export interface CreateExamSubjectConfigRequest {
  subjectId: string;
  examDate: string;
  startTime: string;           // "09:00:00"
  endTime: string;             // "12:00:00"
  maxMarks: number;
  passMarks: number;
  theoryMaxMarks?: number;
  practicalMaxMarks?: number;
  roomId?: string;
}

// Student Marks
export interface StudentMarkResponse {
  id: string;
  entryCode: string;
  examSubjectConfigId: string;
  subjectName: string;
  subjectCode: string;
  studentId: string;
  studentName?: string;
  admissionNumber?: string;
  marksObtained?: number;
  maxMarks?: number;
  theoryMarksObtained?: number;
  practicalMarksObtained?: number;
  percentage?: number;
  grade?: string;
  gradePoint?: number;
  isAbsent: boolean;
  isExempt: boolean;
  isPassing: boolean;
  remarks?: string;
  evaluatedByStaffId?: string;
  evaluatedByStaffName?: string;
  status: MarkEntryStatus;
  createdAt: string;
  updatedAt: string;
}

export interface SubmitStudentMarkRequest {
  studentId: string;
  marksObtained?: number;
  theoryMarksObtained?: number;
  practicalMarksObtained?: number;
  isAbsent?: boolean;
  isExempt?: boolean;
  remarks?: string;
}

export interface BulkStudentMarksRequest {
  examSubjectConfigId: string;
  entries: SubmitStudentMarkRequest[];
}

export interface BulkStudentMarksResponse {
  examSubjectConfigId: string;
  totalProcessed: number;
  createdCount: number;
  updatedCount: number;
}
```

---

## 3. Endpoints Matrix

### 3.1 Grading Scales

| Method | Endpoint | Description |
| :--- | :--- | :--- |
| `POST` | `/api/v1/grading-scales` | Create new grading scale with tiers |
| `GET` | `/api/v1/grading-scales` | List all active grading scales |
| `GET` | `/api/v1/grading-scales/{id}` | Get grading scale details with tiers |
| `PUT` | `/api/v1/grading-scales/{id}` | Update grading scale and replace tiers |
| `DELETE` | `/api/v1/grading-scales/{id}` | Soft delete grading scale |

#### Example: Create Grading Scale
```http
POST /api/v1/grading-scales HTTP/1.1
Host: api.vidyaerp.com
Authorization: Bearer <accessToken>
X-Tenant-ID: tenant-school-uuid
Content-Type: application/json

{
  "name": "CBSE Standard 10-Point Scale",
  "description": "Standard secondary grading system",
  "isDefault": true,
  "tiers": [
    { "grade": "A1", "minPercentage": 91.0, "maxPercentage": 100.0, "gradePoint": 10.0, "description": "Outstanding", "isPassing": true, "sortOrder": 1 },
    { "grade": "A2", "minPercentage": 81.0, "maxPercentage": 90.0, "gradePoint": 9.0, "description": "Excellent", "isPassing": true, "sortOrder": 2 },
    { "grade": "B1", "minPercentage": 71.0, "maxPercentage": 80.0, "gradePoint": 8.0, "description": "Very Good", "isPassing": true, "sortOrder": 3 },
    { "grade": "B2", "minPercentage": 61.0, "maxPercentage": 70.0, "gradePoint": 7.0, "description": "Good", "isPassing": true, "sortOrder": 4 },
    { "grade": "C1", "minPercentage": 51.0, "maxPercentage": 60.0, "gradePoint": 6.0, "description": "Fair", "isPassing": true, "sortOrder": 5 },
    { "grade": "C2", "minPercentage": 41.0, "maxPercentage": 50.0, "gradePoint": 5.0, "description": "Average", "isPassing": true, "sortOrder": 6 },
    { "grade": "D", "minPercentage": 33.0, "maxPercentage": 40.0, "gradePoint": 4.0, "description": "Pass", "isPassing": true, "sortOrder": 7 },
    { "grade": "E", "minPercentage": 0.0, "maxPercentage": 32.99, "gradePoint": 0.0, "description": "Needs Improvement", "isPassing": false, "sortOrder": 8 }
  ]
}
```

---

### 3.2 Exam Terms

| Method | Endpoint | Query Params | Description |
| :--- | :--- | :--- | :--- |
| `POST` | `/api/v1/exam-terms` | — | Create new exam term (Mid-term, Final, etc.) |
| `GET` | `/api/v1/exam-terms` | `academicYearId`, `status`, `page`, `size` | Paginated search of exam terms |
| `GET` | `/api/v1/exam-terms/{id}` | — | Get exam term details |
| `GET` | `/api/v1/exam-terms/academic-year/{academicYearId}` | — | List all terms for an academic year |
| `PUT` | `/api/v1/exam-terms/{id}` | — | Update exam term |
| `PATCH` | `/api/v1/exam-terms/{id}/status` | `status` (Query Param) | Transition term status (`PLANNED`, `SCHEDULED`, `COMPLETED`, etc.) |
| `DELETE` | `/api/v1/exam-terms/{id}` | — | Soft delete exam term |

#### Example: Create Exam Term
```http
POST /api/v1/exam-terms HTTP/1.1
Host: api.vidyaerp.com
Authorization: Bearer <accessToken>
X-Tenant-ID: tenant-school-uuid
Content-Type: application/json

{
  "academicYearId": "550e8400-e29b-41d4-a716-446655440000",
  "name": "Term 1 Mid-Term Examination",
  "startDate": "2026-09-15",
  "endDate": "2026-09-30",
  "weightagePercentage": 25.0
}
```

---

### 3.3 Exam Schedules & Subject Configs

| Method | Endpoint | Query Params | Description |
| :--- | :--- | :--- | :--- |
| `POST` | `/api/v1/exam-schedules` | — | Create class schedule binding Term + AcademicClass |
| `GET` | `/api/v1/exam-schedules` | `termId`, `classId`, `status`, `page`, `size` | Search schedules |
| `GET` | `/api/v1/exam-schedules/{id}` | — | Get schedule with all configured subject papers |
| `POST` | `/api/v1/exam-schedules/{scheduleId}/subjects` | — | Add subject exam paper date/time/marks/room |
| `DELETE` | `/api/v1/exam-schedules/subjects/{subjectConfigId}` | — | Remove subject paper from schedule |
| `PATCH` | `/api/v1/exam-schedules/{id}/status` | `status` (Query Param) | Transition status (`PUBLISHED`, `LOCKED`, etc.) |

#### Example: Add Subject Paper to Schedule
```http
POST /api/v1/exam-schedules/781f8f30-8d82-416b-bc54-5a9d3a123456/subjects HTTP/1.1
Host: api.vidyaerp.com
Authorization: Bearer <accessToken>
X-Tenant-ID: tenant-school-uuid
Content-Type: application/json

{
  "subjectId": "1b9d6bcd-bbfd-4b2d-9b5d-ab8dfbbd4bed",
  "examDate": "2026-09-20",
  "startTime": "09:30:00",
  "endTime": "12:30:00",
  "maxMarks": 100.0,
  "passMarks": 35.0,
  "theoryMaxMarks": 80.0,
  "practicalMaxMarks": 20.0,
  "roomId": "8c9d6bcd-bbfd-4b2d-9b5d-ab8dfbbd4bbb"
}
```

---

### 3.4 Student Marks Entry & Evaluation

| Method | Endpoint | Description |
| :--- | :--- | :--- |
| `POST` | `/api/v1/exam-marks/subject-configs/{configId}/students` | Submit single student mark |
| `POST` | `/api/v1/exam-marks/bulk` | Fast bulk marks entry for entire class |
| `GET` | `/api/v1/exam-marks/{id}` | Get specific mark entry |
| `GET` | `/api/v1/exam-marks/subject-configs/{configId}` | Get all student mark entries for subject paper |
| `GET` | `/api/v1/exam-marks/students/{studentId}` | Get student's report card marks across all subjects |
| `POST` | `/api/v1/exam-marks/subject-configs/{configId}/lock` | Lock marks after verification (prevents edits) |

#### Example: Bulk Submit Marks
```http
POST /api/v1/exam-marks/bulk HTTP/1.1
Host: api.vidyaerp.com
Authorization: Bearer <accessToken>
X-Tenant-ID: tenant-school-uuid
Content-Type: application/json

{
  "examSubjectConfigId": "9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d",
  "entries": [
    {
      "studentId": "2c7bbd7c-feb3-4149-bc29-7a84f6705378",
      "marksObtained": 92.5,
      "theoryMarksObtained": 74.0,
      "practicalMarksObtained": 18.5,
      "remarks": "Excellent conceptual clarity"
    },
    {
      "studentId": "6c1e37d1-1842-4e01-b133-bf92bb1f0595",
      "marksObtained": 78.0,
      "theoryMarksObtained": 60.0,
      "practicalMarksObtained": 18.0
    },
    {
      "studentId": "d8b62b34-9872-463c-bb9f-298e7dfa773c",
      "isAbsent": true,
      "remarks": "Medical leave on exam day"
    }
  ]
}
```

---

## 4. React Frontend Integration Examples

### 4.1 React Hook: Fetch Marks Register
```typescript
import { useQuery } from '@tanstack/react-query';
import { apiClient } from '@/api/client';
import { StudentMarkResponse } from './types';

export function useSubjectMarks(configId: string) {
  return useQuery({
    queryKey: ['exam-marks', configId],
    queryFn: async () => {
      const res = await apiClient.get<{ data: StudentMarkResponse[] }>(
        `/api/v1/exam-marks/subject-configs/${configId}`
      );
      return res.data.data;
    },
    enabled: Boolean(configId),
  });
}
```

### 4.2 React Component: Bulk Marks Entry Grid
```tsx
import React, { useState } from 'react';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { apiClient } from '@/api/client';
import { SubmitStudentMarkRequest } from './types';

interface BulkMarksGridProps {
  configId: string;
  students: { id: string; name: string; rollNo: string }[];
  maxMarks: number;
}

export const BulkMarksGrid: React.FC<BulkMarksGridProps> = ({ configId, students, maxMarks }) => {
  const queryClient = useQueryClient();
  const [marks, setMarks] = useState<Record<string, { marksObtained: number; isAbsent: boolean; remarks: string }>>({});

  const mutation = useMutation({
    mutationFn: async () => {
      const entries: SubmitStudentMarkRequest[] = Object.entries(marks).map(([studentId, data]) => ({
        studentId,
        marksObtained: data.isAbsent ? 0 : data.marksObtained,
        isAbsent: data.isAbsent,
        remarks: data.remarks,
      }));

      await apiClient.post('/api/v1/exam-marks/bulk', {
        examSubjectConfigId: configId,
        entries,
      });
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['exam-marks', configId] });
      alert('Marks saved successfully!');
    },
  });

  return (
    <div className="rounded-lg border p-4 shadow-sm bg-white">
      <h3 className="text-lg font-semibold mb-4">Class Marks Entry (Max: {maxMarks})</h3>
      <table className="w-full text-left text-sm">
        <thead>
          <tr className="border-b bg-gray-50">
            <th className="p-2">Roll No</th>
            <th className="p-2">Student Name</th>
            <th className="p-2">Marks Obtained</th>
            <th className="p-2">Absent?</th>
            <th className="p-2">Remarks</th>
          </tr>
        </thead>
        <tbody>
          {students.map((student) => {
            const row = marks[student.id] || { marksObtained: 0, isAbsent: false, remarks: '' };
            return (
              <tr key={student.id} className="border-b">
                <td className="p-2 font-medium">{student.rollNo}</td>
                <td className="p-2">{student.name}</td>
                <td className="p-2">
                  <input
                    type="number"
                    max={maxMarks}
                    min={0}
                    disabled={row.isAbsent}
                    value={row.marksObtained || ''}
                    onChange={(e) =>
                      setMarks((prev) => ({
                        ...prev,
                        [student.id]: { ...row, marksObtained: parseFloat(e.target.value) || 0 },
                      }))
                    }
                    className="w-24 rounded border px-2 py-1"
                  />
                </td>
                <td className="p-2">
                  <input
                    type="checkbox"
                    checked={row.isAbsent}
                    onChange={(e) =>
                      setMarks((prev) => ({
                        ...prev,
                        [student.id]: { ...row, isAbsent: e.target.checked },
                      }))
                    }
                  />
                </td>
                <td className="p-2">
                  <input
                    type="text"
                    value={row.remarks || ''}
                    placeholder="Optional remarks"
                    onChange={(e) =>
                      setMarks((prev) => ({
                        ...prev,
                        [student.id]: { ...row, remarks: e.target.value },
                      }))
                    }
                    className="w-full rounded border px-2 py-1"
                  />
                </td>
              </tr>
            );
          })}
        </tbody>
      </table>

      <div className="mt-4 flex justify-end">
        <button
          onClick={() => mutation.mutate()}
          disabled={mutation.isPending}
          className="rounded bg-indigo-600 px-4 py-2 text-white hover:bg-indigo-700 disabled:opacity-50"
        >
          {mutation.isPending ? 'Saving...' : 'Submit All Marks'}
        </button>
      </div>
    </div>
  );
};
```
