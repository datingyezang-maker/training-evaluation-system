package cn.training.evaluation.modules.training;

import cn.training.evaluation.common.*;
import org.springframework.web.bind.annotation.*;
import java.util.*;

/** 业务 API；细粒度权限和数据范围集中在服务层执行。 */
@RestController
@RequestMapping("/api/business")
public class BusinessController {
    private final TrainingService service;
    @org.springframework.beans.factory.annotation.Value("${app.public-base-url}") private String publicBaseUrl;
    public BusinessController(TrainingService service){this.service=service;}
    /** 班次工作区的所有数据以同一班次范围过滤，不接受客户端传入负责人。 */
    @GetMapping("/classes/{id}/overview") public ApiResponse<?> overview(@PathVariable long id){
        service.scope(id);Map<String,Object> result=new LinkedHashMap<>();
        result.put("training_class",service.one("SELECT c.*,u.display_name owner_name,u.username owner_account FROM training_class c JOIN app_user u ON u.id=c.owner_id WHERE c.id=?",id));
        result.put("students",service.members(id));
        result.put("lessons",service.db().queryForList("SELECT l.*,t.name teacher_name FROM lesson l JOIN teacher t ON t.id=l.teacher_id WHERE l.class_id=? ORDER BY l.taught_at DESC",id));
        result.put("tasks",service.db().queryForList("SELECT t.*,l.name lesson_name,(SELECT COUNT(*) FROM eligibility e WHERE e.task_id=t.id) expected,(SELECT COUNT(*) FROM answer a JOIN eligibility e ON e.id=a.eligibility_id WHERE e.task_id=t.id) submitted FROM evaluation_task t JOIN lesson l ON l.id=t.lesson_id WHERE l.class_id=? AND t.deleted_at IS NULL ORDER BY t.id DESC",id));
        return ApiResponse.ok(result);
    }
    @GetMapping("/classes/{id}/candidates") public ApiResponse<?> candidates(@PathVariable long id,@RequestParam String keyword){
        service.write("CLASS_MANAGE");service.scope(id);String term=keyword.trim();if(term.length()<2||term.length()>80)throw new BusinessException("请输入至少2个字符的姓名或学号");
        return ApiResponse.ok(service.db().queryForList("SELECT id,name,code FROM student WHERE (LOCATE(?,name)>0 OR LOCATE(?,code)>0) AND id NOT IN(SELECT student_id FROM enrollment WHERE class_id=?) ORDER BY code LIMIT 50",term,term,id));
    }
    @GetMapping("/tasks/{id}/share") public ApiResponse<?> share(@PathVariable long id){service.write("TASK_MANAGE");Map<String,Object> task=service.task(id);Map<String,Object> result=new LinkedHashMap<>();result.put("url",publicBaseUrl.replaceAll("/$","")+"/h5/?token="+task.get("token"));return ApiResponse.ok(result);}
    @GetMapping("/{entity}") public ApiResponse<?> list(@PathVariable String entity){return ApiResponse.ok(service.list(entity));}
    @PostMapping("/{entity}") public ApiResponse<?> create(@PathVariable String entity,@RequestBody Map<String,Object> data){service.save(entity,null,data);return ApiResponse.ok(null);}
    @PutMapping("/{entity}/{id}") public ApiResponse<?> update(@PathVariable String entity,@PathVariable long id,@RequestBody Map<String,Object> data){service.save(entity,id,data);return ApiResponse.ok(null);}
    @DeleteMapping("/{entity}/{id}") public ApiResponse<?> delete(@PathVariable String entity,@PathVariable long id){service.delete(entity,id);return ApiResponse.ok(null);}
    @GetMapping("/classes/{id}/members") public ApiResponse<?> members(@PathVariable long id){return ApiResponse.ok(service.members(id));}
    @PostMapping("/classes/{id}/members/{student}") public ApiResponse<?> enroll(@PathVariable long id,@PathVariable long student){service.enroll(id,student,false);return ApiResponse.ok(null);}
    @DeleteMapping("/classes/{id}/members/{student}") public ApiResponse<?> unenroll(@PathVariable long id,@PathVariable long student){service.enroll(id,student,true);return ApiResponse.ok(null);}
    @PostMapping("/tasks/{id}/status") public ApiResponse<?> status(@PathVariable long id,@RequestBody Map<String,Object> data){service.status(id,TrainingService.text(data,"status",20));return ApiResponse.ok(null);}
    @GetMapping("/tasks/{id}/codes") public ApiResponse<?> codes(@PathVariable long id){service.write("TASK_MANAGE");service.task(id);service.audit("EXPORT_CODES","tasks/"+id);return ApiResponse.ok(service.db().queryForList("SELECT s.code,s.name,e.access_code,CASE WHEN a.id IS NULL THEN '未提交' ELSE '已提交' END submission_status FROM eligibility e JOIN student s ON s.id=e.student_id LEFT JOIN answer a ON a.eligibility_id=e.id WHERE e.task_id=?",id));}
}
