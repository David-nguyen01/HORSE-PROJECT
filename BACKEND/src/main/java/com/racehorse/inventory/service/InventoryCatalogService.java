package com.racehorse.inventory.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.racehorse.common.exception.BusinessException;
import com.racehorse.common.exception.ResourceNotFoundException;
import com.racehorse.inventory.dto.CreateInventoryCatalogRequest;
import com.racehorse.inventory.dto.InventoryCatalogResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class InventoryCatalogService {

    private static final String INVENTORY_MODULE = "INVENTORY";

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public InventoryCatalogService(JdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public List<InventoryCatalogResponse> getAll(String type) {
        String recordType = resolveRecordType(type);

        String sql;
        Object[] params;
        if (recordType != null) {
            sql = """
                    select id, record_type, status, data_json, created_at, updated_at
                    from module_records
                    where module_code = ? and record_type = ?
                    order by created_at desc
                    """;
            params = new Object[]{INVENTORY_MODULE, recordType};
        } else {
            sql = """
                    select id, record_type, status, data_json, created_at, updated_at
                    from module_records
                    where module_code = ? and record_type in ('INVENTORY_CATEGORIES', 'INVENTORY_ITEMS')
                    order by record_type, created_at desc
                    """;
            params = new Object[]{INVENTORY_MODULE};
        }

        return jdbc.query(sql, (rs, rowNum) -> mapRow(rs), params);
    }

    @Transactional
    public InventoryCatalogResponse create(CreateInventoryCatalogRequest request) {
        String recordType = request.type() == CreateInventoryCatalogRequest.CatalogType.CATEGORY
                ? "INVENTORY_CATEGORIES" : "INVENTORY_ITEMS";

        UUID id = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();
        String json = toJson(Map.of(
                "name", request.name(),
                "description", request.description() != null ? request.description() : "",
                "unit", request.unit() != null ? request.unit() : ""
        ));

        jdbc.update("""
                insert into module_records (
                    id, module_code, record_type, status, data_json, created_at, updated_at
                ) values (?, ?, ?, 'ACTIVE', ?, ?, ?)
                """, id, INVENTORY_MODULE, recordType, json, now, now);

        return new InventoryCatalogResponse(
                id, request.type().name(), request.name(),
                request.description(), request.unit(), "ACTIVE", now, now);
    }

    @Transactional
    public InventoryCatalogResponse update(UUID id, CreateInventoryCatalogRequest request) {
        Map<String, Object> existing = findById(id);

        String json = toJson(Map.of(
                "name", request.name(),
                "description", request.description() != null ? request.description() : "",
                "unit", request.unit() != null ? request.unit() : ""
        ));

        OffsetDateTime now = OffsetDateTime.now();
        jdbc.update("""
                update module_records
                set data_json = ?, updated_at = ?
                where id = ? and module_code = ?
                """, json, now, id, INVENTORY_MODULE);

        String recordType = (String) existing.get("record_type");
        String typeName = "INVENTORY_CATEGORIES".equals(recordType) ? "CATEGORY" : "ITEM";
        return new InventoryCatalogResponse(
                id, typeName, request.name(),
                request.description(), request.unit(),
                (String) existing.get("status"), (OffsetDateTime) existing.get("created_at"), now);
    }

    @Transactional
    public InventoryCatalogResponse deactivate(UUID id) {
        Map<String, Object> existing = findById(id);

        if ("INACTIVE".equals(existing.get("status"))) {
            throw new BusinessException("Mục này đã bị vô hiệu hóa");
        }

        // In-use check: items referenced by inventory balances/stock
        String recordType = (String) existing.get("record_type");
        if ("INVENTORY_ITEMS".equals(recordType)) {
            Integer inUseCount = jdbc.queryForObject("""
                    select count(*) from module_records
                    where module_code = 'INVENTORY' and record_type = 'INVENTORY_BALANCES'
                      and json_value(data_json, '$.item_id') = ?
                      and status = 'ACTIVE'
                    """, Integer.class, id.toString());
            if (inUseCount != null && inUseCount > 0) {
                throw new BusinessException(
                        "Không thể vô hiệu hóa vật phẩm đang được sử dụng trong " + inUseCount + " bản ghi tồn kho");
            }
        }

        // Categories: check if any active items reference this category
        if ("INVENTORY_CATEGORIES".equals(recordType)) {
            Integer childCount = jdbc.queryForObject("""
                    select count(*) from module_records
                    where module_code = 'INVENTORY' and record_type = 'INVENTORY_ITEMS'
                      and json_value(data_json, '$.category_id') = ?
                      and status = 'ACTIVE'
                    """, Integer.class, id.toString());
            if (childCount != null && childCount > 0) {
                throw new BusinessException(
                        "Không thể vô hiệu hóa danh mục đang chứa " + childCount + " vật phẩm đang hoạt động");
            }
        }

        OffsetDateTime now = OffsetDateTime.now();
        jdbc.update("""
                update module_records
                set status = 'INACTIVE', updated_at = ?
                where id = ? and module_code = ?
                """, now, id, INVENTORY_MODULE);

        return toResponse(id, existing, "INACTIVE", now);
    }

    @Transactional
    public InventoryCatalogResponse reactivate(UUID id) {
        Map<String, Object> existing = findById(id);

        if ("ACTIVE".equals(existing.get("status"))) {
            throw new BusinessException("Mục này đang hoạt động");
        }

        OffsetDateTime now = OffsetDateTime.now();
        jdbc.update("""
                update module_records
                set status = 'ACTIVE', updated_at = ?
                where id = ? and module_code = ?
                """, now, id, INVENTORY_MODULE);

        return toResponse(id, existing, "ACTIVE", now);
    }

    private Map<String, Object> findById(UUID id) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                select record_type, status, data_json, created_at, updated_at
                from module_records
                where id = ? and module_code = ?
                  and record_type in ('INVENTORY_CATEGORIES', 'INVENTORY_ITEMS')
                """, id, INVENTORY_MODULE);

        if (rows.isEmpty()) {
            throw new ResourceNotFoundException("Không tìm thấy mục catalog: " + id);
        }
        return rows.getFirst();
    }

    private InventoryCatalogResponse toResponse(UUID id, Map<String, Object> row, String status, OffsetDateTime updatedAt) {
        String recordType = (String) row.get("record_type");
        String typeName = "INVENTORY_CATEGORIES".equals(recordType) ? "CATEGORY" : "ITEM";
        try {
            Map<String, Object> data = mapper.readValue((String) row.get("data_json"), Map.class);
            return new InventoryCatalogResponse(
                    id, typeName,
                    (String) data.getOrDefault("name", ""),
                    (String) data.getOrDefault("description", ""),
                    (String) data.getOrDefault("unit", ""),
                    status,
                    (OffsetDateTime) row.get("created_at"),
                    updatedAt);
        } catch (Exception e) {
            throw new BusinessException("Lỗi đọc dữ liệu catalog: " + e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private InventoryCatalogResponse mapRow(java.sql.ResultSet rs) {
        try {
            UUID id = rs.getObject("id", UUID.class);
            String recordType = rs.getString("record_type");
            String typeName = "INVENTORY_CATEGORIES".equals(recordType) ? "CATEGORY" : "ITEM";
            String status = rs.getString("status");
            Map<String, Object> data = mapper.readValue(rs.getString("data_json"), Map.class);
            return new InventoryCatalogResponse(
                    id, typeName,
                    (String) data.getOrDefault("name", ""),
                    (String) data.getOrDefault("description", ""),
                    (String) data.getOrDefault("unit", ""),
                    status,
                    rs.getObject("created_at", OffsetDateTime.class),
                    rs.getObject("updated_at", OffsetDateTime.class));
        } catch (Exception e) {
            throw new BusinessException("Lỗi đọc dữ liệu catalog");
        }
    }

    private String resolveRecordType(String type) {
        if (type == null || type.isBlank()) return null;
        return switch (type.toUpperCase()) {
            case "CATEGORY" -> "INVENTORY_CATEGORIES";
            case "ITEM" -> "INVENTORY_ITEMS";
            default -> null;
        };
    }

    private String toJson(Map<String, String> data) {
        try {
            return mapper.writeValueAsString(data);
        } catch (JsonProcessingException e) {
            throw new BusinessException("Lỗi serialize dữ liệu catalog");
        }
    }
}
