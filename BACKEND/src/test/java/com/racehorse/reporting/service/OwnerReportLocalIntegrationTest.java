package com.racehorse.reporting.service;

import com.racehorse.common.exception.BusinessException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("local")
@EnabledIfEnvironmentVariable(named = "RUN_LOCAL_DB_INTEGRATION", matches = "true")
class OwnerReportLocalIntegrationTest {

    @Autowired
    private OwnerReportService service;

    @Autowired
    private JdbcTemplate jdbc;

    private UUID owner1;
    private UUID owner2;
    private UUID manager;
    private UUID horseId;
    private final List<UUID> createdTransactions = new ArrayList<>();
    private final List<UUID> createdReports = new ArrayList<>();

    @BeforeEach
    void setUp() {
        owner1 = UUID.fromString("44444444-4444-4444-4444-444444444444");
        manager = UUID.fromString("5D036EA7-77FD-4D63-90AC-B90E1E4CAE11");

        owner2 = jdbc.queryForObject("""
                select top 1 u.id from dbo.users u
                join dbo.user_roles ur on ur.user_id=u.id
                where ur.role='HORSE_OWNER' and u.id <> ?
                """, UUID.class, owner1);

        horseId = jdbc.queryForObject("""
                select top 1 horse_id from dbo.horse_ownerships where owner_id=?
                """, UUID.class, owner1);
    }

    @AfterEach
    void tearDown() {
        for (UUID reportId : createdReports) {
            jdbc.update("delete from dbo.owner_reports where id=?", reportId);
        }
        for (UUID txId : createdTransactions) {
            jdbc.update("delete from dbo.financial_transactions where id=?", txId);
        }
        jdbc.update("delete from dbo.owner_reports where period_start >= '2029-01-01'");
    }

    @Test
    void firstReportSucceedsAndComputesContentHash() {
        LocalDate start = LocalDate.of(2029, 1, 1);
        LocalDate end = LocalDate.of(2029, 1, 31);

        OwnerReportService.ReportView view = service.generate(owner1, start, end, manager);
        assertThat(view).isNotNull();
        createdReports.add(view.id());

        String hashInDb = jdbc.queryForObject(
                "select content_hash from dbo.owner_reports where id=?", String.class, view.id());
        assertThat(hashInDb).isNotNull().hasSize(64);
    }

    @Test
    void sequentialDuplicateReportIsRejectedWithoutInsertingNewRow() {
        LocalDate start = LocalDate.of(2029, 2, 1);
        LocalDate end = LocalDate.of(2029, 2, 28);

        OwnerReportService.ReportView first = service.generate(owner1, start, end, manager);
        createdReports.add(first.id());

        int countBefore = jdbc.queryForObject(
                "select count(*) from dbo.owner_reports where owner_user_id=? and period_start=? and period_end=?",
                Integer.class, owner1, start, end);
        assertThat(countBefore).isEqualTo(1);

        assertThatThrownBy(() -> service.generate(owner1, start, end, manager))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Báo cáo cho kỳ này với nội dung tương đương đã tồn tại");

        int countAfter = jdbc.queryForObject(
                "select count(*) from dbo.owner_reports where owner_user_id=? and period_start=? and period_end=?",
                Integer.class, owner1, start, end);
        assertThat(countAfter).isEqualTo(1);
    }

    @Test
    void samePeriodDifferentContentAllowedAndPreservesBothReports() {
        LocalDate start = LocalDate.of(2029, 3, 1);
        LocalDate end = LocalDate.of(2029, 3, 31);

        OwnerReportService.ReportView report1 = service.generate(owner1, start, end, manager);
        createdReports.add(report1.id());

        // Add a new transaction for horseId in that period
        UUID txId = UUID.randomUUID();
        createdTransactions.add(txId);
        jdbc.update("""
                insert into dbo.financial_transactions(id, horse_id, transaction_date, transaction_type, category, amount, currency, description, recorded_by_user_id)
                values(?, ?, '2029-03-15', 'EXPENSE', 'FEED_COST', 1500000, 'VND', N'Thức ăn bổ sung kỳ 3', ?)
                """, txId, horseId, manager);

        OwnerReportService.ReportView report2 = service.generate(owner1, start, end, manager);
        createdReports.add(report2.id());

        assertThat(report2.id()).isNotEqualTo(report1.id());

        int count = jdbc.queryForObject(
                "select count(*) from dbo.owner_reports where owner_user_id=? and period_start=? and period_end=?",
                Integer.class, owner1, start, end);
        assertThat(count).isEqualTo(2);

        String hash1 = jdbc.queryForObject("select content_hash from dbo.owner_reports where id=?", String.class, report1.id());
        String hash2 = jdbc.queryForObject("select content_hash from dbo.owner_reports where id=?", String.class, report2.id());
        assertThat(hash1).isNotEqualTo(hash2);
    }

    @Test
    void transactionAmountChangeInSamePeriodAllowsNewReportAndPreservesOld() {
        LocalDate start = LocalDate.of(2029, 9, 1);
        LocalDate end = LocalDate.of(2029, 9, 30);

        UUID txId = UUID.randomUUID();
        createdTransactions.add(txId);
        jdbc.update("""
                insert into dbo.financial_transactions(id, horse_id, transaction_date, transaction_type, category, amount, currency, description, recorded_by_user_id)
                values(?, ?, '2029-09-10', 'EXPENSE', 'FEED_COST', 5000000.00, 'VND', N'Chi phí thức ăn 9', ?)
                """, txId, horseId, manager);

        // First report with 5,000,000
        OwnerReportService.ReportView report1 = service.generate(owner1, start, end, manager);
        createdReports.add(report1.id());

        String hash1 = jdbc.queryForObject("select content_hash from dbo.owner_reports where id=?", String.class, report1.id());

        // Alter transaction amount in database without changing ID
        jdbc.update("update dbo.financial_transactions set amount = 7500000.00 where id = ?", txId);

        // Generate second report for exact same owner and exact same period
        OwnerReportService.ReportView report2 = service.generate(owner1, start, end, manager);
        createdReports.add(report2.id());

        String hash2 = jdbc.queryForObject("select content_hash from dbo.owner_reports where id=?", String.class, report2.id());

        assertThat(report2.id()).isNotEqualTo(report1.id());
        assertThat(hash2).isNotEqualTo(hash1);

        int count = jdbc.queryForObject(
                "select count(*) from dbo.owner_reports where owner_user_id=? and period_start=? and period_end=?",
                Integer.class, owner1, start, end);
        assertThat(count).isEqualTo(2);
    }

    @Test
    void concurrentEquivalentRequestsProduceExactlyOneCommittedReport() throws Exception {
        LocalDate start = LocalDate.of(2029, 4, 1);
        LocalDate end = LocalDate.of(2029, 4, 30);

        int threads = 2;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch readyLatch = new CountDownLatch(threads);
        CountDownLatch startLatch = new CountDownLatch(1);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger duplicateCount = new AtomicInteger(0);
        List<UUID> generatedIds = new CopyOnWriteArrayList<>();

        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            futures.add(executor.submit(() -> {
                readyLatch.countDown();
                try {
                    startLatch.await();
                    OwnerReportService.ReportView view = service.generate(owner1, start, end, manager);
                    successCount.incrementAndGet();
                    generatedIds.add(view.id());
                } catch (BusinessException ex) {
                    if (ex.getMessage().contains("tương đương đã tồn tại")) {
                        duplicateCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    // unexpected
                }
            }));
        }

        readyLatch.await(5, TimeUnit.SECONDS);
        startLatch.countDown();

        for (Future<?> f : futures) {
            f.get(10, TimeUnit.SECONDS);
        }
        executor.shutdown();

        createdReports.addAll(generatedIds);

        assertThat(successCount.get()).isEqualTo(1);
        assertThat(duplicateCount.get()).isEqualTo(1);

        int committedInDb = jdbc.queryForObject(
                "select count(*) from dbo.owner_reports where owner_user_id=? and period_start=? and period_end=?",
                Integer.class, owner1, start, end);
        assertThat(committedInDb).isEqualTo(1);
    }

    @Test
    void concurrentRequestsWithDifferentContentInSamePeriodBothCommit() throws Exception {
        LocalDate start = LocalDate.of(2029, 5, 1);
        LocalDate end = LocalDate.of(2029, 5, 31);

        // Pre-insert transaction 1 and transaction 2
        UUID tx1 = UUID.randomUUID();
        createdTransactions.add(tx1);
        jdbc.update("""
                insert into dbo.financial_transactions(id, horse_id, transaction_date, transaction_type, category, amount, currency, description, recorded_by_user_id)
                values(?, ?, '2029-05-10', 'EXPENSE', 'FEED_COST', 1000000, 'VND', N'Thức ăn kỳ 5 - tx1', ?)
                """, tx1, horseId, manager);

        UUID tx2 = UUID.randomUUID();
        createdTransactions.add(tx2);
        jdbc.update("""
                insert into dbo.financial_transactions(id, horse_id, transaction_date, transaction_type, category, amount, currency, description, recorded_by_user_id)
                values(?, ?, '2029-05-20', 'EXPENSE', 'MEDICAL_COST', 2500000, 'VND', N'Thú y kỳ 5 - tx2', ?)
                """, tx2, horseId, manager);

        List<OwnerReportService.HorseItem> horses = service.queryOwnerHorses(owner1);
        OwnerReportService.TransactionItem item1 = new OwnerReportService.TransactionItem(
                tx1, horseId, LocalDate.of(2029, 5, 10), "EXPENSE", "FEED_COST", new BigDecimal("1000000.00"), "Thức ăn kỳ 5 - tx1");
        OwnerReportService.TransactionItem item2 = new OwnerReportService.TransactionItem(
                tx2, horseId, LocalDate.of(2029, 5, 20), "EXPENSE", "MEDICAL_COST", new BigDecimal("2500000.00"), "Thú y kỳ 5 - tx2");

        String hashContent1 = OwnerReportService.computeContentHash(horses, List.of(item1));
        String hashContent2 = OwnerReportService.computeContentHash(horses, List.of(item1, item2));
        assertThat(hashContent1).isNotEqualTo(hashContent2);

        int threads = 2;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch readyLatch = new CountDownLatch(threads);
        CountDownLatch startLatch = new CountDownLatch(1);

        AtomicInteger successCount = new AtomicInteger(0);
        List<UUID> generatedIds = new CopyOnWriteArrayList<>();
        List<String> computedHashes = new CopyOnWriteArrayList<>();

        // Thread 1: Concurrent insert with business content 1 (tx1 only)
        UUID id1 = UUID.randomUUID();
        Future<?> f1 = executor.submit(() -> {
            readyLatch.countDown();
            try {
                startLatch.await();
                jdbc.update("insert into dbo.owner_reports(id,owner_user_id,period_start,period_end,generated_at,generated_by_user_id,horse_ids_json,transaction_ids_json,content_hash) values(?,?,?,?,?,?,?,?,?)",
                        id1, owner1, start, end, OffsetDateTime.now(), manager, "[]", "[]", hashContent1);
                successCount.incrementAndGet();
                generatedIds.add(id1);
                computedHashes.add(hashContent1);
            } catch (Exception ignored) {}
        });

        // Thread 2: Concurrently generates report via service (which reads tx1 + tx2 and computes hashContent2)
        Future<?> f2 = executor.submit(() -> {
            readyLatch.countDown();
            try {
                startLatch.await();
                OwnerReportService.ReportView view = service.generate(owner1, start, end, manager);
                successCount.incrementAndGet();
                generatedIds.add(view.id());
                String hash = jdbc.queryForObject("select content_hash from dbo.owner_reports where id=?", String.class, view.id());
                computedHashes.add(hash);
            } catch (Exception ignored) {}
        });

        readyLatch.await(5, TimeUnit.SECONDS);
        startLatch.countDown();

        f1.get(10, TimeUnit.SECONDS);
        f2.get(10, TimeUnit.SECONDS);

        executor.shutdown();

        createdReports.addAll(generatedIds);

        assertThat(successCount.get()).isEqualTo(2);
        assertThat(generatedIds).hasSize(2);
        assertThat(generatedIds.get(0)).isNotEqualTo(generatedIds.get(1));

        assertThat(computedHashes).hasSize(2);
        assertThat(computedHashes.get(0)).isNotEqualTo(computedHashes.get(1));

        int committedInDb = jdbc.queryForObject(
                "select count(*) from dbo.owner_reports where owner_user_id=? and period_start=? and period_end=?",
                Integer.class, owner1, start, end);
        assertThat(committedInDb).isEqualTo(2);
    }

    @Test
    void differentOwnersHandledIndependently() {
        LocalDate start = LocalDate.of(2029, 6, 1);
        LocalDate end = LocalDate.of(2029, 6, 30);

        OwnerReportService.ReportView view1 = service.generate(owner1, start, end, manager);
        createdReports.add(view1.id());

        OwnerReportService.ReportView view2 = service.generate(owner2, start, end, manager);
        createdReports.add(view2.id());

        assertThat(view1.id()).isNotEqualTo(view2.id());
        assertThat(view1.ownerId()).isEqualTo(owner1);
        assertThat(view2.ownerId()).isEqualTo(owner2);
    }
}
