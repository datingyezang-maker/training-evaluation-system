package cn.training.evaluation.modules.identity;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import cn.training.evaluation.common.BusinessException;
import java.util.Locale;

/** 学号从00001递增，至少五位；事务行锁保证注册、导入、手动新增并发时不重号。 */
@Service
public class StudentNumbers {
    private final JdbcTemplate jdbc;
    public StudentNumbers(JdbcTemplate jdbc){this.jdbc=jdbc;}
    @Transactional(propagation=Propagation.MANDATORY)
    public String next(){
        long value=jdbc.queryForObject("SELECT next_value FROM student_number_sequence WHERE sequence_id=1 FOR UPDATE",Long.class);
        String code;
        do{
            if(value>=Long.MAX_VALUE)throw new BusinessException("学号序列已用尽");
            code=String.format(Locale.ROOT,"%05d",value++);
        }while(jdbc.queryForObject("SELECT COUNT(*) FROM student WHERE code=?",Integer.class,code)>0);
        jdbc.update("UPDATE student_number_sequence SET next_value=? WHERE sequence_id=1",value);
        return code;
    }
}
