package com.racehorse.reporting.service;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.racehorse.audit.service.AuditService;
import com.racehorse.common.exception.BusinessException;
import com.racehorse.common.exception.ResourceNotFoundException;
import com.racehorse.finance.service.FinanceService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.*;

@Service
public class OwnerReportService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final FinanceService finance;
    private final AuditService audit;

    public record TransactionItem(
            UUID id,
            UUID horseId,
            LocalDate date,
            String type,
            String category,
            BigDecimal amount,
            String description
    ) {}

    public OwnerReportService(JdbcTemplate jdbc, ObjectMapper json, FinanceService finance, AuditService audit) {
        this.jdbc = jdbc; this.json = json; this.finance = finance; this.audit = audit;
    }

    public static List<UUID> normalizeIds(Collection<UUID> ids) {
        if (ids == null || ids.isEmpty()) return List.of();
        return ids.stream()
                .filter(Objects::nonNull)
                .distinct()
                .sorted(Comparator.comparing(UUID::toString))
                .toList();
    }

    private static String formatAmount(BigDecimal amount) {
        if (amount == null) return "0.00";
        return amount.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private static String clean(String s) {
        return s == null ? "" : s.trim();
    }

    private static String canonicalHorse(HorseItem h) {
        return (h.id() == null ? "" : h.id().toString().toLowerCase(Locale.ROOT))
                + ":" + clean(h.code())
                + ":" + clean(h.name());
    }

    private static String canonicalTransaction(TransactionItem t) {
        return (t.id() == null ? "" : t.id().toString().toLowerCase(Locale.ROOT))
                + ":" + (t.horseId() == null ? "" : t.horseId().toString().toLowerCase(Locale.ROOT))
                + ":" + (t.date() == null ? "" : t.date().toString())
                + ":" + clean(t.type()).toUpperCase(Locale.ROOT)
                + ":" + clean(t.category()).toUpperCase(Locale.ROOT)
                + ":" + formatAmount(t.amount())
                + ":" + clean(t.description());
    }

    public static String computeContentHash(Collection<HorseItem> horses, Collection<TransactionItem> transactions) {
        List<String> canonicalHorses = horses == null ? List.of() :
                horses.stream()
                        .filter(Objects::nonNull)
                        .filter(h -> h.id() != null)
                        .sorted(Comparator.comparing(h -> h.id().toString()))
                        .map(OwnerReportService::canonicalHorse)
                        .distinct()
                        .toList();

        List<String> canonicalTransactions = transactions == null ? List.of() :
                transactions.stream()
                        .filter(Objects::nonNull)
                        .filter(t -> t.id() != null)
                        .sorted(Comparator.comparing(t -> t.id().toString()))
                        .map(OwnerReportService::canonicalTransaction)
                        .distinct()
                        .toList();

        StringBuilder sb = new StringBuilder();
        sb.append("horses:").append(String.join(",", canonicalHorses));
        sb.append(";transactions:").append(String.join(",", canonicalTransactions));

        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(sb.toString().getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(64);
            for (byte b : digest) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }

    public static String computeContentHashFromIds(Collection<UUID> horses, Collection<UUID> transactionIds) {
        List<HorseItem> hItems = horses == null ? List.of() :
                horses.stream().filter(Objects::nonNull).map(id -> new HorseItem(id, "", "")).toList();
        List<TransactionItem> tItems = transactionIds == null ? List.of() :
                transactionIds.stream().filter(Objects::nonNull).map(id -> new TransactionItem(id, null, null, "", "", BigDecimal.ZERO, "")).toList();
        return computeContentHash(hItems, tItems);
    }

    public List<HorseItem> queryOwnerHorses(UUID ownerId) {
        String sql = """
                select ho.horse_id as id, h.code, h.name
                from horse_ownerships ho
                left join horses h on h.id = ho.horse_id
                where ho.owner_id = ?
                order by ho.horse_id
                """;
        return jdbc.query(sql, (rs, row) -> new HorseItem(
                rs.getObject("id", UUID.class),
                rs.getString("code"),
                rs.getString("name")), ownerId);
    }

    public List<TransactionItem> queryTransactionItems(List<UUID> horseIds, LocalDate start, LocalDate end) {
        if (horseIds == null || horseIds.isEmpty()) return List.of();
        String placeholders = String.join(",", Collections.nCopies(horseIds.size(), "?"));
        List<Object> args = new ArrayList<>(horseIds);
        args.add(start);
        args.add(end);
        String sql = "select id, horse_id, transaction_date, transaction_type, category, amount, description "
                + "from financial_transactions where horse_id in (" + placeholders + ") and transaction_date between ? and ? "
                + "order by id";
        return jdbc.query(sql, (rs, row) -> new TransactionItem(
                rs.getObject("id", UUID.class),
                rs.getObject("horse_id", UUID.class),
                rs.getObject("transaction_date", LocalDate.class),
                rs.getString("transaction_type"),
                rs.getString("category"),
                rs.getBigDecimal("amount"),
                rs.getString("description")), args.toArray());
    }

    @Transactional
    public ReportView generate(UUID ownerId, LocalDate start, LocalDate end, UUID actor) {
        if (start == null || end == null || start.isAfter(end)) throw new BusinessException("Khoảng báo cáo không hợp lệ");
        Integer owner = jdbc.queryForObject("select count(*) from users u join user_roles ur on ur.user_id=u.id where u.id=? and ur.role='HORSE_OWNER'",
                Integer.class, ownerId);
        if (owner == null || owner == 0) throw new BusinessException("Tài khoản không phải chủ sở hữu ngựa");

        List<HorseItem> horses = queryOwnerHorses(ownerId);
        List<UUID> horseIds = horses.stream().map(HorseItem::id).filter(Objects::nonNull).toList();
        List<TransactionItem> transactions = queryTransactionItems(horseIds, start, end);
        List<UUID> transactionIds = transactions.stream().map(TransactionItem::id).filter(Objects::nonNull).toList();

        List<UUID> normHorses = normalizeIds(horseIds);
        List<UUID> normTransactions = normalizeIds(transactionIds);
        String contentHash = computeContentHash(horses, transactions);

        Integer duplicateExists = jdbc.queryForObject(
                "select count(*) from owner_reports where owner_user_id=? and period_start=? and period_end=? and content_hash=?",
                Integer.class, ownerId, start, end, contentHash);
        if (duplicateExists != null && duplicateExists > 0) {
            throw new BusinessException("Báo cáo cho kỳ này với nội dung tương đương đã tồn tại");
        }

        UUID id = UUID.randomUUID();
        try {
            jdbc.update("insert into owner_reports(id,owner_user_id,period_start,period_end,generated_at,generated_by_user_id,horse_ids_json,transaction_ids_json,content_hash) values(?,?,?,?,?,?,?,?,?)",
                    id,ownerId,start,end,OffsetDateTime.now(),actor,idsJson(normHorses),idsJson(normTransactions),contentHash);
        } catch (org.springframework.dao.DuplicateKeyException exception) {
            throw new BusinessException("Báo cáo cho kỳ này với nội dung tương đương đã tồn tại");
        }
        String ownerName = jdbc.queryForObject("select username from users where id=?", String.class, ownerId);
        audit.record(actor, "REPORTING", "REPORT_GENERATE", "OWNER_REPORT", id,
                "Lập báo cáo tài chính cho chủ ngựa " + ownerName + " kỳ " + start + " đến " + end,
                null, null);
        return get(id, actor, true);
    }

    @Transactional(readOnly = true)
    public List<OwnerOption> getOwners() {
        String sql = """
                select u.id, u.username, u.email,
                       (select count(*) from horse_ownerships ho where ho.owner_id=u.id) as horse_count
                from users u
                join user_roles ur on ur.user_id=u.id
                where ur.role='HORSE_OWNER' and u.enabled=1
                order by u.username
                """;
        return jdbc.query(sql, (rs, row) -> new OwnerOption(
                rs.getObject("id", UUID.class),
                rs.getString("username"),
                rs.getString("email"),
                rs.getInt("horse_count")));
    }

    @Transactional(readOnly = true)
    public List<ReportSummary> list(UUID requester, boolean manager, LocalDate from, LocalDate to) {
        StringBuilder sql = new StringBuilder("select r.id,r.owner_user_id,u.username,r.period_start,r.period_end,r.generated_at,r.generated_by_user_id from owner_reports r join users u on u.id=r.owner_user_id where 1=1");
        List<Object> args = new ArrayList<>();
        if (!manager) { sql.append(" and r.owner_user_id=?"); args.add(requester); }
        if (from != null) { sql.append(" and r.period_end>=?"); args.add(from); }
        if (to != null) { sql.append(" and r.period_start<=?"); args.add(to); }
        sql.append(" order by r.period_end desc,r.generated_at desc");
        return jdbc.query(sql.toString(), (rs,row)->new ReportSummary(rs.getObject("id",UUID.class),
                rs.getObject("owner_user_id",UUID.class),rs.getString("username"),rs.getObject("period_start",LocalDate.class),
                rs.getObject("period_end",LocalDate.class),rs.getObject("generated_at",OffsetDateTime.class),
                rs.getObject("generated_by_user_id",UUID.class)), args.toArray());
    }

    @Transactional(readOnly = true)
    public ReportView get(UUID id, UUID requester, boolean manager) {
        String sql="select r.*,u.username from owner_reports r join users u on u.id=r.owner_user_id where r.id=?";
        List<ReportRow> rows=jdbc.query(sql,(rs,row)->new ReportRow(rs.getObject("owner_user_id",UUID.class),rs.getString("username"),
                rs.getObject("period_start",LocalDate.class),rs.getObject("period_end",LocalDate.class),
                rs.getObject("generated_at",OffsetDateTime.class),rs.getObject("generated_by_user_id",UUID.class),
                rs.getString("horse_ids_json"),rs.getString("transaction_ids_json")),id);
        if(rows.isEmpty()) throw new ResourceNotFoundException("Không tìm thấy báo cáo: "+id);
        ReportRow row=rows.getFirst(); UUID ownerId=row.ownerId();
        if(!manager&&!ownerId.equals(requester)) throw new ResourceNotFoundException("Không tìm thấy báo cáo: "+id);
        LocalDate start=row.start(),end=row.end();
        List<UUID> horseIds=parseIds(row.horseIdsJson());
        List<HorseItem> horses=horseIds.isEmpty()?List.of():horseItems(horseIds);
        List<FinanceService.TransactionView> transactions=finance.byIds(parseIds(row.transactionIdsJson()));
        BigDecimal expense=transactions.stream().filter(v->"EXPENSE".equals(v.transactionType())).map(FinanceService.TransactionView::amount).reduce(BigDecimal.ZERO,BigDecimal::add);
        BigDecimal revenue=transactions.stream().filter(v->"REVENUE".equals(v.transactionType())).map(FinanceService.TransactionView::amount).reduce(BigDecimal.ZERO,BigDecimal::add);
        return new ReportView(id,ownerId,row.username(),start,end,row.generatedAt(),row.generatedBy(),horses,transactions,
                "VND",expense,revenue,revenue.subtract(expense));
    }

    public byte[] jsonDownload(UUID id, UUID requester, boolean manager) {
        try { return json.writerWithDefaultPrettyPrinter().writeValueAsString(get(id,requester,manager)).getBytes(StandardCharsets.UTF_8); }
        catch (JacksonException exception) { throw new IllegalStateException("Không thể xuất báo cáo JSON",exception); }
    }

    public byte[] csvDownload(UUID id, UUID requester, boolean manager) {
        ReportView r = get(id, requester, manager);
        StringBuilder sb = new StringBuilder();
        sb.append('\uFEFF');
        sb.append("BÁO CÁO TÀI CHÍNH CHỦ SỞ HỮU NGỰA\n");
        sb.append("Mã báo cáo:,").append(r.id()).append("\n");
        sb.append("Chủ sở hữu:,").append(escapeCsv(r.ownerUsername())).append("\n");
        sb.append("Kỳ báo cáo:,").append(r.periodStart()).append(" đến ").append(r.periodEnd()).append("\n");
        sb.append("Thời điểm lập:,").append(r.generatedAt()).append("\n");
        sb.append("Tổng chi phí (VND):,").append(r.expense()).append("\n");
        sb.append("Tổng doanh thu (VND):,").append(r.revenue()).append("\n");
        sb.append("Số dư ròng (VND):,").append(r.net()).append("\n\n");

        sb.append("DANH SÁCH NGỰA TRONG KỲ\n");
        sb.append("Mã ngựa,Tên ngựa\n");
        for (HorseItem h : r.horses()) {
            sb.append(escapeCsv(h.code())).append(',').append(escapeCsv(h.name())).append('\n');
        }
        sb.append("\nCHI TIẾT GIAO DỊCH PHÁT SINH\n");
        sb.append("Ngày,Loại,Danh mục,Số tiền (VND),Ngựa,Chuồng,Mô tả\n");
        for (FinanceService.TransactionView t : r.transactions()) {
            sb.append(escapeCsv(t.transactionDate() == null ? "" : t.transactionDate().toString())).append(',');
            sb.append(escapeCsv("EXPENSE".equals(t.transactionType()) ? "Chi phí" : "Doanh thu")).append(',');
            sb.append(escapeCsv(t.category())).append(',');
            sb.append(t.amount() == null ? "0" : t.amount().toString()).append(',');
            sb.append(escapeCsv(t.horseName() == null ? "" : t.horseName())).append(',');
            sb.append(escapeCsv(t.stableName() == null ? "" : t.stableName())).append(',');
            sb.append(escapeCsv(t.description() == null ? "" : t.description())).append('\n');
        }
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    private String escapeCsv(String val) {
        if (val == null) return "\"\"";
        return "\"" + val.replace("\"", "\"\"") + "\"";
    }

    private List<UUID> transactionIds(List<UUID> horses,LocalDate start,LocalDate end){
        if(horses.isEmpty()) return List.of();
        String placeholders=String.join(",",Collections.nCopies(horses.size(),"?"));
        List<Object> args=new ArrayList<>(horses); args.add(start);args.add(end);
        return jdbc.query("select id from financial_transactions where horse_id in ("+placeholders+") and transaction_date between ? and ?",
                (rs,row)->rs.getObject(1,UUID.class),args.toArray());
    }
    private List<HorseItem> horseItems(List<UUID> ids){
        String placeholders=String.join(",",Collections.nCopies(ids.size(),"?"));
        return jdbc.query("select id,code,name from horses where id in ("+placeholders+") order by name",
                (rs,row)->new HorseItem(rs.getObject("id",UUID.class),rs.getString("code"),rs.getString("name")),ids.toArray());
    }
    private String idsJson(List<UUID> ids){ try{return json.writeValueAsString(ids.stream().map(id->Map.of("id",id)).toList());}catch(JacksonException e){throw new IllegalStateException(e);} }
    private List<UUID> parseIds(String value){
        try{List<UUID> ids=new ArrayList<>(); for(JsonNode node:json.readTree(value)){String id=node.isTextual()?node.asText():node.path("id").asText();if(!id.isBlank())ids.add(UUID.fromString(id));}return ids;}
        catch(Exception exception){throw new IllegalStateException("Danh sách ngựa trong báo cáo không hợp lệ",exception);}
    }
    private UUID uuid(Object value){return value instanceof UUID id?id:UUID.fromString(String.valueOf(value));}
    private record ReportRow(UUID ownerId,String username,LocalDate start,LocalDate end,OffsetDateTime generatedAt,UUID generatedBy,String horseIdsJson,String transactionIdsJson){}

    public record OwnerOption(UUID id, String username, String email, int horseCount) {}
    public record ReportSummary(UUID id,UUID ownerId,String ownerUsername,LocalDate periodStart,LocalDate periodEnd,
                                OffsetDateTime generatedAt,UUID generatedBy){}
    public record HorseItem(UUID id,String code,String name){}
    public record ReportView(UUID id,UUID ownerId,String ownerUsername,LocalDate periodStart,LocalDate periodEnd,
                             OffsetDateTime generatedAt,UUID generatedBy,List<HorseItem> horses,
                             List<FinanceService.TransactionView> transactions,String currency,
                             BigDecimal expense,BigDecimal revenue,BigDecimal net){}
}
