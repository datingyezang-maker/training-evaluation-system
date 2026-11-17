package cn.training.evaluation.modules.evaluation;

import cn.training.evaluation.common.BusinessException;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.*;

/** 可变维度使用关系表保存；发布后模板仍不可变，防止历史评分含义变化。 */
@Service
public class DimensionService {
 private final JdbcTemplate jdbc;
 public DimensionService(JdbcTemplate jdbc){this.jdbc=jdbc;}
 public List<Map<String,Object>> list(Object template){return jdbc.queryForList("SELECT id,label,weight,position_no FROM template_dimension WHERE template_id=? ORDER BY position_no",template);}
 public List<Map<String,Object>> validate(Map<String,Object> data){
  List<?> raw;if(data.containsKey("dimensions")){if(!(data.get("dimensions") instanceof List))throw new BusinessException("评分维度格式不正确");raw=(List<?>)data.get("dimensions");}else{List<Map<String,Object>> old=new ArrayList<>();for(String key:Arrays.asList("teaching","content","organization")){Map<String,Object>d=new HashMap<>();d.put("label",data.get(key+"_label"));d.put("weight",data.get(key+"_weight"));old.add(d);}raw=old;}
  if(raw.isEmpty()||raw.size()>30)throw new BusinessException("请设置1—30个评分维度");List<Map<String,Object>> result=new ArrayList<>();Set<String> labels=new HashSet<>();for(Object item:raw){if(!(item instanceof Map))throw new BusinessException("评分维度格式不正确");Map<?,?>d=(Map<?,?>)item;String label=d.get("label")==null?"":d.get("label").toString().trim();int weight;try{weight=Integer.parseInt(String.valueOf(d.get("weight")));}catch(Exception e){throw new BusinessException("权重必须为1—100整数");}if(label.isEmpty()||label.length()>80||!labels.add(label))throw new BusinessException("维度名称不能为空、重复或超过80字");if(weight<1||weight>100)throw new BusinessException("权重必须为1—100整数");Map<String,Object> clean=new LinkedHashMap<>();clean.put("label",label);clean.put("weight",weight);result.add(clean);}return result;
 }
 public void replace(long template,List<Map<String,Object>> dimensions){jdbc.update("DELETE FROM template_dimension WHERE template_id=?",template);for(int i=0;i<dimensions.size();i++)jdbc.update("INSERT INTO template_dimension(template_id,position_no,label,weight) VALUES(?,?,?,?)",template,i+1,dimensions.get(i).get("label"),dimensions.get(i).get("weight"));}
}
