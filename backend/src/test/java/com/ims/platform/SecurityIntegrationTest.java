package com.ims.platform;

import com.fasterxml.jackson.databind.ObjectMapper;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SecurityIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    @Test
    void protectsUserApi() throws Exception {
        mvc.perform(get("/api/users/me"))
            .andExpect(status().isUnauthorized())
            .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void permitsCustomerRegistration() throws Exception {
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"email":"new@example.com","password":"a-secure-password","displayName":"New User"}
                """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.accessToken").isString());
    }

    @Test
    void permitsConfiguredCorsOrigin() throws Exception {
        mvc.perform(options("/api/plans")
                .header("Origin","http://localhost:5173")
                .header("Access-Control-Request-Method","GET"))
            .andExpect(status().isOk())
            .andExpect(header().string("Access-Control-Allow-Origin","http://localhost:5173"))
            .andExpect(header().string("Access-Control-Allow-Methods",org.hamcrest.Matchers.containsString("GET")));
    }

    @Test
    void preventsCustomersFromUsingAdminEndpoints() throws Exception {
        String body=mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"email":"customer-role@example.com","password":"a-secure-password","displayName":"Customer"}
                    """))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
        String accessToken=json.readTree(body).path("accessToken").asText();

        mvc.perform(get("/api/users").header("Authorization","Bearer "+accessToken))
            .andExpect(status().isForbidden())
            .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void allowsAuthenticatedPasswordChange() throws Exception {
        String body=mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"email":"pwd-change@example.com","password":"old-password-12","displayName":"Pwd User"}
                    """))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
        String accessToken=json.readTree(body).path("accessToken").asText();

        mvc.perform(post("/api/users/me/password").header("Authorization","Bearer "+accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"currentPassword":"old-password-12","newPassword":"new-password-99"}
                    """))
            .andExpect(status().isNoContent());

        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"email":"pwd-change@example.com","password":"new-password-99"}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.accessToken").isString());
    }
}
