package com.racehorse.admin;

import com.racehorse.common.exception.BusinessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.*;
import java.math.BigDecimal;

@Service
public class ManagementDashboardService {
    private final JdbcTemplate jdbc;
    public ManagementDashboardService(JdbcTemplate jdbc){this.jdbc=jdbc;}
    @Transactional(readOnly=true)
    public Map<String,Object> dashboard(String month,String compareTo){
        YearMonth current=parseMonth(month);YearMonth previous=compareTo==null||compareTo.isBlank()?current.minusMonths(1):parseMonth(compareTo);
        return Map.of("month",current.toString(),"compareTo",previous.toString(),"currency","VND",
                "current",metrics(current),"previous",metrics(previous),
                "activeHorses",jdbc.queryForObject("select count(*) from horses where archived_at is null",Integer.class),
                "activeAccounts",jdbc.queryForObject("select count(*) from users where enabled=1",Integer.class));
    }
    private YearMonth parseMonth(String value){try{YearMonth m=YearMonth.parse(value);if(m.getYear()<2000||m.getYear()>2100)throw new IllegalArgumentException();return m;}catch(Exception e){throw new BusinessException("Tháng phải có dạng YYYY-MM, trong khoảng 2000–2100");}}
    private Map<String,Object> metrics(YearMonth month){
        LocalDate start=month.atDay(1),end=month.plusMonths(1).atDay(1);
        Map<String,Object> result=new LinkedHashMap<>();
        result.put("evaluations",jdbc.queryForObject("select count(*) from training_evaluations where session_date>=? and session_date<?",Integer.class,start,end));
        result.put("horsesTrained",jdbc.queryForObject("select count(distinct horse_id) from training_evaluations where session_date>=? and session_date<?",Integer.class,start,end));
        for(String status:List.of("GREEN","YELLOW","RED"))result.put(status.toLowerCase(Locale.ROOT),jdbc.queryForObject("select count(*) from training_evaluations where session_date>=? and session_date<? and fitness_status=?",Integer.class,start,end,status));
        BigDecimal expense=amount(start,end,"EXPENSE",null),revenue=amount(start,end,"REVENUE",null);
        result.put("expense",expense);result.put("revenue",revenue);result.put("net",revenue.subtract(expense));
        result.put("feedCost",amount(start,end,"EXPENSE","FEED_COST"));result.put("medicalCost",amount(start,end,"EXPENSE","MEDICAL_COST"));result.put("operatingCost",amount(start,end,"EXPENSE","OPERATING_COST"));result.put("prizeRevenue",amount(start,end,"REVENUE","PRIZE_REVENUE"));
        return result;
    }
    private BigDecimal amount(LocalDate start,LocalDate end,String type,String category){
        String sql="select coalesce(sum(amount),0) from financial_transactions where currency='VND' and (source_entity_type is null or source_entity_type<>'WALLET') and transaction_date>=? and transaction_date<? and transaction_type=?";
        return category==null?jdbc.queryForObject(sql,BigDecimal.class,start,end,type):jdbc.queryForObject(sql+" and category=?",BigDecimal.class,start,end,type,category);
    }
}
