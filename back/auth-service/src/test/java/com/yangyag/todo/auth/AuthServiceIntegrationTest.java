package com.yangyag.todo.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItems;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yangyag.todo.auth.entity.User;
import com.yangyag.todo.auth.entity.UserRole;
import com.yangyag.todo.auth.repository.RefreshTokenRepository;
import com.yangyag.todo.auth.repository.UserRepository;
import com.yangyag.todo.auth.service.TokenHashService;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthServiceIntegrationTest {

    private static final String ADMIN_PASSWORD = "AdminPass123!";
    private static final String USER_PASSWORD = "UserPass123!";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private TokenHashService tokenHashService;

    private User adminUser;
    private User regularUser;

    @BeforeEach
    void setUp() {
        refreshTokenRepository.deleteAll();
        userRepository.deleteAll();
        adminUser = persistUser("admin", ADMIN_PASSWORD, "Admin User", UserRole.ADMIN, true);
        regularUser = persistUser("member", USER_PASSWORD, "Member User", UserRole.USER, true);
    }

    @Test
    void loginReturnsAccessAndRefreshTokensForValidCredentials() throws Exception {
        TokenPair tokens = login("admin", ADMIN_PASSWORD);

        assertThat(tokens.accessToken()).isNotBlank();
        assertThat(tokens.refreshToken()).isNotBlank();
        assertThat(tokens.accessToken()).isNotEqualTo(tokens.refreshToken());
        assertThat(refreshTokenRepository.findByTokenHash(tokenHashService.hash(tokens.refreshToken()))).isPresent();
    }

    @Test
    void loginRejectsWrongPassword() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(APPLICATION_JSON)
                        .content(json(Map.of(
                                "loginId", "admin",
                                "password", "wrong-password"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").isNotEmpty());
    }

    @Test
    void refreshIssuesNewTokenPairAfterLogin() throws Exception {
        TokenPair initialTokens = login("admin", ADMIN_PASSWORD);

        MvcResult result = mockMvc.perform(post("/api/auth/refresh")
                        .contentType(APPLICATION_JSON)
                        .content(json(Map.of("refreshToken", initialTokens.refreshToken()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isString())
                .andExpect(jsonPath("$.refreshToken").isString())
                .andReturn();

        TokenPair refreshedTokens = readTokens(result);
        assertThat(refreshedTokens.accessToken()).isNotBlank();
        assertThat(refreshedTokens.refreshToken()).isNotBlank();
        assertThat(refreshedTokens.refreshToken()).isNotEqualTo(initialTokens.refreshToken());
        assertThat(refreshTokenRepository.count()).isEqualTo(1);
        assertThat(refreshTokenRepository.findByTokenHash(tokenHashService.hash(initialTokens.refreshToken()))).isEmpty();
        assertThat(refreshTokenRepository.findByTokenHash(tokenHashService.hash(refreshedTokens.refreshToken()))).isPresent();
    }

    @Test
    void refreshRejectsInvalidToken() throws Exception {
        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(APPLICATION_JSON)
                        .content(json(Map.of("refreshToken", "not-a-valid-refresh-token"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").isNotEmpty());
    }

    @Test
    void logoutRevokesRefreshToken() throws Exception {
        TokenPair tokens = login("admin", ADMIN_PASSWORD);

        mockMvc.perform(post("/api/auth/logout")
                        .contentType(APPLICATION_JSON)
                        .content(json(Map.of("refreshToken", tokens.refreshToken()))))
                .andExpect(status().isNoContent());

        assertThat(refreshTokenRepository.findByTokenHash(tokenHashService.hash(tokens.refreshToken()))).isEmpty();

        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(APPLICATION_JSON)
                        .content(json(Map.of("refreshToken", tokens.refreshToken()))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").isNotEmpty());
    }

    @Test
    void meRequiresAuthenticationAndReturnsCurrentUser() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").isNotEmpty());

        TokenPair tokens = login("admin", ADMIN_PASSWORD);

        mockMvc.perform(get("/api/auth/me")
                        .header(HttpHeaders.AUTHORIZATION, bearer(tokens.accessToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(adminUser.getId().toString()))
                .andExpect(jsonPath("$.loginId").value("admin"))
                .andExpect(jsonPath("$.name").value("Admin User"))
                .andExpect(jsonPath("$.role").value("admin"))
                .andExpect(jsonPath("$.isActive").value(true));
    }

    @Test
    void adminCanCreateListAndDeleteUsers() throws Exception {
        TokenPair adminTokens = login("admin", ADMIN_PASSWORD);

        MvcResult createResult = mockMvc.perform(post("/api/auth/users")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminTokens.accessToken()))
                        .contentType(APPLICATION_JSON)
                        .content(json(Map.of(
                                "loginId", "created-user",
                                "password", "CreatedPass123!",
                                "name", "Created User",
                                "role", "user",
                                "isActive", true))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isString())
                .andExpect(jsonPath("$.loginId").value("created-user"))
                .andExpect(jsonPath("$.name").value("Created User"))
                .andExpect(jsonPath("$.role").value("user"))
                .andExpect(jsonPath("$.isActive").value(true))
                .andReturn();

        UUID createdUserId = UUID.fromString(objectMapper.readTree(createResult.getResponse().getContentAsString())
                .get("id")
                .asText());

        mockMvc.perform(get("/api/auth/users")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminTokens.accessToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].loginId", hasItems("admin", "member", "created-user")));

        mockMvc.perform(delete("/api/auth/users/{userId}", createdUserId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminTokens.accessToken())))
                .andExpect(status().isNoContent());

        assertThat(userRepository.existsById(createdUserId)).isFalse();
    }

    @Test
    void nonAdminCannotAccessAdminEndpoints() throws Exception {
        TokenPair userTokens = login("member", USER_PASSWORD);

        mockMvc.perform(get("/api/auth/users")
                        .header(HttpHeaders.AUTHORIZATION, bearer(userTokens.accessToken())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").isNotEmpty());

        mockMvc.perform(post("/api/auth/users")
                        .header(HttpHeaders.AUTHORIZATION, bearer(userTokens.accessToken()))
                        .contentType(APPLICATION_JSON)
                        .content(json(Map.of(
                                "loginId", "blocked-user",
                                "password", "BlockedPass123!",
                                "name", "Blocked User",
                                "role", "user",
                                "isActive", true))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").isNotEmpty());

        mockMvc.perform(delete("/api/auth/users/{userId}", adminUser.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(userTokens.accessToken())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").isNotEmpty());
    }

    private User persistUser(String loginId, String rawPassword, String name, UserRole role, boolean active) {
        User user = new User();
        user.setLoginId(loginId);
        user.setPasswordHash(passwordEncoder.encode(rawPassword));
        user.setName(name);
        user.setRole(role);
        user.setActive(active);
        return userRepository.save(user);
    }

    private TokenPair login(String loginId, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(APPLICATION_JSON)
                        .content(json(Map.of(
                                "loginId", loginId,
                                "password", password))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isString())
                .andExpect(jsonPath("$.refreshToken").isString())
                .andReturn();

        return readTokens(result);
    }

    private TokenPair readTokens(MvcResult result) throws Exception {
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        return new TokenPair(
                body.get("accessToken").asText(),
                body.get("refreshToken").asText());
    }

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }

    private String bearer(String accessToken) {
        return "Bearer " + accessToken;
    }

    private record TokenPair(String accessToken, String refreshToken) {
    }
}
