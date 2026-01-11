package SLICGL.example.SLICGL_Inter_Bant_Transfer_Management.Security;

import jakarta.servlet.http.HttpSession;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
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

    @Before("@annotation(requiresPermission)")
    public void checkPermission(RequiresPermission requiresPermission) {
        String userId = (String) session.getAttribute("userId");
        if (userId == null) {
            throw new SecurityException("Access Denied: You are not logged in.");
        }
        String Sql = "SELECT COUNT(*) > 0 FROM user_function uf WHERE uf.user_id = ? AND uf.function_id = ?";
        try {
            Integer count = template.queryForObject(
                    Sql,
                    Integer.class,
                    userId,
                    requiresPermission.value()
            );
            if (count == null || count == 0) {
                throw new SecurityException("Access Denied: You don't have permission to access this function.");
            }
        } catch (Exception e) {
            throw new SecurityException("Error checking permissions: " + e.getMessage());
        }
    }
}
