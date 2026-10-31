package com.racehorse.wallet;

import com.racehorse.common.exception.BusinessException;
import com.racehorse.common.exception.InvalidInputException;
import com.racehorse.common.exception.ResourceNotFoundException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.SecureRandom;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.HashMap;

@Service
public class WalletService {
    private static final BigDecimal MIN_TOP_UP = BigDecimal.valueOf(100_000);
    static final String SERVICE_TERMS_VERSION = "EQUINE-EMERGENCY-2026-10";
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final PayosGateway gateway;
    private final Clock clock;
    private final ZoneId clubZone;
    private final SecureRandom random = new SecureRandom();

    public WalletService(JdbcTemplate jdbc, ObjectMapper json, PayosGateway gateway, Clock clock,
                         @Value("${app.club-time-zone}") String clubZone) {
        this.jdbc = jdbc;
        this.json = json;
        this.gateway = gateway;
        this.clock = clock;
        this.clubZone = ZoneId.of(clubZone);
    }

    public record HorsePlanView(UUID horseId, String code, String name, String activePlan,
                                String nextPlan, LocalDate planStartedOn, LocalDate nextDueDate,
                                LocalDate paidUntil, String paymentStatus, String state,
                                BigDecimal renewalAmountVnd) { }
    public record WalletView(BigDecimal balanceVnd, List<HorsePlanView> horses,
                             BigDecimal renewalAmountVnd, boolean fakeGateway, boolean vnpayGateway) { }
    public record OrderView(UUID id, long orderCode, BigDecimal amountVnd, String status,
                            Instant expiresAt, String checkoutUrl, String qrCode, boolean fakeGateway,
                            boolean vnpayGateway) { }
    public record PlanQuote(String plan, BigDecimal priceVnd, BigDecimal unusedCreditVnd,
                            BigDecimal lateFeeVnd, BigDecimal walletBalanceVnd,
                            BigDecimal additionalTopUpNeededVnd) { }

    @Transactional(readOnly = true)
    public WalletView wallet(UUID ownerId) {
        Map<String, Object> row = owner(ownerId, false);
        List<HorsePlanView> horses = horses(ownerId);
        BigDecimal renewal = horses.stream().map(HorsePlanView::renewalAmountVnd)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new WalletView((BigDecimal) row.get("wallet_balance_vnd"), horses, renewal,
                gateway.isFake(), gateway.isVnpay());
    }

    private List<HorsePlanView> horses(UUID ownerId) {
        return jdbc.query("select h.id,h.code,h.name,h.active_service_plan,h.next_service_plan,"
                        + "h.service_plan_started_on,h.service_next_due_date,h.paid_until,h.payment_status "
                        + "from horses h join horse_ownerships ho on ho.horse_id=h.id "
                        + "where ho.owner_id=? and h.archived_at is null "
                        + "order by case when h.service_next_due_date is null then 1 else 0 end,"
                        + "h.service_next_due_date,h.id",
                (rs, index) -> {
                    String current = rs.getString("active_service_plan");
                    String scheduled = rs.getString("next_service_plan");
                    LocalDate due = rs.getDate("service_next_due_date") == null ? null
                            : rs.getDate("service_next_due_date").toLocalDate();
                    long overdue = due == null ? 0 : Math.max(0, ChronoUnit.DAYS.between(due, today()));
                    WalletPlan renewing = current == null ? null : HorsePlanPolicy.renewalPlan(
                            WalletPlan.valueOf(current), scheduled == null ? null : WalletPlan.valueOf(scheduled));
                    return new HorsePlanView(rs.getObject("id", UUID.class), rs.getString("code"),
                            rs.getString("name"), current, scheduled,
                            rs.getDate("service_plan_started_on") == null ? null
                                    : rs.getDate("service_plan_started_on").toLocalDate(), due,
                            rs.getDate("paid_until") == null ? null : rs.getDate("paid_until").toLocalDate(),
                            rs.getString("payment_status"), state(current, due, today()),
                            renewing == null ? BigDecimal.ZERO : renewing.price().add(lateFee(renewing.price(), overdue)));
                }, ownerId);
    }

    @Transactional
    public OrderView createTopUp(UUID ownerId, long amountVnd, String ipAddress) {
        if (amountVnd < MIN_TOP_UP.longValue())
            throw new InvalidInputException("Số tiền nạp tối thiểu là 100.000 VND");
        owner(ownerId, false);
        long code = 100_000_000L + random.nextLong(900_000_000L);
        UUID id = UUID.randomUUID();
        Instant expires = clock.instant().plus(gateway.isVnpay() ? 15 : 3, ChronoUnit.MINUTES);
        String description = Long.toString(code);
        jdbc.update("insert into module_records(id,module_code,record_type,user_id,status,event_date,data_json,"
                        + "payos_order_code,payos_expected_amount_vnd,payos_expires_at) "
                        + "values(?,'SYSTEM','PAYOS_TOPUP',?,'PENDING',?,'{}',?,?,?)",
                id, ownerId, today(), code, BigDecimal.valueOf(amountVnd), OffsetDateTime.ofInstant(expires, ZoneOffset.UTC));
        PayosGateway.Link link = gateway.create(code, amountVnd, description, expires, ipAddress);
        jdbc.update("update module_records set data_json=?,updated_at=SYSDATETIMEOFFSET() where id=?",
                json.writeValueAsString(Map.of("paymentLinkId", link.paymentLinkId(),
                        "checkoutUrl", link.checkoutUrl() == null ? "" : link.checkoutUrl(),
                        "qrCode", link.qrCode() == null ? "" : link.qrCode(),
                        "fakeGateway", gateway.isFake())), id);
        return new OrderView(id, code, BigDecimal.valueOf(amountVnd), "PENDING", expires,
                link.checkoutUrl(), link.qrCode(), gateway.isFake(), gateway.isVnpay());
    }

    @Transactional
    public OrderView order(UUID ownerId, UUID orderId) {
        Map<String, Object> row = orderRow(orderId, ownerId, false);
        JsonNode details = json.readTree((String) row.get("data_json"));
        boolean fakeOrder = details.path("fakeGateway").asBoolean(false)
                || details.path("paymentLinkId").asText("").startsWith("fake-");
        if ("PENDING".equals(row.get("status")) && clock.instant().isAfter(expiry(row))) {
            if (!fakeOrder && !gateway.isFake() && !gateway.isVnpay()) {
                PayosGateway.Status remote = gateway.getStatus(((Number) row.get("payos_order_code")).longValue());
                if ("PAID".equals(remote.status())) {
                    complete(orderId, remote.amountVnd(), false);
                } else {
                    jdbc.update("update module_records set status=?,updated_at=SYSDATETIMEOFFSET() "
                            + "where id=? and status='PENDING'",
                            "CANCELLED".equals(remote.status()) ? "CANCELLED" : "EXPIRED", orderId);
                }
            } else {
                jdbc.update("update module_records set status='EXPIRED',updated_at=SYSDATETIMEOFFSET() "
                        + "where id=? and status='PENDING'", orderId);
            }
            row = orderRow(orderId, ownerId, false);
        }
        String checkoutUrl = details.path("checkoutUrl").asText("");
        String qrCode = details.path("qrCode").asText("");
        return new OrderView(orderId, ((Number) row.get("payos_order_code")).longValue(),
                (BigDecimal) row.get("payos_expected_amount_vnd"), (String) row.get("status"),
                expiry(row), checkoutUrl.isBlank() ? null : checkoutUrl,
                qrCode.isBlank() ? null : qrCode, fakeOrder, gateway.isVnpay());
    }

    @Transactional
    public void acceptWebhook(JsonNode body) {
        if (gateway.isFake() || !gateway.verifyWebhook(body))
            throw new InvalidInputException("Webhook PayOS không hợp lệ");
        JsonNode data = body.path("data");
        if (!body.path("success").asBoolean(false) || !"00".equals(body.path("code").asText())
                || !"00".equals(data.path("code").asText())) return;
        long code = data.path("orderCode").asLong(-1);
        List<UUID> ids = jdbc.query("select id from module_records where payos_order_code=?",
                (rs, n) -> rs.getObject(1, UUID.class), code);
        if (ids.isEmpty()) {
            UUID reviewId = UUID.nameUUIDFromBytes(("payos-unmatched:" + code).getBytes(StandardCharsets.UTF_8));
            notifyManagers("wallet-unmatched:" + code, "PAYMENT_REVIEW",
                    "Cần đối soát webhook " + paymentProviderName() + " không có đơn local", reviewId);
            audit(null, "PAYMENT_REVIEW", reviewId,
                    "Webhook " + paymentProviderName() + " đã ký nhưng không có đơn local: " + code);
            return;
        }
        complete(ids.getFirst(), data.path("amount").asLong(-1), false);
    }

    @Transactional
    public Map<String, String> acceptVnpayIpn(Map<String, String> parameters) {
        if (!gateway.isVnpay() || !gateway.verifyVnpay(parameters))
            return Map.of("RspCode", "97", "Message", "Invalid signature");
        if (!"00".equals(parameters.get("vnp_ResponseCode"))
                || !"00".equals(parameters.get("vnp_TransactionStatus")))
            return Map.of("RspCode", "00", "Message", "Confirm Success");
        try {
            long orderCode = Long.parseLong(parameters.getOrDefault("vnp_TxnRef", ""));
            long amountRaw = Long.parseLong(parameters.getOrDefault("vnp_Amount", ""));
            if (amountRaw <= 0 || amountRaw % 100 != 0)
                return Map.of("RspCode", "04", "Message", "Invalid amount");
            List<UUID> ids = jdbc.query("select id from module_records where payos_order_code=? "
                            + "and module_code='SYSTEM' and record_type='PAYOS_TOPUP'",
                    (rs, n) -> rs.getObject(1, UUID.class), orderCode);
            if (ids.isEmpty()) return Map.of("RspCode", "01", "Message", "Order not found");
            complete(ids.getFirst(), amountRaw / 100, false);
            return Map.of("RspCode", "00", "Message", "Confirm Success");
        } catch (NumberFormatException exception) {
            return Map.of("RspCode", "04", "Message", "Invalid request data");
        }
    }

    @Transactional
    public void completeFake(UUID ownerId, UUID orderId) {
        if (!gateway.isFake()) throw new ResourceNotFoundException("Không tìm thấy thao tác này");
        Map<String, Object> row = orderRow(orderId, ownerId, false);
        JsonNode details = json.readTree((String) row.get("data_json"));
        if (!details.path("fakeGateway").asBoolean(false))
            throw new ResourceNotFoundException("Không tìm thấy đơn mô phỏng");
        complete(orderId, ((BigDecimal) row.get("payos_expected_amount_vnd")).longValueExact(), true);
    }

    private void complete(UUID orderId, long receivedVnd, boolean fake) {
        Map<String, Object> order = orderRow(orderId, null, true);
        String status = (String) order.get("status");
        if ("PAID".equals(status)) return;
        UUID ownerId = (UUID) order.get("user_id");
        BigDecimal expected = (BigDecimal) order.get("payos_expected_amount_vnd");
        if (expected.compareTo(BigDecimal.valueOf(receivedVnd)) != 0) {
            notifyManagers("wallet-mismatch:" + orderId, "PAYMENT_REVIEW",
                    "Cần đối soát số tiền đơn nạp ví", orderId);
            audit(null, "PAYMENT_REVIEW", orderId,
                    paymentProviderName() + " báo số tiền khác đơn; ví chưa được cộng");
            return;
        }
        if (!"PENDING".equals(status) || clock.instant().isAfter(expiry(order))) {
            notifyManagers("wallet-expired:" + orderId, "PAYMENT_REVIEW",
                    "Cần đối soát đơn nạp ví hết hạn", orderId);
            audit(null, "PAYMENT_REVIEW", orderId,
                    paymentProviderName() + " báo thanh toán cho đơn hết hạn; ví chưa được cộng");
            if (fake) throw new BusinessException("Đơn nạp ví đã hết hạn");
            if ("PENDING".equals(status))
                jdbc.update("update module_records set status='EXPIRED',updated_at=SYSDATETIMEOFFSET() where id=?", orderId);
            return;
        }
        owner(ownerId, true);
        jdbc.update("update users set wallet_balance_vnd=wallet_balance_vnd+?,version=version+1,"
                + "updated_at=SYSDATETIMEOFFSET() where id=?", expected, ownerId);
        ledger(ownerId, "REVENUE", "WALLET_TOPUP", expected, orderId,
                fake ? "Nạp ví mô phỏng" : "Nạp ví qua " + paymentProviderName());
        jdbc.update("update module_records set status='PAID',updated_at=SYSDATETIMEOFFSET() where id=?", orderId);
        notifyUser(ownerId, "wallet-topup:" + orderId, "WALLET_TOPUP",
                "Nạp ví thành công", "Ví đã được cộng " + expected.toPlainString() + " VND", orderId);
        renewIfDue(ownerId);
        audit(ownerId, "WALLET_TOPUP", orderId,
                "Ghi có ví từ đơn " + paymentProviderName() + " " + orderId);
    }

    @Transactional(readOnly = true)
    public PlanQuote quote(UUID ownerId, UUID horseId, WalletPlan newPlan) {
        Map<String, Object> wallet = owner(ownerId, false);
        Map<String, Object> horse = horsePlanRow(ownerId, horseId, false);
        if (horse.get("active_service_plan") != null)
            throw new BusinessException("Ngựa đã có gói; hãy chọn gói cho kỳ thanh toán kế tiếp");
        BigDecimal balance = (BigDecimal) wallet.get("wallet_balance_vnd");
        return new PlanQuote(newPlan.name(), newPlan.price(), BigDecimal.ZERO, BigDecimal.ZERO,
                balance, newPlan.price().subtract(balance).max(BigDecimal.ZERO));
    }

    @Transactional
    public WalletView purchase(UUID ownerId, UUID horseId, WalletPlan newPlan, boolean acceptedTerms) {
        requireAcceptedTerms(acceptedTerms);
        Map<String, Object> wallet = owner(ownerId, true);
        Map<String, Object> horse = horsePlanRow(ownerId, horseId, true);
        if (horse.get("active_service_plan") != null)
            throw new BusinessException("Ngựa đã có gói; hãy chọn gói cho kỳ thanh toán kế tiếp");
        BigDecimal balance = (BigDecimal) wallet.get("wallet_balance_vnd");
        if (balance.compareTo(newPlan.price()) < 0)
            throw new BusinessException("Số dư chưa đủ; cần nạp thêm ít nhất "
                    + newPlan.price().subtract(balance).toPlainString() + " VND");
        UUID eventId = UUID.randomUUID();
        LocalDate due = today().plusDays(newPlan.days());
        jdbc.update("update users set wallet_balance_vnd=?,version=version+1,updated_at=SYSDATETIMEOFFSET() where id=?",
                balance.subtract(newPlan.price()), ownerId);
        jdbc.update("update horses set active_service_plan=?,next_service_plan=null,service_plan_started_on=?,"
                        + "service_next_due_date=?,payment_status='PAID',paid_until=?,"
                        + "status=case when status='PENDING_PAYMENT' then 'ACTIVE' else status end,"
                        + "updated_at=SYSDATETIMEOFFSET(),version=version+1 where id=?",
                newPlan.name(), today(), due, due.minusDays(1), horseId);
        recordPlanEvent(eventId, ownerId, horseId, "SERVICE_PLAN_PURCHASE", newPlan, null, true);
        ledger(ownerId, "EXPENSE", "SERVICE_FEE", newPlan.price(), eventId, "Gói dịch vụ cho ngựa " + horseId);
        notifyUser(ownerId, "wallet-plan:" + eventId, "SERVICE_PLAN", "Gói dịch vụ đã kích hoạt",
                "Gói " + newPlan.days() + " ngày của ngựa có hiệu lực đến " + due.minusDays(1), horseId);
        notifyManagersForHorseWithoutVet(horseId, eventId);
        audit(ownerId, "SERVICE_PLAN_PURCHASE", eventId, "Kích hoạt gói " + newPlan.name());
        return wallet(ownerId);
    }

    @Transactional
    public WalletView scheduleNextPlan(UUID ownerId, UUID horseId, WalletPlan nextPlan, boolean acceptedTerms) {
        requireAcceptedTerms(acceptedTerms);
        owner(ownerId, true);
        Map<String, Object> horse = horsePlanRow(ownerId, horseId, true);
        String current = (String) horse.get("active_service_plan");
        if (current == null) throw new BusinessException("Ngựa chưa có gói; hãy mua gói đầu tiên");
        jdbc.update("update horses set next_service_plan=?,updated_at=SYSDATETIMEOFFSET(),"
                        + "version=version+1 where id=?",
                current.equals(nextPlan.name()) ? null : nextPlan.name(), horseId);
        recordPlanEvent(UUID.randomUUID(), ownerId, horseId, "SERVICE_PLAN_TERMS_ACCEPTANCE",
                nextPlan, null, true);
        return wallet(ownerId);
    }

    private void requireAcceptedTerms(boolean acceptedTerms) {
        if (!acceptedTerms) throw new BusinessException("Bạn cần đồng ý điều khoản dịch vụ trước khi đăng ký gói");
    }

    @Transactional
    public void renewOne(UUID ownerId) {
        owner(ownerId, true);
        renewIfDue(ownerId);
    }

    @Transactional
    public WalletView renewHorseNow(UUID ownerId, UUID horseId) {
        owner(ownerId, true);
        if (!renewHorseDue(ownerId, horseId))
            throw new BusinessException("Số dư chưa đủ hoặc ngựa chưa đến kỳ gia hạn");
        return wallet(ownerId);
    }

    @Transactional(readOnly = true)
    public List<UUID> ownersWithDuePlan() {
        return jdbc.query("select distinct ho.owner_id from horse_ownerships ho join horses h on h.id=ho.horse_id "
                        + "where h.archived_at is null and h.active_service_plan is not null "
                        + "and h.service_next_due_date<=?",
                (rs, n) -> rs.getObject(1, UUID.class), today());
    }

    private void renewIfDue(UUID ownerId) {
        for (HorsePlanView item : horses(ownerId)) {
            if (item.activePlan() == null || item.nextDueDate() == null
                    || item.nextDueDate().isAfter(today())) continue;
            renewHorseDue(ownerId, item.horseId());
        }
    }

    private boolean renewHorseDue(UUID ownerId, UUID horseId) {
            Map<String, Object> horse = horsePlanRow(ownerId, horseId, true);
            LocalDate due = date(horse.get("service_next_due_date"));
            if (horse.get("active_service_plan") == null || due == null || due.isAfter(today()))
                return false;
            WalletPlan current = WalletPlan.valueOf((String) horse.get("active_service_plan"));
            String nextName = (String) horse.get("next_service_plan");
            WalletPlan plan = HorsePlanPolicy.renewalPlan(current,
                    nextName == null ? null : WalletPlan.valueOf(nextName));
            long daysLate = Math.max(0, ChronoUnit.DAYS.between(due, today()));
            BigDecimal penalty = lateFee(plan.price(), daysLate);
            BigDecimal charge = plan.price().add(penalty);
            BigDecimal balance = (BigDecimal) owner(ownerId, true).get("wallet_balance_vnd");
            if (balance.compareTo(charge) < 0) {
                jdbc.update("update horses set payment_status='UNPAID',"
                                + "status=case when status='ACTIVE' then 'PENDING_PAYMENT' else status end,"
                                + "updated_at=SYSDATETIMEOFFSET(),version=version+1 "
                                + "where id=? and payment_status='PAID'", horseId);
                return false;
            }
            UUID eventId = UUID.randomUUID();
            LocalDate nextDue = today().plusDays(plan.days());
            jdbc.update("update users set wallet_balance_vnd=?,version=version+1,"
                            + "updated_at=SYSDATETIMEOFFSET() where id=?", balance.subtract(charge), ownerId);
            jdbc.update("update horses set active_service_plan=?,next_service_plan=null,"
                            + "service_plan_started_on=?,service_next_due_date=?,payment_status='PAID',paid_until=?,"
                            + "status=case when status='PENDING_PAYMENT' then 'ACTIVE' else status end,"
                            + "updated_at=SYSDATETIMEOFFSET(),version=version+1 where id=?",
                    plan.name(), today(), nextDue, nextDue.minusDays(1), horseId);
            recordPlanEvent(eventId, ownerId, horseId, "SERVICE_PLAN_RENEWAL", plan, due, false);
            ledger(ownerId, "EXPENSE", "SERVICE_FEE", plan.price(), eventId,
                    "Tự gia hạn gói cho ngựa " + horseId);
            if (penalty.signum() > 0) ledger(ownerId, "EXPENSE", "LATE_FEE", penalty, eventId,
                    "Phụ phí quá hạn của ngựa " + horseId);
            notifyUser(ownerId, "wallet-renew:" + horseId + ":" + due, "SERVICE_PLAN",
                    "Gói dịch vụ đã tự gia hạn", "Ví đã thanh toán " + charge.toPlainString() + " VND", horseId);
            notifyManagersForHorseWithoutVet(horseId, eventId);
            audit(ownerId, "SERVICE_PLAN_RENEWAL", eventId, "Tự gia hạn gói " + plan.name());
            return true;
    }

    private void recordPlanEvent(UUID eventId, UUID ownerId, UUID horseId, String type,
                                 WalletPlan plan, LocalDate previousDue, boolean acceptedTerms) {
        Map<String, String> data = new HashMap<>();
        data.put("plan", plan.name());
        if (previousDue != null) data.put("previousDueDate", previousDue.toString());
        if (acceptedTerms) {
            data.put("termsVersion", SERVICE_TERMS_VERSION);
            data.put("termsAcceptedAt", OffsetDateTime.now(clock).toString());
        }
        jdbc.update("insert into module_records(id,module_code,record_type,horse_id,user_id,status,event_date,data_json) "
                        + "values(?,'SYSTEM',?,?,?, ?,?,?)",
                eventId, type, horseId, ownerId,
                "SERVICE_PLAN_TERMS_ACCEPTANCE".equals(type) ? "ACCEPTED" : "PAID",
                today(), json.writeValueAsString(data));
    }

    private Map<String, Object> horsePlanRow(UUID ownerId, UUID horseId, boolean lock) {
        String sql = "select id,active_service_plan,next_service_plan,service_next_due_date "
                + "from horses " + (lock ? "with (UPDLOCK,HOLDLOCK) " : "")
                + "where id=? and archived_at is null and exists(select 1 from horse_ownerships "
                + "where horse_id=horses.id and owner_id=?)";
        List<Map<String, Object>> rows = jdbc.queryForList(sql, horseId, ownerId);
        if (rows.isEmpty()) throw new ResourceNotFoundException("Không tìm thấy ngựa thuộc sở hữu của bạn");
        return rows.getFirst();
    }

    static BigDecimal lateFee(BigDecimal base, long overdueDays) {
        if (overdueDays <= 1) return BigDecimal.ZERO;
        return base.multiply(overdueDays <= 3 ? new BigDecimal("0.10") : new BigDecimal("0.20"))
                .setScale(0, RoundingMode.DOWN);
    }

    static String state(String plan, LocalDate due, LocalDate today) {
        if (plan == null || due == null) return "NO_PLAN";
        long overdue = ChronoUnit.DAYS.between(due, today);
        return overdue <= 0 ? "ACTIVE" : overdue == 1 ? "GRACE"
                : overdue <= 3 ? "LIMITED" : "SUSPENDED";
    }

    private Map<String, Object> owner(UUID ownerId, boolean lock) {
        String sql = "select id,wallet_balance_vnd,active_service_plan,service_plan_started_on,service_next_due_date "
                + "from users " + (lock ? "with (UPDLOCK,HOLDLOCK) " : "") + "where id=? and enabled=1 "
                + "and exists(select 1 from user_roles where user_id=users.id and role='HORSE_OWNER')";
        List<Map<String, Object>> rows = jdbc.queryForList(sql, ownerId);
        if (rows.isEmpty()) throw new ResourceNotFoundException("Không tìm thấy ví của chủ ngựa");
        return rows.getFirst();
    }

    private Map<String, Object> orderRow(UUID id, UUID ownerId, boolean lock) {
        String sql = "select id,user_id,status,data_json,payos_order_code,payos_expected_amount_vnd,"
                + "datediff_big(second,convert(datetimeoffset,'1970-01-01T00:00:00+00:00'),"
                + "switchoffset(payos_expires_at,'+00:00')) payos_expires_epoch "
                + "from module_records " + (lock ? "with (UPDLOCK,HOLDLOCK) " : "")
                + "where id=? and module_code='SYSTEM' and record_type='PAYOS_TOPUP'"
                + (ownerId == null ? "" : " and user_id=?");
        List<Map<String, Object>> rows = ownerId == null
                ? jdbc.query(sql, (rs, n) -> mapOrder(rs), id)
                : jdbc.query(sql, (rs, n) -> mapOrder(rs), id, ownerId);
        if (rows.isEmpty()) throw new ResourceNotFoundException("Không tìm thấy đơn nạp ví");
        return rows.getFirst();
    }

    private Map<String, Object> mapOrder(java.sql.ResultSet rs) throws java.sql.SQLException {
        Map<String, Object> row = new HashMap<>();
        row.put("id", rs.getObject("id", UUID.class));
        row.put("user_id", rs.getObject("user_id", UUID.class));
        row.put("status", rs.getString("status"));
        row.put("data_json", rs.getString("data_json"));
        row.put("payos_order_code", rs.getLong("payos_order_code"));
        row.put("payos_expected_amount_vnd", rs.getBigDecimal("payos_expected_amount_vnd"));
        row.put("payos_expires_at", Instant.ofEpochSecond(rs.getLong("payos_expires_epoch")));
        return row;
    }

    private Instant expiry(Map<String, Object> row) {
        Object value = row.get("payos_expires_at");
        return value instanceof Instant instant ? instant
                : value instanceof OffsetDateTime offset ? offset.toInstant()
                : value instanceof java.sql.Timestamp timestamp ? timestamp.toInstant()
                : ((java.util.Date) value).toInstant();
    }

    private LocalDate date(Object value) {
        return value == null ? null : ((java.sql.Date) value).toLocalDate();
    }

    private LocalDate today() { return LocalDate.now(clock.withZone(clubZone)); }

    private void ledger(UUID ownerId, String type, String category, BigDecimal amount,
                        UUID sourceId, String description) {
        jdbc.update("insert into financial_transactions(id,transaction_date,transaction_type,category,amount,"
                        + "currency,description,source_entity_type,source_entity_id,recorded_by_user_id,wallet_owner_user_id) "
                        + "values(?,?,?,?,?,'VND',?,'WALLET',?,?,?)",
                UUID.randomUUID(), today(), type, category, amount, description, sourceId, ownerId, ownerId);
    }

    private void notifyManagersForHorseWithoutVet(UUID horseId, UUID eventId) {
        Integer missingVet = jdbc.queryForObject("select count(*) from horses where id=? "
                + "and diagnosing_vet_user_id is null", Integer.class, horseId);
        if (missingVet != null && missingVet > 0)
            notifyManagers("wallet-vet:" + horseId + ":" + eventId, "PAID_HORSE_NEEDS_VET",
                    "Ngựa đã thanh toán cần bác sĩ thú y", horseId);
    }

    private void notifyManagers(String key, String type, String title, UUID relatedId) {
        String relatedType = "PAYMENT_REVIEW".equals(type) ? "WALLET" : "HORSE";
        jdbc.update("insert into notifications(id,recipient_user_id,notification_type,priority,title,message,"
                        + "related_entity_type,related_entity_id,deduplication_key) "
                        + "select newid(),ur.user_id,?,'HIGH',?,?, ?,?,concat(?,':',cast(ur.user_id as varchar(36))) "
                        + "from user_roles ur join users u on u.id=ur.user_id where ur.role='CLUB_MANAGER' and u.enabled=1 "
                        + "and not exists(select 1 from notifications n where n.deduplication_key="
                        + "concat(?,':',cast(ur.user_id as varchar(36))))",
                type, title, title, relatedType, relatedId, key, key);
    }

    private void notifyUser(UUID ownerId, String key, String type, String title, String message, UUID relatedId) {
        jdbc.update("insert into notifications(id,recipient_user_id,notification_type,priority,title,message,"
                        + "related_entity_type,related_entity_id,deduplication_key) "
                        + "select newid(),? ,?,'NORMAL',?,?,'WALLET',?,? "
                        + "where not exists(select 1 from notifications where deduplication_key=?)",
                ownerId, type, title, message, relatedId, key, key);
    }

    private void audit(UUID actor, String action, UUID eventId, String description) {
        jdbc.update("insert into audit_logs(actor_user_id,occurred_at,module_code,action_code,entity_type,entity_id,"
                        + "description) values(?,SYSDATETIMEOFFSET(),'ACCESS',?,'WALLET',?,?)",
                actor, action, eventId, description);
    }

    private String paymentProviderName() {
        return gateway.isVnpay() ? "VNPAY" : "PayOS";
    }
}
