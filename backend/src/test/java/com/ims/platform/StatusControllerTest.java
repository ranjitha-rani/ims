package com.ims.platform;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class StatusControllerTest {
    @Autowired MockMvc mvc;

    @Test
    void publicStatusIsAccessibleWithoutAuth() throws Exception {
        mvc.perform(get("/api/public/status"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("UP"))
            .andExpect(jsonPath("$.api").value("UP"))
            .andExpect(jsonPath("$.database").value("UP"))
            .andExpect(jsonPath("$.redis").value("DISABLED"))
            .andExpect(jsonPath("$.kafka").value("DISABLED"))
            .andExpect(jsonPath("$.timestamp").isString());
    }
}
