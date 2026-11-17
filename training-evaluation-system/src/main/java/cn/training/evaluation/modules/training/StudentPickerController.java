package cn.training.evaluation.modules.training;

import cn.training.evaluation.common.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

/** 按班次权限提供学员选择器，只返回必要档案字段，不返回手机号或账号信息。 */
@RestController
@RequestMapping("/api/business/classes/{classId}")
public class StudentPickerController {
 private final TrainingService service;
 public StudentPickerController(TrainingService service){this.service=service;}
 @GetMapping("/student-picker")public ApiResponse<?> candidates(@PathVariable long classId,@RequestParam(defaultValue="")String keyword,@RequestParam(defaultValue="1")int page){
  service.write("CLASS_MANAGE");service.scope(classId);String term=keyword.trim();if(term.length()>80||page<1||page>100000)throw new BusinessException("筛选参数无效");
  String where=" FROM student s WHERE NOT EXISTS(SELECT 1 FROM enrollment e WHERE e.class_id=? AND e.student_id=s.id) AND (LOCATE(?,s.name)>0 OR LOCATE(?,s.code)>0)";
  long total=service.db().queryForObject("SELECT COUNT(*)"+where,Long.class,classId,term,term);Map<String,Object> result=new LinkedHashMap<>();result.put("total",total);result.put("page",page);result.put("pageSize",100);result.put("items",service.db().queryForList("SELECT s.id,s.name,s.code"+where+" ORDER BY s.name,s.id LIMIT 100 OFFSET ?",classId,term,term,(page-1)*100));return ApiResponse.ok(result);
 }
 @PostMapping("/members/select")@Transactional public ApiResponse<?> select(@PathVariable long classId,@RequestBody Map<String,Object> data){
  service.write("CLASS_MANAGE");service.scope(classId);Object raw=data.get("student_ids");if(!(raw instanceof List))throw new BusinessException("请选择学员");List<?> submitted=(List<?>)raw;if(submitted.isEmpty()||submitted.size()>200)throw new BusinessException("单次请选择1—200位学员");Set<Long> ids=new LinkedHashSet<>();for(Object item:submitted){long id=TrainingService.num(item);if(id<1)throw new BusinessException("学员编号无效");ids.add(id);}
  service.one("SELECT id FROM training_class WHERE id=? FOR UPDATE",classId);
  // 全部校验成功后才开始关联，任一不存在的学员会使整批回滚。
  for(Long id:ids)if(service.db().queryForObject("SELECT COUNT(*) FROM student WHERE id=?",Integer.class,id)==0)throw new BusinessException("有学员已不存在，请刷新名单后重选");
  int added=0;for(Long id:ids)if(service.db().queryForObject("SELECT COUNT(*) FROM enrollment WHERE class_id=? AND student_id=?",Integer.class,classId,id)==0){service.db().update("INSERT INTO enrollment(class_id,student_id) VALUES(?,?)",classId,id);added++;}
  service.audit("SELECT_ENROLL","classes/"+classId+" added/"+added);Map<String,Object> result=new LinkedHashMap<>();result.put("added",added);result.put("skipped",ids.size()-added);return ApiResponse.ok(result);
 }
}
