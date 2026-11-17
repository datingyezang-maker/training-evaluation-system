package cn.training.evaluation;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** 验证安全边界、迁移和真实登录链路，不只测试返回值结构。 */
@SpringBootTest(properties = {"app.security.admin-password=integration-only-password", "spring.datasource.url=jdbc:h2:mem:foundation;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE", "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa", "spring.datasource.password="})
@AutoConfigureMockMvc
class FoundationIntegrationTest {
    @Test void workspaceAssetsAreAvailableBeforeLogin()throws Exception{
        mvc.perform(get("/assets/workspace-ui.css")).andExpect(status().isOk());
        mvc.perform(get("/assets/workspace-ui.js")).andExpect(status().isOk());
        mvc.perform(get("/assets/experience.css")).andExpect(status().isOk());
        mvc.perform(get("/assets/experience.js")).andExpect(status().isOk());
        mvc.perform(get("/account/")).andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("自有移动端与人员中心视觉规范")));
    }
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Test void directoryEntrancesForwardToExistingPages() throws Exception {
        for (String path : new String[]{"/admin", "/admin/"}) {
            mvc.perform(get(path)).andExpect(status().isOk()).andExpect(forwardedUrl("/admin/index.html"));
        }
        for (String path : new String[]{"/h5", "/h5/"}) {
            mvc.perform(get(path)).andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("<style>"))).andExpect(content().string(org.hamcrest.Matchers.containsString("new AbortController()")));
        }
        mvc.perform(get("/admin/index.html")).andExpect(status().isOk());
        mvc.perform(get("/h5/index.html")).andExpect(status().isOk());
    }
    @Test void publicHealthChecksDatabase() throws Exception {
        mvc.perform(get("/api/public/health")).andExpect(status().isOk()).andExpect(jsonPath("$.data").value("UP"));
    }
    @Test void anonymousCannotReadAdminData() throws Exception {
        mvc.perform(get("/api/admin/modules")).andExpect(status().isUnauthorized());
    }
    @Test void ordinaryUserCannotReadAdminData() throws Exception {
        if(jdbc.queryForObject("SELECT COUNT(*) FROM app_user WHERE username='business'",Integer.class)==0)jdbc.update("INSERT INTO app_user(username,password_hash,role,enabled) VALUES('business','unused','BUSINESS',TRUE)");
        mvc.perform(get("/api/admin/modules").with(user("business").roles("BUSINESS"))).andExpect(status().isForbidden());
    }
    @Test void loginRequiresCsrfAndCorrectPassword() throws Exception {
        mvc.perform(post("/api/auth/login").param("username", "admin").param("password", "integration-only-password")).andExpect(status().isForbidden());
        mvc.perform(post("/api/auth/login").with(csrf()).param("username", "admin").param("password", "wrong")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/login").with(csrf()).param("username", "admin").param("password", "integration-only-password")).andExpect(status().isOk());
    }
    @Test void adminCanReadModulesAndAudit() throws Exception {
        mvc.perform(get("/api/admin/modules").with(user("admin").roles("ADMIN"))).andExpect(status().isOk()).andExpect(jsonPath("$.data[0].status").value("已接入"));
        mvc.perform(get("/api/admin/audit").with(user("admin").roles("ADMIN"))).andExpect(status().isOk());
        assertTrue(jdbc.queryForObject("SELECT COUNT(*) FROM audit_log WHERE actor = 'admin'", Integer.class) > 0);
    }
}
