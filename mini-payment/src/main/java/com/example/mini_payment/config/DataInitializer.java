package com.example.mini_payment.config;

import com.example.mini_payment.entity.Account;
import com.example.mini_payment.repository.AccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final AccountRepository accountRepository;

    @Override
    public void run(String... args) {
        if (accountRepository.count() == 0) {
            Account acc1 = Account.builder()
                    .accountNumber("ACC001")
                    .ownerName("Nguyen Tran")
                    .balance(new BigDecimal("5000000.00")) // 5 triệu
                    .build();

            Account acc2 = Account.builder()
                    .accountNumber("ACC002")
                    .ownerName("Tech Company")
                    .balance(new BigDecimal("100000.00")) // 100k
                    .build();

            accountRepository.save(acc1);
            accountRepository.save(acc2);
            System.out.println("Đã khởi tạo 2 tài khoản mẫu: ACC001 (5 triệu) và ACC002 (100k)");
        }
    }
}