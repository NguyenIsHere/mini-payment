package com.example.mini_payment.controller;

import com.example.mini_payment.dto.CreateAccountRequestDTO;
import com.example.mini_payment.entity.Account;
import com.example.mini_payment.exception.GlobalExceptionHandler;
import com.example.mini_payment.service.AccountService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.math.BigDecimal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class AccountControllerTest {

    private MockMvc mockMvc;

    @Mock
    private AccountService accountService;

    @InjectMocks
    private AccountController accountController;

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();

        mockMvc = MockMvcBuilders.standaloneSetup(accountController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();
    }

    @Test
    @DisplayName("POST /api/accounts returns 201 Created for valid payload")
    void createAccount_ValidPayload_Returns201Created() throws Exception {
        Account created = Account.builder()
                .id(1L)
                .accountNumber("ACC001")
                .ownerName("Nguyen Tran")
                .balance(new BigDecimal("100000.00"))
                .build();

        when(accountService.createAccount(any(CreateAccountRequestDTO.class))).thenReturn(created);

        String requestJson = """
                {
                  "accountNumber": "ACC001",
                  "ownerName": "Nguyen Tran",
                  "initialBalance": 100000.00
                }
                """;

        mockMvc.perform(post("/api/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accountNumber").value("ACC001"))
                .andExpect(jsonPath("$.ownerName").value("Nguyen Tran"))
                .andExpect(jsonPath("$.balance").value(100000.00));
    }

    @Test
    @DisplayName("POST /api/accounts returns 400 Bad Request when accountNumber is blank")
    void createAccount_BlankAccountNumber_Returns400BadRequest() throws Exception {
        String invalidJson = """
                {
                  "accountNumber": "",
                  "ownerName": "Nguyen Tran",
                  "initialBalance": 100000.00
                }
                """;

        mockMvc.perform(post("/api/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidJson))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(accountService);
    }

    @Test
    @DisplayName("POST /api/accounts returns 400 Bad Request when account already exists")
    void createAccount_DuplicateAccount_Returns400BadRequest() throws Exception {
        when(accountService.createAccount(any(CreateAccountRequestDTO.class)))
                .thenThrow(new IllegalArgumentException("Account number already exists: ACC001"));

        String requestJson = """
                {
                  "accountNumber": "ACC001",
                  "ownerName": "Nguyen Tran",
                  "initialBalance": 100000.00
                }
                """;

        mockMvc.perform(post("/api/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Invalid data"))
                .andExpect(jsonPath("$.message").value("Account number already exists: ACC001"));
    }

    @Test
    @DisplayName("GET /api/accounts/{accountNumber} returns 200 OK with account details")
    void getAccount_ExistingAccount_Returns200Ok() throws Exception {
        Account account = Account.builder()
                .id(1L)
                .accountNumber("ACC001")
                .ownerName("Nguyen Tran")
                .balance(new BigDecimal("250000.00"))
                .build();

        when(accountService.getAccountByNumber("ACC001")).thenReturn(account);

        mockMvc.perform(get("/api/accounts/ACC001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountNumber").value("ACC001"))
                .andExpect(jsonPath("$.balance").value(250000.00));
    }
}
