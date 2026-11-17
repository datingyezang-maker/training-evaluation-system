package cn.training.evaluation.modules.system;

import cn.training.evaluation.common.ApiResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import java.util.*;

/** 数据库连通检查、管理功能目录与基础审计查询。 */
@RestController
public class SystemController {
    private final JdbcTemplate jdbc;
    private final cn.training.evaluation.modules.training.TrainingService training;
    public SystemController(JdbcTemplate jdbc, cn.training.evaluation.modules.training.TrainingService training) { this.jdbc = jdbc; this.training=training; }
    @GetMapping("/api/public/health")
    public ApiResponse<String> health() {
        jdbc.queryForObject("SELECT 1", Integer.class);
        return ApiResponse.ok("UP");
    }
    @GetMapping("/api/admin/modules")
    public ApiResponse<List<Map<String, String>>> modules() {
        training.admin();
        String[][] definitions = {
            {"班次管理", "班次档案、负责人、学员关联"}, {"课程与教师", "课程档案、教师档案、具体授课安排"},
            {"学员管理", "名单维护、Excel 导入与预校验"}, {"评价任务", "模板版本、开放期限、二维码"},
            {"评分采集", "身份核验、防重复提交、回执"}, {"统计报表", "班次、课程、教师统计与 Excel 导出"},
            {"纸质兜底", "PDF 评价表、人工补录与去重"}, {"用户权限", "持久化账号、角色、班次数据范围"}
        };
        List<Map<String, String>> result = new ArrayList<>();
        for (String[] definition : definitions) {
            Map<String, String> item = new LinkedHashMap<>();
            item.put("name", definition[0]); item.put("description", definition[1]); item.put("status", "已接入");
            result.add(item);
        }
        return ApiResponse.ok(result);
    }
    @GetMapping("/api/admin/audit")
    public ApiResponse<List<Map<String, Object>>> audit() {
        training.admin();
        return ApiResponse.ok(jdbc.queryForList("SELECT id, actor, action, path, http_status, created_at FROM audit_log ORDER BY id DESC LIMIT 100"));
    }
}
