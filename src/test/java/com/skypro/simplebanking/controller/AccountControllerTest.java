package com.skypro.simplebanking.controller;

import com.skypro.simplebanking.entity.Account;
import com.skypro.simplebanking.entity.AccountCurrency;
import com.skypro.simplebanking.entity.User;
import com.skypro.simplebanking.repository.AccountRepository;
import com.skypro.simplebanking.repository.UserRepository;
import com.skypro.simplebanking.service.UserService;
import net.minidev.json.JSONObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import static com.skypro.simplebanking.forTests.ForTests.getAuthenticationHeader;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;


@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
public class AccountControllerTest {

    @Container
    private static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:13")
            .withUsername("postgres")
            .withPassword("Anna_098!");

    @DynamicPropertySource
    static void postgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private DataSource dataSource;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private AccountRepository accountRepository;
    @Autowired
    MockMvc mockMvc;
    @Autowired
    private UserService userService;
    @Autowired
    private PasswordEncoder passwordEncoder;

    @AfterEach
    public void cleanData(){
        userRepository.deleteAll();
        accountRepository.deleteAll();
    }

    @BeforeEach
    void addUsersToRepository() {

        User user1 = new User();
        user1.setUsername("Anna");
        user1.setPassword(passwordEncoder.encode("Anna123"));
        userRepository.save(user1);
        createAccounts(user1);

        User user2 = new User();
        user2.setUsername("Oleg");
        user2.setPassword(passwordEncoder.encode("Oleg123"));
        userRepository.save(user2);
        createAccounts(user2);
    }

    void createAccounts(User user) {

        user.setAccounts(new ArrayList<>());
        for (AccountCurrency currency : AccountCurrency.values()) {
            Account account = new Account();
            account.setUser(user);
            account.setAccountCurrency(currency);
            account.setAmount(1500L);
            user.getAccounts().add(account);
            accountRepository.save(account);
        }
    }

    Account getAnyAccount() {
        List<Account> accounts = accountRepository.findAll();
        return accounts.get(2);
    }

    JSONObject getBalanceChangeRequest(Long amount) {
        JSONObject balanceChangeRequest = new JSONObject();
        balanceChangeRequest.put("amount", amount);
        return balanceChangeRequest;
    }

    @Test
    void testPostgresql() throws SQLException {
        try (Connection conn = dataSource.getConnection()) {
            assertThat(conn).isNotNull();
        }
    }

    @DisplayName("Получение данных по id аккаунта и id пользователя ")
    @Test
    void getUserAccountTest_Ok() throws Exception {

        Account account = getAnyAccount();
        User user = account.getUser();

        mockMvc.perform(get("/account/{id}", account.getId())
                        .header(HttpHeaders.AUTHORIZATION,getAuthenticationHeader(user.getUsername(), "Anna123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(account.getId()))
                .andExpect(jsonPath("$.currency").value(account.getAccountCurrency().name()))
                .andExpect(jsonPath("$.amount").value(1500L));

    }

    @DisplayName("Ошибка при авторизации - юзер не найден")
    @Test
    void getUserAccountTest_Unauthorized() throws Exception {

        Account account = getAnyAccount();

        mockMvc.perform(get("/account/{id}", account.getId())
                        .header(HttpHeaders.AUTHORIZATION,
                                getAuthenticationHeader("user", "user")))
                .andExpect(status().isUnauthorized());
    }

    @DisplayName("Ошибка получения данных - не верный id аккаунта")
    @Test
    void getUserAccountTest_NotFoundAccountId() throws Exception {

        Account account = getAnyAccount();
        User user = account.getUser();

        mockMvc.perform(get("/account/{id}", account.getId() + 100)
                        .header(HttpHeaders.AUTHORIZATION,
                                getAuthenticationHeader(user.getUsername(), "Anna123")))
                .andExpect(status().isNotFound());
    }

    @DisplayName("Пополнение счета")
    @Test
    void depositToAccountTest_Ok() throws Exception {

        Account account = getAnyAccount();
        User user = account.getUser();

        mockMvc.perform(post("/account/deposit/{id}", account.getId())
                        .header(HttpHeaders.AUTHORIZATION,
                                getAuthenticationHeader(user.getUsername(), "Anna123"))
                        .content(getBalanceChangeRequest(500L).toString())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amount").value(2000));
    }

    @DisplayName("Пополнение счета - сумма меньше нуля")
    @Test
    void depositToAccountTest_AmountWasNotValidated() throws Exception {

        Account account = getAnyAccount();
        User user = account.getUser();

        mockMvc.perform(post("/account/deposit/{id}", account.getId())
                        .header(HttpHeaders.AUTHORIZATION,
                                getAuthenticationHeader(user.getUsername(), "Anna123"))
                        .content(getBalanceChangeRequest(-1L).toString())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
    }

    @DisplayName("Списание средств со счета")
    @Test
    void withdrawFromAccountTest_Ok () throws Exception {

        Account account = getAnyAccount();
        User user = account.getUser();

        mockMvc.perform(post("/account/withdraw/{id}", account.getId())
                        .header(HttpHeaders.AUTHORIZATION,
                                getAuthenticationHeader(user.getUsername(), "Anna123"))
                        .content(getBalanceChangeRequest(500L).toString())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amount").value(1000));
    }

    @DisplayName("Списание средств со счета - сумма меньше нуля")
    @Test
    void withdrawFromAccountTest_AmountWasNotValidated() throws Exception {

        Account account = getAnyAccount();
        User user = account.getUser();

        mockMvc.perform(post("/account/withdraw/{id}", account.getId())
                        .header(HttpHeaders.AUTHORIZATION,
                                getAuthenticationHeader(user.getUsername(), "Anna123"))
                        .content(getBalanceChangeRequest(-500L).toString())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
    }

    @DisplayName("Списание средств со счета - сумма для списания превышает сумму на счете")
    @Test
    void withdrawFromAccountTest_AmountIsGreater() throws Exception {

        Account account = getAnyAccount();
        User user = account.getUser();

        mockMvc.perform(post("/account/withdraw/{id}", account.getId())
                        .header(HttpHeaders.AUTHORIZATION,
                                getAuthenticationHeader(user.getUsername(), "Anna123"))
                        .content(getBalanceChangeRequest(2000L).toString())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
    }

}
