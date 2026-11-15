package com.fptu.exe.skillswap.modules.booking.service;

import com.fptu.exe.skillswap.modules.booking.domain.AvailabilityMentorMutationLock;
import com.fptu.exe.skillswap.modules.booking.repository.AvailabilityMentorMutationLockRepository;
import com.fptu.exe.skillswap.shared.exception.BaseException;
import com.fptu.exe.skillswap.shared.exception.ErrorCode;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Locale;
import java.util.UUID;

/** Serializes mentor availability mutations per mentor across PostgreSQL and test databases. */
@Component
public class AvailabilityMentorMutationLocker {

    private final AvailabilityMentorMutationLockRepository lockRepository;
    private final boolean postgresDatabase;

    public AvailabilityMentorMutationLocker(
            AvailabilityMentorMutationLockRepository lockRepository,
            ObjectProvider<DataSource> dataSourceProvider
    ) {
        this.lockRepository = lockRepository;
        this.postgresDatabase = detectPostgres(dataSourceProvider.getIfAvailable());
    }

    public void lock(UUID mentorUserId) {
        if (mentorUserId == null) {
            throw new BaseException(ErrorCode.NOT_FOUND, "Không tìm thấy hồ sơ mentor");
        }
        if (lockRepository.findByMentorUserIdForUpdate(mentorUserId).isPresent()) {
            return;
        }
        if (postgresDatabase) {
            lockRepository.ensureLockRowExistsPostgres(mentorUserId);
        } else if (!lockRepository.existsById(mentorUserId)) {
            lockRepository.saveAndFlush(new AvailabilityMentorMutationLock(mentorUserId));
        }
        lockRepository.findByMentorUserIdForUpdate(mentorUserId)
                .orElseThrow(() -> new BaseException(ErrorCode.NOT_FOUND, "Không tìm thấy hồ sơ mentor"));
    }

    private static boolean detectPostgres(DataSource dataSource) {
        if (dataSource == null) {
            return false;
        }
        try (Connection connection = dataSource.getConnection()) {
            String productName = connection.getMetaData().getDatabaseProductName();
            return productName != null && productName.toLowerCase(Locale.ROOT).contains("postgresql");
        } catch (SQLException ex) {
            return false;
        }
    }
}
