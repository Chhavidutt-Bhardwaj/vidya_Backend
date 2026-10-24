package com.vidya.master.community.dto;

import com.vidya.master.community.entity.Gender;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * DTO for creating/updating community personal details.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CommunityPersonalDetailRequest {

    private String contactPersonName;

    private String designation;

    private String alternateEmail;

    private String alternatePhone;

    @Past(message = "Date of birth must be in the past")
    private LocalDate dateOfBirth;

    private Gender gender;

    @Pattern(regexp = "[A-Z]{5}[0-9]{4}[A-Z]{1}", message = "Invalid PAN number format")
    private String panNumber;

    @Pattern(regexp = "[0-9]{12}", message = "Invalid Aadhaar number format")
    private String aadhaarNumber;
}
