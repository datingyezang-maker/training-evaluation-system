package cn.training.evaluation.modules.identity;

import cn.training.evaluation.common.ApiResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;
import javax.servlet.http.HttpServletRequest;
import java.util.*;

/** 登录前后均重新获取 CSRF token，避免会话更新导致旧 token 失效。 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final cn.training.evaluation.modules.training.TrainingService service;
    public AuthController(cn.training.evaluation.modules.training.TrainingService service){this.service=service;}
    @GetMapping("/csrf")
    public ApiResponse<Map<String, String>> csrf(HttpServletRequest request) {
        CsrfToken token = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
        Map<String, String> result = new LinkedHashMap<>();
        result.put("headerName", token.getHeaderName());
        result.put("token", token.getToken());
        return ApiResponse.ok(result);
    }
    @GetMapping("/me")
    public ApiResponse<Map<String, Object>> me(Authentication auth) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.putAll(service.user());
        result.put("permissions",service.permissions(result.get("id")));
        result.put("workspace_allowed",service.workspaceAllowed());
        result.put("authorities", auth.getAuthorities());
        return ApiResponse.ok(result);
    }
    @PostMapping("/password")
    @org.springframework.transaction.annotation.Transactional
    public ApiResponse<?> password(@RequestBody Map<String,Object> data, HttpServletRequest request) {
        Map<String,Object> user=service.user();
        String old=cn.training.evaluation.modules.training.TrainingService.text(data,"old_password",72);
        String next=cn.training.evaluation.modules.training.TrainingService.text(data,"new_password",72);
        if(next.length()<12||next.getBytes(java.nio.charset.StandardCharsets.UTF_8).length>72)throw new cn.training.evaluation.common.BusinessException("新密码至少12位且UTF-8编码不超过72字节");
        String hash=service.db().queryForObject("SELECT password_hash FROM app_user WHERE id=?",String.class,user.get("id"));
        if(!org.springframework.security.crypto.factory.PasswordEncoderFactories.createDelegatingPasswordEncoder().matches(old,hash))throw new cn.training.evaluation.common.BusinessException("原密码错误");
        service.db().update("UPDATE app_user SET password_hash=? WHERE id=?",DatabaseUsers.hash(next),user.get("id"));
        service.audit("PASSWORD_CHANGE","self");request.getSession().invalidate();
        org.springframework.security.core.context.SecurityContextHolder.clearContext();return ApiResponse.ok(null);
    }
}
