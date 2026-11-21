package com.vidya.tenantdata.assessment.repository;

import com.vidya.tenantdata.assessment.entity.QuestionPaperItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface QuestionPaperItemRepository extends JpaRepository<QuestionPaperItem, UUID> {

    List<QuestionPaperItem> findByPaperIdOrderByPosition(UUID paperId);

    @Modifying
    @Query("DELETE FROM QuestionPaperItem i WHERE i.paperId = :paperId")
    void deleteByPaper(@Param("paperId") UUID paperId);
}
