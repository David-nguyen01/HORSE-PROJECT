package com.racehorse.wallet;

import com.racehorse.common.exception.BusinessException;
import com.racehorse.common.exception.InvalidInputException;
import com.racehorse.common.exception.ResourceNotFoundException;
import com.racehorse.servicepackage.entity.ServicePackageEntity;
import com.racehorse.servicepackage.repository.ServicePackageRepository;
import org.springframework.beans.factory.annotation.Autowired;
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
import java.util.LinkedHashMap;
import java.util.Locale;

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
    private final ServicePackageRepository packageRepository;

    @Autowired
    public WalletService(JdbcTemplate jdbc, ObjectMapper json, PayosGateway gateway, Clock clock,
                         @Value("${app.club-time-zone}") String clubZone,
                         ServicePackageRepository packageRepository) {
        this.jdbc = jdbc;
        this.json = json;
        this.gateway = gateway;
        this.clock = clock;
        this.clubZone = ZoneId.of(clubZone);
        this.packageRepository = packageRepository;
    }

    public WalletService(JdbcTemplate jdbc, ObjectMapper json, PayosGateway gateway, Clock clock,
                         String clubZone) {
        this(jdbc, json, gateway, clock, clubZone, null);
    }

    public record DiscontinuedExpirySummaryResponse(boolean hasUnresolvedExpiredPackages, String warningMessage) { }

    public record HorsePlanView(UUID horseId, String code, String name, String activePlan,
                                String nextPlan, LocalDate planStartedOn, LocalDate nextDueDate,
                                LocalDate paidUntil, String paymentStatus, String state,
                                BigDecimal renewalAmountVnd,
                                boolean hasDiscontinuedExpiryAlert,
                                String discontinuedPackageCode) {
        public HorsePlanView(UUID horseId, String code, String name, String activePlan,
                             String nextPlan, LocalDate planStartedOn, LocalDate nextDueDate,
                             LocalDate paidUntil, String paymentStatus, String state,
                             BigDecimal renewalAmountVnd) {
            this(horseId, code, name, activePlan, nextPlan, planStartedOn, nextDueDate, paidUntil,
                    paymentStatus, state, renewalAmountVnd, false, null);
        }
    }
    public record WalletView(BigDecimal balanceVnd, List<HorsePlanView> horses,
                             BigDecimal renewalAmountVnd, boolean fakeGateway, boolean vnpayGateway) { }
    public record OrderView(UUID id, long orderCode, BigDecimal amountVnd, String status,
                            Instant expiresAt, String checkoutUrl, String qrCode, boolean fakeGateway,
                            boolean vnpayGateway) { }
    public record PlanQuote(String plan, BigDecimal priceVnd, BigDecimal unusedCreditVnd,
                            BigDecimal lateFeeVnd, BigDecimal walletBalanceVnd,
                            BigDecimal additionalTopUpNeededVnd) { }
    public record PaymentHistoryItem(UUID id, OffsetDateTime createdAt, String category,
                                     String description, BigDecimal amountVnd, String transactionType) { }

    @Transactional(readOnly = true)
    public List<PaymentHistoryItem> paymentHistory(UUID ownerId, int offset, int limit) {
        return jdbc.query("select id,created_at,category,description,amount,transaction_type "
                        + "from financial_transactions "
                        + "where wallet_owner_user_id=? and source_entity_type='WALLET' "
                        + "and category in ('WALLET_TOPUP','SERVICE_FEE','LATE_FEE') "
                        + "order by created_at desc,id desc offset ? rows fetch next ? rows only",
                (rs, index) -> new PaymentHistoryItem(rs.getObject("id", UUID.class),
                        rs.getObject("created_at", OffsetDateTime.class), rs.getString("category"),
                        rs.getString("description"), rs.getBigDecimal("amount"),
                        rs.getString("transaction_type")),
                ownerId, offset, limit);
    }

    @Transactional(readOnly = true)
    public WalletView wallet(UUID ownerId) {
        Map<String, Object> row = owner(ownerId, false);
        List<HorsePlanView> horses = horses(ownerId);
        BigDecimal renewal = horses.stream().map(HorsePlanView::renewalAmountVnd)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new WalletView((BigDecimal) row.get("wallet_balance_vnd"), horses, renewal,
                false, gateway.isVnpay());
    }

    private List<HorsePlanView> horses(UUID ownerId) {
        Map<UUID, String> discontinuedAlerts = resolveDiscontinuedAlertsForOwner(ownerId);
        return jdbc.query("select h.id,h.code,h.name,h.active_service_plan,h.next_service_plan,"
                        + "h.service_plan_started_on,h.service_next_due_date,h.paid_until,h.payment_status "
                        + "from horses h join horse_ownerships ho on ho.horse_id=h.id "
                        + "where ho.owner_id=? and h.archived_at is null "
                        + "order by case when h.service_next_due_date is null then 1 else 0 end,"
                        + "h.service_next_due_date,h.id",
                (rs, index) -> {
                    UUID horseId = rs.getObject("id", UUID.class);
                    String current = rs.getString("active_service_plan");
                    String scheduled = rs.getString("next_service_plan");
                    LocalDate due = rs.getDate("service_next_due_date") == null ? null
                            : rs.getDate("service_next_due_date").toLocalDate();
                    LocalDate paidUntil = rs.getDate("paid_until") == null ? null : rs.getDate("paid_until").toLocalDate();
                    long overdue = due == null ? 0 : Math.max(0, ChronoUnit.DAYS.between(due, today()));
                    String targetCode = scheduled != null && !scheduled.isBlank() ? scheduled : current;
                    BigDecimal planPrice = resolvePlanPriceSafely(targetCode);
                    BigDecimal renewalAmount = BigDecimal.ZERO;
                    if (planPrice != null) {
                        renewalAmount = planPrice.add(lateFee(planPrice, overdue));
                    }
                    boolean hasAlert = discontinuedAlerts.containsKey(horseId);
                    String alertPkgCode = discontinuedAlerts.get(horseId);
                    return new HorsePlanView(horseId, rs.getString("code"),
                            rs.getString("name"), current, scheduled,
                            rs.getDate("service_plan_started_on") == null ? null
                                    : rs.getDate("service_plan_started_on").toLocalDate(), due,
                            paidUntil,
                            rs.getString("payment_status"), state(current, due, today()),
                            renewalAmount,
                            hasAlert,
                            alertPkgCode);
                }, ownerId);
    }

    @Transactional
    public OrderView createTopUp(UUID ownerId, long amountVnd, String ipAddress) {
        if (amountVnd < MIN_TOP_UP.longValue())
            throw new InvalidInputException("Số tiền nạp tối thiểu là 100.000 VND");
        if (!gateway.isVnpay()) {
            throw new BusinessException("Cổng thanh toán VNPAY chưa được kích hoạt trên hệ thống.");
        }
        owner(ownerId, false);
        long code = 100_000_000L + random.nextLong(900_000_000L);
        UUID id = UUID.randomUUID();
        Instant expires = clock.instant().plus(15, ChronoUnit.MINUTES);
        String description = Long.toString(code);
        jdbc.update("insert into module_records(id,module_code,record_type,user_id,status,event_date,data_json,"
                        + "payos_order_code,payos_expected_amount_vnd,payos_expires_at) "
                        + "values(?,'SYSTEM','PAYOS_TOPUP',?,'PENDING',?,'{}',?,?,?)",
                id, ownerId, today(), code, BigDecimal.valueOf(amountVnd), OffsetDateTime.ofInstant(expires, ZoneOffset.UTC));
        PayosGateway.Link link = gateway.create(code, amountVnd, description, expires, ipAddress);
        jdbc.update("update module_records set data_json=?,updated_at=SYSDATETIMEOFFSET() where id=?",
                json.writeValueAsString(Map.of("paymentLinkId", link.paymentLinkId() == null ? "" : link.paymentLinkId(),
                        "checkoutUrl", link.checkoutUrl() == null ? "" : link.checkoutUrl(),
                        "qrCode", link.qrCode() == null ? "" : link.qrCode())), id);
        return new OrderView(id, code, BigDecimal.valueOf(amountVnd), "PENDING", expires,
                link.checkoutUrl(), link.qrCode(), false, gateway.isVnpay());
    }

    @Transactional
    public OrderView order(UUID ownerId, UUID orderId) {
        Map<String, Object> row = orderRow(orderId, ownerId, false);
        JsonNode details = json.readTree((String) row.get("data_json"));
        if ("PENDING".equals(row.get("status")) && clock.instant().isAfter(expiry(row))) {
            jdbc.update("update module_records set status='EXPIRED',updated_at=SYSDATETIMEOFFSET() "
                    + "where id=? and status='PENDING'", orderId);
            row = orderRow(orderId, ownerId, false);
        }
        String checkoutUrl = details.path("checkoutUrl").asText("");
        String qrCode = details.path("qrCode").asText("");
        return new OrderView(orderId, ((Number) row.get("payos_order_code")).longValue(),
                (BigDecimal) row.get("payos_expected_amount_vnd"), (String) row.get("status"),
                expiry(row), checkoutUrl.isBlank() ? null : checkoutUrl,
                qrCode.isBlank() ? null : qrCode, false, gateway.isVnpay());
    }

    @Transactional
    public void acceptWebhook(JsonNode body) {
        if (!gateway.verifyWebhook(body))
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
        complete(ids.getFirst(), data.path("amount").asLong(-1));
    }

    @Transactional
    public Map<String, String> acceptVnpayIpn(Map<String, String> parameters) {
        if (!gateway.isVnpay() || !gateway.verifyVnpay(parameters))
            return Map.of("RspCode", "97", "Message", "Invalid signature");
        try {
            long orderCode = Long.parseLong(parameters.getOrDefault("vnp_TxnRef", ""));
            long amountRaw = Long.parseLong(parameters.getOrDefault("vnp_Amount", ""));
            if (amountRaw <= 0 || amountRaw % 100 != 0)
                return Map.of("RspCode", "04", "Message", "Invalid amount");
            long receivedVnd = amountRaw / 100;

            List<Map<String, Object>> rows = jdbc.queryForList(
                    "select id, status, payos_expected_amount_vnd from module_records "
                            + "where payos_order_code=? and module_code='SYSTEM' and record_type='PAYOS_TOPUP'",
                    orderCode);
            if (rows.isEmpty()) return Map.of("RspCode", "01", "Message", "Order not found");

            Map<String, Object> order = rows.getFirst();
            UUID orderId = UUID.fromString(order.get("id").toString());
            String currentStatus = (String) order.get("status");

            if ("PAID".equals(currentStatus)) {
                return Map.of("RspCode", "02", "Message", "Order already confirmed");
            }

            BigDecimal expected = (BigDecimal) order.get("payos_expected_amount_vnd");
            if (expected.compareTo(BigDecimal.valueOf(receivedVnd)) != 0) {
                notifyManagers("wallet-mismatch:" + orderId, "PAYMENT_REVIEW",
                        "Cần đối soát số tiền đơn nạp ví VNPAY", orderId);
                audit(null, "PAYMENT_REVIEW", orderId,
                        "VNPAY báo số tiền khác đơn: nhận " + receivedVnd + ", mong đợi " + expected);
                return Map.of("RspCode", "04", "Message", "Invalid amount");
            }

            if (!"00".equals(parameters.get("vnp_ResponseCode"))
                    || !"00".equals(parameters.get("vnp_TransactionStatus"))) {
                jdbc.update("update module_records set status='FAILED',updated_at=SYSDATETIMEOFFSET() "
                        + "where id=? and status='PENDING'", orderId);
                return Map.of("RspCode", "00", "Message", "Confirm Success");
            }

            complete(orderId, receivedVnd);
            return Map.of("RspCode", "00", "Message", "Confirm Success");
        } catch (NumberFormatException exception) {
            return Map.of("RspCode", "04", "Message", "Invalid request data");
        }
    }

    private static UUID toUuid(Object obj) {
        if (obj == null) return null;
        if (obj instanceof UUID u) return u;
        if (obj instanceof String s) return UUID.fromString(s);
        return UUID.fromString(obj.toString());
    }

    private void complete(UUID orderId, long receivedVnd) {
        Map<String, Object> order = orderRow(orderId, null, true);
        String status = (String) order.get("status");
        if ("PAID".equals(status)) return;
        UUID ownerId = toUuid(order.get("user_id"));
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
            if ("PENDING".equals(status))
                jdbc.update("update module_records set status='EXPIRED',updated_at=SYSDATETIMEOFFSET() where id=?", orderId);
            return;
        }
        owner(ownerId, true);
        jdbc.update("update users set wallet_balance_vnd=wallet_balance_vnd+?,version=version+1,"
                + "updated_at=SYSDATETIMEOFFSET() where id=?", expected, ownerId);
        ledger(ownerId, "REVENUE", "WALLET_TOPUP", expected, orderId,
                "Nạp ví qua " + paymentProviderName());
        jdbc.update("update module_records set status='PAID',updated_at=SYSDATETIMEOFFSET() where id=?", orderId);
        notifyUser(ownerId, "wallet-topup:" + orderId, "WALLET_TOPUP",
                "Nạp ví thành công", "Ví đã được cộng " + expected.toPlainString() + " VND", orderId);
        renewIfDue(ownerId);
        audit(ownerId, "WALLET_TOPUP", orderId,
                "Ghi có ví từ đơn " + paymentProviderName() + " " + orderId);
    }

    @Transactional(readOnly = true)
    public PlanQuote quote(UUID ownerId, UUID horseId, String packageCode) {
        Map<String, Object> wallet = owner(ownerId, false);
        Map<String, Object> horse = horsePlanRow(ownerId, horseId, false);
        if (horse.get("active_service_plan") != null)
            throw new BusinessException("Ngựa đã có gói; hãy chọn gói cho kỳ thanh toán kế tiếp");
        ServicePackageEntity pkg = resolveActivePackage(packageCode);
        BigDecimal balance = (BigDecimal) wallet.get("wallet_balance_vnd");
        return new PlanQuote(pkg.getCode(), pkg.getPriceVnd(), BigDecimal.ZERO, BigDecimal.ZERO,
                balance, pkg.getPriceVnd().subtract(balance).max(BigDecimal.ZERO));
    }

    @Transactional(readOnly = true)
    public PlanQuote quote(UUID ownerId, UUID horseId, WalletPlan newPlan) {
        return quote(ownerId, horseId, newPlan != null ? newPlan.name() : null);
    }

    @Transactional
    public WalletView purchase(UUID ownerId, UUID horseId, String packageCode, boolean acceptedTerms) {
        requireAcceptedTerms(acceptedTerms);
        ServicePackageEntity pkg = resolveActivePackage(packageCode);
        Map<String, Object> wallet = owner(ownerId, true);
        Map<String, Object> horse = horsePlanRow(ownerId, horseId, true);
        if (horse.get("active_service_plan") != null)
            throw new BusinessException("Ngựa đã có gói; hãy chọn gói cho kỳ thanh toán kế tiếp");
        BigDecimal balance = (BigDecimal) wallet.get("wallet_balance_vnd");
        BigDecimal price = pkg.getPriceVnd();
        int durationDays = pkg.getDurationDays();
        if (balance.compareTo(price) < 0)
            throw new BusinessException("Số dư chưa đủ; cần nạp thêm ít nhất "
                    + price.subtract(balance).toPlainString() + " VND");
        UUID eventId = UUID.randomUUID();
        LocalDate due = today().plusDays(durationDays);
        jdbc.update("update users set wallet_balance_vnd=?,version=version+1,updated_at=SYSDATETIMEOFFSET() where id=?",
                balance.subtract(price), ownerId);
        jdbc.update("update horses set active_service_plan=?,next_service_plan=null,service_plan_started_on=?,"
                        + "service_next_due_date=?,payment_status='PAID',paid_until=?,"
                        + "status=case when status='PENDING_PAYMENT' then 'ACTIVE' else status end,"
                        + "updated_at=SYSDATETIMEOFFSET(),version=version+1 where id=?",
                pkg.getCode(), today(), due, due.minusDays(1), horseId);
        recordPlanPurchaseSnapshot(eventId, ownerId, horseId, "SERVICE_PLAN_PURCHASE",
                pkg, price, today(), due.minusDays(1), due, null, true);
        ledger(ownerId, "EXPENSE", "SERVICE_FEE", price, eventId, "Gói dịch vụ cho ngựa " + horseId);
        notifyUser(ownerId, "wallet-plan:" + eventId, "SERVICE_PLAN", "Gói dịch vụ đã kích hoạt",
                "Gói " + durationDays + " ngày của ngựa có hiệu lực đến " + due.minusDays(1), horseId);
        notifyManagersForHorseWithoutVet(horseId, eventId);
        audit(ownerId, "SERVICE_PLAN_PURCHASE", eventId, "Kích hoạt gói " + pkg.getCode());
        resolveDiscontinuedExpiryAlerts(horseId, pkg.getCode(), eventId, "REPLACEMENT_PACKAGE_ACTIVATED");
        return wallet(ownerId);
    }

    @Transactional
    public WalletView purchase(UUID ownerId, UUID horseId, WalletPlan newPlan, boolean acceptedTerms) {
        return purchase(ownerId, horseId, newPlan != null ? newPlan.name() : null, acceptedTerms);
    }

    @Transactional
    public WalletView scheduleNextPlan(UUID ownerId, UUID horseId, String packageCode, boolean acceptedTerms) {
        requireAcceptedTerms(acceptedTerms);
        owner(ownerId, true);
        Map<String, Object> horse = horsePlanRow(ownerId, horseId, true);
        String current = (String) horse.get("active_service_plan");
        if (current == null) throw new BusinessException("Ngựa chưa có gói; hãy mua gói đầu tiên");
        ServicePackageEntity pkg = resolveActivePackage(packageCode);
        String targetPlan = current.equalsIgnoreCase(pkg.getCode()) ? null : pkg.getCode();
        jdbc.update("update horses set next_service_plan=?,updated_at=SYSDATETIMEOFFSET(),"
                        + "version=version+1 where id=?",
                targetPlan, horseId);
        UUID eventId = UUID.randomUUID();
        recordPlanSchedulingSnapshot(eventId, ownerId, horseId, "SERVICE_PLAN_TERMS_ACCEPTANCE",
                pkg, true);
        return wallet(ownerId);
    }

    @Transactional
    public WalletView scheduleNextPlan(UUID ownerId, UUID horseId, WalletPlan nextPlan, boolean acceptedTerms) {
        return scheduleNextPlan(ownerId, horseId, nextPlan != null ? nextPlan.name() : null, acceptedTerms);
    }

    private ServicePackageEntity resolveActivePackage(String code) {
        if (code == null || code.isBlank()) {
            throw new InvalidInputException("Mã gói dịch vụ không được để trống");
        }
        String normalized = code.trim().toUpperCase(Locale.ROOT);
        if (packageRepository == null) {
            throw new IllegalStateException("ServicePackageRepository is not configured");
        }
        ServicePackageEntity pkg = packageRepository.findByCode(normalized)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy gói dịch vụ với mã: " + code));
        if (!pkg.isActive()) {
            throw new BusinessException("Gói dịch vụ " + pkg.getName() + " (" + code + ") đã được chuyển vào Thư viện dự phòng và không còn khả dụng để đăng ký mới.");
        }
        return pkg;
    }

    private BigDecimal resolvePlanPriceSafely(String code) {
        if (code == null || code.isBlank()) return null;
        String normalized = code.trim().toUpperCase(Locale.ROOT);
        if (packageRepository != null) {
            try {
                var pkgOpt = packageRepository.findByCode(normalized);
                if (pkgOpt.isPresent()) {
                    return pkgOpt.get().getPriceVnd();
                }
            } catch (Exception ignored) {
            }
        }
        try {
            return WalletPlan.valueOf(normalized).price();
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private void recordPlanPurchaseSnapshot(UUID eventId, UUID ownerId, UUID horseId, String type,
                                            ServicePackageEntity pkg, BigDecimal totalChargedVnd,
                                            LocalDate startedOn, LocalDate paidUntil, LocalDate nextDue,
                                            LocalDate previousDue, boolean acceptedTerms) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("eventType", type);
        data.put("packageId", pkg.getId() != null ? pkg.getId().toString() : null);
        data.put("packageCode", pkg.getCode());
        data.put("packageName", pkg.getName());
        data.put("priceVnd", pkg.getPriceVnd());
        data.put("durationDays", pkg.getDurationDays());
        data.put("packageVersion", pkg.getVersion());
        data.put("startedOn", startedOn != null ? startedOn.toString() : null);
        data.put("paidUntil", paidUntil != null ? paidUntil.toString() : null);
        data.put("nextDueDate", nextDue != null ? nextDue.toString() : null);
        if (previousDue != null) data.put("previousDueDate", previousDue.toString());
        data.put("totalChargedVnd", totalChargedVnd);
        data.put("plan", pkg.getCode());
        if (acceptedTerms) {
            data.put("termsVersion", SERVICE_TERMS_VERSION);
            data.put("termsAcceptedAt", OffsetDateTime.now(clock).toString());
        }
        try {
            jdbc.update("insert into module_records(id,module_code,record_type,horse_id,user_id,status,event_date,data_json) "
                            + "values(?,'SYSTEM',?,?,?, ?,?,?)",
                    eventId, type, horseId, ownerId,
                    "SERVICE_PLAN_TERMS_ACCEPTANCE".equals(type) ? "ACCEPTED" : "PAID",
                    today(), json.writeValueAsString(data));
        } catch (Exception e) {
            throw new RuntimeException("Lỗi ghi bản ghi module_records", e);
        }
    }

    private void recordPlanSchedulingSnapshot(UUID eventId, UUID ownerId, UUID horseId, String type,
                                              ServicePackageEntity pkg, boolean acceptedTerms) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("eventType", type);
        data.put("packageId", pkg.getId() != null ? pkg.getId().toString() : null);
        data.put("packageCode", pkg.getCode());
        data.put("packageName", pkg.getName());
        data.put("priceVnd", pkg.getPriceVnd());
        data.put("durationDays", pkg.getDurationDays());
        data.put("packageVersion", pkg.getVersion());
        data.put("plan", pkg.getCode());
        if (acceptedTerms) {
            data.put("termsVersion", SERVICE_TERMS_VERSION);
            data.put("termsAcceptedAt", OffsetDateTime.now(clock).toString());
        }
        try {
            jdbc.update("insert into module_records(id,module_code,record_type,horse_id,user_id,status,event_date,data_json) "
                            + "values(?,'SYSTEM',?,?,?, ?,?,?)",
                    eventId, type, horseId, ownerId, "ACCEPTED",
                    today(), json.writeValueAsString(data));
        } catch (Exception e) {
            throw new RuntimeException("Lỗi ghi bản ghi module_records", e);
        }
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
        Map<String, Object> horse = horsePlanRow(ownerId, horseId, false);
        LocalDate due = date(horse.get("service_next_due_date"));
        if (due != null && due.isAfter(today())) {
            throw new BusinessException("Ngựa chưa đến kỳ gia hạn");
        }
        String currentCode = (String) horse.get("active_service_plan");
        String nextCode = (String) horse.get("next_service_plan");
        String targetCode = (nextCode != null && !nextCode.isBlank()) ? nextCode.trim() : (currentCode != null ? currentCode.trim() : null);
        if (targetCode != null && packageRepository != null) {
            var pkgOpt = packageRepository.findByCode(targetCode.toUpperCase(Locale.ROOT));
            if (pkgOpt.isPresent() && !pkgOpt.get().isActive()) {
                throw new BusinessException("Gói dịch vụ " + pkgOpt.get().getName() + " (" + targetCode + ") đã được chuyển vào Thư viện dự phòng và không thể gia hạn. Vui lòng chọn gói đang hoạt động.");
            }
        }
        if (!renewHorseDue(ownerId, horseId)) {
            throw new BusinessException("Số dư chưa đủ hoặc gói dịch vụ không khả dụng để gia hạn");
        }
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

        String currentCode = (String) horse.get("active_service_plan");
        String nextCode = (String) horse.get("next_service_plan");
        String targetCode = (nextCode != null && !nextCode.isBlank()) ? nextCode.trim() : (currentCode != null ? currentCode.trim() : null);

        if (targetCode == null || targetCode.isBlank()) {
            return false;
        }

        // 1. Resolve package through repository
        ServicePackageEntity pkg = null;
        if (packageRepository != null) {
            pkg = packageRepository.findByCode(targetCode.toUpperCase(Locale.ROOT)).orElse(null);
        }

        // If not found in catalog, cannot safely charge arbitrary amount
        if (pkg == null) {
            jdbc.update("update horses set payment_status='UNPAID',"
                            + "status=case when status='ACTIVE' then 'PENDING_PAYMENT' else status end,"
                            + "updated_at=SYSDATETIMEOFFSET(),version=version+1 "
                            + "where id=? and payment_status='PAID'", horseId);
            return false;
        }

        // 2. Reserve Package Protection
        if (!pkg.isActive()) {
            jdbc.update("update horses set payment_status='UNPAID',"
                            + "status=case when status='ACTIVE' then 'PENDING_PAYMENT' else status end,"
                            + "updated_at=SYSDATETIMEOFFSET(),version=version+1 "
                            + "where id=? and payment_status='PAID'", horseId);
            notifyUser(ownerId, "wallet-reserve:" + horseId + ":" + due, "SERVICE_PLAN_RETIRED",
                    "Gói dịch vụ đã chuyển vào Thư viện dự phòng",
                    "Gói dịch vụ " + pkg.getName() + " (" + pkg.getCode() + ") của ngựa đã ngừng cung cấp. Vui lòng chọn gói đang hoạt động để tiếp tục duy trì dịch vụ.",
                    horseId);
            return false;
        }

        // 3. Policy A: Resolve price and duration from current catalog entity at execution time
        BigDecimal basePrice = pkg.getPriceVnd();
        int durationDays = pkg.getDurationDays();
        long daysLate = Math.max(0, ChronoUnit.DAYS.between(due, today()));
        BigDecimal penalty = lateFee(basePrice, daysLate);
        BigDecimal charge = basePrice.add(penalty);
        BigDecimal balance = (BigDecimal) owner(ownerId, true).get("wallet_balance_vnd");

        // 4. Insufficient Balance Check
        if (balance.compareTo(charge) < 0) {
            jdbc.update("update horses set payment_status='UNPAID',"
                            + "status=case when status='ACTIVE' then 'PENDING_PAYMENT' else status end,"
                            + "updated_at=SYSDATETIMEOFFSET(),version=version+1 "
                            + "where id=? and payment_status='PAID'", horseId);
            return false;
        }

        // 5. Successful Renewal Execution
        UUID eventId = UUID.randomUUID();
        LocalDate nextDue = today().plusDays(durationDays);
        LocalDate newPaidUntil = nextDue.minusDays(1);
        LocalDate previousPaidUntil = date(horse.get("paid_until"));

        jdbc.update("update users set wallet_balance_vnd=?,version=version+1,"
                        + "updated_at=SYSDATETIMEOFFSET() where id=?", balance.subtract(charge), ownerId);
        jdbc.update("update horses set active_service_plan=?,next_service_plan=null,"
                        + "service_plan_started_on=?,service_next_due_date=?,payment_status='PAID',paid_until=?,"
                        + "status=case when status='PENDING_PAYMENT' then 'ACTIVE' else status end,"
                        + "updated_at=SYSDATETIMEOFFSET(),version=version+1 where id=?",
                pkg.getCode(), today(), nextDue, newPaidUntil, horseId);

        // 6. Immutable Snapshot
        recordPlanRenewalSnapshot(eventId, ownerId, horseId, "SERVICE_PLAN_RENEWAL",
                pkg, basePrice, penalty, charge, due, previousPaidUntil, today(), newPaidUntil, nextDue);

        // 7. Ledger Entries
        ledger(ownerId, "EXPENSE", "SERVICE_FEE", basePrice, eventId,
                "Tự gia hạn gói cho ngựa " + horseId);
        if (penalty.signum() > 0) {
            ledger(ownerId, "EXPENSE", "LATE_FEE", penalty, eventId,
                    "Phụ phí quá hạn của ngựa " + horseId);
        }

        // 8. Notifications & Audit
        notifyUser(ownerId, "wallet-renew:" + horseId + ":" + due, "SERVICE_PLAN",
                "Gói dịch vụ đã tự gia hạn", "Ví đã thanh toán " + charge.toPlainString() + " VND", horseId);
        notifyManagersForHorseWithoutVet(horseId, eventId);
        audit(ownerId, "SERVICE_PLAN_RENEWAL", eventId, "Tự gia hạn gói " + pkg.getCode());
        resolveDiscontinuedExpiryAlerts(horseId, pkg.getCode(), eventId, "REPLACEMENT_PACKAGE_ACTIVATED");
        return true;
    }

    private void recordPlanRenewalSnapshot(UUID eventId, UUID ownerId, UUID horseId, String type,
                                            ServicePackageEntity pkg, BigDecimal basePriceVnd,
                                            BigDecimal penaltyVnd, BigDecimal totalChargedVnd,
                                            LocalDate previousDue, LocalDate previousPaidUntil,
                                            LocalDate startedOn, LocalDate newPaidUntil, LocalDate nextDue) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("eventType", type);
        data.put("packageId", pkg.getId() != null ? pkg.getId().toString() : null);
        data.put("packageCode", pkg.getCode());
        data.put("packageName", pkg.getName());
        data.put("priceVnd", basePriceVnd);
        data.put("durationDays", pkg.getDurationDays());
        data.put("packageVersion", pkg.getVersion());
        data.put("previousPaidUntil", previousPaidUntil != null ? previousPaidUntil.toString() : (previousDue != null ? previousDue.minusDays(1).toString() : null));
        data.put("previousDueDate", previousDue != null ? previousDue.toString() : null);
        data.put("renewalStartedOn", startedOn != null ? startedOn.toString() : null);
        data.put("newPaidUntil", newPaidUntil != null ? newPaidUntil.toString() : null);
        data.put("nextDueDate", nextDue != null ? nextDue.toString() : null);
        data.put("penaltyVnd", penaltyVnd != null ? penaltyVnd : BigDecimal.ZERO);
        data.put("totalChargedVnd", totalChargedVnd);
        data.put("plan", pkg.getCode());
        data.put("termsVersion", SERVICE_TERMS_VERSION);
        data.put("termsAcceptedAt", OffsetDateTime.now(clock).toString());

        try {
            jdbc.update("insert into module_records(id,module_code,record_type,horse_id,user_id,status,event_date,data_json) "
                            + "values(?,'SYSTEM',?,?,?, 'PAID',?,?)",
                    eventId, type, horseId, ownerId,
                    today(), json.writeValueAsString(data));
        } catch (Exception e) {
            throw new RuntimeException("Lỗi ghi bản ghi module_records", e);
        }
    }

    private Map<String, Object> horsePlanRow(UUID ownerId, UUID horseId, boolean lock) {
        String sql = "select id,active_service_plan,next_service_plan,service_next_due_date,paid_until "
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
                + "from users " + (lock ? "with (UPDLOCK,HOLDLOCK) " : "") + "where id=? and cast(enabled as varchar(5)) in ('1', 'true', 't') "
                + "and exists(select 1 from user_roles where user_id=users.id and role='HORSE_OWNER')";
        List<Map<String, Object>> rows = jdbc.queryForList(sql, ownerId);
        if (rows.isEmpty()) throw new ResourceNotFoundException("Không tìm thấy ví của chủ ngựa");
        return rows.getFirst();
    }

    private Map<String, Object> orderRow(UUID id, UUID ownerId, boolean lock) {
        String sql = "select id,user_id,status,data_json,payos_order_code,payos_expected_amount_vnd,"
                + "payos_expires_at "
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
        java.sql.Timestamp expiresAt = rs.getTimestamp("payos_expires_at");
        row.put("payos_expires_at", expiresAt == null ? null : expiresAt.toInstant());
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
                        + "from user_roles ur join users u on u.id=ur.user_id where ur.role='CLUB_MANAGER' and cast(u.enabled as varchar(5)) in ('1', 'true', 't') "
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

    @Transactional(readOnly = true)
    public DiscontinuedExpirySummaryResponse discontinuedExpirySummary(UUID ownerId) {
        Map<UUID, String> alerts = resolveDiscontinuedAlertsForOwner(ownerId);
        boolean hasAlert = !alerts.isEmpty();
        String message = hasAlert
                ? "Một hoặc nhiều ngựa của bạn có gói dịch vụ đã ngừng cung cấp và đã hết hạn. Vui lòng kiểm tra mục Ví & Dịch vụ để chọn gói mới."
                : null;
        return new DiscontinuedExpirySummaryResponse(hasAlert, message);
    }

    Map<UUID, String> resolveDiscontinuedAlertsForOwner(UUID ownerId) {
        if (jdbc == null) return Map.of();
        LocalDate today = today();
        Map<UUID, String> result = new HashMap<>();
        try {
            List<Map<String, Object>> rows = jdbc.queryForList(
                    "select mr.horse_id, mr.data_json, h.paid_until, mr.status "
                            + "from dbo.module_records mr "
                            + "join dbo.horse_ownerships ho on ho.horse_id = mr.horse_id "
                            + "join dbo.horses h on h.id = mr.horse_id "
                            + "where mr.module_code = 'SYSTEM' "
                            + "  and mr.record_type = 'DISCONTINUED_HORSE_EXPIRY_ALERT' "
                            + "  and ho.owner_id = ? "
                            + "  and h.archived_at is null "
                            + "  and mr.status in ('PENDING_EXPIRY', 'EXPIRED')",
                    ownerId);

            for (Map<String, Object> row : rows) {
                UUID horseId = toUuid(row.get("horse_id"));
                String status = (String) row.get("status");
                LocalDate paidUntil = null;
                Object paidUntilObj = row.get("paid_until");
                if (paidUntilObj instanceof java.sql.Date d) {
                    paidUntil = d.toLocalDate();
                } else if (paidUntilObj instanceof LocalDate ld) {
                    paidUntil = ld;
                }
                boolean isExpired = "EXPIRED".equals(status) || (paidUntil != null && paidUntil.isBefore(today));
                if (isExpired) {
                    String pkgCode = null;
                    try {
                        String jsonStr = (String) row.get("data_json");
                        if (json != null && jsonStr != null) {
                            JsonNode node = json.readTree(jsonStr);
                            if (node.has("packageCode")) {
                                pkgCode = node.get("packageCode").asText();
                            }
                        }
                    } catch (Exception ignored) {}
                    result.put(horseId, pkgCode);
                }
            }
        } catch (Exception ignored) {}
        return result;
    }

    private void resolveDiscontinuedExpiryAlerts(UUID horseId, String packageCode, UUID resolvingPeriodId, String resolutionType) {
        if (jdbc == null) return;
        OffsetDateTime now = OffsetDateTime.now(clock);
        List<Map<String, Object>> rows = jdbc.queryForList(
                "select id, data_json from dbo.module_records "
                        + "where module_code = 'SYSTEM' "
                        + "  and record_type = 'DISCONTINUED_HORSE_EXPIRY_ALERT' "
                        + "  and horse_id = ? "
                        + "  and status in ('PENDING_EXPIRY', 'EXPIRED')",
                horseId);

        for (Map<String, Object> row : rows) {
            UUID id = toUuid(row.get("id"));
            String dataJsonStr = (String) row.get("data_json");
            Map<String, Object> data = new LinkedHashMap<>();
            try {
                if (json != null && dataJsonStr != null) {
                    data = json.readValue(dataJsonStr, Map.class);
                }
            } catch (Exception ignored) {}
            data.put("resolvedAt", now.toString());
            data.put("resolutionType", resolutionType);
            data.put("resolvingPackageCode", packageCode);
            data.put("resolvingPeriodId", resolvingPeriodId != null ? resolvingPeriodId.toString() : null);

            String updatedJson;
            try {
                updatedJson = json != null ? json.writeValueAsString(data) : dataJsonStr;
            } catch (Exception e) {
                updatedJson = dataJsonStr;
            }

            jdbc.update("update dbo.module_records set status = 'RESOLVED', data_json = ?, updated_at = SYSDATETIMEOFFSET() where id = ?",
                    updatedJson, id);
        }
    }
}
