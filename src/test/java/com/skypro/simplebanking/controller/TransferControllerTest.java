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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
public class TransferControllerTest {


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

    User getFirstUser(){
        List<User> users = userRepository.findAll();
        return users.get(0);
    }

    User getSecondUser(){
        List<User> users = userRepository.findAll();
        return users.get(1);
    }

    List<Account> getAccountsUser(User user){
        return accountRepository.findAll().stream()
                .filter(a -> a.getUser().getUsername().equals(user.getUsername()))
                .toList();
    }

    JSONObject getTransferRequest(Long fromAccountId, Long toUserId, Long toAccountId, Long amount) {
        JSONObject transferRequest = new JSONObject();
        transferRequest.put("fromAccountId", fromAccountId);
        transferRequest.put("toUserId", toUserId);
        transferRequest.put("toAccountId", toAccountId);
        transferRequest.put("amount", amount);
        return transferRequest;
    }

    @Test
    void testPostgresql() throws SQLException {
        try (Connection conn = dataSource.getConnection()) {
            assertThat(conn).isNotNull();
        }
    }

    @DisplayName("Перевод средств между счетами разных пользователей")
    @Test
    void transferTest_Ok() throws Exception {

        User fromUser = getFirstUser();
        List<Account> fromUserAccounts = getAccountsUser(fromUser);
        Account fromAccount = fromUserAccounts.get(2);

        User toUser= getSecondUser();
        List<Account> toUserAccounts = getAccountsUser(toUser);
        Account toAccount = toUserAccounts.get(2);

        JSONObject transfer = getTransferRequest(fromAccount.getId(), toUser.getId(), toAccount.getId(), 500L);

        mockMvc.perform(post("/transfer")
                        .header(HttpHeaders.AUTHORIZATION,
                                getAuthenticationHeader(fromUser.getUsername(), "Anna123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(transfer.toString()))
                .andExpect(status().isOk());
    }

    @DisplayName("Перевод средств между счетами разных пользователей - разные валюты")
    @Test
    void transferTest_IncorrectAccountCurrency() throws Exception {

        User fromUser = getFirstUser();
        List<Account> fromUserAccounts = getAccountsUser(fromUser);
        Account fromAccount = fromUserAccounts.get(2);

        User toUser= getSecondUser();
        List<Account> toUserAccounts = getAccountsUser(toUser);
        Account toAccount = toUserAccounts.get(1);

        JSONObject transfer = getTransferRequest(fromAccount.getId(), toUser.getId(), toAccount.getId(), 500L);

        mockMvc.perform(post("/transfer")
                        .header(HttpHeaders.AUTHORIZATION,
                                getAuthenticationHeader(fromUser.getUsername(), "Anna123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(transfer.toString()))
                .andExpect(status().isBadRequest());
    }

    @DisplayName("Перевод средств между счетами разных пользователей - неверная сумма")
    @Test
    void transferTest_IncorrectAmount() throws Exception {

        User fromUser = getFirstUser();
        List<Account> fromUserAccounts = getAccountsUser(fromUser);
        Account fromAccount = fromUserAccounts.get(2);

        User toUser= getSecondUser();
        List<Account> toUserAccounts = getAccountsUser(toUser);
        Account toAccount = toUserAccounts.get(2);

        JSONObject transfer = getTransferRequest(fromAccount.getId(), toUser.getId(), toAccount.getId(), -100L);

        mockMvc.perform(post("/transfer")
                        .header(HttpHeaders.AUTHORIZATION,
                                getAuthenticationHeader(fromUser.getUsername(), "Anna123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(transfer.toString()))
                .andExpect(status().isBadRequest());

        JSONObject transfer2 = getTransferRequest(fromAccount.getId(), toUser.getId(), toAccount.getId(), 2000L);

        mockMvc.perform(post("/transfer")
                        .header(HttpHeaders.AUTHORIZATION,
                                getAuthenticationHeader(fromUser.getUsername(), "Anna123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(transfer2.toString()))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(String.format("Cannot withdraw %d %s", 2000, fromAccount.getAccountCurrency().name())));
    }

    @DisplayName("Перевод средств между счетами разных пользователей - неверный id аккаунта")
    @Test
    void transferTest_IncorrectIdAccount() throws Exception {

        User fromUser = getFirstUser();
        List<Account> fromUserAccounts = getAccountsUser(fromUser);
        Account fromAccount = fromUserAccounts.get(2);

        User toUser= getSecondUser();
        List<Account> toUserAccounts = getAccountsUser(toUser);
        Account toAccount = toUserAccounts.get(2);

        JSONObject transfer = getTransferRequest(fromAccount.getId(), toUser.getId(), toAccount.getId()+20, 500L);

        mockMvc.perform(post("/transfer")
                        .header(HttpHeaders.AUTHORIZATION,
                                getAuthenticationHeader(fromUser.getUsername(), "Anna123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(transfer.toString()))
                .andExpect(status().isNotFound());

        JSONObject transfer2 = getTransferRequest(fromAccount.getId()+20, toUser.getId(), toAccount.getId(), 2000L);

        mockMvc.perform(post("/transfer")
                        .header(HttpHeaders.AUTHORIZATION,
                                getAuthenticationHeader(fromUser.getUsername(), "Anna123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(transfer2.toString()))
                .andExpect(status().isNotFound());

    }
}
