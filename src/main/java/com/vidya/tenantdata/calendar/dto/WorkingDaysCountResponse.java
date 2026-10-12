package com.vidya.tenantdata.calendar.dto;

import java.time.LocalDate;

public record WorkingDaysCountResponse(
        LocalDate from,
        LocalDate to,
        long workingDaysCount
) {}
