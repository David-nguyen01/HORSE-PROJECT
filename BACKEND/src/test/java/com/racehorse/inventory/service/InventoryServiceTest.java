package com.racehorse.inventory.service;

import com.racehorse.common.exception.BusinessException;
import com.racehorse.common.exception.ResourceNotFoundException;
import com.racehorse.finance.service.FinanceService;
import com.racehorse.inventory.dto.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class InventoryServiceTest {

    private JdbcTemplate jdbc;
    private ObjectMapper json;
    private Clock clock;
    private InventoryService service;
    private FinanceService finance;

    @BeforeEach
    void setUp() {
        jdbc = mock(JdbcTemplate.class);
        json = new ObjectMapper();
        clock = Clock.fixed(Instant.parse("2026-09-25T10:00:00Z"), ZoneOffset.UTC);
        finance = mock(FinanceService.class);
        service = new InventoryService(jdbc, json, clock, finance);
    }

    @Test
    void createItemPersistsActiveItem() {
        UUID actorId = UUID.randomUUID();
        CreateInventoryItemRequest req = new CreateInventoryItemRequest(
                "Cỏ khô Alfalfa", "FEED", "kg", 50.0, 100.0, "Kho Thức Ăn A", "Cỏ khô Mỹ thượng hạng");

        String itemJson = """
                {
                  "itemName": "Cỏ khô Alfalfa",
                  "category": "FEED",
                  "unit": "kg",
                  "quantityOnHand": 50.0,
                  "minThreshold": 100.0,
                  "location": "Kho Thức Ăn A",
                  "description": "Cỏ khô Mỹ thượng hạng"
                }
                """;

        when(jdbc.query(contains("where id=? and module_code='INVENTORY' and record_type='INVENTORY_ITEM'"),
                any(RowMapper.class), any()))
                .thenAnswer(inv -> {
                    RowMapper<InventoryItemResponse> mapper = inv.getArgument(1);
                    java.sql.ResultSet rs = mock(java.sql.ResultSet.class);
                    when(rs.getObject("id", UUID.class)).thenReturn(UUID.randomUUID());
                    when(rs.getString("status")).thenReturn("ACTIVE");
                    when(rs.getObject("created_at", java.time.OffsetDateTime.class)).thenReturn(java.time.OffsetDateTime.now());
                    when(rs.getObject("updated_at", java.time.OffsetDateTime.class)).thenReturn(java.time.OffsetDateTime.now());
                    when(rs.getString("data_json")).thenReturn(itemJson);
                    return List.of(mapper.mapRow(rs, 1));
                });

        InventoryItemResponse res = service.createItem(actorId, req);

        assertThat(res.itemName()).isEqualTo("Cỏ khô Alfalfa");
        assertThat(res.category()).isEqualTo("FEED");
        assertThat(res.quantityOnHand()).isEqualTo(50.0);
        assertThat(res.minThreshold()).isEqualTo(100.0);
        assertThat(res.isLowStock()).isTrue(); // 50 <= 100

        verify(jdbc).update(contains("insert into module_records"), any(), eq(actorId), any(), anyString());
    }

    @Test
    void updateStockUpdatesQuantityOnHand() {
        UUID itemId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();

        String itemJson = """
                {
                  "itemName": "Cỏ khô Alfalfa",
                  "category": "FEED",
                  "unit": "kg",
                  "quantityOnHand": 50.0,
                  "minThreshold": 100.0
                }
                """;

        when(jdbc.query(contains("where id=? and module_code='INVENTORY' and record_type='INVENTORY_ITEM'"),
                any(RowMapper.class), eq(itemId)))
                .thenAnswer(inv -> {
                    RowMapper<InventoryItemResponse> mapper = inv.getArgument(1);
                    java.sql.ResultSet rs = mock(java.sql.ResultSet.class);
                    when(rs.getObject("id", UUID.class)).thenReturn(itemId);
                    when(rs.getString("status")).thenReturn("ACTIVE");
                    when(rs.getObject("created_at", java.time.OffsetDateTime.class)).thenReturn(java.time.OffsetDateTime.now());
                    when(rs.getObject("updated_at", java.time.OffsetDateTime.class)).thenReturn(java.time.OffsetDateTime.now());
                    when(rs.getString("data_json")).thenReturn(itemJson);
                    return List.of(mapper.mapRow(rs, 1));
                });

        when(jdbc.queryForObject(eq("select data_json from module_records where id=?"), eq(String.class), eq(itemId)))
                .thenReturn(itemJson);

        UpdateStockRequest req = new UpdateStockRequest(200.0, "Nhập kho 150kg");

        service.updateStock(itemId, actorId, req);

        verify(jdbc).update(contains("update module_records set data_json=?, updated_at=SYSDATETIMEOFFSET() where id=?"),
                contains("\"quantityOnHand\":200.0"), eq(itemId));
    }

    @Test
    void reviewReplenishmentApprovalWaitsForReceiptBeforeIncreasingStock() {
        UUID reqId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();
        UUID managerId = UUID.randomUUID();
        when(jdbc.queryForList(contains("with (updlock,holdlock)"), eq(reqId)))
                .thenReturn(List.of(Map.of("status", "PENDING_APPROVAL")));

        String reqJson = """
                {
                  "itemId": "%s",
                  "itemName": "Cỏ khô Alfalfa",
                  "category": "FEED",
                  "requestedQuantity": 100.0,
                  "unit": "kg",
                  "reason": "Sắp hết",
                  "proposedByUserId": "%s",
                  "proposedByName": "groom_1"
                }
                """.formatted(itemId, UUID.randomUUID());

        when(jdbc.query(contains("where id=? and module_code='INVENTORY' and record_type='REPLENISHMENT_REQUEST'"),
                any(RowMapper.class), eq(reqId)))
                .thenAnswer(inv -> {
                    RowMapper<ReplenishmentResponse> mapper = inv.getArgument(1);
                    java.sql.ResultSet rs = mock(java.sql.ResultSet.class);
                    when(rs.getObject("id", UUID.class)).thenReturn(reqId);
                    when(rs.getString("status")).thenReturn("PENDING_APPROVAL");
                    when(rs.getObject("created_at", java.time.OffsetDateTime.class)).thenReturn(java.time.OffsetDateTime.now());
                    when(rs.getString("data_json")).thenReturn(reqJson);
                    return List.of(mapper.mapRow(rs, 1));
                });

        when(jdbc.queryForObject(eq("select data_json from module_records where id=?"), eq(String.class), eq(reqId)))
                .thenReturn(reqJson);

        when(jdbc.query(eq("select username from users where id=?"), any(RowMapper.class), eq(managerId)))
                .thenReturn(List.of("club_manager"));

        String currentItemJson = """
                {
                  "itemName": "Cỏ khô Alfalfa",
                  "category": "FEED",
                  "quantityOnHand": 40.0
                }
                """;
        when(jdbc.queryForObject(eq("select data_json from module_records where id=?"), eq(String.class), eq(itemId)))
                .thenReturn(currentItemJson);

        ReviewReplenishmentRequest reviewReq = new ReviewReplenishmentRequest("APPROVED", "Đã duyệt nhập kho khẩn");

        service.reviewReplenishment(reqId, managerId, reviewReq);

        // Verify proposal status update
        verify(jdbc).update(contains("update module_records set status=?, data_json=?, updated_at=SYSDATETIMEOFFSET() where id=?"),
                eq("APPROVED"), anyString(), eq(reqId));

        // Approval alone does not claim that the goods physically arrived.
        verify(jdbc, never()).update(contains("update module_records set data_json=?, updated_at=SYSDATETIMEOFFSET() where id=?"),
                anyString(), eq(itemId));
    }

    @Test
    void reviewReplenishmentRejectsAlreadyProcessed() {
        UUID reqId = UUID.randomUUID();
        UUID managerId = UUID.randomUUID();
        when(jdbc.queryForList(contains("with (updlock,holdlock)"), eq(reqId)))
                .thenReturn(List.of(Map.of("status", "APPROVED")));

        when(jdbc.query(contains("where id=? and module_code='INVENTORY' and record_type='REPLENISHMENT_REQUEST'"),
                any(RowMapper.class), eq(reqId)))
                .thenAnswer(inv -> {
                    RowMapper<ReplenishmentResponse> mapper = inv.getArgument(1);
                    java.sql.ResultSet rs = mock(java.sql.ResultSet.class);
                    when(rs.getObject("id", UUID.class)).thenReturn(reqId);
                    when(rs.getString("status")).thenReturn("APPROVED");
                    when(rs.getObject("created_at", java.time.OffsetDateTime.class)).thenReturn(java.time.OffsetDateTime.now());
                    when(rs.getString("data_json")).thenReturn("{}");
                    return List.of(mapper.mapRow(rs, 1));
                });

        ReviewReplenishmentRequest reviewReq = new ReviewReplenishmentRequest("APPROVED", "Again");

        assertThatThrownBy(() -> service.reviewReplenishment(reqId, managerId, reviewReq))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("đã được xử lý trước đó");
    }

    @Test
    void getItemMapsReferencePriceCorrectly() {
        UUID itemId = UUID.randomUUID();
        String itemJson = """
                {
                  "itemName": "Cỏ khô Alfalfa",
                  "category": "FEED",
                  "unit": "kg",
                  "quantityOnHand": 850.0,
                  "minThreshold": 300.0,
                  "referencePrice": 45000.0
                }
                """;

        when(jdbc.query(contains("where id=? and module_code='INVENTORY' and record_type='INVENTORY_ITEM'"),
                any(RowMapper.class), eq(itemId)))
                .thenAnswer(inv -> {
                    RowMapper<InventoryItemResponse> mapper = inv.getArgument(1);
                    java.sql.ResultSet rs = mock(java.sql.ResultSet.class);
                    when(rs.getObject("id", UUID.class)).thenReturn(itemId);
                    when(rs.getString("status")).thenReturn("ACTIVE");
                    when(rs.getObject("created_at", java.time.OffsetDateTime.class)).thenReturn(java.time.OffsetDateTime.now());
                    when(rs.getObject("updated_at", java.time.OffsetDateTime.class)).thenReturn(java.time.OffsetDateTime.now());
                    when(rs.getString("data_json")).thenReturn(itemJson);
                    return List.of(mapper.mapRow(rs, 1));
                });

        InventoryItemResponse item = service.getItem(itemId);
        assertThat(item.referencePrice()).isEqualTo(45000.0);
        assertThat(item.isLowStock()).isFalse();
    }

    @Test
    void createReplenishmentSucceedsForPositiveIntegerQuantity() {
        UUID actorId = UUID.randomUUID();
        CreateReplenishmentRequest req = new CreateReplenishmentRequest(
                null, "Cỏ linh lăng", "FEED", 50.0, "kg", "Cần bổ sung gấp");

        String reqJson = """
                {
                  "itemId": null,
                  "itemName": "Cỏ linh lăng",
                  "category": "FEED",
                  "requestedQuantity": 50,
                  "unit": "kg",
                  "reason": "Cần bổ sung gấp",
                  "proposedByUserId": "%s",
                  "proposedByName": "staff_1"
                }
                """.formatted(actorId);

        when(jdbc.query(eq("select username from users where id=?"), any(RowMapper.class), eq(actorId)))
                .thenReturn(List.of("staff_1"));

        when(jdbc.query(contains("where id=? and module_code='INVENTORY' and record_type='REPLENISHMENT_REQUEST'"),
                any(RowMapper.class), any()))
                .thenAnswer(inv -> {
                    RowMapper<ReplenishmentResponse> mapper = inv.getArgument(1);
                    java.sql.ResultSet rs = mock(java.sql.ResultSet.class);
                    when(rs.getObject("id", UUID.class)).thenReturn(UUID.randomUUID());
                    when(rs.getString("status")).thenReturn("PENDING_APPROVAL");
                    when(rs.getObject("created_at", java.time.OffsetDateTime.class)).thenReturn(java.time.OffsetDateTime.now());
                    when(rs.getString("data_json")).thenReturn(reqJson);
                    return List.of(mapper.mapRow(rs, 1));
                });

        ReplenishmentResponse res = service.createReplenishment(actorId, req);
        assertThat(res.itemName()).isEqualTo("Cỏ linh lăng");
        assertThat(res.requestedQuantity()).isEqualTo(50.0);
        verify(jdbc).update(contains("insert into module_records"), any(), eq(actorId), any(), anyString());
    }

    @Test
    void createReplenishmentRejectsDecimalQuantity() {
        UUID actorId = UUID.randomUUID();
        CreateReplenishmentRequest req = new CreateReplenishmentRequest(
                null, "Cỏ linh lăng", "FEED", 49.81, "kg", "Cần bổ sung");

        assertThatThrownBy(() -> service.createReplenishment(actorId, req))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Số lượng yêu cầu phải là số nguyên dương lớn hơn 0");
    }

    @Test
    void createReplenishmentRejectsZeroOrNegativeQuantity() {
        UUID actorId = UUID.randomUUID();
        CreateReplenishmentRequest reqZero = new CreateReplenishmentRequest(
                null, "Cỏ linh lăng", "FEED", 0.0, "kg", "Cần bổ sung");
        assertThatThrownBy(() -> service.createReplenishment(actorId, reqZero))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Số lượng yêu cầu phải là số nguyên dương lớn hơn 0");

        CreateReplenishmentRequest reqNeg = new CreateReplenishmentRequest(
                null, "Cỏ linh lăng", "FEED", -1.0, "kg", "Cần bổ sung");
        assertThatThrownBy(() -> service.createReplenishment(actorId, reqNeg))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Số lượng yêu cầu phải là số nguyên dương lớn hơn 0");
    }

    @Test
    void createReplenishmentLocksMasterFieldsWhenItemIdProvided() {
        UUID actorId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();

        // User attempts to tamper with name/category/unit in the request
        CreateReplenishmentRequest req = new CreateReplenishmentRequest(
                itemId, "Tên giả mạo", "MEDICATION", 20.0, "tấn", "Cần bổ sung");

        String masterItemJson = """
                {
                  "itemName": "Yến mạch nguyên hạt",
                  "category": "FEED",
                  "unit": "kg",
                  "quantityOnHand": 120.0,
                  "minThreshold": 200.0
                }
                """;

        when(jdbc.query(contains("where id=? and module_code='INVENTORY' and record_type='INVENTORY_ITEM'"),
                any(RowMapper.class), eq(itemId)))
                .thenAnswer(inv -> {
                    RowMapper<InventoryItemResponse> mapper = inv.getArgument(1);
                    java.sql.ResultSet rs = mock(java.sql.ResultSet.class);
                    when(rs.getObject("id", UUID.class)).thenReturn(itemId);
                    when(rs.getString("status")).thenReturn("ACTIVE");
                    when(rs.getObject("created_at", java.time.OffsetDateTime.class)).thenReturn(java.time.OffsetDateTime.now());
                    when(rs.getObject("updated_at", java.time.OffsetDateTime.class)).thenReturn(java.time.OffsetDateTime.now());
                    when(rs.getString("data_json")).thenReturn(masterItemJson);
                    return List.of(mapper.mapRow(rs, 1));
                });

        when(jdbc.query(eq("select username from users where id=?"), any(RowMapper.class), eq(actorId)))
                .thenReturn(List.of("staff_1"));

        when(jdbc.query(contains("where id=? and module_code='INVENTORY' and record_type='REPLENISHMENT_REQUEST'"),
                any(RowMapper.class), any()))
                .thenAnswer(inv -> {
                    RowMapper<ReplenishmentResponse> mapper = inv.getArgument(1);
                    java.sql.ResultSet rs = mock(java.sql.ResultSet.class);
                    when(rs.getObject("id", UUID.class)).thenReturn(UUID.randomUUID());
                    when(rs.getString("status")).thenReturn("PENDING_APPROVAL");
                    when(rs.getObject("created_at", java.time.OffsetDateTime.class)).thenReturn(java.time.OffsetDateTime.now());
                    when(rs.getString("data_json")).thenReturn("""
                            {
                              "itemId": "%s",
                              "itemName": "Yến mạch nguyên hạt",
                              "category": "FEED",
                              "requestedQuantity": 20,
                              "unit": "kg",
                              "reason": "Cần bổ sung"
                            }
                            """.formatted(itemId));
                    return List.of(mapper.mapRow(rs, 1));
                });

        ReplenishmentResponse res = service.createReplenishment(actorId, req);

        // Verify master fields from item are preserved, overriding the request's tampered values
        verify(jdbc).update(contains("insert into module_records"), any(), eq(actorId), any(),
                argThat(jsonStr -> jsonStr.toString().contains("Yến mạch nguyên hạt")
                        && jsonStr.toString().contains("FEED")
                        && jsonStr.toString().contains("\"unit\":\"kg\"")));
    }

    @Test
    void updateItemUpdatesMasterDetails() {
        UUID itemId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();

        String itemJson = """
                {
                  "itemName": "Gạc y tế tiệt trùng",
                  "category": "MEDICATION",
                  "unit": "hộp",
                  "quantityOnHand": 10.0,
                  "minThreshold": 15.0,
                  "location": "Tủ y tế 1",
                  "description": "Gạc thấm hút",
                  "referencePrice": 120000.0
                }
                """;

        when(jdbc.query(contains("where id=? and module_code='INVENTORY' and record_type='INVENTORY_ITEM'"),
                any(RowMapper.class), eq(itemId)))
                .thenAnswer(inv -> {
                    RowMapper<InventoryItemResponse> mapper = inv.getArgument(1);
                    java.sql.ResultSet rs = mock(java.sql.ResultSet.class);
                    when(rs.getObject("id", UUID.class)).thenReturn(itemId);
                    when(rs.getString("status")).thenReturn("ACTIVE");
                    when(rs.getObject("created_at", java.time.OffsetDateTime.class)).thenReturn(java.time.OffsetDateTime.now());
                    when(rs.getObject("updated_at", java.time.OffsetDateTime.class)).thenReturn(java.time.OffsetDateTime.now());
                    when(rs.getString("data_json")).thenReturn(itemJson);
                    return List.of(mapper.mapRow(rs, 1));
                });

        when(jdbc.queryForObject(eq("select data_json from module_records where id=?"), eq(String.class), eq(itemId)))
                .thenReturn(itemJson);

        UpdateInventoryItemRequest req = new UpdateInventoryItemRequest(
                "Gạc y tế tiệt trùng cao cấp", "MEDICATION", "hộp", 20.0,
                "Tủ y tế trung tâm", "Gạc tiệt trùng chất lượng cao", 135000.0, "ACTIVE");

        service.updateItem(itemId, actorId, req);

        verify(jdbc).update(contains("update module_records set status=?, data_json=?, updated_at=SYSDATETIMEOFFSET() where id=?"),
                eq("ACTIVE"),
                argThat(jsonStr -> jsonStr.toString().contains("Gạc y tế tiệt trùng cao cấp")
                        && jsonStr.toString().contains("135000")),
                eq(itemId));
    }

    @Test
    void deleteItemDeactivatesItem() {
        UUID itemId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();

        String itemJson = """
                {
                  "itemName": "Thuốc xịt sát trùng",
                  "category": "MEDICATION",
                  "unit": "chai"
                }
                """;

        when(jdbc.query(contains("where id=? and module_code='INVENTORY' and record_type='INVENTORY_ITEM'"),
                any(RowMapper.class), eq(itemId)))
                .thenAnswer(inv -> {
                    RowMapper<InventoryItemResponse> mapper = inv.getArgument(1);
                    java.sql.ResultSet rs = mock(java.sql.ResultSet.class);
                    when(rs.getObject("id", UUID.class)).thenReturn(itemId);
                    when(rs.getString("status")).thenReturn("ACTIVE");
                    when(rs.getObject("created_at", java.time.OffsetDateTime.class)).thenReturn(java.time.OffsetDateTime.now());
                    when(rs.getObject("updated_at", java.time.OffsetDateTime.class)).thenReturn(java.time.OffsetDateTime.now());
                    when(rs.getString("data_json")).thenReturn(itemJson);
                    return List.of(mapper.mapRow(rs, 1));
                });

        service.deleteItem(itemId, actorId);

        verify(jdbc).update(contains("update module_records set status='INACTIVE', updated_at=SYSDATETIMEOFFSET() where id=?"), eq(itemId));
    }

    @Test
    void usageSubtractsStockAndCreatesLinkedExpense() {
        UUID itemId = UUID.randomUUID();
        UUID managerId = UUID.randomUUID();
        UUID stableId = UUID.randomUUID();
        UUID transactionId = UUID.randomUUID();
        when(jdbc.queryForList(contains("with (updlock,holdlock)"), eq(itemId)))
                .thenReturn(List.of(Map.of("status", "ACTIVE", "data_json", """
                        {"itemName":"Cỏ khô","category":"FEED","unit":"kg","quantityOnHand":5.0}
                        """)));
        FinanceService.TransactionView transaction = mock(FinanceService.TransactionView.class);
        when(transaction.id()).thenReturn(transactionId);
        when(finance.create(eq(managerId), isNull(), eq(stableId), any(), eq("EXPENSE"), eq("FEED_COST"),
                eq(new BigDecimal("300.00")), anyString(), eq("INVENTORY_USAGE"), any()))
                .thenReturn(transaction);

        InventoryUsageResponse result = service.recordUsage(itemId, managerId,
                new RecordInventoryUsageRequest(new BigDecimal("3"), new BigDecimal("100"), null, stableId, "Ca sáng"));

        assertThat(result.remainingQuantity()).isEqualByComparingTo("2.0");
        assertThat(result.financeTransactionId()).isEqualTo(transactionId);
        verify(jdbc).update(contains("update module_records set data_json=?"),
                contains("\"quantityOnHand\":2.0"), eq(itemId));
        verify(jdbc).update(contains("'STOCK_USAGE'"), any(), isNull(), eq(managerId), any(), anyString());
    }

    @Test
    void usageCannotExceedAvailableStockOrCreateExpense() {
        UUID itemId = UUID.randomUUID();
        UUID stableId = UUID.randomUUID();
        when(jdbc.queryForList(contains("with (updlock,holdlock)"), eq(itemId)))
                .thenReturn(List.of(Map.of("status", "ACTIVE", "data_json", """
                        {"itemName":"Cỏ khô","category":"FEED","unit":"kg","quantityOnHand":2.0}
                        """)));

        assertThatThrownBy(() -> service.recordUsage(itemId, UUID.randomUUID(),
                new RecordInventoryUsageRequest(new BigDecimal("3"), new BigDecimal("100"), null, stableId, "")))
                .isInstanceOf(BusinessException.class).hasMessageContaining("vượt quá tồn kho");
        verifyNoInteractions(finance);
    }

    @Test
    void alreadyFulfilledReplenishmentCannotIncreaseStockAgain() {
        UUID requestId = UUID.randomUUID();
        when(jdbc.queryForList(contains("with (updlock,holdlock)"), eq(requestId)))
                .thenReturn(List.of(Map.of("status", "FULFILLED", "data_json", "{}")));

        assertThatThrownBy(() -> service.receiveReplenishment(requestId, UUID.randomUUID(),
                new ReceiveReplenishmentRequest(null, null, null, null)))
                .isInstanceOf(BusinessException.class).hasMessageContaining("chưa nhập kho");
        verify(jdbc, never()).update(anyString(), org.mockito.ArgumentMatchers.<Object[]>any());
        verifyNoInteractions(finance);
    }

    @Test
    void receivingApprovedRequestAddsStockAndMarksItFulfilled() {
        UUID requestId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();
        UUID managerId = UUID.randomUUID();
        String requestJson = """
                {"itemId":"%s","itemName":"Cỏ khô","category":"FEED",
                 "requestedQuantity":4,"unit":"kg","reason":"Bổ sung"}
                """.formatted(itemId);
        when(jdbc.queryForList(contains("with (updlock,holdlock)"), eq(requestId)))
                .thenReturn(List.of(Map.of("status", "APPROVED", "data_json", requestJson)));
        when(jdbc.queryForList(contains("with (updlock,holdlock)"), eq(itemId)))
                .thenReturn(List.of(Map.of("status", "ACTIVE", "data_json", """
                        {"itemName":"Cỏ khô","category":"FEED","unit":"kg","quantityOnHand":6}
                        """)));
        when(jdbc.query(contains("record_type='REPLENISHMENT_REQUEST'"), any(RowMapper.class), eq(requestId)))
                .thenAnswer(inv -> {
                    RowMapper<ReplenishmentResponse> mapper = inv.getArgument(1);
                    java.sql.ResultSet rs = mock(java.sql.ResultSet.class);
                    when(rs.getObject("id", UUID.class)).thenReturn(requestId);
                    when(rs.getString("status")).thenReturn("APPROVED");
                    when(rs.getObject("created_at", java.time.OffsetDateTime.class))
                            .thenReturn(java.time.OffsetDateTime.now());
                    when(rs.getString("data_json")).thenReturn(requestJson);
                    return List.of(mapper.mapRow(rs, 1));
                });
        when(jdbc.query(contains("record_type='INVENTORY_ITEM'"), any(RowMapper.class), eq(itemId)))
                .thenAnswer(inv -> {
                    RowMapper<InventoryItemResponse> mapper = inv.getArgument(1);
                    java.sql.ResultSet rs = mock(java.sql.ResultSet.class);
                    when(rs.getObject("id", UUID.class)).thenReturn(itemId);
                    when(rs.getString("status")).thenReturn("ACTIVE");
                    when(rs.getString("data_json")).thenReturn("""
                            {"itemName":"Cỏ khô","category":"FEED","unit":"kg","quantityOnHand":6}
                            """);
                    return List.of(mapper.mapRow(rs, 1));
                });

        service.receiveReplenishment(requestId, managerId,
                new ReceiveReplenishmentRequest(null, null, null, null));

        verify(jdbc).update(contains("update module_records set data_json=?"),
                contains("\"quantityOnHand\":10"), eq(itemId));
        verify(jdbc).update(contains("status='FULFILLED'"),
                contains("\"receivedByUserId\":\"" + managerId + "\""), eq(requestId));
    }
}
