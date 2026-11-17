package cn.training.evaluation.modules.identity;

import cn.training.evaluation.common.*;
import cn.training.evaluation.modules.training.TrainingService;
import com.google.zxing.*;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import javax.servlet.http.HttpServletResponse;
import java.util.*;

/** 教师端直接组织本人授课。复用业务服务的授权、班次范围、名单快照和重复发布约束。 */
@RestController
@RequestMapping("/api/portal/teaching")
public class TeacherWorkspaceController {
    private final TrainingService service;
    private final String baseUrl;
    public TeacherWorkspaceController(TrainingService service,@Value("${app.public-base-url}")String baseUrl){this.service=service;this.baseUrl=baseUrl;}
    private Map<String,Object> teacher(){
        Map<String,Object> user=service.user();
        if(!"TEACHER".equals(user.get("role")))throw new org.springframework.security.access.AccessDeniedException("仅教师可使用授课工作台");
        if(user.get("teacher_id")==null)throw new BusinessException("请先联系管理员绑定教师档案");
        return user;
    }
    private void ownLesson(long id,Map<String,Object> user){
        Map<String,Object> lesson=service.one("SELECT teacher_id,class_id FROM lesson WHERE id=?",id);
        if(!Objects.equals(lesson.get("teacher_id"),user.get("teacher_id")))throw new org.springframework.security.access.AccessDeniedException("只能操作本人授课");
        service.scope(TrainingService.num(lesson.get("class_id")));
    }
    @GetMapping("/options") public ApiResponse<?> options(){
        Map<String,Object> user=teacher(),result=new LinkedHashMap<>();List<String> permissions=service.permissions(user.get("id"));
        result.put("can_create_lesson",permissions.contains("LESSON_MANAGE"));result.put("can_publish",permissions.contains("TASK_MANAGE"));
        result.put("classes",service.db().queryForList("SELECT c.id,c.name,c.start_date,c.end_date,(SELECT COUNT(*) FROM enrollment e WHERE e.class_id=c.id) student_count FROM training_class c WHERE c.owner_id=? ORDER BY c.id DESC",user.get("id")));
        result.put("lessons",service.db().queryForList("SELECT l.id,l.name,c.name class_name,(SELECT COUNT(*) FROM enrollment e WHERE e.class_id=c.id) student_count FROM lesson l JOIN training_class c ON c.id=l.class_id WHERE c.owner_id=? AND l.teacher_id=? AND NOT EXISTS(SELECT 1 FROM evaluation_task t WHERE t.lesson_id=l.id AND t.deleted_at IS NULL) ORDER BY l.id DESC",user.get("id"),user.get("teacher_id")));
        result.put("templates",permissions.contains("TASK_MANAGE")?service.list("templates"):Collections.emptyList());
        return ApiResponse.ok(result);
    }
    @PostMapping("/lessons") @Transactional public ApiResponse<?> createLesson(@RequestBody Map<String,Object> body){
        Map<String,Object> user=teacher();service.write("LESSON_MANAGE");
        Map<String,Object> safe=new LinkedHashMap<>();safe.put("name",body.get("name"));safe.put("class_id",body.get("class_id"));safe.put("taught_at",body.get("taught_at"));safe.put("teacher_id",user.get("teacher_id"));
        service.save("lessons",null,safe);return ApiResponse.ok(null);
    }
    @PostMapping("/tasks") @Transactional public ApiResponse<?> publish(@RequestBody Map<String,Object> body){
        Map<String,Object> user=teacher();service.write("TASK_MANAGE");long lesson=TrainingService.num(body.get("lesson_id"));ownLesson(lesson,user);
        Map<String,Object> safe=new LinkedHashMap<>();for(String key:Arrays.asList("lesson_id","template_id","opens_at","closes_at"))if(body.containsKey(key))safe.put(key,body.get(key));
        service.save("tasks",null,safe);
        Map<String,Object> task=service.one("SELECT id,token FROM evaluation_task WHERE lesson_id=? AND deleted_at IS NULL",lesson),result=new LinkedHashMap<>();
        result.put("task_id",task.get("id"));result.put("url",baseUrl.replaceAll("/$","")+"/h5/?token="+task.get("token"));return ApiResponse.ok(result);
    }
    @GetMapping("/tasks/{id}/qr") public void qr(@PathVariable long id,HttpServletResponse response)throws Exception{
        Map<String,Object> user=teacher();service.write("TASK_MANAGE");Map<String,Object> task=service.task(id);ownLesson(TrainingService.num(task.get("lesson_id")),user);
        String url=baseUrl.replaceAll("/$","")+"/h5/?token="+task.get("token");response.setContentType("image/png");service.audit("QR_EXPORT","teacher/tasks/"+id);
        MatrixToImageWriter.writeToStream(new MultiFormatWriter().encode(url,BarcodeFormat.QR_CODE,400,400),"PNG",response.getOutputStream());
    }
}
