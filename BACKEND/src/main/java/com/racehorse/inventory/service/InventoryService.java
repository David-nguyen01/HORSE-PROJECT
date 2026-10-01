package com.racehorse.inventory.service;

import com.racehorse.common.exception.BusinessException;
import com.racehorse.common.exception.ResourceNotFoundException;
import com.racehorse.inventory.dto.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.*;

@Service
public class InventoryService {

    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final Clock clock;

    public InventoryService(JdbcTemplate jdbc, ObjectMapper json, Clock clock) {
        this.jdbc = jdbc;
        this.json = json;
        this.clock = clock;
    }

    // ==================== INVENTORY ITEMS ====================

    @Transactional
    public InventoryItemResponse createItem(UUID actorId, CreateInventoryItemRequest request) {
        String category = request.category().trim().toUpperCase(Locale.ROOT);
        UUID itemId = UUID.randomUUID();
        LocalDate today = LocalDate.now(clock);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("itemName", request.itemName().trim());
        data.put("category", category);
        data.put("unit", request.unit().trim());
        data.put("quantityOnHand", request.initialQuantity());
        data.put("minThreshold", request.minThreshold());
        data.put("location", request.location() == null ? "" : request.location().trim());
        data.put("description", request.description() == null ? "" : request.description().trim());
        if (request.referencePrice() != null) {
            data.put("referencePrice", request.referencePrice());
        }

        jdbc.update(
                "insert into module_records(id, module_code, record_type, horse_id, user_id, event_date, status, data_json, created_at, updated_at) " +
                        "values (?, 'INVENTORY', 'INVENTORY_ITEM', NULL, ?, ?, 'ACTIVE', ?, SYSDATETIMEOFFSET(), SYSDATETIMEOFFSET())",
                itemId, actorId, today, toJson(data));

        return getItem(itemId);
    }

    @Transactional(readOnly = true)
    public InventoryItemResponse getItem(UUID itemId) {
        List<InventoryItemResponse> list = jdbc.query(
                "select id, status, data_json, created_at, updated_at from module_records " +
                        "where id=? and module_code='INVENTORY' and record_type='INVENTORY_ITEM'",
                (rs, rowNum) -> mapItemResponse(rs), itemId);
        if (list.isEmpty()) {
            throw new ResourceNotFoundException("Không tìm thấy mặt hàng trong kho: " + itemId);
        }
        return list.getFirst();
    }

    @Transactional(readOnly = true)
    public List<InventoryItemResponse> listItems(String category, Boolean lowStockOnly) {
        StringBuilder sql = new StringBuilder(
                "select id, status, data_json, created_at, updated_at from module_records " +
                        "where module_code='INVENTORY' and record_type='INVENTORY_ITEM' and status='ACTIVE'");
        List<Object> args = new ArrayList<>();

        if (category != null && !category.isBlank()) {
            sql.append(" and JSON_VALUE(data_json, '$.category') = ?");
            args.add(category.trim().toUpperCase(Locale.ROOT));
        }

        sql.append(" order by JSON_VALUE(data_json, '$.itemName') asc");

        List<InventoryItemResponse> results = jdbc.query(sql.toString(), (rs, rowNum) -> mapItemResponse(rs), args.toArray());
        if (Boolean.TRUE.equals(lowStockOnly)) {
            return results.stream().filter(InventoryItemResponse::isLowStock).toList();
        }
        return results;
    }

    @Transactional
    public InventoryItemResponse updateStock(UUID itemId, UUID actorId, UpdateStockRequest request) {
        InventoryItemResponse existing = getItem(itemId);
        String currentJson = jdbc.queryForObject(
                "select data_json from module_records where id=?", String.class, itemId);
        Map<String, Object> data = readMap(currentJson);

        data.put("quantityOnHand", request.newQuantity());
        data.put("lastStockUpdateReason", request.reason() == null ? "Cập nhật tồn kho" : request.reason().trim());
        data.put("lastUpdatedByUserId", actorId.toString());

        jdbc.update(
                "update module_records set data_json=?, updated_at=SYSDATETIMEOFFSET() where id=?",
                toJson(data), itemId);

        return getItem(itemId);
    }

    // ==================== REPLENISHMENT REQUESTS ====================

    @Transactional
    public ReplenishmentResponse createReplenishment(UUID actorId, CreateReplenishmentRequest request) {
        if (request.requestedQuantity() == null || request.requestedQuantity() <= 0 || request.requestedQuantity() != Math.floor(request.requestedQuantity())) {
            throw new BusinessException("Số lượng yêu cầu phải là số nguyên dương lớn hơn 0");
        }

        String itemName = request.itemName().trim();
        String category = request.category().trim().toUpperCase(Locale.ROOT);
        String unit = request.unit().trim();

        if (request.itemId() != null) {
            InventoryItemResponse item = getItem(request.itemId());
            itemName = item.itemName();
            category = item.category();
            unit = item.unit();
        }

        List<String> names = jdbc.query(
                "select username from users where id=?",
                (rs, rn) -> rs.getString("username"), actorId);
        String proposerName = names.isEmpty() ? "Nhân viên chuồng" : names.getFirst();

        UUID reqId = UUID.randomUUID();
        LocalDate today = LocalDate.now(clock);
        OffsetDateTime now = OffsetDateTime.now(clock);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("itemId", request.itemId() == null ? null : request.itemId().toString());
        data.put("itemName", itemName);
        data.put("category", category);
        data.put("requestedQuantity", (long) Math.floor(request.requestedQuantity()));
        data.put("unit", unit);
        data.put("reason", request.reason().trim());
        data.put("proposedByUserId", actorId.toString());
        data.put("proposedByName", proposerName);
        data.put("proposedAt", now.toString());
        data.put("reviewedByUserId", null);
        data.put("reviewedByName", null);
        data.put("reviewedAt", null);
        data.put("reviewNotes", null);

        jdbc.update(
                "insert into module_records(id, module_code, record_type, horse_id, user_id, event_date, status, data_json, created_at, updated_at) " +
                        "values (?, 'INVENTORY', 'REPLENISHMENT_REQUEST', NULL, ?, ?, 'PENDING_APPROVAL', ?, SYSDATETIMEOFFSET(), SYSDATETIMEOFFSET())",
                reqId, actorId, today, toJson(data));

        return getReplenishment(reqId);
    }

    @Transactional(readOnly = true)
    public ReplenishmentResponse getReplenishment(UUID id) {
        List<ReplenishmentResponse> list = jdbc.query(
                "select id, status, data_json, created_at from module_records " +
                        "where id=? and module_code='INVENTORY' and record_type='REPLENISHMENT_REQUEST'",
                (rs, rowNum) -> mapReplenishmentResponse(rs), id);
        if (list.isEmpty()) {
            throw new ResourceNotFoundException("Không tìm thấy đề xuất bổ sung tồn kho: " + id);
        }
        return list.getFirst();
    }

    @Transactional(readOnly = true)
    public List<ReplenishmentResponse> listReplenishments(String status) {
        StringBuilder sql = new StringBuilder(
                "select id, status, data_json, created_at from module_records " +
                        "where module_code='INVENTORY' and record_type='REPLENISHMENT_REQUEST'");
        List<Object> args = new ArrayList<>();

        if (status != null && !status.isBlank()) {
            sql.append(" and status=?");
            args.add(status.trim().toUpperCase(Locale.ROOT));
        }

        sql.append(" order by created_at desc");

        return jdbc.query(sql.toString(), (rs, rowNum) -> mapReplenishmentResponse(rs), args.toArray());
    }

    @Transactional
    public ReplenishmentResponse reviewReplenishment(UUID id, UUID actorId, ReviewReplenishmentRequest request) {
        ReplenishmentResponse existing = getReplenishment(id);
        if (!"PENDING_APPROVAL".equals(existing.status())) {
            throw new BusinessException("Đề xuất bổ sung này đã được xử lý trước đó");
        }

        String targetStatus = request.status().trim().toUpperCase(Locale.ROOT);
        if (!List.of("APPROVED", "REJECTED").contains(targetStatus)) {
            throw new BusinessException("Trạng thái phê duyệt không hợp lệ (chỉ chấp nhận APPROVED hoặc REJECTED)");
        }

        List<String> names = jdbc.query(
                "select username from users where id=?",
                (rs, rn) -> rs.getString("username"), actorId);
        String reviewerName = names.isEmpty() ? "Quản lý CLB" : names.getFirst();

        OffsetDateTime now = OffsetDateTime.now(clock);

        String currentJson = jdbc.queryForObject(
                "select data_json from module_records where id=?", String.class, id);
        Map<String, Object> data = readMap(currentJson);

        data.put("reviewedByUserId", actorId.toString());
        data.put("reviewedByName", reviewerName);
        data.put("reviewedAt", now.toString());
        data.put("reviewNotes", request.reviewNotes() == null ? null : request.reviewNotes().trim());

        // If approved and attached to an existing item, automatically increase quantityOnHand
        if ("APPROVED".equals(targetStatus) && existing.itemId() != null) {
            increaseItemStock(existing.itemId(), existing.requestedQuantity(), actorId);
        }

        jdbc.update(
                "update module_records set status=?, data_json=?, updated_at=SYSDATETIMEOFFSET() where id=?",
                targetStatus, toJson(data), id);

        return getReplenishment(id);
    }

    private void increaseItemStock(UUID itemId, double addedQuantity, UUID actorId) {
        String itemJson = jdbc.queryForObject(
                "select data_json from module_records where id=?", String.class, itemId);
        Map<String, Object> data = readMap(itemJson);

        double currentQty = ((Number) data.getOrDefault("quantityOnHand", 0.0)).doubleValue();
        double newQty = currentQty + addedQuantity;
        data.put("quantityOnHand", newQty);
        data.put("lastStockUpdateReason", "Bổ sung theo đề xuất được duyệt (" + addedQuantity + ")");
        data.put("lastUpdatedByUserId", actorId.toString());

        jdbc.update(
                "update module_records set data_json=?, updated_at=SYSDATETIMEOFFSET() where id=?",
                toJson(data), itemId);
    }

    // ==================== MAPPERS & HELPERS ====================

    private InventoryItemResponse mapItemResponse(java.sql.ResultSet rs) throws java.sql.SQLException {
        UUID id = rs.getObject("id", UUID.class);
        String status = rs.getString("status");
        OffsetDateTime createdAt = rs.getObject("created_at", OffsetDateTime.class);
        OffsetDateTime updatedAt = rs.getObject("updated_at", OffsetDateTime.class);

        JsonNode node = read(rs.getString("data_json"));
        String itemName = node.path("itemName").asText("");
        String category = node.path("category").asText("OTHER");
        String unit = node.path("unit").asText("");
        double quantityOnHand = node.path("quantityOnHand").asDouble(0.0);
        double minThreshold = node.path("minThreshold").asDouble(0.0);
        String location = node.path("location").asText("");
        String description = node.path("description").asText("");
        Double referencePrice = node.hasNonNull("referencePrice") ? node.path("referencePrice").asDouble() : null;
        boolean isLowStock = quantityOnHand <= minThreshold;

        return new InventoryItemResponse(
                id, itemName, category, unit, quantityOnHand, minThreshold,
                location, description, referencePrice, isLowStock, status, createdAt, updatedAt);
    }

    private ReplenishmentResponse mapReplenishmentResponse(java.sql.ResultSet rs) throws java.sql.SQLException {
        UUID id = rs.getObject("id", UUID.class);
        String status = rs.getString("status");
        OffsetDateTime createdAt = rs.getObject("created_at", OffsetDateTime.class);

        JsonNode node = read(rs.getString("data_json"));
        String itemIdStr = node.path("itemId").isNull() ? null : node.path("itemId").asText(null);
        UUID itemId = itemIdStr == null || itemIdStr.isBlank() ? null : UUID.fromString(itemIdStr);

        String itemName = node.path("itemName").asText("");
        String category = node.path("category").asText("OTHER");
        double requestedQuantity = node.path("requestedQuantity").asDouble(0.0);
        String unit = node.path("unit").asText("");
        String reason = node.path("reason").asText("");

        String proposedByStr = node.path("proposedByUserId").isNull() ? null : node.path("proposedByUserId").asText(null);
        UUID proposedByUserId = proposedByStr == null || proposedByStr.isBlank() ? null : UUID.fromString(proposedByStr);
        String proposedByName = node.path("proposedByName").isNull() ? null : node.path("proposedByName").asText(null);
        String proposedAtStr = node.path("proposedAt").isNull() ? null : node.path("proposedAt").asText(null);
        OffsetDateTime proposedAt = proposedAtStr == null || proposedAtStr.isBlank() ? createdAt : OffsetDateTime.parse(proposedAtStr);

        String reviewedByStr = node.path("reviewedByUserId").isNull() ? null : node.path("reviewedByUserId").asText(null);
        UUID reviewedByUserId = reviewedByStr == null || reviewedByStr.isBlank() ? null : UUID.fromString(reviewedByStr);
        String reviewedByName = node.path("reviewedByName").isNull() ? null : node.path("reviewedByName").asText(null);
        String reviewedAtStr = node.path("reviewedAt").isNull() ? null : node.path("reviewedAt").asText(null);
        OffsetDateTime reviewedAt = reviewedAtStr == null || reviewedAtStr.isBlank() ? null : OffsetDateTime.parse(reviewedAtStr);
        String reviewNotes = node.path("reviewNotes").isNull() ? null : node.path("reviewNotes").asText(null);

        return new ReplenishmentResponse(
                id, itemId, itemName, category, requestedQuantity, unit, reason, status,
                proposedByUserId, proposedByName, proposedAt,
                reviewedByUserId, reviewedByName, reviewedAt, reviewNotes);
    }

    private JsonNode read(String raw) {
        if (raw == null || raw.isBlank()) return json.createObjectNode();
        try {
            return json.readTree(raw);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to parse inventory data_json: " + e.getMessage(), e);
        }
    }

    private Map<String, Object> readMap(String raw) {
        if (raw == null || raw.isBlank()) return new LinkedHashMap<>();
        try {
            return json.readValue(raw, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            throw new IllegalStateException("Failed to parse JSON map: " + e.getMessage(), e);
        }
    }

    private String toJson(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize to JSON: " + e.getMessage(), e);
        }
    }
}
