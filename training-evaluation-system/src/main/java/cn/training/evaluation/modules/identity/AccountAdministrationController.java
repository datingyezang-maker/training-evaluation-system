package cn.training.evaluation.modules.identity;

import cn.training.evaluation.common.*;
import cn.training.evaluation.modules.training.TrainingService;
import org.springframework.web.bind.annotation.*;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

/** 账号审核与解锁独立于普通资料编辑，避免误操作绕过审核。 */
@RestController
@RequestMapping("/api/business/accounts")
public class AccountAdministrationController {
 private final TrainingService service;
 public AccountAdministrationController(TrainingService service){this.service=service;}
 @PostMapping("/{id}/review") @Transactional public ApiResponse<?> review(@PathVariable long id,@RequestBody Map<String,Object> data){
  service.admin();String result=TrainingService.text(data,"result",20),reason=TrainingService.text(data,"reason",200);if(!Arrays.asList("APPROVED","REJECTED").contains(result))throw new BusinessException("审核结果无效");
  Map<String,Object> account=service.one("SELECT * FROM app_user WHERE id=? FOR UPDATE",id);if(!"PENDING".equals(account.get("review_status")))throw new BusinessException("该账号已处理，请刷新列表");
  service.db().update("UPDATE app_user SET review_status=?,review_reason=?,reviewed_by=?,reviewed_at=CURRENT_TIMESTAMP,enabled=? WHERE id=?",result,reason,service.user().get("username"),"APPROVED".equals(result),id);service.audit("ACCOUNT_REVIEW","users/"+id+" "+result+" 原因："+reason);return ApiResponse.ok(null);
 }
 @PostMapping("/{id}/unlock") @Transactional public ApiResponse<?> unlock(@PathVariable long id,@RequestBody Map<String,Object> data){service.admin();service.one("SELECT id FROM app_user WHERE id=? FOR UPDATE",id);String reason=TrainingService.text(data,"reason",200);service.db().update("UPDATE app_user SET login_failures=0,locked_until=NULL WHERE id=?",id);service.audit("ACCOUNT_UNLOCK","users/"+id+" 原因："+reason);return ApiResponse.ok(null);}
 @PostMapping("/{id}/reset-password") @Transactional public ApiResponse<?> reset(@PathVariable long id,@RequestBody Map<String,Object> data){service.admin();service.one("SELECT id FROM app_user WHERE id=? FOR UPDATE",id);String reason=TrainingService.text(data,"reason",200),password=PhoneAccounts.password(data.get("password"));service.db().update("UPDATE app_user SET password_hash=?,login_failures=0,locked_until=NULL WHERE id=?",DatabaseUsers.hash(password),id);service.audit("PASSWORD_RESET","users/"+id+" 原因："+reason);return ApiResponse.ok(null);}
}
