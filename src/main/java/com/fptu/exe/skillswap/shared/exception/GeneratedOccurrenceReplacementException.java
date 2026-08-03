package com.fptu.exe.skillswap.shared.exception;

import com.fptu.exe.skillswap.modules.booking.dto.response.AvailabilityTemplateReplacementConflictResponse;
import lombok.Getter;

import java.util.List;

/** Signals that a direct-slot mutation needs explicit template-version confirmation. */
@Getter
public class GeneratedOccurrenceReplacementException extends BaseException {
    private final List<AvailabilityTemplateReplacementConflictResponse> occurrences;

    public GeneratedOccurrenceReplacementException(List<AvailabilityTemplateReplacementConflictResponse> occurrences) {
        super(ErrorCode.GENERATED_OCCURRENCE_REPLACEMENT_REQUIRED);
        this.occurrences = List.copyOf(occurrences);
    }
}
