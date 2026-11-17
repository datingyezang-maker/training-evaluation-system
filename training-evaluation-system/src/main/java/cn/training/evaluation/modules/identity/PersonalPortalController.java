package cn.training.evaluation.modules.identity;

import cn.training.evaluation.common.*;
import cn.training.evaluation.modules.training.TrainingService;
import cn.training.evaluation.modules.evaluation.EvaluationService;
import org.springframework.web.bind.annotation.*;
import java.util.*;

/** 人员端由数据库关联档案决定范围，不能通过请求参数切换成其他学员或教师。 */
@RestController
@RequestMapping("/api/portal")
public class PersonalPortalController {
 private final TrainingService service;private final EvaluationService evaluation;
 public PersonalPortalController(TrainingService service,EvaluationService evaluation){this.service=service;this.evaluation=evaluation;}
 /** 教师只能查看本人课程的汇总；尚未结束的任务不提前公开评分，不返回个人答卷。 */
 @GetMapping("/lessons/{id}/feedback") public ApiResponse<?> feedback(@PathVariable long id){
  Map<String,Object> user=service.user();
  if(!"TEACHER".equals(user.get("role")))throw new org.springframework.security.access.AccessDeniedException("仅教师可查看授课反馈");
  Map<String,Object> lesson=service.one("SELECT l.id,l.name lesson_name,l.taught_at,c.name class_name,t.name teacher_name,l.teacher_id FROM lesson l JOIN training_class c ON c.id=l.class_id JOIN teacher t ON t.id=l.teacher_id WHERE l.id=?",id);
  if(!Objects.equals(lesson.get("teacher_id"),user.get("teacher_id")))throw new org.springframework.security.access.AccessDeniedException("仅可查看本人授课反馈");
  String finished=" AND (t.status IN('CLOSED','COMPLETED') OR (t.status='OPEN' AND t.closes_at<CURRENT_TIMESTAMP))";
  Map<String,Object> result=new LinkedHashMap<>();lesson.remove("teacher_id");result.put("lesson",lesson);
  result.put("summary",service.db().queryForMap("SELECT COUNT(e.id) expected,COUNT(a.id) submitted,AVG(a.total_score) average_score FROM evaluation_task t LEFT JOIN eligibility e ON e.task_id=t.id LEFT JOIN answer a ON a.eligibility_id=e.id WHERE t.lesson_id=? AND t.deleted_at IS NULL"+finished,id));
  result.put("released",service.db().queryForObject("SELECT COUNT(*) FROM evaluation_task t WHERE t.lesson_id=? AND t.deleted_at IS NULL"+finished,Integer.class,id)>0);
  result.put("dimensions",service.db().queryForList("SELECT d.label,d.weight,AVG(ad.score) average_score,COUNT(ad.answer_id) responses FROM evaluation_task t JOIN template_dimension d ON d.template_id=t.template_id LEFT JOIN eligibility e ON e.task_id=t.id LEFT JOIN answer a ON a.eligibility_id=e.id LEFT JOIN answer_dimension ad ON ad.answer_id=a.id AND ad.dimension_id=d.id WHERE t.lesson_id=? AND t.deleted_at IS NULL"+finished+" GROUP BY d.label,d.weight ORDER BY d.label",id));
  return ApiResponse.ok(result);
 }
 @GetMapping("/courses") public ApiResponse<?> courses(){Map<String,Object> u=service.user();if("STUDENT".equals(u.get("role")))return ApiResponse.ok(service.db().queryForList("SELECT t.id,t.token,t.status,t.opens_at,t.closes_at,l.taught_at,a.created_at submitted_at,a.receipt,l.name lesson_name,c.name class_name,te.name teacher_name,CASE WHEN a.id IS NULL THEN '未提交' ELSE '已提交' END submission_status FROM eligibility e JOIN evaluation_task t ON t.id=e.task_id JOIN lesson l ON l.id=t.lesson_id JOIN training_class c ON c.id=l.class_id JOIN teacher te ON te.id=l.teacher_id LEFT JOIN answer a ON a.eligibility_id=e.id WHERE t.deleted_at IS NULL AND e.student_id=? ORDER BY t.id DESC",u.get("student_id")));
  return ApiResponse.ok(service.db().queryForList("SELECT l.id,l.class_id,c.owner_id,(SELECT COUNT(*) FROM evaluation_task existing WHERE existing.lesson_id=l.id AND existing.deleted_at IS NULL) task_count,l.name lesson_name,l.taught_at,c.name class_name,COUNT(e.id) expected,COUNT(a.id) submitted,AVG(CASE WHEN t.status IN('CLOSED','COMPLETED') OR t.closes_at<CURRENT_TIMESTAMP THEN a.total_score ELSE NULL END) average_score FROM lesson l JOIN training_class c ON c.id=l.class_id LEFT JOIN evaluation_task t ON t.lesson_id=l.id AND t.status<>'REVOKED' AND t.deleted_at IS NULL LEFT JOIN evaluation_template tp ON tp.id=t.template_id LEFT JOIN eligibility e ON e.task_id=t.id LEFT JOIN answer a ON a.eligibility_id=e.id WHERE l.teacher_id=? GROUP BY l.id,l.class_id,c.owner_id,l.name,l.taught_at,c.name ORDER BY l.id DESC",u.get("teacher_id")));
 }
 @PostMapping("/evaluate") public ApiResponse<?> evaluate(@RequestBody Map<String,Object> data){Map<String,Object> u=service.user();if(!"STUDENT".equals(u.get("role")))throw new org.springframework.security.access.AccessDeniedException("仅学员可评价");String token=TrainingService.text(data,"token",64);Map<String,Object> eligible=service.one("SELECT e.access_code FROM eligibility e JOIN evaluation_task t ON t.id=e.task_id WHERE t.token=? AND e.student_id=?",token,u.get("student_id"));Map<String,Object> safe=new HashMap<>(data);safe.put("access_code",eligible.get("access_code"));return ApiResponse.ok(evaluation.submit(safe,false));}
}
