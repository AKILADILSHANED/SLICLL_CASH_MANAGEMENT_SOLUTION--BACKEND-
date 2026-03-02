package SLICGL.example.SLICGL_Inter_Bant_Transfer_Management.Security;

import jakarta.servlet.http.HttpSession;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class SecurityAspect {
    @Autowired
    HttpSession session;
    @Autowired
    JdbcTemplate template;

    @Around("@annotation(requiresPermission)")
    public Object checkPermission(ProceedingJoinPoint joinPoint, RequiresPermission requiresPermission) throws Throwable {
        String userId = (String) session.getAttribute("userId");
        if (userId == null) {
            throw new SecurityException("Session Expired. Please Re-login to the system");
        }
        String Sql = "SELECT COUNT(*) > 0 FROM user_function uf WHERE uf.user_id = ? AND uf.function_id = ?";
        Integer count = template.queryForObject(
                Sql,
                Integer.class,
                userId,
                requiresPermission.value()
        );
        if (count == null || count == 0) {
            throw new SecurityException("Access Denied: No permission to access this function");
        }
        return joinPoint.proceed();
    }
}
