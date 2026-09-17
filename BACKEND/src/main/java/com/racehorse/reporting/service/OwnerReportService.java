package com.racehorse.reporting.service;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.racehorse.common.exception.BusinessException;
import com.racehorse.common.exception.ResourceNotFoundException;
import com.racehorse.finance.service.FinanceService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.*;

@Service
public class OwnerReportService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final FinanceService finance;

    public OwnerReportService(JdbcTemplate jdbc, ObjectMapper json, FinanceService finance) {
        this.jdbc = jdbc; this.json = json; this.finance = finance;
    }

    @Transactional
    public ReportView generate(UUID ownerId, LocalDate start, LocalDate end, UUID actor) {
        if (start == null || end == null || start.isAfter(end)) throw new BusinessException("Khoảng báo cáo không hợp lệ");
        Integer owner = jdbc.queryForObject("select count(*) from users u join user_roles ur on ur.user_id=u.id where u.id=? and ur.role='HORSE_OWNER'",
                Integer.class, ownerId);
        if (owner == null || owner == 0) throw new BusinessException("Tài khoản không phải chủ sở hữu ngựa");
        List<UUID> horses = jdbc.query("select horse_id from horse_ownerships where owner_id=?",
                (rs,row)->rs.getObject(1, UUID.class), ownerId);
        List<UUID> transactionIds = transactionIds(horses, start, end);
        UUID id = UUID.randomUUID();
        try {
            jdbc.update("insert into owner_reports(id,owner_user_id,period_start,period_end,generated_at,generated_by_user_id,horse_ids_json,transaction_ids_json) values(?,?,?,?,?,?,?,?)",
                    id,ownerId,start,end,OffsetDateTime.now(),actor,idsJson(horses),idsJson(transactionIds));
        } catch (org.springframework.dao.DuplicateKeyException exception) {
            throw new BusinessException("Báo cáo cho kỳ này đã tồn tại");
        }
        return get(id, actor, true);
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

    public record ReportSummary(UUID id,UUID ownerId,String ownerUsername,LocalDate periodStart,LocalDate periodEnd,
                                OffsetDateTime generatedAt,UUID generatedBy){}
    public record HorseItem(UUID id,String code,String name){}
    public record ReportView(UUID id,UUID ownerId,String ownerUsername,LocalDate periodStart,LocalDate periodEnd,
                             OffsetDateTime generatedAt,UUID generatedBy,List<HorseItem> horses,
                             List<FinanceService.TransactionView> transactions,String currency,
                             BigDecimal expense,BigDecimal revenue,BigDecimal net){}
}
