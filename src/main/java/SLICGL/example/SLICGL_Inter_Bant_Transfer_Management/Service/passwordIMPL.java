package SLICGL.example.SLICGL_Inter_Bant_Transfer_Management.Service;

import SLICGL.example.SLICGL_Inter_Bant_Transfer_Management.APIResponse.customAPIResponse;
import SLICGL.example.SLICGL_Inter_Bant_Transfer_Management.DTO.userListForPasswordResetDTO;
import SLICGL.example.SLICGL_Inter_Bant_Transfer_Management.PasswordUtil.PasswordUtil;
import SLICGL.example.SLICGL_Inter_Bant_Transfer_Management.Repositoriy.UserRepo;
import SLICGL.example.SLICGL_Inter_Bant_Transfer_Management.SqlMappers.userListForPasswordResetMapper;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.JdbcAccessor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Random;

@Service
public class passwordIMPL implements passwordService{
    @Autowired
    UserRepo UserRepository;

    @Autowired
    HandleEmail emailService;

    @Autowired
    JdbcTemplate template;

    @Override
    @Transactional
    public ResponseEntity<customAPIResponse<String>> unlockPassword(String userId) {
        try{
            //Update login attempts;
            int affectedRows = UserRepository.unlockPassword(userId);
            if(affectedRows > 0){
                return ResponseEntity.status(HttpStatus.OK).body(new customAPIResponse<String>(
                        true,
                        "Password unlocked successfully. Please login with existing password!",
                        null
                ));
            }else {
                return ResponseEntity.status(HttpStatus.CONFLICT).body(new customAPIResponse<String>(
                        false,
                        "Password unlock failed. Please contact administrator!",
                        null
                ));
            }
        }catch (Exception e){
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new customAPIResponse<String>(
                    false,
                    "Un-expected error occurred while unlocking the password. Please contact administrator!",
                    null
            ));
        }
    }

    @Transactional
    @Override
    public ResponseEntity<customAPIResponse<String>> resetPassword(String userId) {
        try{
            // Generate 6-digit random temporary password
            Random random = new Random();
            String tempPassword = String.format("%06d", new Random().nextInt(999999));

            // Encrypt the password
            String encryptedPassword = PasswordUtil.encryptPassword(tempPassword);

            //Update login attempts and temporary password;
            int affectedRows = UserRepository.resetPassword(encryptedPassword, userId);

            String Sql = "SELECT user_id, user_epf, user_first_name, user_last_name, user_email FROM user WHERE user_id = ?";
            List<userListForPasswordResetDTO> userList = template.query(Sql, new userListForPasswordResetMapper(), userId);
            if(!userList.isEmpty()){
                // Send email with temporary password
                emailService.sendPasswordResetEmail(
                        userList.get(0).getEmail(),
                        userId,
                        userList.get(0).getFirstName(),
                        userList.get(0).getLastName(),
                        tempPassword
                );
            }else {
                return ResponseEntity.status(HttpStatus.CONFLICT).body(new customAPIResponse<String>(
                        false,
                        "User not found!",
                        null
                ));
            }

            if(affectedRows > 0){
                return ResponseEntity.status(HttpStatus.OK).body(new customAPIResponse<String>(
                        true,
                        "Password reset message sent successfully!",
                        null
                ));
            }else {
                return ResponseEntity.status(HttpStatus.CONFLICT).body(new customAPIResponse<String>(
                        false,
                        "Password reset failed. Please contact administrator!",
                        null
                ));
            }
        }catch (Exception e){
            System.out.println(e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new customAPIResponse<String>(
                    false,
                    "Un-expected error occurred while re-setting the password. Please contact administrator!",
                    null
            ));
        }
    }
}
