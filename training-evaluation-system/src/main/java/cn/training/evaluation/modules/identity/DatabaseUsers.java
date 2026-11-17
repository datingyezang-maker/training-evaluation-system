package cn.training.evaluation.modules.identity;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import javax.annotation.PostConstruct;
import java.util.*;

/** 用户和 BCrypt 摘要持久化；启动密码只用于首次初始化，不覆盖已修改密码。 */
@Service
public class DatabaseUsers implements UserDetailsService {
    private final JdbcTemplate jdbc;
    private final String initialPassword;
    public DatabaseUsers(JdbcTemplate jdbc, @Value("${app.security.admin-password}") String password) {
        this.jdbc = jdbc; this.initialPassword = password;
    }
    @PostConstruct
    public void initialize() {
        if (jdbc.queryForObject("SELECT COUNT(*) FROM app_user", Integer.class) == 0) {
            if (initialPassword == null || initialPassword.length() < 12 || initialPassword.getBytes(java.nio.charset.StandardCharsets.UTF_8).length>72) throw new IllegalStateException("首次启动需设置至少12位且不超过72字节的管理员密码");
            jdbc.update("INSERT INTO app_user(username,password_hash,role,enabled) VALUES(?,?,?,TRUE)", "admin", hash(initialPassword), "ADMIN");
        }
    }
    public static String hash(String password) { return "{bcrypt}" + new BCryptPasswordEncoder().encode(password); }
    @Override public UserDetails loadUserByUsername(String username) {
        List<Map<String,Object>> rows = jdbc.queryForList("SELECT * FROM app_user WHERE username=?", username);
        if (rows.isEmpty()) throw new UsernameNotFoundException("账号不存在");
        Map<String,Object> row = rows.get(0);
        java.sql.Timestamp locked=(java.sql.Timestamp)row.get("locked_until");
        return User.withUsername(username).password((String) row.get("password_hash")).roles((String) row.get("role"))
                .disabled(!Boolean.parseBoolean(row.get("enabled").toString())||!"APPROVED".equals(row.get("review_status"))).accountLocked(locked!=null&&locked.after(new java.util.Date())).build();
    }
    /** 连续失败5次锁定15分钟，统一失败提示避免泄露账户存在性。 */
    @org.springframework.transaction.annotation.Transactional
    public void failed(String name){
        if(name==null||name.length()>80)return;
        jdbc.update("UPDATE app_user SET login_failures=CASE WHEN locked_until IS NOT NULL AND locked_until<CURRENT_TIMESTAMP THEN 1 ELSE login_failures+1 END WHERE username=?",name);
        jdbc.update("UPDATE app_user SET locked_until=? WHERE username=? AND login_failures>=5",new java.sql.Timestamp(System.currentTimeMillis()+15*60*1000),name);
    }
    public void succeeded(String name){jdbc.update("UPDATE app_user SET login_failures=0,locked_until=NULL WHERE username=?",name);}
}
