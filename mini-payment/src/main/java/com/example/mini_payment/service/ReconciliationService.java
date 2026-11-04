package com.example.mini_payment.service;

import com.example.mini_payment.entity.Account;
import com.example.mini_payment.entity.TransactionStatus;
import com.example.mini_payment.repository.AccountRepository;
import com.example.mini_payment.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReconciliationService {

    private final Executor taskExecutor;
    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;

    public void runDailyReconciliation() {
        log.info("Bắt đầu đối soát tài chính đa luồng (Keyset Pagination)...");

        int pageSize = 1000;
        long lastId = 0L; // Bắt đầu bằng 0, duyệt từ record đầu tiên
        int batchCount = 0;

        List<CompletableFuture<Void>> futures = new ArrayList<>();

        while (true) {
            // Chỉ dùng PageRequest để lấy LIMIT 1000, luôn ép lấy từ đầu (trang 0) của mốc Cursor
            PageRequest limit = PageRequest.of(0, pageSize);

            // Gọi DB với mốc lastId hiện tại
            List<AccountRepository.AccountCursorProjection> chunk =
                    accountRepository.findAccountsWithCursor(lastId, limit);

            // Nếu DB không trả về dòng nào thì thoát vòng lặp
            if (chunk.isEmpty()) {
                break;
            }

            // CẬP NHẬT CURSOR: Lấy ID của phần tử cuối cùng trong danh sách làm mốc cho vòng lặp sau
            lastId = chunk.get(chunk.size() - 1).getId();

            // Bóc tách danh sách AccountNumber đưa cho Thread xử lý
            List<String> accountNumbers = chunk.stream()
                    .map(AccountRepository.AccountCursorProjection::getAccountNumber)
                    .toList();

            String batchName = "Batch-" + batchCount++;

            // Thẩy vào Thread Pool
            CompletableFuture<Void> future = CompletableFuture.runAsync(
                    () -> processBatch(accountNumbers, batchName),
                    taskExecutor
            );
            futures.add(future);

            // TỐI ƯU THOÁT VÒNG LẶP: Nếu lấy lên được ít hơn 1000 dòng, chắc chắn đây là mẻ cuối cùng
            if (chunk.size() < pageSize) {
                break;
            }
        }

        try {
            // Chờ tất cả các Batch xử lý xong
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
            log.info("Hoàn tất đối soát toàn bộ hệ thống gồm {} batch!", futures.size());
        } catch (Exception e) {
            log.error("Có lỗi nghiêm trọng trong quá trình chờ đối soát tổng!", e);
        }
    }

    // Đánh dấu @Transactional(readOnly = true) để tối ưu hiệu năng vì chỉ truy vấn (SELECT)
    @Transactional(readOnly = true)
    public void processBatch(List<String> accountNumbers, String batchName) {
        log.info("Thread [{}] đang tính toán đối soát cho {}", Thread.currentThread().getName(), batchName);

        for (String accNumber : accountNumbers) {
            try {
                Account account = accountRepository.findByAccountNumber(accNumber)
                        .orElse(null);

                if (account == null) {
                    log.warn("[{}] Bỏ qua đối soát, không tìm thấy tài khoản: {}", batchName, accNumber);
                    continue;
                }

                // 1. Tính tổng tiền nhận (Incoming)
                BigDecimal totalIn = transactionRepository.sumAmountByTargetAccountNumberAndStatus(
                        accNumber, TransactionStatus.SUCCESS);
                if (totalIn == null) totalIn = BigDecimal.ZERO;

                // 2. Tính tổng tiền gửi (Outgoing)
                BigDecimal totalOut = transactionRepository.sumAmountBySourceAccountNumberAndStatus(
                        accNumber, TransactionStatus.SUCCESS);
                if (totalOut == null) totalOut = BigDecimal.ZERO;

                // 3. Công thức đối soát: Số dư hiện tại có khớp với (Nhận - Gửi) hay không?
                // (Giả định hệ thống PoC bắt đầu từ 0đ. Nếu có nạp tiền thì cộng thêm 'totalDeposit')
                BigDecimal expectedBalance = totalIn.subtract(totalOut);

                if (account.getBalance().compareTo(expectedBalance) == 0) {
                    log.info("[{}] TÀI KHOẢN {} KHỚP: {}", batchName, accNumber, account.getBalance());
                } else {
                    log.error("[{}] LỆCH SỔ TÀI KHOẢN {}: Thực tế DB={}, Tính toán={}",
                            batchName, accNumber, account.getBalance(), expectedBalance);
                    // Thực tế: Lưu thông tin lệch sổ vào bảng ReconciliationError để con người (Kế toán) xử lý
                }

            } catch (Exception e) {
                // Bắt buộc catch ở đây để nếu 1 tài khoản lỗi, nó không làm chết cả Batch
                log.error("[{}] Lỗi hệ thống khi đối soát tài khoản {}", batchName, accNumber, e);
            }
        }
    }
}