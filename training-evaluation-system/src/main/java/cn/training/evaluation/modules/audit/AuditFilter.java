package cn.training.evaluation.modules.audit;

import org.slf4j.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import javax.servlet.*;
import javax.servlet.http.*;
import java.io.IOException;
import java.security.Principal;
import org.springframework.security.core.context.SecurityContextHolder;

/** 基础访问审计：只记录动作元数据，绝不保存密码、请求正文和查询参数。业务前后值审计后续补充。 */
@Component
public class AuditFilter extends OncePerRequestFilter {
    private static final Logger LOG = LoggerFactory.getLogger(AuditFilter.class);
    private final JdbcTemplate jdbc;
    public AuditFilter(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Principal before = SecurityContextHolder.getContext().getAuthentication();
        try { chain.doFilter(request, response); }
        finally {
            String path = request.getRequestURI();
            if (path.startsWith("/api/admin/") || path.equals("/api/auth/login") || path.equals("/api/auth/logout")) {
                Principal after = SecurityContextHolder.getContext().getAuthentication();
                String actor = after != null ? after.getName() : before != null ? before.getName() : "anonymous";
                try {
                    jdbc.update("INSERT INTO audit_log(actor, action, path, http_status) VALUES (?, ?, ?, ?)",
                            actor, request.getMethod(), path, response.getStatus());
                } catch (RuntimeException error) {
                    // 当前仅基础访问审计。正式业务写操作需与业务变更在同一事务中写审计，失败则回滚。
                    LOG.error("审计记录失败，需检查数据库状态");
                }
            }
        }
    }
}
