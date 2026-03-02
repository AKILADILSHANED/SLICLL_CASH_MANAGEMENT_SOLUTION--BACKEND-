package SLICGL.example.SLICGL_Inter_Bant_Transfer_Management.Logs;

import SLICGL.example.SLICGL_Inter_Bant_Transfer_Management.APIResponse.customAPIResponse;
import SLICGL.example.SLICGL_Inter_Bant_Transfer_Management.HttpRequestUtil.HttpRequestUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import jakarta.servlet.http.HttpSession;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalTime;

@Aspect
@Component
public class LogAspect {

    @Autowired
    HttpSession session;
    @Autowired
    HttpRequestUtil httpRequest;
    @Autowired
    private com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    private static final Logger logger = LoggerFactory.getLogger(LogAspect.class);

    @Around("@annotation(logActivity)")
    public Object logActivity(ProceedingJoinPoint joinPoint, LogActivity logActivity) throws Throwable {
        //Get relevant user id in current session
        String sessionUser = (String) session.getAttribute("userId");
        String action = joinPoint.getSignature().getName();
        String ipAddress = httpRequest.getClientIP();
        Object[] params = joinPoint.getArgs();

        ObjectMapper prettyMapper = objectMapper.copy();
        prettyMapper.enable(SerializationFeature.INDENT_OUTPUT);

        //This log will be printed before each method starts
        logger.info("\n" + "-".repeat(90) + "\n" +
                "Log Date: " + LocalDate.now() + "\n" +
                "Log Time: " + LocalTime.now() + "\n" +
                "User: " + sessionUser + "\n" +
                "Action: " + action + "\n" +
                "Status: " + "Started" + "\n" +
                "Description: " + logActivity.methodDescription() + "\n" +
                "Message: " + "N/A" + "\n" +
                "Params: " + prettyMapper.writeValueAsString(params) + "\n" +
                "Return Result: " + "null" + "\n" +
                "IP Address: " + ipAddress + "\n" +
                "-".repeat(90));

        try {
            //Proceed the method
            Object result = joinPoint.proceed();
            //Check whether the result is a customAPIResponse;
            if (result instanceof ResponseEntity) {
                //Check whether the customAPIResponse success message is true or false
                ResponseEntity<?> responseEntity = (ResponseEntity<?>) result;
                Object body = responseEntity.getBody();
                if (body instanceof customAPIResponse) {
                    customAPIResponse<?> apiResponse = (customAPIResponse<?>) body;
                    if (apiResponse.isSuccess()) {
                        // Log a success message here
                        logger.info("\n" + "-".repeat(90) + "\n" +
                                "Log Date: " + LocalDate.now() + "\n" +
                                "Log Time: " + LocalTime.now() + "\n" +
                                "User: " + sessionUser + "\n" +
                                "Action: " + action + "\n" +
                                "Status: " + "Succeed" + "\n" +
                                "Description: " + logActivity.methodDescription() + "\n" +
                                "Message: " + apiResponse.getMessage() + "\n" +
                                "Params: " + prettyMapper.writeValueAsString(params) + "\n" +
                                "Return Result: " + (apiResponse.getResponseObject() != null ?
                                prettyMapper.writeValueAsString(apiResponse.getResponseObject()) : "null") + "\n" +
                                "IP Address: " + ipAddress + "\n" +
                                "-".repeat(90));
                        return result;
                    } else {
                        // Log an error message here
                        logger.error("\n" + "-".repeat(90) + "\n" +
                                "Log Date: " + LocalDate.now() + "\n" +
                                "Log Time: " + LocalTime.now() + "\n" +
                                "User: " + sessionUser + "\n" +
                                "Action: " + action + "\n" +
                                "Status: " + "Failed" + "\n" +
                                "Description: " + logActivity.methodDescription() + "\n" +
                                "Message: " + apiResponse.getMessage() + "\n" +
                                "Params: " + prettyMapper.writeValueAsString(params) + "\n" +
                                "Return Result: " + (apiResponse.getResponseObject() != null ?
                                prettyMapper.writeValueAsString(apiResponse.getResponseObject()) : "null") + "\n" +
                                "IP Address: " + ipAddress + "\n" +
                                "-".repeat(90));
                        return result;
                    }
                } else {
                    return result;
                }
            } else {
                //When the method does not return a customAPIResponse
                logger.info("\n" + "-".repeat(90) + "\n" +
                        "Log Date: " + LocalDate.now() + "\n" +
                        "Log Time: " + LocalTime.now() + "\n" +
                        "User: " + sessionUser + "\n" +
                        "Action: " + action + "\n" +
                        "Status: " + "Executed" + "\n" +
                        "Description: " + logActivity.methodDescription() + "\n" +
                        "Message: " + "N/A" + "\n" +
                        "Params: " + prettyMapper.writeValueAsString(params) + "\n" +
                        "Return Result: " + result + "\n" +
                        "IP Address: " + ipAddress + "\n" +
                        "-".repeat(90));
                return result;
            }
        } catch (Exception e) {
            //This log message display any exception is occurred
            logger.error("\n" + "-".repeat(90) + "\n" +
                    "Log Date: " + LocalDate.now() + "\n" +
                    "Log Time: " + LocalTime.now() + "\n" +
                    "User: " + sessionUser + "\n" +
                    "Action: " + action + "\n" +
                    "Status: " + "Failed" + "\n" +
                    "Description: " + logActivity.methodDescription() + "\n" +
                    "Message: " + e.getMessage() + "\n" +
                    "Params: " + prettyMapper.writeValueAsString(params) + "\n" +
                    "Return Result: " + "null" + "\n" +
                    "IP Address: " + ipAddress + "\n" +
                    "-".repeat(90));
            throw e;
        }
    }
}
