package com.vidya.tenantdata.assessment.entity;

import com.vidya.tenantdata.assessment.enums.QuestionType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** One section of a blueprint. Difficulty counts are minimums; the rest may be any difficulty. */
@Entity
@Table(name = "blueprint_section")
@Getter
@Setter
@NoArgsConstructor
public class BlueprintSection {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "blueprint_id", nullable = false)
    private UUID blueprintId;

    @Column(name = "position")
    private int position;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "instructions")
    private String instructions;

    @Enumerated(EnumType.STRING)
    @Column(name = "question_type")
    private QuestionType questionType;

    @Column(name = "marks_per_question", nullable = false)
    private BigDecimal marksPerQuestion;

    @Column(name = "question_count")
    private int questionCount;

    @Column(name = "attempt_count")
    private Integer attemptCount;

    @Column(name = "easy_count")
    private int easyCount;

    @Column(name = "medium_count")
    private int mediumCount;

    @Column(name = "hard_count")
    private int hardCount;

    @Column(name = "bloom_levels")
    private String bloomLevels;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "unit_ids", columnDefinition = "jsonb")
    private List<String> unitIds;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "outcome_ids", columnDefinition = "jsonb")
    private List<String> outcomeIds;
}
