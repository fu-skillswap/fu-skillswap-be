package com.fptu.exe.skillswap.modules.admin.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Repository;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Repository
public class AdminDashboardQueryRepository {

    @PersistenceContext
    private EntityManager entityManager;

    public UserOverviewCountsRow fetchUserOverviewCounts() {
        Object[] row = (Object[]) entityManager.createNativeQuery("""
                select
                    (select count(*) from users) as total_users,
                    (select count(*) from users where deleted_at is null and status = 'ACTIVE') as active_users,
                    (select count(*) from users where deleted_at is null and status = 'BANNED') as banned_users,
                    (select count(*) from users where deleted_at is not null or status = 'DELETED') as deleted_users,
                    (
                        select count(distinct u.id)
                        from users u
                        join user_roles mentee_role on mentee_role.user_id = u.id and mentee_role.role = 'MENTEE'
                        where u.deleted_at is null
                          and u.status <> 'DELETED'
                          and not exists (
                                select 1
                                from user_roles mentor_role
                                where mentor_role.user_id = u.id
                                  and mentor_role.role = 'MENTOR'
                          )
                          and not exists (
                                select 1
                                from user_roles admin_role
                                where admin_role.user_id = u.id
                                  and admin_role.role in ('ADMIN', 'SYSTEM_ADMIN')
                          )
                    ) as mentee_only_users,
                    (
                        select count(distinct u.id)
                        from users u
                        join user_roles mentor_role on mentor_role.user_id = u.id and mentor_role.role = 'MENTOR'
                        where u.deleted_at is null
                          and u.status <> 'DELETED'
                          and not exists (
                                select 1
                                from user_roles admin_role
                                where admin_role.user_id = u.id
                                  and admin_role.role in ('ADMIN', 'SYSTEM_ADMIN')
                          )
                    ) as mentor_users
                """).getSingleResult();

        return new UserOverviewCountsRow(
                toLong(row[0]),
                toLong(row[1]),
                toLong(row[2]),
                toLong(row[3]),
                toLong(row[4]),
                toLong(row[5])
        );
    }

    public Map<String, Long> countMentorVerificationByStatus() {
        return countByStatus("mentor_verification_requests");
    }

    public Map<String, Long> countBookingsByStatus() {
        return countByStatus("bookings");
    }

    public Map<String, Long> countForumReportsByStatus() {
        return countByStatus("forum_reports");
    }

    public Map<String, Long> countPayoutRequestsByStatus() {
        return countByStatus("payout_requests");
    }

    public Map<String, Long> countPaymentOrdersByStatus() {
        return countByStatus("payment_orders");
    }

    public Map<String, Long> countEmailOutboxByStatus() {
        return countByStatus("email_outbox");
    }

    public List<DailyCountRow> countUsersCreatedByDay(LocalDateTime fromInclusive, LocalDateTime toExclusive) {
        return countDailyEvents("users", "created_at", fromInclusive, toExclusive, "");
    }

    public List<DailyCountRow> countMentorVerificationSubmittedByDay(LocalDateTime fromInclusive, LocalDateTime toExclusive) {
        return countDailyEvents("mentor_verification_requests", "submitted_at", fromInclusive, toExclusive, "");
    }

    public List<DailyCountRow> countBookingsCreatedByDay(LocalDateTime fromInclusive, LocalDateTime toExclusive) {
        return countDailyEvents("bookings", "created_at", fromInclusive, toExclusive, "");
    }

    public List<DailyCountRow> countPaymentOrdersPaidByDay(LocalDateTime fromInclusive, LocalDateTime toExclusive) {
        return countDailyEvents(
                "payment_orders",
                "coalesce(paid_at, updated_at)",
                fromInclusive,
                toExclusive,
                "and status = 'PAID'"
        );
    }

    public List<DailyCountRow> countForumReportsCreatedByDay(LocalDateTime fromInclusive, LocalDateTime toExclusive) {
        return countDailyEvents("forum_reports", "created_at", fromInclusive, toExclusive, "");
    }

    public List<DailyCountRow> countPayoutRequestsCreatedByDay(LocalDateTime fromInclusive, LocalDateTime toExclusive) {
        return countDailyEvents("payout_requests", "requested_at", fromInclusive, toExclusive, "");
    }

    private Map<String, Long> countByStatus(String tableName) {
        @SuppressWarnings("unchecked")
        List<Object[]> rows = entityManager.createNativeQuery("""
                select status, count(*)
                from %s
                group by status
                order by status asc
                """.formatted(tableName)).getResultList();

        Map<String, Long> counts = new LinkedHashMap<>();
        for (Object[] row : rows) {
            counts.put(String.valueOf(row[0]), toLong(row[1]));
        }
        return counts;
    }

    private List<DailyCountRow> countDailyEvents(
            String tableName,
            String timeExpression,
            LocalDateTime fromInclusive,
            LocalDateTime toExclusive,
            String additionalPredicate
    ) {
        @SuppressWarnings("unchecked")
        List<Object[]> rows = entityManager.createNativeQuery("""
                select cast(%s as date) as bucket_date, count(*) as total
                from %s
                where %s is not null
                  and %s >= :fromInclusive
                  and %s < :toExclusive
                  %s
                group by cast(%s as date)
                order by bucket_date asc
                """.formatted(
                timeExpression,
                tableName,
                timeExpression,
                timeExpression,
                timeExpression,
                additionalPredicate == null ? "" : additionalPredicate,
                timeExpression
        ))
                .setParameter("fromInclusive", fromInclusive)
                .setParameter("toExclusive", toExclusive)
                .getResultList();

        return rows.stream()
                .map(row -> new DailyCountRow(toLocalDate(row[0]), toLong(row[1])))
                .toList();
    }

    private long toLong(Object value) {
        return value == null ? 0L : ((Number) value).longValue();
    }

    private LocalDate toLocalDate(Object value) {
        if (value instanceof LocalDate localDate) {
            return localDate;
        }
        if (value instanceof Date sqlDate) {
            return sqlDate.toLocalDate();
        }
        if (value instanceof Timestamp timestamp) {
            return timestamp.toLocalDateTime().toLocalDate();
        }
        return LocalDate.parse(String.valueOf(value));
    }

    public FinancialOverviewRow fetchFinancialOverview(LocalDateTime fromInclusive) {
        Object[] row = (Object[]) entityManager.createNativeQuery("""
                select
                    coalesce(sum(gross_scoin), 0) as gmv,
                    coalesce(sum(commission_scoin), 0) as platform_fee
                from payment_orders
                where status = 'PAID'
                  and coalesce(paid_at, updated_at) >= :fromInclusive
                """)
                .setParameter("fromInclusive", fromInclusive)
                .getSingleResult();

        java.math.BigDecimal totalEscrow = (java.math.BigDecimal) entityManager.createNativeQuery("""
                select coalesce(sum(balance), 0.00) from settlement_accounts
                """).getSingleResult();

        Number totalCredit = (Number) entityManager.createNativeQuery("""
                select coalesce(sum(balance), 0) from credit_ledger_accounts
                """).getSingleResult();

        return new FinancialOverviewRow(
                toLong(row[0]),
                toLong(row[1]),
                totalEscrow,
                totalCredit.longValue()
        );
    }

    public RetentionOverviewRow fetchRetentionOverview(LocalDateTime yesterday, LocalDateTime lastMonth) {
        Object[] row = (Object[]) entityManager.createNativeQuery("""
                select
                    (select count(*) from users) as total_users,
                    (select count(distinct user_id) from user_roles where role = 'MENTOR') as total_mentors,
                    (select count(*) from users where last_login_at >= :yesterday) as dau,
                    (select count(*) from users where last_login_at >= :lastMonth) as mau
                """)
                .setParameter("yesterday", yesterday)
                .setParameter("lastMonth", lastMonth)
                .getSingleResult();

        long totalUsers = toLong(row[0]);
        long totalMentors = toLong(row[1]);
        double conversionRate = totalUsers == 0 ? 0.0 : (totalMentors * 100.0 / totalUsers);

        return new RetentionOverviewRow(
                conversionRate,
                toLong(row[2]),
                toLong(row[3])
        );
    }

    public record UserOverviewCountsRow(
            long total,
            long active,
            long banned,
            long deleted,
            long menteeOnly,
            long mentor
    ) {
    }

    public record DailyCountRow(
            LocalDate bucketDate,
            long total
    ) {
    }

    public record FinancialOverviewRow(
            long gmv30dScoin,
            long platformFee30dScoin,
            java.math.BigDecimal totalEscrowVnd,
            long totalCreditLedgerScoin
    ) {
    }

    public record RetentionOverviewRow(
            double signupToMentorConversionRate,
            long dau,
            long mau
    ) {
    }
}
