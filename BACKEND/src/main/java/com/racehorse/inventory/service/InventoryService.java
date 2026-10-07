package com.racehorse.inventory.service;

import com.racehorse.common.exception.BusinessException;
import com.racehorse.common.exception.ResourceNotFoundException;
import com.racehorse.finance.service.FinanceService;
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
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

@Service
public class InventoryService {

    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final Clock clock;
    private final FinanceService finance;

    public InventoryService(JdbcTemplate jdbc, ObjectMapper json, Clock clock, FinanceService finance) {
        this.jdbc = jdbc;
        this.json = json;
        this.clock = clock;
        this.finance = finance;
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
        return listItems(category, lowStockOnly, null);
    }

    @Transactional(readOnly = true)
    public List<InventoryItemResponse> listItems(String category, Boolean lowStockOnly, String status) {
        StringBuilder sql = new StringBuilder(
                "select id, status, data_json, created_at, updated_at from module_records " +
                        "where module_code='INVENTORY' and record_type='INVENTORY_ITEM'");
        List<Object> args = new ArrayList<>();

        if (status == null || status.isBlank()) {
            sql.append(" and status='ACTIVE'");
        } else if (!"ALL".equalsIgnoreCase(status.trim())) {
            sql.append(" and status=?");
            args.add(status.trim().toUpperCase(Locale.ROOT));
        }

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
    public InventoryItemResponse updateItem(UUID itemId, UUID actorId, UpdateInventoryItemRequest request) {
        getItem(itemId);
        String currentJson = jdbc.queryForObject(
                "select data_json from module_records where id=?", String.class, itemId);
        Map<String, Object> data = readMap(currentJson);

        data.put("itemName", request.itemName().trim());
        data.put("category", request.category().trim().toUpperCase(Locale.ROOT));
        data.put("unit", request.unit().trim());
        data.put("minThreshold", request.minThreshold());
        data.put("location", request.location() == null ? "" : request.location().trim());
        data.put("description", request.description() == null ? "" : request.description().trim());
        if (request.referencePrice() != null) {
            data.put("referencePrice", request.referencePrice());
        }

        String targetStatus = (request.status() != null && !request.status().isBlank())
                ? request.status().trim().toUpperCase(Locale.ROOT)
                : null;

        if (targetStatus != null) {
            jdbc.update(
                    "update module_records set status=?, data_json=?, updated_at=SYSDATETIMEOFFSET() where id=?",
                    targetStatus, toJson(data), itemId);
        } else {
            jdbc.update(
                    "update module_records set data_json=?, updated_at=SYSDATETIMEOFFSET() where id=?",
                    toJson(data), itemId);
        }

        return getItem(itemId);
    }

    @Transactional
    public void deleteItem(UUID itemId, UUID actorId) {
        getItem(itemId);
        jdbc.update(
                "update module_records set status='INACTIVE', updated_at=SYSDATETIMEOFFSET() where id=?",
                itemId);
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

    @Transactional
    public InventoryUsageResponse recordUsage(UUID itemId, UUID actorId, RecordInventoryUsageRequest request) {
        if (request.horseId() == null && request.stableId() == null) {
            throw new BusinessException("Cần chọn ngựa hoặc chuồng sử dụng vật tư");
        }
        if (request.horseId() != null && request.stableId() != null) {
            throw new BusinessException("Chỉ chọn một đối tượng sử dụng: ngựa hoặc chuồng");
        }
        if (request.quantity() == null || request.quantity().signum() <= 0 || request.unitPriceVnd() == null
                || request.unitPriceVnd().signum() <= 0) {
            throw new BusinessException("Số lượng và đơn giá phải lớn hơn 0");
        }

        // Lock the item row until the stock movement and its financial entry commit.
        List<Map<String, Object>> rows = jdbc.queryForList(
                "select status,data_json from module_records with (updlock,holdlock) "
                        + "where id=? and module_code='INVENTORY' and record_type='INVENTORY_ITEM'", itemId);
        if (rows.isEmpty()) throw new ResourceNotFoundException("Không tìm thấy mặt hàng trong kho: " + itemId);
        Map<String, Object> row = rows.getFirst();
        if (!"ACTIVE".equals(row.get("status"))) throw new BusinessException("Mặt hàng đã ngừng sử dụng");
        Map<String, Object> item = readMap((String) row.get("data_json"));
        BigDecimal available = new BigDecimal(item.getOrDefault("quantityOnHand", 0).toString());
        if (available.compareTo(request.quantity()) < 0) throw new BusinessException("Số lượng xuất vượt quá tồn kho hiện tại");

        String category = Objects.toString(item.get("category"), "");
        String financeCategory = switch (category) {
            case "FEED", "SUPPLEMENT" -> "FEED_COST";
            case "MEDICATION" -> "MEDICAL_COST";
            case "EQUIPMENT" -> "OPERATING_COST";
            default -> throw new BusinessException("Danh mục vật tư không hỗ trợ ghi chi phí: " + category);
        };
        if ("MEDICAL_COST".equals(financeCategory) && request.horseId() == null) {
            throw new BusinessException("Vật tư y tế cần chọn ngựa được sử dụng");
        }
        String itemName = Objects.toString(item.get("itemName"), "");
        String unit = Objects.toString(item.get("unit"), "");
        BigDecimal remaining = available.subtract(request.quantity());
        BigDecimal totalCost = request.quantity().multiply(request.unitPriceVnd()).setScale(2, RoundingMode.HALF_UP);
        if (totalCost.signum() <= 0) throw new BusinessException("Chi phí xuất dùng phải tối thiểu 0,01 VND");
        if (totalCost.precision() > 19) throw new BusinessException("Giá trị xuất dùng vượt giới hạn tiền tệ");
        UUID usageId = UUID.randomUUID();
        LocalDate today = LocalDate.now(clock);
        OffsetDateTime now = OffsetDateTime.now(clock);
        String notes = request.notes() == null ? "" : request.notes().trim();

        item.put("quantityOnHand", remaining);
        item.put("lastStockUpdateReason", "Xuất dùng " + request.quantity().toPlainString() + " " + unit);
        item.put("lastUpdatedByUserId", actorId.toString());
        jdbc.update("update module_records set data_json=?,updated_at=SYSDATETIMEOFFSET() where id=?", toJson(item), itemId);

        Map<String, Object> movement = new LinkedHashMap<>();
        movement.put("itemId", itemId.toString());
        movement.put("itemName", itemName);
        movement.put("category", category);
        movement.put("quantity", request.quantity());
        movement.put("unit", unit);
        movement.put("remainingQuantity", remaining);
        movement.put("unitPriceVnd", request.unitPriceVnd());
        movement.put("totalCostVnd", totalCost);
        movement.put("stableId", request.stableId() == null ? null : request.stableId().toString());
        movement.put("notes", notes);
        jdbc.update("insert into module_records(id,module_code,record_type,horse_id,user_id,event_date,status,data_json) "
                        + "values(?,'INVENTORY','STOCK_USAGE',?,?,?,'RECORDED',?)",
                usageId, request.horseId(), actorId, today, toJson(movement));

        FinanceService.TransactionView transaction = finance.create(
                actorId, request.horseId(), request.stableId(), today, "EXPENSE", financeCategory,
                totalCost, "Xuất dùng " + request.quantity().toPlainString() + " " + unit + " " + itemName
                        + (notes.isBlank() ? "" : ": " + notes), "INVENTORY_USAGE", usageId);
        return new InventoryUsageResponse(usageId, itemId, itemName, request.quantity(), unit, remaining,
                request.unitPriceVnd(), totalCost, request.horseId(), request.stableId(), transaction.id(), notes, now);
    }

    @Transactional(readOnly = true)
    public List<InventoryUsageResponse> listUsage(int limit) {
        if (limit < 1 || limit > 100) throw new BusinessException("Giới hạn lịch sử xuất dùng không hợp lệ");
        return jdbc.query("select top (?) m.id,m.horse_id,m.data_json,m.created_at,ft.id finance_id "
                        + "from module_records m left join financial_transactions ft "
                        + "on ft.source_entity_type='INVENTORY_USAGE' and ft.source_entity_id=m.id "
                        + "where m.module_code='INVENTORY' and m.record_type='STOCK_USAGE' "
                        + "order by m.created_at desc,m.id desc", (rs, row) -> {
                    JsonNode data = read(rs.getString("data_json"));
                    String stable = data.path("stableId").asText(null);
                    return new InventoryUsageResponse(rs.getObject("id", UUID.class),
                            UUID.fromString(data.path("itemId").asText()), data.path("itemName").asText(),
                            data.path("quantity").decimalValue(), data.path("unit").asText(),
                            data.path("remainingQuantity").decimalValue(), data.path("unitPriceVnd").decimalValue(),
                            data.path("totalCostVnd").decimalValue(), rs.getObject("horse_id", UUID.class),
                            stable == null ? null : UUID.fromString(stable), rs.getObject("finance_id", UUID.class),
                            data.path("notes").asText(""), rs.getObject("created_at", OffsetDateTime.class));
                }, limit);
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
        List<Map<String, Object>> locked = jdbc.queryForList(
                "select status from module_records with (updlock,holdlock) "
                        + "where id=? and module_code='INVENTORY' and record_type='REPLENISHMENT_REQUEST'", id);
        if (locked.isEmpty()) throw new ResourceNotFoundException("Không tìm thấy đề xuất bổ sung tồn kho: " + id);
        if (!"PENDING_APPROVAL".equals(locked.getFirst().get("status"))) {
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

        jdbc.update(
                "update module_records set status=?, data_json=?, updated_at=SYSDATETIMEOFFSET() where id=?",
                targetStatus, toJson(data), id);

        if ("APPROVED".equals(targetStatus) && Boolean.TRUE.equals(request.receiveImmediately())) {
            return receiveReplenishment(id, actorId, new ReceiveReplenishmentRequest(null, null, null, null));
        }
        return getReplenishment(id);
    }

    @Transactional
    public ReplenishmentResponse receiveReplenishment(UUID id, UUID actorId, ReceiveReplenishmentRequest request) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "select status,data_json from module_records with (updlock,holdlock) "
                        + "where id=? and module_code='INVENTORY' and record_type='REPLENISHMENT_REQUEST'", id);
        if (rows.isEmpty()) throw new ResourceNotFoundException("Không tìm thấy đề xuất bổ sung tồn kho: " + id);
        Map<String, Object> row = rows.getFirst();
        if (!"APPROVED".equals(row.get("status"))) {
            throw new BusinessException("Chỉ có thể nhập kho đề xuất đã duyệt và chưa nhập kho");
        }
        Map<String, Object> data = readMap((String) row.get("data_json"));
        ReplenishmentResponse proposal = getReplenishment(id);
        UUID itemId = proposal.itemId() != null ? proposal.itemId() : request.catalogItemId();
        if (proposal.itemId() != null && request.catalogItemId() != null
                && !proposal.itemId().equals(request.catalogItemId())) {
            throw new BusinessException("Không thể chuyển đề xuất sang mặt hàng khác");
        }
        if (itemId == null) {
            InventoryItemResponse created = createItem(actorId, new CreateInventoryItemRequest(
                    proposal.itemName(), proposal.category(), proposal.unit(), 0.0,
                    request.minThreshold() == null ? 0.0 : request.minThreshold(),
                    request.location(), "Tạo từ đề xuất vật tư " + id,
                    request.referencePriceVnd() == null ? null : request.referencePriceVnd().doubleValue()));
            itemId = created.id();
        } else {
            InventoryItemResponse catalog = getItem(itemId);
            if (!"ACTIVE".equals(catalog.status())) throw new BusinessException("Mặt hàng trong danh mục đã ngừng sử dụng");
            if (!proposal.category().equals(catalog.category()) || !proposal.unit().equals(catalog.unit())) {
                throw new BusinessException("Danh mục hoặc đơn vị của vật tư nhận không khớp đề xuất");
            }
        }
        increaseItemStock(itemId, BigDecimal.valueOf(proposal.requestedQuantity()), actorId,
                request.referencePriceVnd());
        data.put("itemId", itemId.toString());
        data.put("receivedByUserId", actorId.toString());
        data.put("receivedAt", OffsetDateTime.now(clock).toString());
        jdbc.update("update module_records set status='FULFILLED',data_json=?,updated_at=SYSDATETIMEOFFSET() where id=?",
                toJson(data), id);
        return getReplenishment(id);
    }

    private void increaseItemStock(UUID itemId, BigDecimal addedQuantity, UUID actorId, BigDecimal price) {
        List<Map<String, Object>> rows = jdbc.queryForList("select status,data_json from module_records with (updlock,holdlock) "
                + "where id=? and module_code='INVENTORY' and record_type='INVENTORY_ITEM'", itemId);
        if (rows.isEmpty()) throw new ResourceNotFoundException("Không tìm thấy mặt hàng trong kho: " + itemId);
        Map<String, Object> row = rows.getFirst();
        if (!"ACTIVE".equals(row.get("status"))) throw new BusinessException("Mặt hàng trong danh mục đã ngừng sử dụng");
        Map<String, Object> data = readMap((String) row.get("data_json"));
        BigDecimal currentQty = new BigDecimal(data.getOrDefault("quantityOnHand", 0).toString());
        data.put("quantityOnHand", currentQty.add(addedQuantity));
        if (price != null) data.put("referencePrice", price);
        data.put("lastStockUpdateReason", "Nhập kho theo đề xuất được duyệt (" + addedQuantity.toPlainString() + ")");
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
