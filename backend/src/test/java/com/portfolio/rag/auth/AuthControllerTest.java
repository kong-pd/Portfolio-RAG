package com.portfolio.rag.auth;

import com.portfolio.rag.auth.dto.LoginResponse;
import com.portfolio.rag.common.ApiException;
import com.portfolio.rag.config.JwtAuthenticationFilter;
import com.portfolio.rag.config.SecurityConfig;
import com.portfolio.rag.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class})
class AuthControllerTest {

    private static final String VALID_TOKEN = "valid-token";
    private static final long USER_ID = 7L;

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AuthService authService;
    @MockBean
    private JwtService jwtService;
    @MockBean
    private UserRepository userRepository; // required by SecurityConfig#userDetailsService

    @BeforeEach
    void stubJwt() {
        given(jwtService.validateDetailed(anyString()))
                .willReturn(new JwtService.ValidationResult(null, JwtService.ERROR_UNAUTHORIZED));
        given(jwtService.validateDetailed(VALID_TOKEN))
                .willReturn(new JwtService.ValidationResult(USER_ID, null));
    }

    // ------------------------------------------------------------------
    // register
    // ------------------------------------------------------------------

    @Test
    void register_validBody_returns201() throws Exception {
        given(authService.register("new@example.com", "password123")).willReturn(1L);

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"new@example.com\",\"password\":\"password123\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userId").value(1));
    }

    @Test
    void register_invalidEmail_returns400() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"not-an-email\",\"password\":\"password123\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void register_shortPassword_returns400() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"new@example.com\",\"password\":\"short\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void register_duplicateEmail_returns409WithCode() throws Exception {
        willThrow(new ApiException(HttpStatus.CONFLICT, "EMAIL_ALREADY_EXISTS", "该邮箱已被注册"))
                .given(authService).register("dup@example.com", "password123");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"dup@example.com\",\"password\":\"password123\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_EXISTS"));
    }

    // ------------------------------------------------------------------
    // login
    // ------------------------------------------------------------------

    @Test
    void login_validCredentials_returns200WithTokens() throws Exception {
        given(authService.login("u@example.com", "password123"))
                .willReturn(new LoginResponse("access-token", "refresh-token-uuid"));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"u@example.com\",\"password\":\"password123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("access-token"))
                .andExpect(jsonPath("$.refreshToken").value("refresh-token-uuid"));
    }

    @Test
    void login_wrongCredentials_returns401() throws Exception {
        willThrow(new ApiException(HttpStatus.UNAUTHORIZED, "BAD_CREDENTIALS", "邮箱或密码错误"))
                .given(authService).login("u@example.com", "wrongpassword");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"u@example.com\",\"password\":\"wrongpassword\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("BAD_CREDENTIALS"));
    }

    // ------------------------------------------------------------------
    // refresh
    // ------------------------------------------------------------------

    @Test
    void refresh_validToken_returns200() throws Exception {
        String refreshToken = UUID.randomUUID().toString();
        given(authService.refresh(refreshToken))
                .willReturn(new LoginResponse("new-access-token", refreshToken));

        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + refreshToken + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("new-access-token"))
                .andExpect(jsonPath("$.refreshToken").value(refreshToken));
    }

    @Test
    void refresh_invalidToken_returns401() throws Exception {
        willThrow(new ApiException(HttpStatus.UNAUTHORIZED, "REFRESH_TOKEN_INVALID", "刷新令牌无效或已过期"))
                .given(authService).refresh("bad-token");

        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"bad-token\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("REFRESH_TOKEN_INVALID"));
    }

    // ------------------------------------------------------------------
    // logout
    // ------------------------------------------------------------------

    @Test
    void logout_authenticated_returns204() throws Exception {
        String refreshToken = UUID.randomUUID().toString();

        mockMvc.perform(post("/api/auth/logout")
                        .header("Authorization", "Bearer " + VALID_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + refreshToken + "\"}"))
                .andExpect(status().isNoContent());

        verify(authService).logout(USER_ID, refreshToken);
    }

    @Test
    void logout_unauthenticated_returns401() throws Exception {
        mockMvc.perform(post("/api/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + UUID.randomUUID() + "\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void logout_expiredToken_returns401WithTokenExpiredCode() throws Exception {
        given(jwtService.validateDetailed("expired-token"))
                .willReturn(new JwtService.ValidationResult(null, JwtService.ERROR_TOKEN_EXPIRED));

        mockMvc.perform(post("/api/auth/logout")
                        .header("Authorization", "Bearer expired-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + UUID.randomUUID() + "\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("TOKEN_EXPIRED"));
    }
}
