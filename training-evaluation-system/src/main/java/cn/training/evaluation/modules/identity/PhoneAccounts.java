package cn.training.evaluation.modules.identity;

import cn.training.evaluation.common.BusinessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import java.util.*;

/** 手机号按中国大陆11位格式校验；格式校验不等于短信所有权验证。 */
@Service
public class PhoneAccounts {
 private final JdbcTemplate jdbc;
 private final StudentNumbers numbers;
 public PhoneAccounts(JdbcTemplate jdbc,StudentNumbers numbers){this.jdbc=jdbc;this.numbers=numbers;}
 public static String phone(Object value){String phone=value==null?"":value.toString().trim();if(!phone.matches("1[3-9][0-9]{9}"))throw new BusinessException("请输入有效的中国大陆11位手机号");return phone;}
 public static String password(Object value){String s=value==null?"":value.toString();if(s.length()<12||s.getBytes(java.nio.charset.StandardCharsets.UTF_8).length>72)throw new BusinessException("密码至少12位且不超过72字节");return s;}
 /** 姓名不是唯一标识，不按姓名自动绑定已有人员。管理员可明确选择已有档案编号。 */
 public Map<String,Object> fields(Map<String,Object> data,Long id,String role){
  Map<String,Object> result=new LinkedHashMap<>();boolean mobile="STUDENT".equals(role)||"TEACHER".equals(role);
  Object raw=data.get("phone");if(!mobile&&(raw==null||raw.toString().trim().isEmpty()))return result;
  String phone=phone(raw),name=data.get("display_name")==null?"":data.get("display_name").toString().trim();if(name.isEmpty()||name.length()>80)throw new BusinessException("请填写80字以内姓名");
  if(jdbc.queryForObject("SELECT COUNT(*) FROM app_user WHERE (phone=? OR username=?) AND id<>?",Integer.class,phone,phone,id==null?-1:id)>0)throw new BusinessException("手机号已被注册");
  result.put("phone",phone);result.put("display_name",name);result.put("username",phone);
  if(mobile){String column="STUDENT".equals(role)?"student_id":"teacher_id",table="STUDENT".equals(role)?"student":"teacher";Object profile=data.get(column);
   if((profile==null||profile.toString().isEmpty())&&id!=null)profile=jdbc.queryForObject("SELECT "+column+" FROM app_user WHERE id=?",Long.class,id);
   if(profile!=null&&!profile.toString().isEmpty()){long value;try{value=Long.parseLong(profile.toString());}catch(Exception e){throw new BusinessException("人员档案编号无效");}if(jdbc.queryForObject("SELECT COUNT(*) FROM "+table+" WHERE id=?",Integer.class,value)==0)throw new BusinessException("关联人员档案不存在");result.put(column,value);}
   else{org.springframework.jdbc.support.GeneratedKeyHolder key=new org.springframework.jdbc.support.GeneratedKeyHolder();final boolean student="STUDENT".equals(role);final String studentCode=student?numbers.next():null;jdbc.update(c->{java.sql.PreparedStatement ps=c.prepareStatement(student?"INSERT INTO student(code,name) VALUES(?,?)":"INSERT INTO teacher(name) VALUES(?)",java.sql.Statement.RETURN_GENERATED_KEYS);if(student){ps.setString(1,studentCode);ps.setString(2,name);}else ps.setString(1,name);return ps;},key);result.put(column,key.getKey().longValue());}
  }return result;
 }
}
