package com.fptu.exe.skillswap.modules.admin.repository;

import com.fptu.exe.skillswap.modules.admin.domain.AdminQueueKey;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.nio.ByteBuffer;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public class AdminQueueQueryRepository {

    @PersistenceContext
    private EntityManager entityManager;

    public PageImpl<QueueCaseRow> findQueueItems(
            AdminQueueKey queueKey,
            UUID currentAdminUserId,
            Boolean assignedToMe,
            Boolean unassignedOnly,
            Pageable pageable
    ) {
        String baseSql = buildBaseSql(queueKey);
        String assignmentPredicate = buildAssignmentPredicate(assignedToMe, unassignedOnly);
        String orderByClause = buildOrderByClause(pageable.getSort());

        Query countQuery = entityManager.createNativeQuery("""
                select count(*)
                from (
                    %s
                ) queue_items
                where 1 = 1
                %s
                """.formatted(baseSql, assignmentPredicate));
        bindCommonParameters(countQuery, currentAdminUserId);
        long total = ((Number) countQuery.getSingleResult()).longValue();

        Query dataQuery = entityManager.createNativeQuery("""
                select *
                from (
                    %s
                ) queue_items
                where 1 = 1
                %s
                %s
                limit :limit offset :offset
                """.formatted(baseSql, assignmentPredicate, orderByClause));
        bindCommonParameters(dataQuery, currentAdminUserId);
        dataQuery.setParameter("limit", pageable.getPageSize());
        dataQuery.setParameter("offset", pageable.getOffset());

        @SuppressWarnings("unchecked")
        List<Object[]> rows = dataQuery.getResultList();

        List<QueueCaseRow> content = rows.stream()
                .map(this::mapRow)
                .toList();
        return new PageImpl<>(content, pageable, total);
    }

    private void bindCommonParameters(Query query, UUID currentAdminUserId) {
        if (query.getParameters().stream().anyMatch(parameter -> "currentAdminUserId".equals(parameter.getName()))) {
            query.setParameter("currentAdminUserId", currentAdminUserId);
        }
    }

    private String buildAssignmentPredicate(Boolean assignedToMe, Boolean unassignedOnly) {
        StringBuilder predicate = new StringBuilder();
        if (Boolean.TRUE.equals(assignedToMe)) {
            predicate.append(" and assigned_admin_user_id = :currentAdminUserId");
        }
        if (Boolean.TRUE.equals(unassignedOnly)) {
            predicate.append(" and assigned_admin_user_id is null");
        }
        return predicate.toString();
    }

    private String buildOrderByClause(Sort sort) {
        Sort.Order order = sort.stream().findFirst().orElse(Sort.Order.asc("createdAt"));
        String column = switch (order.getProperty()) {
            case "updatedAt" -> "updated_at";
            case "status" -> "status";
            case "title" -> "title";
            default -> "created_at";
        };
        String direction = order.getDirection().isAscending() ? "asc" : "desc";
        return "order by " + column + " " + direction + ", case_id asc";
    }

    private String buildBaseSql(AdminQueueKey queueKey) {
        return switch (queueKey) {
            case MENTOR_VERIFICATION_PENDING_REVIEW -> """
                    select
                        request.id as case_id,
                        coalesce(mentor.full_name, mentor.email) as title,
                        mentor.email as subtitle,
                        request.status as status,
                        coalesce(request.submitted_at, request.created_at) as created_at,
                        request.updated_at as updated_at,
                        assignment.assigned_admin_user_id as assigned_admin_user_id,
                        assigned_admin.full_name as assigned_admin_display_name,
                        assignment.assigned_at as assigned_at,
                        request.id as detail_ref_id
                    from mentor_verification_requests request
                    join users mentor on mentor.id = request.mentor_user_id
                    left join admin_case_assignments assignment
                        on assignment.case_type = 'MENTOR_VERIFICATION_REQUEST'
                       and assignment.case_id = request.id
                    left join users assigned_admin on assigned_admin.id = assignment.assigned_admin_user_id
                    where request.status = 'PENDING_REVIEW'
                    """;
            case BOOKING_UNDER_REVIEW -> """
                    select
                        booking.id as case_id,
                        coalesce(booking.learning_goal_title, concat('Booking ', cast(booking.id as varchar))) as title,
                        concat(coalesce(mentee.full_name, mentee.email), ' -> ', coalesce(mentor.full_name, mentor.email)) as subtitle,
                        booking.status as status,
                        booking.created_at as created_at,
                        booking.updated_at as updated_at,
                        assignment.assigned_admin_user_id as assigned_admin_user_id,
                        assigned_admin.full_name as assigned_admin_display_name,
                        assignment.assigned_at as assigned_at,
                        booking.id as detail_ref_id
                    from bookings booking
                    join users mentee on mentee.id = booking.mentee_user_id
                    join users mentor on mentor.id = booking.mentor_user_id
                    left join admin_case_assignments assignment
                        on assignment.case_type = 'BOOKING'
                       and assignment.case_id = booking.id
                    left join users assigned_admin on assigned_admin.id = assignment.assigned_admin_user_id
                    where booking.status = 'UNDER_REVIEW'
                    """;
            case FORUM_REPORTS_OPEN -> """
                    select
                        report.id as case_id,
                        concat('Forum report ', report.reason_type) as title,
                        concat(coalesce(reporter.full_name, reporter.email), ' · ', report.target_type) as subtitle,
                        report.status as status,
                        report.created_at as created_at,
                        report.updated_at as updated_at,
                        assignment.assigned_admin_user_id as assigned_admin_user_id,
                        assigned_admin.full_name as assigned_admin_display_name,
                        assignment.assigned_at as assigned_at,
                        report.id as detail_ref_id
                    from forum_reports report
                    join users reporter on reporter.id = report.reporter_user_id
                    left join admin_case_assignments assignment
                        on assignment.case_type = 'FORUM_REPORT'
                       and assignment.case_id = report.id
                    left join users assigned_admin on assigned_admin.id = assignment.assigned_admin_user_id
                    where report.status = 'OPEN'
                    """;
            case PAYOUT_REQUESTS_REQUESTED -> """
                    select
                        payout.id as case_id,
                        concat('Payout ', payout.amount_scoin, ' SCoin') as title,
                        concat(coalesce(mentor.full_name, mentor.email), ' · ', payout.bank_name_snapshot, ' · ', payout.bank_account_number_masked_snapshot) as subtitle,
                        payout.status as status,
                        payout.requested_at as created_at,
                        payout.updated_at as updated_at,
                        assignment.assigned_admin_user_id as assigned_admin_user_id,
                        assigned_admin.full_name as assigned_admin_display_name,
                        assignment.assigned_at as assigned_at,
                        payout.id as detail_ref_id
                    from payout_requests payout
                    left join users mentor on mentor.id = payout.mentor_user_id
                    left join admin_case_assignments assignment
                        on assignment.case_type = 'PAYOUT_REQUEST'
                       and assignment.case_id = payout.id
                    left join users assigned_admin on assigned_admin.id = assignment.assigned_admin_user_id
                    where payout.status = 'REQUESTED'
                    """;
            case PAYMENT_ORDERS_FAILED -> """
                    select
                        payment.id as case_id,
                        concat('Payment order ', payment.order_code) as title,
                        concat(coalesce(payer.full_name, payer.email), ' · booking ', cast(payment.booking_id as varchar(36))) as subtitle,
                        payment.status as status,
                        payment.created_at as created_at,
                        payment.updated_at as updated_at,
                        assignment.assigned_admin_user_id as assigned_admin_user_id,
                        assigned_admin.full_name as assigned_admin_display_name,
                        assignment.assigned_at as assigned_at,
                        payment.booking_id as detail_ref_id
                    from payment_orders payment
                    left join users payer on payer.id = payment.payer_user_id
                    left join admin_case_assignments assignment
                        on assignment.case_type = 'PAYMENT_ORDER'
                       and assignment.case_id = payment.id
                    left join users assigned_admin on assigned_admin.id = assignment.assigned_admin_user_id
                    where payment.status = 'FAILED'
                    """;
            case EMAIL_OUTBOX_FAILED -> """
                    select
                        email.id as case_id,
                        email.subject as title,
                        email.to_email as subtitle,
                        email.status as status,
                        email.created_at as created_at,
                        coalesce(email.sent_at, email.created_at) as updated_at,
                        assignment.assigned_admin_user_id as assigned_admin_user_id,
                        assigned_admin.full_name as assigned_admin_display_name,
                        assignment.assigned_at as assigned_at,
                        email.id as detail_ref_id
                    from email_outbox email
                    left join admin_case_assignments assignment
                        on assignment.case_type = 'EMAIL_OUTBOX'
                       and assignment.case_id = email.id
                    left join users assigned_admin on assigned_admin.id = assignment.assigned_admin_user_id
                    where email.status = 'FAILED'
                    """;
            case BOOKINGS_ACCEPTED_AWAITING_PAYMENT -> """
                    select
                        booking.id as case_id,
                        coalesce(booking.learning_goal_title, concat('Booking ', cast(booking.id as varchar))) as title,
                        concat(coalesce(mentee.full_name, mentee.email), ' -> ', coalesce(mentor.full_name, mentor.email)) as subtitle,
                        booking.status as status,
                        booking.created_at as created_at,
                        booking.updated_at as updated_at,
                        assignment.assigned_admin_user_id as assigned_admin_user_id,
                        assigned_admin.full_name as assigned_admin_display_name,
                        assignment.assigned_at as assigned_at,
                        booking.id as detail_ref_id
                    from bookings booking
                    join users mentee on mentee.id = booking.mentee_user_id
                    join users mentor on mentor.id = booking.mentor_user_id
                    left join admin_case_assignments assignment
                        on assignment.case_type = 'BOOKING'
                       and assignment.case_id = booking.id
                    left join users assigned_admin on assigned_admin.id = assignment.assigned_admin_user_id
                    where booking.status = 'ACCEPTED_AWAITING_PAYMENT'
                    """;
        };
    }

    private QueueCaseRow mapRow(Object[] row) {
        return new QueueCaseRow(
                toUuid(row[0]),
                toStringValue(row[1]),
                toStringValue(row[2]),
                toStringValue(row[3]),
                toLocalDateTime(row[4]),
                toLocalDateTime(row[5]),
                toUuid(row[6]),
                toStringValue(row[7]),
                toLocalDateTime(row[8]),
                toUuid(row[9])
        );
    }

    private UUID toUuid(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof UUID uuid) {
            return uuid;
        }
        if (value instanceof byte[] bytes && bytes.length == 16) {
            ByteBuffer buffer = ByteBuffer.wrap(bytes);
            return new UUID(buffer.getLong(), buffer.getLong());
        }
        return UUID.fromString(String.valueOf(value));
    }

    private String toStringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private LocalDateTime toLocalDateTime(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof LocalDateTime localDateTime) {
            return localDateTime;
        }
        if (value instanceof Timestamp timestamp) {
            return timestamp.toLocalDateTime();
        }
        return LocalDateTime.parse(String.valueOf(value).replace(' ', 'T'));
    }

    public record QueueCaseRow(
            UUID caseId,
            String title,
            String subtitle,
            String status,
            LocalDateTime createdAt,
            LocalDateTime updatedAt,
            UUID assignedAdminUserId,
            String assignedAdminDisplayName,
            LocalDateTime assignedAt,
            UUID detailRefId
    ) {
    }
}
