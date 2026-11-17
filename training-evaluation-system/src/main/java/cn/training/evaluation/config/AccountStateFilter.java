package cn.training.evaluation.config;

import cn.training.evaluation.modules.identity.DatabaseUsers;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.security.core.*;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import javax.servlet.*;
import javax.servlet.http.*;
import java.io.IOException;

/** 每个请求核对数据库账号状态、角色和密码摘要，停用/改密后旧会话立即失效。 */
@Component
public class AccountStateFilter extends OncePerRequestFilter {
    private final DatabaseUsers users;
    public AccountStateFilter(DatabaseUsers users){this.users=users;}
    /** 静态样式和脚本是公开资源，不必为每个子资源重复读取账号；业务 API 仍逐次校验。 */
    @Override protected boolean shouldNotFilter(HttpServletRequest request){return request.getServletPath().startsWith("/assets/");}
    @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain)throws ServletException,IOException{
        Authentication current=SecurityContextHolder.getContext().getAuthentication();
        if(current!=null && !(current instanceof AnonymousAuthenticationToken)){
            try{
                UserDetails latest=users.loadUserByUsername(current.getName());
                Object loginHash=request.getSession(false)==null?null:request.getSession(false).getAttribute("accountHash");
                if(!latest.isEnabled() || (loginHash!=null && !loginHash.equals(latest.getPassword()))) {
                    SecurityContextHolder.clearContext();if(request.getSession(false)!=null)request.getSession(false).invalidate();
                }else SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(latest,null,latest.getAuthorities()));
            }catch(org.springframework.security.core.userdetails.UsernameNotFoundException ex){SecurityContextHolder.clearContext();if(request.getSession(false)!=null)request.getSession(false).invalidate();}
        }
        chain.doFilter(request,response);
    }
}
