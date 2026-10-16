package com.racehorse.reporting.service;

import com.racehorse.audit.service.AuditService;
import com.racehorse.common.exception.BusinessException;
import com.racehorse.common.exception.ResourceNotFoundException;
import com.racehorse.finance.service.FinanceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OwnerReportServiceTest {

    @Mock
    private JdbcTemplate jdbc;

    @Mock
    private FinanceService finance;

    @Mock
    private AuditService audit;

    private ObjectMapper json;
    private OwnerReportService service;

    private final UUID ownerId = UUID.randomUUID();
    private final UUID actorId = UUID.randomUUID();
    private final UUID reportId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        json = new ObjectMapper();
        service = new OwnerReportService(jdbc, json, finance, audit);
    }

    @Test
    void generateValidatesDateRanges() {
        assertThatThrownBy(() -> service.generate(ownerId, null, LocalDate.now(), actorId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Khoảng báo cáo không hợp lệ");

        assertThatThrownBy(() -> service.generate(ownerId, LocalDate.of(2026, 10, 10), LocalDate.of(2026, 10, 1), actorId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Khoảng báo cáo không hợp lệ");
    }

    @Test
    void generateValidatesOwnerRole() {
        LocalDate start = LocalDate.of(2026, 9, 1);
        LocalDate end = LocalDate.of(2026, 9, 30);

        when(jdbc.queryForObject(contains("from users u join user_roles"), eq(Integer.class), eq(ownerId)))
                .thenReturn(0);

        assertThatThrownBy(() -> service.generate(ownerId, start, end, actorId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Tài khoản không phải chủ sở hữu ngựa");
    }

    @Test
    void generateThrowsWhenDuplicateReportExistsInPreCheck() {
        LocalDate start = LocalDate.of(2026, 9, 1);
        LocalDate end = LocalDate.of(2026, 9, 30);

        when(jdbc.queryForObject(contains("from users u join user_roles"), eq(Integer.class), eq(ownerId)))
                .thenReturn(1);
        when(jdbc.query(contains("from horse_ownerships"), any(RowMapper.class), eq(ownerId)))
                .thenReturn(List.of());
        when(jdbc.queryForObject(contains("from owner_reports where owner_user_id=? and period_start=? and period_end=? and content_hash=?"),
                eq(Integer.class), eq(ownerId), eq(start), eq(end), anyString()))
                .thenReturn(1);

        assertThatThrownBy(() -> service.generate(ownerId, start, end, actorId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Báo cáo cho kỳ này với nội dung tương đương đã tồn tại");

        verify(jdbc, never()).update(contains("insert into owner_reports"), any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void generateThrowsWhenDuplicateKeyExceptionOccurs() {
        LocalDate start = LocalDate.of(2026, 9, 1);
        LocalDate end = LocalDate.of(2026, 9, 30);

        when(jdbc.queryForObject(contains("from users u join user_roles"), eq(Integer.class), eq(ownerId)))
                .thenReturn(1);
        when(jdbc.query(contains("from horse_ownerships"), any(RowMapper.class), eq(ownerId)))
                .thenReturn(List.of());
        when(jdbc.queryForObject(contains("from owner_reports where owner_user_id=? and period_start=? and period_end=? and content_hash=?"),
                eq(Integer.class), eq(ownerId), eq(start), eq(end), anyString()))
                .thenReturn(0);

        when(jdbc.update(contains("insert into owner_reports"), any(), eq(ownerId), eq(start), eq(end), any(), eq(actorId), any(), any(), anyString()))
                .thenThrow(new DuplicateKeyException("Duplicate entry"));

        assertThatThrownBy(() -> service.generate(ownerId, start, end, actorId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Báo cáo cho kỳ này với nội dung tương đương đã tồn tại");
    }

    @Test
    void generateAllowsDifferentContentInSamePeriod() {
        LocalDate start = LocalDate.of(2026, 9, 1);
        LocalDate end = LocalDate.of(2026, 9, 30);
        UUID horseId = UUID.randomUUID();

        when(jdbc.queryForObject(contains("from users u join user_roles"), eq(Integer.class), eq(ownerId)))
                .thenReturn(1);
        when(jdbc.query(contains("from horse_ownerships"), any(RowMapper.class), eq(ownerId)))
                .thenReturn(List.of(new OwnerReportService.HorseItem(horseId, "H01", "Thunder")));
        when(jdbc.query(contains("from financial_transactions"), any(RowMapper.class), any()))
                .thenReturn(List.of());
        when(jdbc.queryForObject(contains("from owner_reports where owner_user_id=? and period_start=? and period_end=? and content_hash=?"),
                eq(Integer.class), eq(ownerId), eq(start), eq(end), anyString()))
                .thenReturn(0);
        when(jdbc.queryForObject(contains("select username from users where id=?"), eq(String.class), eq(ownerId)))
                .thenReturn("owner1");

        when(jdbc.query(contains("from owner_reports r join users u"), any(RowMapper.class), any(UUID.class)))
                .thenAnswer(inv -> {
                    RowMapper<?> mapper = inv.getArgument(1);
                    java.sql.ResultSet rs = mock(java.sql.ResultSet.class);
                    when(rs.getObject("owner_user_id", UUID.class)).thenReturn(ownerId);
                    when(rs.getString("username")).thenReturn("owner1");
                    when(rs.getObject("period_start", LocalDate.class)).thenReturn(start);
                    when(rs.getObject("period_end", LocalDate.class)).thenReturn(end);
                    when(rs.getObject("generated_at", OffsetDateTime.class)).thenReturn(OffsetDateTime.now());
                    when(rs.getObject("generated_by_user_id", UUID.class)).thenReturn(actorId);
                    when(rs.getString("horse_ids_json")).thenReturn("[{\"id\":\"" + horseId + "\"}]");
                    when(rs.getString("transaction_ids_json")).thenReturn("[]");
                    return List.of(mapper.mapRow(rs, 1));
                });

        OwnerReportService.ReportView view = service.generate(ownerId, start, end, actorId);
        assertThat(view).isNotNull();
        verify(jdbc).update(contains("insert into owner_reports"), any(), eq(ownerId), eq(start), eq(end), any(), eq(actorId), any(), any(), anyString());
    }

    @Test
    void contentHashNormalizationHandlesOrderAndDeduplication() {
        UUID h1 = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID h2 = UUID.fromString("22222222-2222-2222-2222-222222222222");
        UUID t1 = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
        UUID t2 = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");

        String hash1 = OwnerReportService.computeContentHashFromIds(List.of(h1, h2), List.of(t1, t2));
        String hash2 = OwnerReportService.computeContentHashFromIds(List.of(h2, h1), List.of(t2, t1));
        String hashWithDups = OwnerReportService.computeContentHashFromIds(List.of(h1, h2, h1), List.of(t1, t2, t2));

        assertThat(hash1).isEqualTo(hash2);
        assertThat(hash1).isEqualTo(hashWithDups);
        assertThat(hash1).hasSize(64);

        String hashDiffTx = OwnerReportService.computeContentHashFromIds(List.of(h1, h2), List.of(t1));
        assertThat(hash1).isNotEqualTo(hashDiffTx);
    }

    @Test
    void contentHashChangesWhenTransactionAmountChangesEvenIfIdRemainsTheSame() {
        UUID h1 = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID t1 = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");

        OwnerReportService.HorseItem horse = new OwnerReportService.HorseItem(h1, "H-01", "Thunder");
        OwnerReportService.TransactionItem txOrig = new OwnerReportService.TransactionItem(
                t1, h1, LocalDate.of(2026, 9, 15), "EXPENSE", "FEED_COST", new BigDecimal("5000000.00"), "Feed");
        OwnerReportService.TransactionItem txAmountChanged = new OwnerReportService.TransactionItem(
                t1, h1, LocalDate.of(2026, 9, 15), "EXPENSE", "FEED_COST", new BigDecimal("6000000.00"), "Feed");

        String hashOrig = OwnerReportService.computeContentHash(List.of(horse), List.of(txOrig));
        String hashChanged = OwnerReportService.computeContentHash(List.of(horse), List.of(txAmountChanged));

        assertThat(hashOrig).isNotEqualTo(hashChanged);
    }

    @Test
    void contentHashChangesWhenTransactionTypeOrCategoryChanges() {
        UUID h1 = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID t1 = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");

        OwnerReportService.HorseItem horse = new OwnerReportService.HorseItem(h1, "H-01", "Thunder");
        OwnerReportService.TransactionItem tx1 = new OwnerReportService.TransactionItem(
                t1, h1, LocalDate.of(2026, 9, 15), "EXPENSE", "FEED_COST", new BigDecimal("5000000.00"), "Feed");
        OwnerReportService.TransactionItem txTypeChanged = new OwnerReportService.TransactionItem(
                t1, h1, LocalDate.of(2026, 9, 15), "REVENUE", "FEED_COST", new BigDecimal("5000000.00"), "Feed");
        OwnerReportService.TransactionItem txCatChanged = new OwnerReportService.TransactionItem(
                t1, h1, LocalDate.of(2026, 9, 15), "EXPENSE", "MEDICAL_COST", new BigDecimal("5000000.00"), "Feed");

        String hash1 = OwnerReportService.computeContentHash(List.of(horse), List.of(tx1));
        String hashType = OwnerReportService.computeContentHash(List.of(horse), List.of(txTypeChanged));
        String hashCat = OwnerReportService.computeContentHash(List.of(horse), List.of(txCatChanged));

        assertThat(hash1).isNotEqualTo(hashType);
        assertThat(hash1).isNotEqualTo(hashCat);
    }

    @Test
    void getThrowsNotFoundWhenReportDoesNotExist() {
        when(jdbc.query(contains("from owner_reports"), any(RowMapper.class), eq(reportId)))
                .thenReturn(List.of());

        assertThatThrownBy(() -> service.get(reportId, actorId, true))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Không tìm thấy báo cáo");
    }

    @Test
    void getThrowsNotFoundWhenNonManagerAccessesOtherOwnersReport() {
        UUID otherOwner = UUID.randomUUID();
        when(jdbc.query(contains("from owner_reports"), any(RowMapper.class), eq(reportId)))
                .thenAnswer(inv -> {
                    RowMapper<?> mapper = inv.getArgument(1);
                    java.sql.ResultSet rs = mock(java.sql.ResultSet.class);
                    when(rs.getObject("owner_user_id", UUID.class)).thenReturn(otherOwner);
                    when(rs.getString("username")).thenReturn("otherOwner");
                    when(rs.getObject("period_start", LocalDate.class)).thenReturn(LocalDate.of(2026, 9, 1));
                    when(rs.getObject("period_end", LocalDate.class)).thenReturn(LocalDate.of(2026, 9, 30));
                    when(rs.getObject("generated_at", OffsetDateTime.class)).thenReturn(OffsetDateTime.now());
                    when(rs.getObject("generated_by_user_id", UUID.class)).thenReturn(actorId);
                    when(rs.getString("horse_ids_json")).thenReturn("[]");
                    when(rs.getString("transaction_ids_json")).thenReturn("[]");
                    return List.of(mapper.mapRow(rs, 1));
                });

        assertThatThrownBy(() -> service.get(reportId, actorId, false))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Không tìm thấy báo cáo");
    }

    @Test
    void getCalculatesExpenseRevenueAndNetCorrectly() {
        UUID horseId = UUID.randomUUID();
        String horseIdsJson = "[{\"id\":\"" + horseId + "\"}]";
        UUID tx1 = UUID.randomUUID();
        UUID tx2 = UUID.randomUUID();
        String txIdsJson = "[{\"id\":\"" + tx1 + "\"},{\"id\":\"" + tx2 + "\"}]";

        when(jdbc.query(contains("from owner_reports"), any(RowMapper.class), eq(reportId)))
                .thenAnswer(inv -> {
                    RowMapper<?> mapper = inv.getArgument(1);
                    java.sql.ResultSet rs = mock(java.sql.ResultSet.class);
                    when(rs.getObject("owner_user_id", UUID.class)).thenReturn(ownerId);
                    when(rs.getString("username")).thenReturn("owner1");
                    when(rs.getObject("period_start", LocalDate.class)).thenReturn(LocalDate.of(2026, 9, 1));
                    when(rs.getObject("period_end", LocalDate.class)).thenReturn(LocalDate.of(2026, 9, 30));
                    when(rs.getObject("generated_at", OffsetDateTime.class)).thenReturn(OffsetDateTime.now());
                    when(rs.getObject("generated_by_user_id", UUID.class)).thenReturn(actorId);
                    when(rs.getString("horse_ids_json")).thenReturn(horseIdsJson);
                    when(rs.getString("transaction_ids_json")).thenReturn(txIdsJson);
                    return List.of(mapper.mapRow(rs, 1));
                });

        when(jdbc.query(contains("from horses where id in"), any(RowMapper.class), any()))
                .thenReturn(List.of(new OwnerReportService.HorseItem(horseId, "H-001", "Thunder")));

        FinanceService.TransactionView tExpense = new FinanceService.TransactionView(
                tx1, horseId, "Thunder", UUID.randomUUID(), "Barn A",
                LocalDate.of(2026, 9, 15), "EXPENSE", "BOARDING", new BigDecimal("5000000"),
                "VND", "Tiền chuồng", "MANUAL", null, actorId, OffsetDateTime.now());
        FinanceService.TransactionView tRevenue = new FinanceService.TransactionView(
                tx2, horseId, "Thunder", UUID.randomUUID(), "Barn A",
                LocalDate.of(2026, 9, 20), "REVENUE", "PRIZE", new BigDecimal("12000000"),
                "VND", "Tiền thưởng đua", "MANUAL", null, actorId, OffsetDateTime.now());

        when(finance.byIds(anyList())).thenReturn(List.of(tExpense, tRevenue));

        OwnerReportService.ReportView view = service.get(reportId, ownerId, false);

        assertThat(view).isNotNull();
        assertThat(view.ownerUsername()).isEqualTo("owner1");
        assertThat(view.expense()).isEqualByComparingTo(new BigDecimal("5000000"));
        assertThat(view.revenue()).isEqualByComparingTo(new BigDecimal("12000000"));
        assertThat(view.net()).isEqualByComparingTo(new BigDecimal("7000000"));
        assertThat(view.horses()).hasSize(1);
        assertThat(view.transactions()).hasSize(2);
    }

    @Test
    void csvDownloadProducesValidCsvWithBom() {
        when(jdbc.query(contains("from owner_reports"), any(RowMapper.class), eq(reportId)))
                .thenAnswer(inv -> {
                    RowMapper<?> mapper = inv.getArgument(1);
                    java.sql.ResultSet rs = mock(java.sql.ResultSet.class);
                    when(rs.getObject("owner_user_id", UUID.class)).thenReturn(ownerId);
                    when(rs.getString("username")).thenReturn("ownerCsv");
                    when(rs.getObject("period_start", LocalDate.class)).thenReturn(LocalDate.of(2026, 9, 1));
                    when(rs.getObject("period_end", LocalDate.class)).thenReturn(LocalDate.of(2026, 9, 30));
                    when(rs.getObject("generated_at", OffsetDateTime.class)).thenReturn(OffsetDateTime.now());
                    when(rs.getObject("generated_by_user_id", UUID.class)).thenReturn(actorId);
                    when(rs.getString("horse_ids_json")).thenReturn("[]");
                    when(rs.getString("transaction_ids_json")).thenReturn("[]");
                    return List.of(mapper.mapRow(rs, 1));
                });
        when(finance.byIds(anyList())).thenReturn(List.of());

        byte[] bytes = service.csvDownload(reportId, ownerId, false);

        assertThat(bytes).isNotEmpty();
        String csv = new String(bytes, StandardCharsets.UTF_8);
        assertThat(csv).startsWith("\uFEFF");
        assertThat(csv).contains("BÁO CÁO TÀI CHÍNH CHỦ SỞ HỮU NGỰA");
        assertThat(csv).contains("ownerCsv");
    }

    @Test
    void getOwnersQueriesActiveHorseOwners() {
        when(jdbc.query(contains("where ur.role='HORSE_OWNER' and u.enabled=1"), any(RowMapper.class)))
                .thenReturn(List.of(new OwnerReportService.OwnerOption(ownerId, "ownerA", "a@test.com", 2)));

        List<OwnerReportService.OwnerOption> owners = service.getOwners();

        assertThat(owners).hasSize(1);
        assertThat(owners.get(0).username()).isEqualTo("ownerA");
        assertThat(owners.get(0).horseCount()).isEqualTo(2);
    }

    @Test
    void listIsolatesOwnerRecordsUnlessManager() {
        LocalDate from = LocalDate.of(2026, 9, 1);
        LocalDate to = LocalDate.of(2026, 9, 30);
        OwnerReportService.ReportSummary summary = new OwnerReportService.ReportSummary(
                reportId, ownerId, "owner1", from, to, OffsetDateTime.now(), actorId);

        when(jdbc.query(anyString(), any(RowMapper.class), any(Object[].class)))
                .thenReturn(List.of(summary));

        List<OwnerReportService.ReportSummary> managerList = service.list(ownerId, true, from, to);
        assertThat(managerList).hasSize(1);

        List<OwnerReportService.ReportSummary> ownerList = service.list(ownerId, false, from, to);
        assertThat(ownerList).hasSize(1);
    }

    @Test
    void jsonDownloadProducesValidJsonBytes() {
        when(jdbc.query(contains("from owner_reports"), any(RowMapper.class), eq(reportId)))
                .thenAnswer(inv -> {
                    RowMapper<?> mapper = inv.getArgument(1);
                    java.sql.ResultSet rs = mock(java.sql.ResultSet.class);
                    when(rs.getObject("owner_user_id", UUID.class)).thenReturn(ownerId);
                    when(rs.getString("username")).thenReturn("ownerJson");
                    when(rs.getObject("period_start", LocalDate.class)).thenReturn(LocalDate.of(2026, 9, 1));
                    when(rs.getObject("period_end", LocalDate.class)).thenReturn(LocalDate.of(2026, 9, 30));
                    when(rs.getObject("generated_at", OffsetDateTime.class)).thenReturn(OffsetDateTime.now());
                    when(rs.getObject("generated_by_user_id", UUID.class)).thenReturn(actorId);
                    when(rs.getString("horse_ids_json")).thenReturn("[]");
                    when(rs.getString("transaction_ids_json")).thenReturn("[]");
                    return List.of(mapper.mapRow(rs, 1));
                });
        when(finance.byIds(anyList())).thenReturn(List.of());

        byte[] bytes = service.jsonDownload(reportId, ownerId, false);
        assertThat(bytes).isNotEmpty();
        String jsonStr = new String(bytes, StandardCharsets.UTF_8);
        assertThat(jsonStr).contains("\"ownerUsername\" : \"ownerJson\"");
        assertThat(jsonStr).contains("\"currency\" : \"VND\"");
    }
}
