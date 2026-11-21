package com.vidya.tenantdata.assessment.bank;

import com.vidya.common.api.ApiResponse;
import com.vidya.tenantdata.assessment.bank.BankDtos.AnswerKey;
import com.vidya.tenantdata.assessment.bank.BankDtos.BlueprintCheck;
import com.vidya.tenantdata.assessment.bank.BankDtos.BlueprintRequest;
import com.vidya.tenantdata.assessment.bank.BankDtos.BlueprintResponse;
import com.vidya.tenantdata.assessment.bank.BankDtos.GenerateRequest;
import com.vidya.tenantdata.assessment.bank.BankDtos.ManualPaperRequest;
import com.vidya.tenantdata.assessment.bank.BankDtos.PaperResponse;
import com.vidya.tenantdata.assessment.bank.BankDtos.PoolCount;
import com.vidya.tenantdata.assessment.bank.BankDtos.QuestionRequest;
import com.vidya.tenantdata.assessment.bank.BankDtos.QuestionResponse;
import com.vidya.tenantdata.assessment.bank.BankDtos.QuestionsRequest;
import com.vidya.tenantdata.assessment.bank.BankDtos.ReplaceItemRequest;
import com.vidya.tenantdata.assessment.bank.BankDtos.ReviewRequest;
import com.vidya.tenantdata.assessment.enums.Difficulty;
import com.vidya.tenantdata.assessment.enums.PaperStatus;
import com.vidya.tenantdata.assessment.enums.QuestionStatus;
import com.vidya.tenantdata.assessment.enums.QuestionType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "Question bank & papers", description = "Questions in ten formats with difficulty, Bloom level and learning outcomes; "
        + "review workflow; blueprints; automatic paper generation in sets; manual assembly; answer keys")
public class QuestionBankController {

    private final QuestionService questions;
    private final PaperService papers;

    // ── questions ──

    @GetMapping("/questions")
    @PreAuthorize("hasAuthority('QUESTION_BANK_READ')")
    public ResponseEntity<ApiResponse<Page<QuestionResponse>>> search(@RequestParam(required = false) UUID subjectId,
                                                                      @RequestParam(required = false) UUID academicClassId,
                                                                      @RequestParam(required = false) QuestionType type,
                                                                      @RequestParam(required = false) Difficulty difficulty,
                                                                      @RequestParam(required = false) QuestionStatus status,
                                                                      @RequestParam(required = false) UUID unitId,
                                                                      @RequestParam(required = false) String q,
                                                                      @RequestParam(defaultValue = "0") int page,
                                                                      @RequestParam(defaultValue = "50") int size) {
        return ResponseEntity.ok(ApiResponse.success("QUESTIONS", "Questions", questions.search(subjectId, academicClassId, type, difficulty, status,
                unitId, q, PageRequest.of(Math.max(0, page), Math.min(Math.max(1, size), 200), Sort.by("createdAt").descending()))));
    }

    @GetMapping("/questions/{id}")
    @PreAuthorize("hasAuthority('QUESTION_BANK_READ')")
    public ResponseEntity<ApiResponse<QuestionResponse>> get(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success("QUESTION", "Question", questions.get(id)));
    }

    @GetMapping("/questions/pool")
    @PreAuthorize("hasAuthority('QUESTION_BANK_READ')")
    @Operation(summary = "Approved questions per type and difficulty", description = "To plan blueprints")
    public ResponseEntity<ApiResponse<List<PoolCount>>> pool(@RequestParam UUID subjectId, @RequestParam(required = false) UUID academicClassId) {
        return ResponseEntity.ok(ApiResponse.success("QUESTION_POOL", "Question pool", questions.pool(subjectId, academicClassId)));
    }

    @PostMapping("/questions")
    @PreAuthorize("hasAuthority('QUESTION_BANK_MANAGE')")
    @Operation(summary = "Add a question", description = "Starts as DRAFT. See the format table in the module doc for options / answer")
    public ResponseEntity<ApiResponse<QuestionResponse>> create(@Valid @RequestBody QuestionRequest request) {
        return ResponseEntity.ok(ApiResponse.success("QUESTION_CREATED", "Question added", questions.create(request)));
    }

    @PostMapping("/questions/bulk")
    @PreAuthorize("hasAuthority('QUESTION_BANK_MANAGE')")
    @Operation(summary = "Add up to 200 questions", description = "All or nothing")
    public ResponseEntity<ApiResponse<List<QuestionResponse>>> bulk(@Valid @RequestBody QuestionsRequest request) {
        return ResponseEntity.ok(ApiResponse.success("QUESTIONS_CREATED", "Questions added", questions.createAll(request.questions())));
    }

    @PutMapping("/questions/{id}")
    @PreAuthorize("hasAuthority('QUESTION_BANK_MANAGE')")
    @Operation(summary = "Edit", description = "Back to DRAFT for review; refused once the question is in a finalised paper")
    public ResponseEntity<ApiResponse<QuestionResponse>> update(@PathVariable UUID id, @Valid @RequestBody QuestionRequest request) {
        return ResponseEntity.ok(ApiResponse.success("QUESTION_UPDATED", "Question updated", questions.update(id, request)));
    }

    @PostMapping("/questions/{id}/submit")
    @PreAuthorize("hasAuthority('QUESTION_BANK_MANAGE')")
    public ResponseEntity<ApiResponse<QuestionResponse>> submit(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success("QUESTION_SUBMITTED", "Sent for review", questions.submit(id)));
    }

    @PostMapping("/questions/{id}/review")
    @PreAuthorize("hasAuthority('QUESTION_BANK_REVIEW')")
    @Operation(summary = "Approve or return", description = "Not by the author; returned questions go back to DRAFT with the note")
    public ResponseEntity<ApiResponse<QuestionResponse>> review(@PathVariable UUID id, @Valid @RequestBody ReviewRequest request) {
        return ResponseEntity.ok(ApiResponse.success("QUESTION_REVIEWED", "Question reviewed", questions.review(id, request.approve(), request.note())));
    }

    @PostMapping("/questions/{id}/retire")
    @PreAuthorize("hasAuthority('QUESTION_BANK_MANAGE')")
    public ResponseEntity<ApiResponse<QuestionResponse>> retire(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success("QUESTION_RETIRED", "Question retired", questions.retire(id)));
    }

    @DeleteMapping("/questions/{id}")
    @PreAuthorize("hasAuthority('QUESTION_BANK_MANAGE')")
    @Operation(summary = "Delete", description = "A question already used in a paper is retired instead")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID id) {
        questions.delete(id);
        return ResponseEntity.ok(ApiResponse.success("QUESTION_DELETED", "Question deleted", null));
    }

    // ── blueprints ──

    @GetMapping("/blueprints")
    @PreAuthorize("hasAuthority('QUESTION_BANK_READ')")
    public ResponseEntity<ApiResponse<List<BlueprintResponse>>> blueprints(@RequestParam(required = false) UUID subjectId,
                                                                           @RequestParam(required = false) UUID academicClassId) {
        return ResponseEntity.ok(ApiResponse.success("BLUEPRINTS", "Blueprints", papers.blueprints(subjectId, academicClassId)));
    }

    @GetMapping("/blueprints/{id}")
    @PreAuthorize("hasAuthority('QUESTION_BANK_READ')")
    public ResponseEntity<ApiResponse<BlueprintResponse>> blueprint(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success("BLUEPRINT", "Blueprint", papers.blueprint(id)));
    }

    @PostMapping("/blueprints")
    @PreAuthorize("hasAuthority('QUESTION_PAPER_MANAGE')")
    @Operation(summary = "Design a blueprint", description = "Sections with question type, marks, count, optional 'attempt any N', "
            + "difficulty split, Bloom levels, units and outcomes. Section marks must add up to the total")
    public ResponseEntity<ApiResponse<BlueprintResponse>> createBlueprint(@Valid @RequestBody BlueprintRequest request) {
        return ResponseEntity.ok(ApiResponse.success("BLUEPRINT_CREATED", "Blueprint created", papers.createBlueprint(request)));
    }

    @PutMapping("/blueprints/{id}")
    @PreAuthorize("hasAuthority('QUESTION_PAPER_MANAGE')")
    public ResponseEntity<ApiResponse<BlueprintResponse>> updateBlueprint(@PathVariable UUID id, @Valid @RequestBody BlueprintRequest request) {
        return ResponseEntity.ok(ApiResponse.success("BLUEPRINT_UPDATED", "Blueprint updated", papers.updateBlueprint(id, request)));
    }

    @PostMapping("/blueprints/{id}/archive")
    @PreAuthorize("hasAuthority('QUESTION_PAPER_MANAGE')")
    public ResponseEntity<ApiResponse<BlueprintResponse>> archiveBlueprint(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success("BLUEPRINT_ARCHIVED", "Blueprint archived", papers.archiveBlueprint(id)));
    }

    @GetMapping("/blueprints/{id}/check")
    @PreAuthorize("hasAuthority('QUESTION_BANK_READ')")
    @Operation(summary = "Can the bank fill it?", description = "Dry run: shortfalls per section and difficulty")
    public ResponseEntity<ApiResponse<BlueprintCheck>> check(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success("BLUEPRINT_CHECK", "Blueprint check", papers.check(id)));
    }

    @PostMapping("/blueprints/{id}/generate")
    @PreAuthorize("hasAuthority('QUESTION_PAPER_MANAGE')")
    @Operation(summary = "Generate a paper", description = "Up to 6 sets (different questions where the bank allows, otherwise reordered); "
            + "the same seed gives the same paper; prefers less-used questions")
    public ResponseEntity<ApiResponse<PaperResponse>> generate(@PathVariable UUID id, @Valid @RequestBody GenerateRequest request) {
        return ResponseEntity.ok(ApiResponse.success("PAPER_GENERATED", "Paper generated", papers.generate(id, request)));
    }

    // ── papers ──

    @PostMapping("/question-papers")
    @PreAuthorize("hasAuthority('QUESTION_PAPER_MANAGE')")
    @Operation(summary = "Assemble a paper by hand")
    public ResponseEntity<ApiResponse<PaperResponse>> manual(@Valid @RequestBody ManualPaperRequest request) {
        return ResponseEntity.ok(ApiResponse.success("PAPER_CREATED", "Paper created", papers.createManual(request)));
    }

    @GetMapping("/question-papers")
    @PreAuthorize("hasAuthority('QUESTION_BANK_READ')")
    public ResponseEntity<ApiResponse<List<PaperResponse>>> list(@RequestParam(required = false) UUID subjectId,
                                                                 @RequestParam(required = false) UUID academicClassId,
                                                                 @RequestParam(required = false) PaperStatus status) {
        return ResponseEntity.ok(ApiResponse.success("PAPERS", "Question papers", papers.papers(subjectId, academicClassId, status)));
    }

    @GetMapping("/question-papers/{id}")
    @PreAuthorize("hasAuthority('QUESTION_BANK_READ')")
    @Operation(summary = "Paper", description = "Answers and explanations are shown only to teachers of the subject")
    public ResponseEntity<ApiResponse<PaperResponse>> paper(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success("PAPER", "Question paper", papers.paper(id, false)));
    }

    @GetMapping("/question-papers/{id}/answer-key")
    @PreAuthorize("hasAuthority('QUESTION_PAPER_MANAGE')")
    @Operation(summary = "Answer key", description = "Auto-marked questions in OMR bubble order")
    public ResponseEntity<ApiResponse<AnswerKey>> key(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success("ANSWER_KEY", "Answer key", papers.answerKey(id)));
    }

    @PutMapping("/question-papers/{id}/items/{itemId}")
    @PreAuthorize("hasAuthority('QUESTION_PAPER_MANAGE')")
    @Operation(summary = "Swap a question", description = "Draft papers only; without questionId picks another approved question "
            + "of the same type and difficulty")
    public ResponseEntity<ApiResponse<PaperResponse>> replace(@PathVariable UUID id, @PathVariable UUID itemId,
                                                              @RequestBody(required = false) ReplaceItemRequest request) {
        return ResponseEntity.ok(ApiResponse.success("PAPER_UPDATED", "Question swapped",
                papers.replaceItem(id, itemId, request == null ? null : request.questionId())));
    }

    @PostMapping("/question-papers/{id}/finalise")
    @PreAuthorize("hasAuthority('QUESTION_PAPER_MANAGE')")
    @Operation(summary = "Finalise", description = "Set A with all its sets; the questions are frozen and their use recorded")
    public ResponseEntity<ApiResponse<PaperResponse>> finalise(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success("PAPER_FINALISED", "Paper finalised", papers.finalise(id)));
    }

    @PostMapping("/question-papers/{id}/archive")
    @PreAuthorize("hasAuthority('QUESTION_PAPER_MANAGE')")
    public ResponseEntity<ApiResponse<PaperResponse>> archive(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success("PAPER_ARCHIVED", "Paper archived", papers.archive(id)));
    }

    @DeleteMapping("/question-papers/{id}")
    @PreAuthorize("hasAuthority('QUESTION_PAPER_MANAGE')")
    @Operation(summary = "Delete a draft paper", description = "With its other sets")
    public ResponseEntity<ApiResponse<Void>> deletePaper(@PathVariable UUID id) {
        papers.delete(id);
        return ResponseEntity.ok(ApiResponse.success("PAPER_DELETED", "Paper deleted", null));
    }
}
