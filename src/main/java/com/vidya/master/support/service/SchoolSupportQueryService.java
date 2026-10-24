package com.vidya.master.support.service;

import com.vidya.master.support.dto.AddQueryCommentRequest;
import com.vidya.master.support.dto.CreateSupportQueryRequest;
import com.vidya.master.support.dto.EscalateQueryRequest;
import com.vidya.master.support.dto.QueryCommentResponse;
import com.vidya.master.support.dto.ResolveQueryRequest;
import com.vidya.master.support.dto.SupportQueryResponse;
import com.vidya.master.support.entity.EscalationLevel;
import com.vidya.master.support.entity.QueryCategory;
import com.vidya.master.support.entity.QueryPriority;
import com.vidya.master.support.entity.QueryStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

public interface SchoolSupportQueryService {

    SupportQueryResponse createQuery(CreateSupportQueryRequest request,
                                     UUID raisedByUserId,
                                     String raisedByName,
                                     String raisedByRole);

    SupportQueryResponse escalateQuery(UUID queryId,
                                       EscalateQueryRequest request,
                                       UUID actorUserId,
                                       String actorName,
                                       String actorRole);

    SupportQueryResponse resolveQuery(UUID queryId,
                                      ResolveQueryRequest request,
                                      UUID actorUserId,
                                      String actorName,
                                      String actorRole);

    QueryCommentResponse addComment(UUID queryId,
                                    AddQueryCommentRequest request,
                                    UUID authorUserId,
                                    String authorName,
                                    String authorRole);

    SupportQueryResponse getQueryById(UUID queryId);

    SupportQueryResponse getQueryByTicketNumber(String ticketNumber);

    Page<SupportQueryResponse> listQueries(UUID schoolId,
                                           UUID communityId,
                                           UUID hubId,
                                           QueryStatus status,
                                           QueryCategory category,
                                           QueryPriority priority,
                                           EscalationLevel currentLevel,
                                           Pageable pageable);

    List<QueryCommentResponse> getComments(UUID queryId, boolean includeInternal);
}
