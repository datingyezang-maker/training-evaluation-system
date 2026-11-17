package cn.training.evaluation.modules.training;

import cn.training.evaluation.common.BusinessException;
import cn.training.evaluation.modules.identity.DatabaseUsers;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.core.context.SecurityContextHolder;
import java.util.*;
import java.time.*;

/** 单体业务服务：所有实体访问在服务端检查角色和班次范围，不依赖前端隐藏按钮。 */
@Service
public class TrainingService {
    private final JdbcTemplate jdbc;
    private final cn.training.evaluation.modules.identity.PhoneAccounts accounts;
    private final cn.training.evaluation.modules.evaluation.DimensionService dimensions;
    private final cn.training.evaluation.modules.identity.StudentNumbers numbers;
    public TrainingService(JdbcTemplate jdbc,cn.training.evaluation.modules.identity.PhoneAccounts accounts,cn.training.evaluation.modules.evaluation.DimensionService dimensions,cn.training.evaluation.modules.identity.StudentNumbers numbers) { this.jdbc = jdbc;this.accounts=accounts;this.dimensions=dimensions;this.numbers=numbers; }
    public JdbcTemplate db() { return jdbc; }
    public Map<String,Object> user() {
        String name = SecurityContextHolder.getContext().getAuthentication().getName();
        // 同一 HTTP 请求中的权限、数据范围、审计复用一次账号查询；绝不跨请求缓存授权。
        org.springframework.web.context.request.RequestAttributes request=org.springframework.web.context.request.RequestContextHolder.getRequestAttributes();
        String cacheKey=TrainingService.class.getName()+".user."+name;
        if(request!=null){Object cached=request.getAttribute(cacheKey,org.springframework.web.context.request.RequestAttributes.SCOPE_REQUEST);if(cached instanceof Map)return (Map<String,Object>)cached;}
        List<Map<String,Object>> rows = jdbc.queryForList("SELECT id,username,phone,display_name,role,enabled,student_id,teacher_id FROM app_user WHERE username=? AND enabled=TRUE", name);
        if(rows.isEmpty()) throw new org.springframework.security.access.AccessDeniedException("账号不可用");
        if(request!=null)request.setAttribute(cacheKey,rows.get(0),org.springframework.web.context.request.RequestAttributes.SCOPE_REQUEST);
        return rows.get(0);
    }
    public void admin() { if(!"ADMIN".equals(user().get("role"))) throw new org.springframework.security.access.AccessDeniedException("需要管理员"); }
    public static final List<String> PERSONAL_PERMISSIONS=Collections.unmodifiableList(Arrays.asList("CLASS_MANAGE","LESSON_MANAGE","TASK_MANAGE"));
    public List<String> permissions(Object userId) { return jdbc.queryForList("SELECT permission_code FROM account_permission WHERE user_id=? ORDER BY permission_code",String.class,userId); }
    /** 使用数据库中的当前授权，而非登录时的角色缓存，保证撤销权限即时生效。 */
    public boolean workspaceAllowed() {
        if(SecurityContextHolder.getContext().getAuthentication()==null || "anonymousUser".equals(SecurityContextHolder.getContext().getAuthentication().getName()))return false;
        Map<String,Object> u=user();
        return Arrays.asList("ADMIN","BUSINESS","VIEWER").contains(u.get("role")) || (Arrays.asList("STUDENT","TEACHER").contains(u.get("role"))&&!permissions(u.get("id")).isEmpty());
    }
    public void write(String permission) {
        Map<String,Object> u=user();
        if(Arrays.asList("ADMIN","BUSINESS").contains(u.get("role")))return;
        if(Arrays.asList("STUDENT","TEACHER").contains(u.get("role"))&&PERSONAL_PERMISSIONS.contains(permission)&&permissions(u.get("id")).contains(permission))return;
        throw new org.springframework.security.access.AccessDeniedException("尚未获得此功能的管理权限");
    }
    private String permissionFor(String entity) {
        switch(entity){case "classes":return "CLASS_MANAGE";case "lessons":return "LESSON_MANAGE";case "tasks":return "TASK_MANAGE";default:return "";}
    }
    public void write() { if(!Arrays.asList("ADMIN","BUSINESS").contains(user().get("role"))) throw new org.springframework.security.access.AccessDeniedException("无维护权限"); }
    public void scope(long classId) {
        Map<String,Object> c = one("SELECT * FROM training_class WHERE id=?", classId);
        Map<String,Object> u = user();
        if(!"ADMIN".equals(u.get("role")) && num(c.get("owner_id"))!=num(u.get("id"))) throw new org.springframework.security.access.AccessDeniedException("班次超出权限范围");
    }
    public Map<String,Object> one(String sql, Object... args) {
        List<Map<String,Object>> rows = jdbc.queryForList(sql,args);
        if(rows.isEmpty()) throw new BusinessException("记录不存在或已删除");
        return rows.get(0);
    }
    public static long num(Object value) {
        try { return Long.parseLong(String.valueOf(value)); } catch(Exception e) { throw new BusinessException("编号格式不正确"); }
    }
    public static String text(Map<String,Object> data,String field,int max) {
        Object v=data.get(field); String s=v==null?"":v.toString().trim();
        if(s.isEmpty()||s.length()>max) throw new BusinessException(field+"不能为空且长度不能超过"+max);
        return s;
    }
    public static LocalDateTime time(Map<String,Object> data,String field) {
        try { return LocalDateTime.parse(text(data,field,30)); } catch(Exception e) { throw new BusinessException(field+"时间格式不正确"); }
    }
    private String table(String entity) {
        switch(entity) {case "templates":return "evaluation_template";case "classes":return "training_class"; case "teachers":return "teacher";case "students":return "student";case "lessons":return "lesson";case "tasks":return "evaluation_task";case "users":return "app_user"; default:throw new BusinessException("未知模块");}
    }
    public List<Map<String,Object>> list(String entity) {
        Map<String,Object> u=user(); boolean all="ADMIN".equals(u.get("role"));
        switch(entity) {
            case "templates":
                List<Map<String,Object>> templates=jdbc.queryForList("SELECT * FROM evaluation_template ORDER BY id DESC");
                Map<Long,List<Map<String,Object>>> byTemplate=new HashMap<>();
                for(Map<String,Object> dimension:jdbc.queryForList("SELECT id,template_id,label,weight,position_no FROM template_dimension ORDER BY template_id,position_no")){
                    long templateId=num(dimension.remove("template_id"));byTemplate.computeIfAbsent(templateId,key->new ArrayList<>()).add(dimension);
                }
                for(Map<String,Object> t:templates){List<Map<String,Object>> configured=byTemplate.getOrDefault(num(t.get("id")),Collections.emptyList());t.put("dimensions",configured);t.put("dimension_count",configured.size());}return templates;
            case "users": admin(); return jdbc.queryForList("SELECT id,username,phone,display_name,role,enabled,student_id,teacher_id,review_status,review_reason,reviewed_by,reviewed_at,login_failures,locked_until FROM app_user ORDER BY id DESC");
            case "classes": return all?jdbc.queryForList("SELECT c.*,(SELECT username FROM app_user u WHERE u.id=c.owner_id) owner_name,(SELECT COUNT(*) FROM enrollment en WHERE en.class_id=c.id) student_count FROM training_class c ORDER BY c.id DESC"):jdbc.queryForList("SELECT c.*,(SELECT username FROM app_user u WHERE u.id=c.owner_id) owner_name,(SELECT COUNT(*) FROM enrollment en WHERE en.class_id=c.id) student_count FROM training_class c WHERE c.owner_id=? ORDER BY c.id DESC",u.get("id"));
            case "lessons": return jdbc.queryForList("SELECT l.*,c.name class_name,t.name teacher_name FROM lesson l JOIN training_class c ON c.id=l.class_id JOIN teacher t ON t.id=l.teacher_id"+(all?"":" WHERE c.owner_id=?")+" ORDER BY l.id DESC",all?new Object[]{}:new Object[]{u.get("id")});
            case "tasks": return jdbc.queryForList("SELECT e.*,l.name lesson_name,c.name class_name,(SELECT COUNT(*) FROM eligibility el WHERE el.task_id=e.id) expected,(SELECT COUNT(*) FROM answer a JOIN eligibility el ON el.id=a.eligibility_id WHERE el.task_id=e.id) submitted FROM evaluation_task e JOIN lesson l ON e.lesson_id=l.id JOIN training_class c ON l.class_id=c.id"+(all?" WHERE e.deleted_at IS NULL":" WHERE e.deleted_at IS NULL AND c.owner_id=?")+" ORDER BY e.id DESC",all?new Object[]{}:new Object[]{u.get("id")});
            case "teachers": return jdbc.queryForList("SELECT * FROM teacher ORDER BY id DESC");
            case "students": return all?jdbc.queryForList("SELECT * FROM student ORDER BY id DESC"):jdbc.queryForList("SELECT DISTINCT s.* FROM student s JOIN enrollment e ON s.id=e.student_id JOIN training_class c ON c.id=e.class_id WHERE c.owner_id=?",u.get("id"));
            default: table(entity); return Collections.emptyList();
        }
    }
    /** 业务写入与操作审计同事务，数据库约束失败时整体回滚。 */
    @Transactional public void save(String entity,Long id,Map<String,Object> d) {
        write(permissionFor(entity)); String table=table(entity); LinkedHashMap<String,Object> values=new LinkedHashMap<>();
        switch(entity) {
            case "templates":
                admin();if(id!=null){one("SELECT id FROM evaluation_template WHERE id=? FOR UPDATE",id);if(jdbc.queryForObject("SELECT COUNT(*) FROM evaluation_task WHERE template_id=?",Integer.class,id)>0)throw new BusinessException("已被发布任务使用的模板不可修改，请新增版本");}
                values.put("name",text(d,"name",120));List<Map<String,Object>> configured=dimensions.validate(d);String[] legacy={"teaching","content","organization"};for(int index=0;index<3;index++){values.put(legacy[index]+"_label",index<configured.size()?configured.get(index).get("label"):"保留字段"+index);values.put(legacy[index]+"_weight",index<configured.size()?configured.get(index).get("weight"):1);}break;
            case "users":
                admin(); String role=text(d,"role",20);
                List<Map<String,Object>> admins=jdbc.queryForList("SELECT id,enabled FROM app_user WHERE role='ADMIN' ORDER BY id FOR UPDATE");
                long activeAdmins=admins.stream().filter(a->Boolean.parseBoolean(a.get("enabled").toString())).count();
                if(!Arrays.asList("ADMIN","BUSINESS","VIEWER","STUDENT","TEACHER").contains(role)) throw new BusinessException("角色无效");
                Map<String,Object> identity=accounts.fields(d,id,role);
                values.put("username",identity.containsKey("username")?identity.get("username"):text(d,"username",80)); values.put("role",role);values.putAll(identity);
                if(!Arrays.asList("true","false").contains(String.valueOf(d.get("enabled"))))throw new BusinessException("启用状态必须为true或false");
                boolean enabled=Boolean.parseBoolean(String.valueOf(d.get("enabled"))); values.put("enabled",enabled);
                if(id!=null) {
                    Map<String,Object> old=one("SELECT * FROM app_user WHERE id=? FOR UPDATE",id);if(enabled&&!"APPROVED".equals(old.get("review_status")))throw new BusinessException("请先通过独立审核入口处理此账号");
                    if("ADMIN".equals(old.get("role")) && Boolean.parseBoolean(old.get("enabled").toString()) && (!enabled||!"ADMIN".equals(role)) && activeAdmins<=1) throw new BusinessException("至少保留一名启用的管理员");
                }
                if(id==null || (d.get("password")!=null&&!d.get("password").toString().isEmpty())) {
                    String password=text(d,"password",72); if(password.length()<12||password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length>72) throw new BusinessException("密码至少12位且UTF-8编码不超过72字节"); values.put("password_hash",DatabaseUsers.hash(password));
                } break;
            case "classes":
                if(id!=null) scope(id);
                values.put("name",text(d,"name",120));
                try {LocalDate start=LocalDate.parse(text(d,"start_date",10)),end=LocalDate.parse(text(d,"end_date",10)); if(end.isBefore(start)) throw new Exception(); values.put("start_date",start.toString());values.put("end_date",end.toString());} catch(Exception e) {throw new BusinessException("班次日期无效或结束日期早于开始日期");}
                long owner="ADMIN".equals(user().get("role"))?num(d.get("owner_id")):num(user().get("id"));
                one("SELECT id FROM app_user WHERE id=? AND enabled=TRUE",owner);values.put("owner_id",owner); break;
            case "teachers": admin();values.put("name",text(d,"name",80));break;
            case "students": admin();values.put("name",text(d,"name",80));values.put("code",id==null?numbers.next():one("SELECT code FROM student WHERE id=?",id).get("code"));break;
            case "lessons":
                long classId=num(d.get("class_id"));scope(classId);
                if(id!=null) {scope(num(one("SELECT class_id FROM lesson WHERE id=?",id).get("class_id"))); if(jdbc.queryForObject("SELECT COUNT(*) FROM evaluation_task WHERE lesson_id=?",Integer.class,id)>0) throw new BusinessException("已有评价任务的授课不允许修改");}
                values.put("class_id",classId);values.put("teacher_id",num(d.get("teacher_id"))); values.put("name",text(d,"name",120));values.put("taught_at",java.sql.Timestamp.valueOf(time(d,"taught_at"))); break;
            case "tasks":
                if(id!=null) throw new BusinessException("评价任务发布后不可改写，请关闭或撤销");
                long lessonId=num(d.get("lesson_id"));scope(num(one("SELECT class_id FROM lesson WHERE id=?",lessonId).get("class_id")));
                one("SELECT id FROM lesson WHERE id=? FOR UPDATE",lessonId);
                if(!jdbc.queryForList("SELECT id FROM evaluation_task WHERE lesson_id=? AND deleted_at IS NULL FOR UPDATE",lessonId).isEmpty())throw new BusinessException("该次授课已发布评价任务，请管理原任务；确需重发时先关闭并删除旧任务");
                jdbc.update("INSERT INTO task_publication_guard(lesson_id,task_id) VALUES(?,NULL)",lessonId);
                LocalDateTime open=time(d,"opens_at"),close=time(d,"closes_at");if(!close.isAfter(open)) throw new BusinessException("截止时间必须晚于开放时间");
                long templateId=d.containsKey("template_id")?num(d.get("template_id")):1L;one("SELECT id FROM evaluation_template WHERE id=? FOR UPDATE",templateId);values.put("template_id",templateId);
                values.put("lesson_id",lessonId);values.put("token",UUID.randomUUID().toString().replace("-",""));values.put("status","OPEN");values.put("opens_at",java.sql.Timestamp.valueOf(open));values.put("closes_at",java.sql.Timestamp.valueOf(close));break;
            default:throw new BusinessException("模块不支持写入");
        }
        if(id==null) {
            String placeholders=String.join(",",Collections.nCopies(values.size(),"?"));
            org.springframework.jdbc.support.GeneratedKeyHolder key=new org.springframework.jdbc.support.GeneratedKeyHolder();
            jdbc.update(connection->{java.sql.PreparedStatement ps=connection.prepareStatement("INSERT INTO "+table+"("+String.join(",",values.keySet())+") VALUES("+placeholders+")",java.sql.Statement.RETURN_GENERATED_KEYS);int index=1;for(Object v:values.values())ps.setObject(index++,v);return ps;},key);
            id=key.getKey().longValue();
            if("users".equals(entity)&&"TEACHER".equals(values.get("role")))cn.training.evaluation.modules.identity.TeacherDefaults.grant(jdbc,id);
            if("tasks".equals(entity)) {
                jdbc.update("UPDATE task_publication_guard SET task_id=? WHERE lesson_id=?",id,d.get("lesson_id"));
                List<Map<String,Object>> students=jdbc.queryForList("SELECT e.student_id FROM enrollment e JOIN lesson l ON l.class_id=e.class_id WHERE l.id=?",d.get("lesson_id"));
                if(students.isEmpty()) throw new BusinessException("班次没有学员，请先关联名单");
                for(Map<String,Object> s:students) jdbc.update("INSERT INTO eligibility(task_id,student_id,access_code) VALUES(?,?,?)",id,s.get("student_id"),UUID.randomUUID().toString().replace("-",""));
            }
        } else {
            Map<String,Object> original=one("SELECT id"+("users".equals(entity)?",role":"")+" FROM "+table+" WHERE id=?",id);List<String> assignments=new ArrayList<>();values.keySet().forEach(k->assignments.add(k+"=?"));List<Object> args=new ArrayList<>(values.values());args.add(id);
            jdbc.update("UPDATE "+table+" SET "+String.join(",",assignments)+" WHERE id=?",args.toArray());
            if("users".equals(entity)&&"TEACHER".equals(values.get("role"))&&!"TEACHER".equals(original.get("role")))cn.training.evaluation.modules.identity.TeacherDefaults.grant(jdbc,id);
        }
        if("templates".equals(entity))dimensions.replace(id,dimensions.validate(d));
        audit("SAVE",entity+"/"+id);
    }
    @Transactional public void delete(String entity,long id) {
        write(permissionFor(entity));String table=table(entity);
        if("tasks".equals(entity)){
            Map<String,Object> task=task(id);long lesson=num(task.get("lesson_id"));one("SELECT id FROM lesson WHERE id=? FOR UPDATE",lesson);Map<String,Object> current=one("SELECT * FROM evaluation_task WHERE id=? AND deleted_at IS NULL FOR UPDATE",id);
            if(!Arrays.asList("CLOSED","REVOKED","COMPLETED").contains(current.get("status")))throw new BusinessException("仅已关闭、已撤销或已完成任务可以删除");
            jdbc.update("UPDATE evaluation_task SET deleted_at=CURRENT_TIMESTAMP,deleted_by=? WHERE id=?",user().get("username"),id);
            List<Map<String,Object>> remaining=jdbc.queryForList("SELECT id FROM evaluation_task WHERE lesson_id=? AND deleted_at IS NULL ORDER BY id DESC FOR UPDATE",lesson);
            if(remaining.isEmpty())jdbc.update("DELETE FROM task_publication_guard WHERE lesson_id=?",lesson);else jdbc.update("UPDATE task_publication_guard SET task_id=? WHERE lesson_id=?",remaining.get(0).get("id"),lesson);
            audit("TASK_DELETE","tasks/"+id+" 历史评分保留");return;
        }
        if("users".equals(entity)) throw new BusinessException("账号请停用");
        if("classes".equals(entity)) scope(id);else if("lessons".equals(entity))scope(num(one("SELECT class_id FROM lesson WHERE id=?",id).get("class_id")));else admin();
        if("templates".equals(entity))jdbc.update("DELETE FROM template_dimension WHERE template_id=?",id);
        if(jdbc.update("DELETE FROM "+table+" WHERE id=?",id)==0)throw new BusinessException("记录不存在");audit("DELETE",entity+"/"+id);
    }
    /** 旧档案导入可保留已有学号；留空则使用与注册相同的自动编号。仅内部导入入口调用。 */
    @Transactional public void importStudent(Map<String,Object> data){
        admin();
        Object supplied=data.get("code");
        if(supplied==null||supplied.toString().trim().isEmpty()){save("students",null,data);return;}
        one("SELECT sequence_id FROM student_number_sequence WHERE sequence_id=1 FOR UPDATE");
        jdbc.update("INSERT INTO student(code,name) VALUES(?,?)",text(data,"code",80),text(data,"name",80));
        audit("IMPORT_STUDENT","student-code/"+data.get("code"));
    }
    @Transactional public void enroll(long classId,long studentId,boolean remove) {
        write("CLASS_MANAGE");scope(classId);one("SELECT id FROM student WHERE id=?",studentId);
        if(remove) jdbc.update("DELETE FROM enrollment WHERE class_id=? AND student_id=?",classId,studentId);
        else jdbc.update("INSERT INTO enrollment(class_id,student_id) VALUES(?,?)",classId,studentId);
        audit(remove?"UNENROLL":"ENROLL","classes/"+classId+"/"+studentId);
    }
    public List<Map<String,Object>> members(long id) {scope(id);return jdbc.queryForList("SELECT s.* FROM student s JOIN enrollment e ON e.student_id=s.id WHERE e.class_id=?",id);}
    public Map<String,Object> task(long id) {Map<String,Object> t=one("SELECT e.*,l.class_id,l.name lesson_name,c.name class_name,te.name teacher_name FROM evaluation_task e JOIN lesson l ON l.id=e.lesson_id JOIN training_class c ON c.id=l.class_id JOIN teacher te ON te.id=l.teacher_id WHERE e.id=? AND e.deleted_at IS NULL",id);scope(num(t.get("class_id")));return t;}
    @Transactional public void status(long id,String status) {write("TASK_MANAGE");task(id);Map<String,Object> current=one("SELECT status FROM evaluation_task WHERE id=? FOR UPDATE",id);if("COMPLETED".equals(current.get("status")))throw new BusinessException("评价已完成，不能重新关闭或撤销");if("REVOKED".equals(current.get("status")))throw new BusinessException("已撤销任务不能恢复");if(!Arrays.asList("CLOSED","REVOKED").contains(status))throw new BusinessException("状态无效");jdbc.update("UPDATE evaluation_task SET status=? WHERE id=?",status,id);audit(status,"tasks/"+id);}
    public void audit(String action,String path) {jdbc.update("INSERT INTO audit_log(actor,action,path,http_status) VALUES(?,?,?,200)",user().get("username"),action,path);}
}
