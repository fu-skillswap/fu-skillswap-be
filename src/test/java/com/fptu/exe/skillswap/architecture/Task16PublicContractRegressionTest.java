package com.fptu.exe.skillswap.architecture;

import com.fptu.exe.skillswap.modules.booking.port.ContentEntitlementQuery;
import com.fptu.exe.skillswap.modules.chat.port.ChatAccessSnapshotPort;
import com.fptu.exe.skillswap.modules.filestorage.port.PublicAssetPort;
import com.fptu.exe.skillswap.modules.forum.port.ForumAdminPort;
import com.fptu.exe.skillswap.modules.mentor.port.MentorContentAccessPort;
import com.fptu.exe.skillswap.modules.mentor.port.MentorBlogAuthorSummary;
import com.fptu.exe.skillswap.modules.notification.port.NotificationCommandPort;
import org.junit.jupiter.api.Test;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

/** Regression guard for the immutable cross-module contracts used by Tasks 10–16. */
class Task16PublicContractRegressionTest {

    @Test
    void publicContractsExposeOnlyImmutableRecordsAndScalarSnapshots() {
        assertRecord(ChatAccessSnapshotPort.ChatAccessSnapshot.class,
                "entitlementId", "status", "completionOutcome", "maintainPostSessionChat", "selectedEndTime");
        assertRecord(PublicAssetPort.AssetMetadata.class,
                "fileId", "ownerUserId", "originalFilename", "contentType", "sizeBytes", "url");
        assertRecord(MentorBlogAuthorSummary.class,
                "mentorUserId", "headline", "verified", "averageRating", "completedSessions", "bookingCtaLabel");
    }

    @Test
    void requiredPortsRemainInterfaces() {
        assertTrue(ContentEntitlementQuery.class.isInterface());
        assertTrue(ForumAdminPort.class.isInterface());
        assertTrue(NotificationCommandPort.class.isInterface());
    }

    private static void assertRecord(Class<?> type, String... expectedNames) {
        assertTrue(type.isRecord(), type.getName() + " must remain an immutable record");
        String[] actual = Arrays.stream(type.getRecordComponents()).map(RecordComponent::getName).toArray(String[]::new);
        assertArrayEquals(expectedNames, actual);
    }
}
