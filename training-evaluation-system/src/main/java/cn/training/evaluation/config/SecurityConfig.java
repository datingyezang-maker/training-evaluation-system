package cn.training.evaluation.config;

import cn.training.evaluation.common.ApiResponse;
import cn.training.evaluation.modules.audit.AuditFilter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.security.web.authentication.logout.LogoutFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.*;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/** 同源 Session 认证、数据库账号状态检查和 CSRF 防护。 */
@Configuration
@EnableGlobalMethodSecurity(prePostEnabled = true)
public class SecurityConfig {
    @Bean
    public SecurityFilterChain security(HttpSecurity http, ObjectMapper mapper, AuditFilter audit, AccountStateFilter accountState, cn.training.evaluation.modules.identity.DatabaseUsers users) throws Exception {
        http.addFilterAfter(accountState, org.springframework.security.web.context.SecurityContextPersistenceFilter.class);
        http.addFilterBefore(audit, LogoutFilter.class);
        http.authorizeRequests()
                .antMatchers("/", "/index.html", "/admin", "/admin/**", "/h5", "/h5/**", "/account", "/account/**", "/assets/**").permitAll()
                .antMatchers(HttpMethod.GET, "/api/auth/csrf", "/api/auth/captcha", "/api/auth/verification-options", "/api/public/**").permitAll()
                .antMatchers(HttpMethod.POST,"/api/auth/register").permitAll()
                .antMatchers(HttpMethod.POST, "/api/public/submit").permitAll()
                .antMatchers("/api/admin/**").hasRole("ADMIN")
                .antMatchers("/api/business/**").access("@trainingService.workspaceAllowed()")
                .antMatchers("/api/portal/**").hasAnyRole("STUDENT","TEACHER")
                .anyRequest().authenticated()
            .and().formLogin().loginProcessingUrl("/api/auth/login")
                .successHandler((req, res, auth) -> {users.succeeded(auth.getName());req.getSession().setAttribute("accountHash", users.loadUserByUsername(auth.getName()).getPassword());write(mapper, res, 200, ApiResponse.ok(auth.getName()));})
                .failureHandler((req, res, ex) -> {users.failed(req.getParameter("username"));write(mapper, res, 401, ApiResponse.error("LOGIN_FAILED", "账号或密码错误，或账号已停用/暂时锁定"));})
            .and().logout().logoutUrl("/api/auth/logout")
                .logoutSuccessHandler((req, res, auth) -> write(mapper, res, 200, ApiResponse.ok(null)))
            .and().exceptionHandling()
                .authenticationEntryPoint((req, res, ex) -> write(mapper, res, 401, ApiResponse.error("UNAUTHORIZED", "请先登录")))
                .accessDeniedHandler((req, res, ex) -> write(mapper, res, 403, ApiResponse.error("FORBIDDEN", "权限不足或请求校验失败")));
        return http.build();
    }
    @Bean public FilterRegistrationBean<AccountStateFilter> accountRegistration(AccountStateFilter filter){FilterRegistrationBean<AccountStateFilter> r=new FilterRegistrationBean<>(filter);r.setEnabled(false);return r;}
    /** 审计只在安全链内执行一次，才能在上下文清理前读取当前用户。 */
    @Bean
    public FilterRegistrationBean<AuditFilter> auditRegistration(AuditFilter filter) {
        FilterRegistrationBean<AuditFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }
    private static void write(ObjectMapper mapper, HttpServletResponse response, int status, Object body) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        mapper.writeValue(response.getWriter(), body);
    }
}
