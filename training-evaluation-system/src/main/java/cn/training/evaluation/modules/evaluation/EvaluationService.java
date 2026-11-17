package cn.training.evaluation.modules.evaluation;

import cn.training.evaluation.common.BusinessException;
import cn.training.evaluation.modules.training.TrainingService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import java.sql.Timestamp;
import java.time.LocalDateTime;

/** 评分模型固定三项1—5分，名称及权重由任务绑定的不可变数据库模板提供。 */
@Service
public class EvaluationService {
    private final TrainingService training;
    public EvaluationService(TrainingService training){this.training=training;}
    public Map<String,Object> publicTask(String token){
        Map<String,Object> task=training.one("SELECT e.id,e.status,e.opens_at,e.closes_at,l.name lesson_name,c.name class_name,t.name teacher_name FROM evaluation_task e JOIN lesson l ON l.id=e.lesson_id JOIN training_class c ON c.id=l.class_id JOIN teacher t ON t.id=l.teacher_id WHERE e.token=? AND e.deleted_at IS NULL",token);
        check(task);Map<String,Object> template=training.one("SELECT t.* FROM evaluation_template t JOIN evaluation_task e ON e.template_id=t.id WHERE e.id=?",task.get("id"));template.put("dimensions",training.db().queryForList("SELECT id,label,weight,position_no FROM template_dimension WHERE template_id=? ORDER BY position_no",template.get("id")));task.put("template",template);return task;
    }
    private void check(Map<String,Object> t){
        LocalDateTime now=LocalDateTime.now();
        if("COMPLETED".equals(t.get("status")))throw new BusinessException("评价已完成，不能再次评价");
        if(!"OPEN".equals(t.get("status")))throw new BusinessException("评价已关闭或撤销");
        if(now.isBefore(((Timestamp)t.get("opens_at")).toLocalDateTime()))throw new BusinessException("评价尚未开放");
        if(now.isAfter(((Timestamp)t.get("closes_at")).toLocalDateTime()))throw new BusinessException("评价已截止");
    }
    @Transactional public String submit(Map<String,Object> data, boolean paper){
        String token=TrainingService.text(data,"token",64),code=TrainingService.text(data,"access_code",64);
        // 锁定任务与资格，序列化同一任务的提交及关闭，避免检查与写入之间的状态竞争。
        Map<String,Object> task=training.one("SELECT * FROM evaluation_task WHERE token=? AND deleted_at IS NULL FOR UPDATE",token);
        if(paper){training.write("TASK_MANAGE");training.task(TrainingService.num(task.get("id")));if("REVOKED".equals(task.get("status")))throw new BusinessException("已撤销任务不能补录");}
        Map<String,Object> eligible=training.one("SELECT * FROM eligibility WHERE task_id=? AND access_code=? FOR UPDATE",task.get("id"),code);
        if(!paper){
            org.springframework.security.core.Authentication auth=org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
            boolean signed=auth!=null&&auth.isAuthenticated()&&!(auth instanceof org.springframework.security.authentication.AnonymousAuthenticationToken);
            boolean phoneBound=training.db().queryForObject("SELECT COUNT(*) FROM app_user WHERE student_id=? AND phone IS NOT NULL",Integer.class,eligible.get("student_id"))>0;
            Map<String,Object> current=signed?training.user():null;
            if(phoneBound||(current!=null&&"STUDENT".equals(current.get("role")))){
                if(current==null||!"STUDENT".equals(current.get("role"))||current.get("student_id")==null||TrainingService.num(current.get("student_id"))!=TrainingService.num(eligible.get("student_id")))throw new org.springframework.security.access.AccessDeniedException("请用本人的学员手机号登录后评价");
            }
        }
        List<Map<String,Object>> previous=training.db().queryForList("SELECT receipt,source FROM answer WHERE eligibility_id=?",eligible.get("id"));
        if(!previous.isEmpty()){
            if(!previous.get(0).get("source").equals(paper?"PAPER":"ONLINE"))throw new BusinessException("已通过其他渠道提交，不能重复录入");
            return previous.get(0).get("receipt").toString();
        }
        // 相同资格重试返回原回执；新答卷必须再次验证任务状态。
        if(paper){if("COMPLETED".equals(task.get("status")))throw new BusinessException("评价已完成，不能重复补录");}else check(task);
        List<Map<String,Object>> dims=training.db().queryForList("SELECT id,weight FROM template_dimension WHERE template_id=? ORDER BY position_no",task.get("template_id"));
        int[] scores=new int[3];String[] fields={"teaching","content","organization"};Map<?,?> submitted=data.get("scores") instanceof Map?(Map<?,?>)data.get("scores"):null;
        if(dims.isEmpty()||(submitted==null&&dims.size()!=3)||(submitted!=null&&submitted.size()!=dims.size()))throw new BusinessException("请完整填写当前模板的所有评分维度");
        List<Integer> points=new ArrayList<>();long sum=0,weights=0;for(int i=0;i<dims.size();i++){Map<String,Object> dim=dims.get(i);Object raw=submitted==null?data.get(fields[i]):submitted.get(String.valueOf(dim.get("id")));long value=TrainingService.num(raw);if(value<1||value>5)throw new BusinessException("各项评分必须是1到5的整数");points.add((int)value);if(i<3)scores[i]=(int)value;long weight=TrainingService.num(dim.get("weight"));sum+=value*weight;weights+=weight;}
        String comment=data.get("comment")==null?"":data.get("comment").toString().trim();if(comment.length()>1000)throw new BusinessException("意见最多1000字");
        String receipt=UUID.randomUUID().toString();
        String paperReference=paper?TrainingService.text(data,"paper_reference",80):null;
        training.db().update("INSERT INTO answer(eligibility_id,teaching,content,organization,comment,source,operator_name,receipt,paper_reference) VALUES(?,?,?,?,?,?,?,?,?)",eligible.get("id"),scores[0],scores[1],scores[2],comment,paper?"PAPER":"ONLINE",paper?training.user().get("username"):null,receipt,paperReference);
        long answerId=training.db().queryForObject("SELECT id FROM answer WHERE receipt=?",Long.class,receipt);training.db().update("UPDATE answer SET total_score=? WHERE id=?",java.math.BigDecimal.valueOf(sum).divide(java.math.BigDecimal.valueOf(weights),6,java.math.RoundingMode.HALF_UP),answerId);
        for(int i=0;i<dims.size();i++)training.db().update("INSERT INTO answer_dimension(answer_id,dimension_id,score) VALUES(?,?,?)",answerId,dims.get(i).get("id"),points.get(i));
        int expected=training.db().queryForObject("SELECT COUNT(*) FROM eligibility WHERE task_id=?",Integer.class,task.get("id"));
        int received=training.db().queryForObject("SELECT COUNT(*) FROM answer a JOIN eligibility e ON e.id=a.eligibility_id WHERE e.task_id=?",Integer.class,task.get("id"));
        if(expected>0&&received>=expected){training.db().update("UPDATE evaluation_task SET status='COMPLETED' WHERE id=?",task.get("id"));training.db().update("INSERT INTO audit_log(actor,action,path,http_status) VALUES(?,'TASK_COMPLETED',?,200)",paper?training.user().get("username"):"student-submit","tasks/"+task.get("id"));}
        if(paper)training.audit("PAPER_ENTRY","tasks/"+task.get("id"));
        return receipt;
    }
}
