package com.racehorse.finance.service;

import com.racehorse.common.exception.BusinessException;
import com.racehorse.common.exception.ResourceNotFoundException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.*;

@Service
public class FinanceService {
    private static final Set<String> TYPES = Set.of("EXPENSE", "REVENUE");
    private static final Set<String> CATEGORIES = Set.of("FEED_COST", "MEDICAL_COST", "OPERATING_COST", "PRIZE_REVENUE");
    private final JdbcTemplate jdbc;

    public FinanceService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Transactional
    public TransactionView create(UUID actor, UUID horseId, UUID stableId, LocalDate date, String type,
                                  String category, BigDecimal amount, String description,
                                  String sourceType, UUID sourceId) {
        String normalizedType = upper(type);
        String normalizedCategory = upper(category);
        if (!TYPES.contains(normalizedType)) throw new BusinessException("transactionType chỉ nhận EXPENSE hoặc REVENUE");
        if (!CATEGORIES.contains(normalizedCategory)) throw new BusinessException("category không hợp lệ");
        if (amount == null || amount.signum() < 0) throw new BusinessException("Số tiền phải lớn hơn hoặc bằng 0");
        if (horseId == null && stableId == null) throw new BusinessException("Giao dịch phải thuộc một ngựa hoặc một chuồng");
        if (Set.of("MEDICAL_COST", "PRIZE_REVENUE").contains(normalizedCategory) && horseId == null)
            throw new BusinessException("Nhóm giao dịch này bắt buộc chọn ngựa");
        requireExists("horses", horseId, "Không tìm thấy ngựa: ");
        requireExists("stables", stableId, "Không tìm thấy chuồng: ");
        UUID id = UUID.randomUUID();
        jdbc.update("insert into financial_transactions(id,horse_id,stable_id,transaction_date,transaction_type,category,amount,currency,description,source_entity_type,source_entity_id,recorded_by_user_id) "
                        + "values(?,?,?,?,?,?,?,'VND',?,?,?,?)",
                id, horseId, stableId, date, normalizedType, normalizedCategory, amount,
                required(description), trimToNull(sourceType), sourceId, actor);
        return get(id, actor, true);
    }

    @Transactional(readOnly = true)
    public TransactionView get(UUID id, UUID requester, boolean manager) {
        List<Object> args = new ArrayList<>();
        String sql = baseSelect() + " where ft.id=?";
        args.add(id);
        if (!manager) {
            sql += " and exists(select 1 from horse_ownerships ho where ho.horse_id=ft.horse_id and ho.owner_id=?)";
            args.add(requester);
        }
        List<TransactionView> rows = jdbc.query(sql, this::map, args.toArray());
        if (rows.isEmpty()) throw new ResourceNotFoundException("Không tìm thấy giao dịch tài chính: " + id);
        return rows.getFirst();
    }

    @Transactional(readOnly = true)
    public List<TransactionView> list(UUID requester, boolean manager, UUID horseId, UUID stableId,
                                      LocalDate from, LocalDate to, String type, String category,
                                      int page, int size) {
        if (page < 0 || size < 1 || size > 200) throw new BusinessException("Phân trang không hợp lệ");
        StringBuilder sql = new StringBuilder(baseSelect()).append(" where 1=1");
        List<Object> args = new ArrayList<>();
        if (!manager) {
            sql.append(" and exists(select 1 from horse_ownerships ho where ho.horse_id=ft.horse_id and ho.owner_id=?)");
            args.add(requester);
        }
        add(sql, args, " and ft.horse_id=?", horseId);
        add(sql, args, " and ft.stable_id=?", stableId);
        add(sql, args, " and ft.transaction_date>=?", from);
        add(sql, args, " and ft.transaction_date<=?", to);
        add(sql, args, " and ft.transaction_type=?", type == null ? null : upper(type));
        add(sql, args, " and ft.category=?", category == null ? null : upper(category));
        sql.append(" order by ft.transaction_date desc,ft.created_at desc offset ? rows fetch next ? rows only");
        args.add(page * size); args.add(size);
        return jdbc.query(sql.toString(), this::map, args.toArray());
    }

    @Transactional(readOnly = true)
    public SummaryView summary(UUID requester, boolean manager, UUID horseId, LocalDate from, LocalDate to) {
        StringBuilder sql = new StringBuilder("select ft.transaction_type,sum(ft.amount) total from financial_transactions ft where 1=1");
        List<Object> args = new ArrayList<>();
        if (!manager) {
            sql.append(" and exists(select 1 from horse_ownerships ho where ho.horse_id=ft.horse_id and ho.owner_id=?)");
            args.add(requester);
        }
        add(sql, args, " and ft.horse_id=?", horseId);
        add(sql, args, " and ft.transaction_date>=?", from);
        add(sql, args, " and ft.transaction_date<=?", to);
        sql.append(" group by ft.transaction_type");
        BigDecimal expense = BigDecimal.ZERO, revenue = BigDecimal.ZERO;
        for (Map<String,Object> row : jdbc.queryForList(sql.toString(), args.toArray())) {
            BigDecimal total = (BigDecimal) row.get("total");
            if ("EXPENSE".equals(row.get("transaction_type"))) expense = total; else revenue = total;
        }
        return new SummaryView("VND", expense, revenue, revenue.subtract(expense));
    }

    @Transactional(readOnly=true)
    public List<TransactionView> byIds(List<UUID> ids){
        if(ids==null||ids.isEmpty())return List.of();
        String placeholders=String.join(",",Collections.nCopies(ids.size(),"?"));
        return jdbc.query(baseSelect()+" where ft.id in ("+placeholders+") order by ft.transaction_date,ft.created_at",
                this::map,ids.toArray());
    }

    private String baseSelect() {
        return "select ft.id,ft.horse_id,h.name horse_name,ft.stable_id,s.name stable_name,ft.transaction_date,"
                + "ft.transaction_type,ft.category,ft.amount,ft.currency,ft.description,ft.source_entity_type,"
                + "ft.source_entity_id,ft.recorded_by_user_id,ft.created_at from financial_transactions ft "
                + "left join horses h on h.id=ft.horse_id left join stables s on s.id=ft.stable_id";
    }

    private TransactionView map(java.sql.ResultSet rs, int row) throws java.sql.SQLException {
        return new TransactionView(rs.getObject("id", UUID.class), rs.getObject("horse_id", UUID.class),
                rs.getString("horse_name"), rs.getObject("stable_id", UUID.class), rs.getString("stable_name"),
                rs.getObject("transaction_date", LocalDate.class), rs.getString("transaction_type"),
                rs.getString("category"), rs.getBigDecimal("amount"), rs.getString("currency"),
                rs.getString("description"), rs.getString("source_entity_type"),
                rs.getObject("source_entity_id", UUID.class), rs.getObject("recorded_by_user_id", UUID.class),
                rs.getObject("created_at", OffsetDateTime.class));
    }

    private void requireExists(String table, UUID id, String message) {
        if (id == null) return;
        Integer count = jdbc.queryForObject("select count(*) from " + table + " where id=?", Integer.class, id);
        if (count == null || count == 0) throw new ResourceNotFoundException(message + id);
    }
    private void add(StringBuilder sql, List<Object> args, String clause, Object value) { if (value != null) { sql.append(clause); args.add(value); } }
    private String upper(String value) { return required(value).toUpperCase(Locale.ROOT); }
    private String required(String value) { if (value == null || value.isBlank()) throw new BusinessException("Mô tả không được để trống"); return value.trim(); }
    private String trimToNull(String value) { return value == null || value.isBlank() ? null : value.trim(); }

    public record TransactionView(UUID id, UUID horseId, String horseName, UUID stableId, String stableName,
                                  LocalDate transactionDate, String transactionType, String category,
                                  BigDecimal amount, String currency, String description, String sourceEntityType,
                                  UUID sourceEntityId, UUID recordedByUserId, OffsetDateTime createdAt) {}
    public record SummaryView(String currency, BigDecimal expense, BigDecimal revenue, BigDecimal net) {}
}
