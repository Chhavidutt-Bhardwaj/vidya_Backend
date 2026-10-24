package com.vidya.tenantdata.reportcard.dto;

import com.vidya.tenantdata.reportcard.enums.AuthorRole;
import com.vidya.tenantdata.reportcard.enums.ConductRating;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Remark entry on student report card")
public class ReportCardRemarkResponse {

    private UUID id;
    private UUID authorStaffId;
    private String authorStaffName;
    private AuthorRole authorRole;
    private String remark;
    private ConductRating conductRating;
    private Instant createdAt;
    private Instant updatedAt;
}
