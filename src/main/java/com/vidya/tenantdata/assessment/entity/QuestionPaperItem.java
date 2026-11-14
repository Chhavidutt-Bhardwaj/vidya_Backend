package com.vidya.tenantdata.assessment.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

/** A question at a position of a paper; children of a case-based question point to the parent item. */
@Entity
@Table(name = "question_paper_item")
@Getter
@Setter
@NoArgsConstructor
public class QuestionPaperItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "paper_id", nullable = false)
    private UUID paperId;

    @Column(name = "section_position")
    private int sectionPosition;

    @Column(name = "section_title", nullable = false)
    private String sectionTitle;

    @Column(name = "section_attempt")
    private Integer sectionAttempt;

    @Column(name = "position")
    private int position;

    @Column(name = "question_id", nullable = false)
    private UUID questionId;

    @Column(name = "parent_item_id")
    private UUID parentItemId;

    @Column(name = "marks", nullable = false)
    private BigDecimal marks;

    @Column(name = "negative_marks", nullable = false)
    private BigDecimal negativeMarks = BigDecimal.ZERO;
}
