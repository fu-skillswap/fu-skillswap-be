package com.fptu.exe.skillswap.modules.booking.repository;

import com.fptu.exe.skillswap.modules.booking.domain.AvailabilityTemplateException;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AvailabilityTemplateExceptionRepository extends JpaRepository<AvailabilityTemplateException, UUID> {
    Optional<AvailabilityTemplateException> findByTemplateIdAndOccurrenceDate(UUID templateId, LocalDate occurrenceDate);
    List<AvailabilityTemplateException> findByTemplateIdAndOccurrenceDateIn(UUID templateId, Collection<LocalDate> dates);
    List<AvailabilityTemplateException> findByTemplateId(UUID templateId);
}
