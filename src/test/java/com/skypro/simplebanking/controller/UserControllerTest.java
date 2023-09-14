package com.skypro.simplebanking.controller;

import com.skypro.simplebanking.repository.AccountRepository;
import com.skypro.simplebanking.repository.UserRepository;
import com.skypro.simplebanking.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

import static com.skypro.simplebanking.forTests.ForTests.createUser;
import static com.skypro.simplebanking.forTests.ForTests.getAuthenticationHeader;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;


@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
public class UserControllerTest {


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

    @BeforeEach
    public void cleanData(){
        userRepository.deleteAll();
//        accountRepository.deleteAll();
    }

    private void addUserToRepository() {
        userService.createUser("Anna", "Anna123");
    }

    void addThreeUsersToRepository() {
        userService.createUser("Anna", "Anna123");
        userService.createUser("Oleg", "Oleg123");
        userService.createUser("Ivan", "ivan123");
    }

    @Test
    void testPostgresql() throws SQLException {
        try (Connection conn = dataSource.getConnection()) {
            assertThat(conn).isNotNull();
        }
    }
//Почему-то тест проходит и при роли Админа, и при при роли Юзера
    @DisplayName("Создание нового пользователя Админом")
    @Test
    @WithMockUser(roles = "ADMIN")
    void createUserTest_Ok() throws Exception {

        mockMvc.perform(post("/user")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createUser().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username", is("Anna")));
    }

    @DisplayName("Создание нового пользователя Юзером")
    @Test
    @WithMockUser(roles = "USER")
    public void createUserTest_ByUser() throws Exception {
        mockMvc.perform(post("/user/")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createUser().toString()))
                .andExpect(status().isForbidden());
    }

    @DisplayName("Ошибка при попытки создания уже существующего пользователя")
    @Test
    @WithMockUser(roles = "ADMIN")
    public void createUserTest_UserExists() throws Exception {

        addUserToRepository();

        mockMvc.perform(post("/user/")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createUser().toString()))
                .andExpect(status().isBadRequest());
    }

    @DisplayName("Получение всех пользователей")
    @Test
    @WithMockUser(roles = "USER")
    void getAllUsersTest_Ok() throws Exception {
        mockMvc.perform(get("/user/list"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());

        addThreeUsersToRepository();

        mockMvc.perform(get("/user/list"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(3));
    }

    @DisplayName("Попытка получить всех пользователей Админом")
    @Test
    @WithMockUser(roles = "ADMIN")
    void getAllUsersTest_ByAdmin() throws Exception {
        mockMvc.perform(get("/user/list"))
                .andExpect(status().isForbidden());
    }

    @DisplayName("Получение профиля пользователя")
    @Test
    @WithMockUser(roles = "USER")
    void getMyProfileTest_Ok() throws Exception {

        addThreeUsersToRepository();

        mockMvc.perform(get("/user/me")
                        .header(HttpHeaders.AUTHORIZATION,
                                getAuthenticationHeader("Anna", "Anna123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("Anna"));
    }

}
