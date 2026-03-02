package SLICGL.example.SLICGL_Inter_Bant_Transfer_Management.HttpRequestUtil;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class HttpRequestUtil {
    @Autowired
    private HttpServletRequest request;

    public String getClientIP() {
        String ipAddress = request.getHeader("X-Forwarded-For");
        if (ipAddress != null && !ipAddress.isEmpty()) {
            ipAddress = ipAddress.split(",")[0].trim();
        } else {
            ipAddress = request.getRemoteAddr();
        }
        return ipAddress;
    }
}
