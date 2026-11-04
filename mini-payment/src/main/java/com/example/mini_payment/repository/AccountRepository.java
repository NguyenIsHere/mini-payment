package com.example.mini_payment.repository;

import com.example.mini_payment.entity.Account;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface AccountRepository extends JpaRepository<Account, Long> {

    Optional<Account> findByAccountNumber(String accountNumber);

    // Khóa dòng (Pessimistic Write Lock) khi cần đảm bảo an toàn tuyệt đối lúc trừ tiền
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Account> findWithLockByAccountNumber(String accountNumber);

    // PROJECTION: Chỉ map đúng 2 cột này lên RAM, bỏ qua toàn bộ phần còn lại của Account
    interface AccountCursorProjection {
        Long getId();

        String getAccountNumber();
    }

    // KEYSET PAGINATION QUERY: Tìm các dòng có id lớn hơn Cursor hiện tại, xếp tăng dần
    // Việc trả về List thay vì Page giúp Spring bỏ qua câu lệnh đếm tổng số dòng (Count Query) gây chậm DB
    @Query("SELECT a.id as id, a.accountNumber as accountNumber FROM Account a WHERE a.id > :lastId ORDER BY a.id ASC")
    List<AccountCursorProjection> findAccountsWithCursor(@Param("lastId") Long lastId, Pageable pageable);
}