package cn.training.evaluation.modules.training;

import cn.training.evaluation.common.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import java.sql.Timestamp;
import java.time.LocalDateTime;

/** 培训组织工作流：批量操作整批校验，延期保留原因，名单进度不关联答卷内容。 */
@RestController
@RequestMapping("/api/business")
public class WorkflowController {
 private final TrainingService service;
 public WorkflowController(TrainingService service){this.service=service;}
 @GetMapping("/lessons/{id}/readiness") public ApiResponse<?> readiness(@PathVariable long id){
  Map<String,Object> lesson=service.one("SELECT l.*,c.name class_name,t.name teacher_name FROM lesson l JOIN training_class c ON c.id=l.class_id JOIN teacher t ON t.id=l.teacher_id WHERE l.id=?",id);service.scope(TrainingService.num(lesson.get("class_id")));
  Map<String,Object> result=new LinkedHashMap<>(lesson);result.put("students",service.db().queryForObject("SELECT COUNT(*) FROM enrollment WHERE class_id=?",Integer.class,lesson.get("class_id")));result.put("tasks",service.db().queryForObject("SELECT COUNT(*) FROM evaluation_task WHERE lesson_id=? AND deleted_at IS NULL",Integer.class,id));return ApiResponse.ok(result);
 }
 @PostMapping("/classes/{id}/members/batch") @Transactional public ApiResponse<?> batch(@PathVariable long id,@RequestBody Map<String,Object> body){
  service.write("CLASS_MANAGE");service.scope(id);Object raw=body.get("codes");if(!(raw instanceof List))throw new BusinessException("请提供学号列表");List<?> values=(List<?>)raw;if(values.isEmpty()||values.size()>200)throw new BusinessException("单次请选择1—200位学员");
  Set<Long> ids=new LinkedHashSet<>();for(Object value:values){String code=String.valueOf(value).trim();if(code.isEmpty()||code.length()>80)throw new BusinessException("学号无效");List<Map<String,Object>> rows=service.db().queryForList("SELECT id FROM student WHERE code=?",code);if(rows.isEmpty())throw new BusinessException("学号不存在："+code+"，整批未写入");ids.add(TrainingService.num(rows.get(0).get("id")));}
  // 与并发批量操作锁定同一班次，已关联的学员跳过，重复操作不会重复插入。
  service.one("SELECT id FROM training_class WHERE id=? FOR UPDATE",id);int added=0;
  for(Long student:ids)if(service.db().queryForObject("SELECT COUNT(*) FROM enrollment WHERE class_id=? AND student_id=?",Integer.class,id,student)==0){service.db().update("INSERT INTO enrollment(class_id,student_id) VALUES(?,?)",id,student);added++;}
  service.audit("BATCH_ENROLL","classes/"+id);Map<String,Object> result=new LinkedHashMap<>();result.put("added",added);result.put("skipped",ids.size()-added);return ApiResponse.ok(result);
 }
 @PostMapping("/templates/{id}/copy") @Transactional public ApiResponse<?> copy(@PathVariable long id){
  service.admin();Map<String,Object> source=service.one("SELECT * FROM evaluation_template WHERE id=?",id);source.put("dimensions",service.db().queryForList("SELECT label,weight FROM template_dimension WHERE template_id=? ORDER BY position_no",id));source.remove("id");String name=source.get("name").toString();source.put("name",name.substring(0,Math.min(110,name.length()))+"（副本）");service.save("templates",null,source);return ApiResponse.ok(null);
 }
 @PostMapping("/tasks/{id}/extend") @Transactional public ApiResponse<?> extend(@PathVariable long id,@RequestBody Map<String,Object> body){
  service.write("TASK_MANAGE");service.task(id);Map<String,Object> task=service.one("SELECT * FROM evaluation_task WHERE id=? FOR UPDATE",id);if(!"OPEN".equals(task.get("status")))throw new BusinessException("已关闭或撤销任务不能延期");
  LocalDateTime next=TrainingService.time(body,"closes_at"),old=((Timestamp)task.get("closes_at")).toLocalDateTime();String reason=TrainingService.text(body,"reason",180);
  if(!next.isAfter(old)||!next.isAfter(LocalDateTime.now()))throw new BusinessException("新截止时间必须晚于原截止时间和当前时间");
  service.db().update("UPDATE evaluation_task SET closes_at=? WHERE id=?",Timestamp.valueOf(next),id);service.audit("TASK_EXTEND","tasks/"+id+" "+old+" -> "+next+" 原因："+reason);return ApiResponse.ok(null);
 }
 @GetMapping("/tasks/{id}/progress") public ApiResponse<?> progress(@PathVariable long id,@RequestParam(defaultValue="all")String status){
  service.write("TASK_MANAGE");service.task(id);if(!Arrays.asList("all","submitted","pending").contains(status))throw new BusinessException("进度筛选无效");
  return ApiResponse.ok(service.db().queryForList("SELECT s.code,s.name,CASE WHEN a.id IS NULL THEN '未提交' ELSE '已提交' END submission_status,a.source,a.created_at FROM eligibility e JOIN student s ON s.id=e.student_id LEFT JOIN answer a ON a.eligibility_id=e.id WHERE e.task_id=?"+("pending".equals(status)?" AND a.id IS NULL":"submitted".equals(status)?" AND a.id IS NOT NULL":"")+" ORDER BY s.code",id));
 }
}
