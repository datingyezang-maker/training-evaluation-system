package cn.training.evaluation.modules.identity;

import cn.training.evaluation.common.*;
import cn.training.evaluation.modules.training.TrainingService;
import org.springframework.web.bind.annotation.*;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

/** 仅管理员可为绑定人员档案的账号授权；前端传来的角色和账号编号不参与授权定位。 */
@RestController
@RequestMapping("/api/admin/profile-permissions/{kind}/{id}")
public class ProfilePermissionController {
    private final TrainingService service;
    public ProfilePermissionController(TrainingService service) { this.service=service; }

    private List<Map<String,Object>> accounts(String kind,long id) {
        service.admin();
        if(!Arrays.asList("students","teachers").contains(kind))throw new BusinessException("不支持的人员类型");
        boolean student="students".equals(kind);
        service.one("SELECT id FROM "+(student?"student":"teacher")+" WHERE id=?",id);
        return service.db().queryForList("SELECT id,username,display_name,enabled FROM app_user WHERE role=? AND "+(student?"student_id":"teacher_id")+"=?",student?"STUDENT":"TEACHER",id);
    }

    @GetMapping public ApiResponse<?> get(@PathVariable String kind,@PathVariable long id) {
        List<Map<String,Object>> accounts=accounts(kind,id);
        Map<String,Object> result=new LinkedHashMap<>();
        result.put("account",accounts.isEmpty()?null:accounts.get(0));
        result.put("permissions",accounts.isEmpty()?Collections.emptyList():service.permissions(accounts.get(0).get("id")));
        return ApiResponse.ok(result);
    }

    @PutMapping @Transactional public ApiResponse<?> save(@PathVariable String kind,@PathVariable long id,@RequestBody Map<String,Object> body) {
        List<Map<String,Object>> accounts=accounts(kind,id);
        if(accounts.size()!=1)throw new BusinessException("请先为此人员创建并绑定唯一的登录账号");
        Object raw=body.get("permissions");
        if(!(raw instanceof List))throw new BusinessException("请提供权限列表");
        Set<String> codes=new LinkedHashSet<>();
        for(Object code:(List<?>)raw){if(!(code instanceof String)||!TrainingService.PERSONAL_PERMISSIONS.contains(code))throw new BusinessException("权限项无效");codes.add((String)code);}
        Object userId=accounts.get(0).get("id");
        // 锁定账号后整体替换，避免并发保存产生混合授权。每次请求从数据库读取，因此撤权立即生效。
        service.one("SELECT id FROM app_user WHERE id=? FOR UPDATE",userId);
        service.db().update("DELETE FROM account_permission WHERE user_id=?",userId);
        for(String code:codes)service.db().update("INSERT INTO account_permission(user_id,permission_code) VALUES(?,?)",userId,code);
        service.audit("ACCOUNT_PERMISSIONS",kind+"/"+id+" account/"+userId+" permissions/"+String.join(",",codes));
        return ApiResponse.ok(null);
    }
}
