package com.vidya.tenantdata.reportcard.dto;

import com.vidya.tenantdata.reportcard.enums.AuthorRole;
import com.vidya.tenantdata.reportcard.enums.ConductRating;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request to add or update remark on student report card")
public class AddReportCardRemarkRequest {

    @Schema(description = "Role of remark author", example = "CLASS_TEACHER")
    private AuthorRole authorRole;

    @NotBlank(message = "Remark cannot be blank")
    @Schema(description = "Teacher or Principal evaluation comment", example = "Diligent student with excellent comprehension.")
    private String remark;

    @Schema(description = "General conduct / behaviour rating", example = "EXCELLENT")
    private ConductRating conductRating;
}
