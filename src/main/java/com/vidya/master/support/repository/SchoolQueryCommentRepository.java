package com.vidya.master.support.repository;

import com.vidya.master.support.entity.SchoolQueryComment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface SchoolQueryCommentRepository extends JpaRepository<SchoolQueryComment, UUID> {

    List<SchoolQueryComment> findAllByQueryIdOrderByCreatedAtAsc(UUID queryId);

    List<SchoolQueryComment> findAllByQueryIdAndInternalNoteFalseOrderByCreatedAtAsc(UUID queryId);
}
