package com.racehorse.wallet.service;

import com.racehorse.common.exception.BusinessException;
import com.racehorse.common.exception.ResourceNotFoundException;
import com.racehorse.horse.entity.Horse;
import com.racehorse.horse.repository.HorseOwnershipRepository;
import com.racehorse.horse.repository.HorseRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Service xử lý nghiệp vụ ví và thanh toán phí dịch vụ.
 * <p>
 * Dữ liệu ví được lưu trong bảng module_records:
 * - module_code = 'SYSTEM'
 * - record_type = 'WALLET_TRANSACTION'
 * - user_id = owner ID
 * - data_json chứa: type (DEPOSIT/SERVICE_FEE/LATE_FEE), amount, balance_after, description, horseId
 * <p>
 * Phí dịch vụ: 500.000 VNĐ / 30 ngày.
 * Phụ phí quá hạn: 10% nếu quá hạn 1-30 ngày, 20% nếu quá hạn > 30 ngày.
 */
@Service
public class WalletService {

    /** Phí dịch vụ chăm sóc cơ bản: 500.000 VNĐ / 30 ngày */
    private static final BigDecimal BASE_SERVICE_FEE = new BigDecimal("500000");
    /** Số ngày mỗi kỳ thanh toán */
    private static final int SERVICE_PERIOD_DAYS = 30;

    private final JdbcTemplate jdbc;
    private final HorseRepository horseRepository;
    private final HorseOwnershipRepository ownershipRepository;
    private final ObjectMapper json;

    public WalletService(
            JdbcTemplate jdbc,
            HorseRepository horseRepository,
            HorseOwnershipRepository ownershipRepository,
            ObjectMapper json) {
        this.jdbc = jdbc;
        this.horseRepository = horseRepository;
        this.ownershipRepository = ownershipRepository;
        this.json = json;
    }

    // ─── XEM SỐ DƯ ──────────────────────────────────────────────

    @Transactional(readOnly = true)
    public BalanceView getBalance(UUID ownerId) {
        BigDecimal balance = computeBalance(ownerId);
        return new BalanceView(ownerId, balance, "VND");
    }

    // ─── NẠP TIỀN ────────────────────────────────────────────────

    @Transactional
    public TransactionView deposit(UUID ownerId, BigDecimal amount, String description) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("Số tiền nạp phải lớn hơn 0");
        }

        BigDecimal currentBalance = computeBalance(ownerId);
        BigDecimal newBalance = currentBalance.add(amount);

        UUID txId = UUID.randomUUID();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("type", "DEPOSIT");
        data.put("amount", amount);
        data.put("balanceBefore", currentBalance);
        data.put("balanceAfter", newBalance);
        data.put("description", description != null ? description.trim() : "Nạp tiền vào ví");

        jdbc.update(
                "insert into module_records(id,module_code,record_type,source_table,user_id,status,data_json,created_at,updated_at) "
                        + "values(?,'SYSTEM','WALLET_TRANSACTION','api',?,'COMPLETED',?,SYSDATETIMEOFFSET(),SYSDATETIMEOFFSET())",
                txId, ownerId, toJson(data));

        return new TransactionView(txId, "DEPOSIT", amount, newBalance,
                description != null ? description.trim() : "Nạp tiền vào ví",
                null, OffsetDateTime.now());
    }

    // ─── THANH TOÁN PHÍ DỊCH VỤ ─────────────────────────────────

    /**
     * Thanh toán phí dịch vụ cho ngựa.
     * <ul>
     *   <li>Tính phụ phí nếu quá hạn: +10% (1-30 ngày), +20% (>30 ngày)</li>
     *   <li>Trừ tiền từ ví</li>
     *   <li>Cập nhật paymentStatus → PAID, paidUntil += 30 ngày</li>
     *   <li>Nếu ngựa PENDING_PAYMENT → chuyển sang PENDING_MEDICAL (dùng paymentStatus)</li>
     * </ul>
     */
    @Transactional
    public PaymentResultView payServiceFee(UUID ownerId, UUID horseId) {
        // 1. Kiểm tra quyền sở hữu
        Horse horse = horseRepository.findDetailById(horseId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy ngựa: " + horseId));
        if (!ownershipRepository.existsByHorseIdAndOwnerId(horseId, ownerId)) {
            throw new ResourceNotFoundException("Bạn không sở hữu ngựa này");
        }

        // 2. Kiểm tra: ngựa phải ở trạng thái PENDING_PAYMENT hoặc PAID (gia hạn)
        String currentPaymentStatus = horse.getPaymentStatus();
        if ("UNPAID".equalsIgnoreCase(currentPaymentStatus)) {
            throw new BusinessException("Ngựa chưa được gửi đăng ký. Vui lòng gửi hồ sơ trước (Submit).");
        }

        // 3. Tính phí
        BigDecimal fee = BASE_SERVICE_FEE;
        BigDecimal lateFee = BigDecimal.ZERO;
        String feeBreakdown = "Phí cơ bản: " + fee + " VNĐ";

        LocalDate today = LocalDate.now();
        LocalDate paidUntil = horse.getPaidUntil();
        if (paidUntil != null && paidUntil.isBefore(today)) {
            long daysOverdue = java.time.temporal.ChronoUnit.DAYS.between(paidUntil, today);
            if (daysOverdue > 30) {
                lateFee = fee.multiply(new BigDecimal("0.20")).setScale(0, RoundingMode.UP);
                feeBreakdown += " + Phụ phí quá hạn 20%: " + lateFee + " VNĐ";
            } else if (daysOverdue > 0) {
                lateFee = fee.multiply(new BigDecimal("0.10")).setScale(0, RoundingMode.UP);
                feeBreakdown += " + Phụ phí quá hạn 10%: " + lateFee + " VNĐ";
            }
        }

        BigDecimal totalFee = fee.add(lateFee);

        // 4. Kiểm tra số dư ví
        BigDecimal currentBalance = computeBalance(ownerId);
        if (currentBalance.compareTo(totalFee) < 0) {
            throw new BusinessException("Số dư ví không đủ. Cần: " + totalFee + " VNĐ, hiện có: " + currentBalance + " VNĐ");
        }

        // 5. Trừ tiền: ghi giao dịch SERVICE_FEE
        BigDecimal newBalance = currentBalance.subtract(totalFee);
        UUID txId = UUID.randomUUID();
        Map<String, Object> txData = new LinkedHashMap<>();
        txData.put("type", "SERVICE_FEE");
        txData.put("amount", totalFee.negate()); // số âm = chi
        txData.put("baseFee", fee);
        txData.put("lateFee", lateFee);
        txData.put("balanceBefore", currentBalance);
        txData.put("balanceAfter", newBalance);
        txData.put("horseId", horseId.toString());
        txData.put("horseName", horse.getName());
        txData.put("description", feeBreakdown);

        jdbc.update(
                "insert into module_records(id,module_code,record_type,source_table,horse_id,user_id,status,data_json,created_at,updated_at) "
                        + "values(?,'SYSTEM','WALLET_TRANSACTION','api',?,?,'COMPLETED',?,SYSDATETIMEOFFSET(),SYSDATETIMEOFFSET())",
                txId, horseId, ownerId, toJson(txData));

        // 6. Ghi giao dịch phụ phí riêng (nếu có) để rõ ràng
        if (lateFee.compareTo(BigDecimal.ZERO) > 0) {
            UUID lateTxId = UUID.randomUUID();
            Map<String, Object> lateData = new LinkedHashMap<>();
            lateData.put("type", "LATE_FEE");
            lateData.put("amount", lateFee.negate());
            lateData.put("balanceBefore", currentBalance);
            lateData.put("balanceAfter", newBalance);
            lateData.put("horseId", horseId.toString());
            lateData.put("description", "Phụ phí quá hạn thanh toán ngựa " + horse.getName());

            // Phụ phí đã được tính trong SERVICE_FEE ở trên, nên đây chỉ là bản ghi audit
            jdbc.update(
                    "insert into module_records(id,module_code,record_type,source_table,horse_id,user_id,status,data_json,created_at,updated_at) "
                            + "values(?,'SYSTEM','WALLET_LATE_FEE_AUDIT','api',?,?,'COMPLETED',?,SYSDATETIMEOFFSET(),SYSDATETIMEOFFSET())",
                    lateTxId, horseId, ownerId, toJson(lateData));
        }

        // 7. Cập nhật trường payment trên Horse (cùng tồn tại với cơ chế cũ)
        LocalDate newPaidUntil;
        if (paidUntil != null && paidUntil.isAfter(today)) {
            // Gia hạn: cộng thêm 30 ngày từ ngày hết hạn hiện tại
            newPaidUntil = paidUntil.plusDays(SERVICE_PERIOD_DAYS);
        } else {
            // Lần đầu hoặc đã hết hạn: tính từ hôm nay
            newPaidUntil = today.plusDays(SERVICE_PERIOD_DAYS);
        }

        horse.setPaymentStatus("PAID");
        horse.setPaidUntil(newPaidUntil);
        horseRepository.save(horse);

        return new PaymentResultView(
                txId, horseId, horse.getName(), totalFee, fee, lateFee,
                newBalance, newPaidUntil, feeBreakdown, "PAID");
    }

    // ─── LỊCH SỬ GIAO DỊCH ──────────────────────────────────────

    @Transactional(readOnly = true)
    public List<TransactionView> getTransactions(UUID ownerId) {
        return jdbc.query(
                "select id, data_json, created_at from module_records "
                        + "where module_code='SYSTEM' and record_type='WALLET_TRANSACTION' and user_id=? "
                        + "order by created_at desc",
                (rs, n) -> {
                    UUID id = rs.getObject("id", UUID.class);
                    OffsetDateTime createdAt = rs.getObject("created_at", OffsetDateTime.class);
                    @SuppressWarnings("unchecked")
                    Map<String, Object> data = readMap(rs.getString("data_json"));
                    String type = (String) data.getOrDefault("type", "UNKNOWN");
                    BigDecimal amount = new BigDecimal(String.valueOf(data.getOrDefault("amount", "0")));
                    BigDecimal balanceAfter = new BigDecimal(String.valueOf(data.getOrDefault("balanceAfter", "0")));
                    String desc = (String) data.getOrDefault("description", "");
                    String horseIdStr = (String) data.get("horseId");
                    UUID txHorseId = horseIdStr != null ? UUID.fromString(horseIdStr) : null;
                    return new TransactionView(id, type, amount, balanceAfter, desc, txHorseId, createdAt);
                },
                ownerId
        );
    }

    // ─── HÀM TÍNH SỐ DƯ ─────────────────────────────────────────

    /**
     * Tính số dư ví bằng cách lấy balanceAfter của giao dịch cuối cùng.
     * Nếu chưa có giao dịch nào → số dư = 0.
     */
    private BigDecimal computeBalance(UUID ownerId) {
        List<BigDecimal> balances = jdbc.query(
                "select top 1 JSON_VALUE(data_json,'$.balanceAfter') as balance "
                        + "from module_records where module_code='SYSTEM' and record_type='WALLET_TRANSACTION' "
                        + "and user_id=? order by created_at desc",
                (rs, n) -> {
                    String val = rs.getString("balance");
                    return val != null ? new BigDecimal(val) : BigDecimal.ZERO;
                },
                ownerId
        );
        return balances.isEmpty() ? BigDecimal.ZERO : balances.getFirst();
    }

    // ─── VIEWS (DTO trả về) ──────────────────────────────────────

    public record BalanceView(UUID ownerId, BigDecimal balance, String currency) {
    }

    public record TransactionView(
            UUID id, String type, BigDecimal amount, BigDecimal balanceAfter,
            String description, UUID horseId, OffsetDateTime createdAt
    ) {
    }

    public record PaymentResultView(
            UUID transactionId, UUID horseId, String horseName,
            BigDecimal totalFee, BigDecimal baseFee, BigDecimal lateFee,
            BigDecimal newBalance, LocalDate paidUntil,
            String feeBreakdown, String newPaymentStatus
    ) {
    }

    // ─── JSON HELPERS ────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private Map<String, Object> readMap(String value) {
        try {
            return new LinkedHashMap<>(json.readValue(value, Map.class));
        } catch (JacksonException e) {
            throw new IllegalStateException("Dữ liệu JSON ví không hợp lệ", e);
        }
    }

    private String toJson(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (JacksonException e) {
            throw new IllegalStateException("Không thể tạo dữ liệu JSON ví", e);
        }
    }
}
