package SLICGL.example.SLICGL_Inter_Bant_Transfer_Management.Service;

import SLICGL.example.SLICGL_Inter_Bant_Transfer_Management.APIResponse.customAPIResponse;
import SLICGL.example.SLICGL_Inter_Bant_Transfer_Management.DTO.*;
import SLICGL.example.SLICGL_Inter_Bant_Transfer_Management.Entity.User;
import SLICGL.example.SLICGL_Inter_Bant_Transfer_Management.Entity.subFunction;
import SLICGL.example.SLICGL_Inter_Bant_Transfer_Management.PasswordUtil.PasswordUtil;
import SLICGL.example.SLICGL_Inter_Bant_Transfer_Management.Repositoriy.SubFunctionRepo;
import SLICGL.example.SLICGL_Inter_Bant_Transfer_Management.Repositoriy.UserRepo;
import SLICGL.example.SLICGL_Inter_Bant_Transfer_Management.SqlMappers.userListForFunctionAuthorityMapper;
import SLICGL.example.SLICGL_Inter_Bant_Transfer_Management.SqlMappers.userListForPasswordResetMapper;
import SLICGL.example.SLICGL_Inter_Bant_Transfer_Management.SqlMappers.userSearchMapper;
import SLICGL.example.SLICGL_Inter_Bant_Transfer_Management.UserAuthentication.authenticateUser;
import jakarta.servlet.http.HttpSession;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.MailException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Random;

@Service
public class UserLoginIMPL implements UserService {

    @Autowired
    UserRepo userRepository;
    @Autowired
    HttpSession session;
    @Autowired
    JdbcTemplate template;
    @Autowired
    HandleEmail emailService;
    @Autowired
    authenticateUser authenticateUser;
    @Autowired
    SubFunctionRepo SubFunctionRepository;

    //Below method handle User Login functionality
    @Transactional
    @Override
    public ResponseEntity<customAPIResponse<userLoginResponseDTO>> userLogin(userLoginDTO login) {
        try{
            //Check whether any active user is available for provided Email ID.
            User loginUser = userRepository.getUserFromEmailAndPassword(login.getUserEmail());
            String encryptedPassword = userRepository.getEncryptedPassword(login.getUserEmail());
            boolean passwordMatching = PasswordUtil.verifyPassword(login.getUserPassword(), encryptedPassword);
            if(loginUser == null || !passwordMatching){
                //Check again whether the provided email is valid;
                String validEmail = userRepository.getUserFromEmail(login.getUserEmail());
                if(!validEmail.isEmpty()){
                    //Increase login attempt by 1
                    int currentAttempt = userRepository.getCurrentAttempt(login.getUserEmail());
                    if(currentAttempt > 2){
                        customAPIResponse<userLoginResponseDTO> response = new customAPIResponse<>(
                                false,
                                "Login attempt exceeded. Your account is locked!",
                                null
                        );
                        return ResponseEntity.status(HttpStatus.OK).body(response);
                    }else {
                        int updatedAttempt = currentAttempt + 1;
                        int remainingAttempts = 3 - updatedAttempt;
                        userRepository.updateLoginAttemptForEmail(updatedAttempt, login.getUserEmail());
                        // when a user not available for given credentials.
                        customAPIResponse<userLoginResponseDTO> response = new customAPIResponse<>(
                                false,
                                "Invalid login credentials. You have " + remainingAttempts + " remaining login attempts",
                                null
                        );
                        return ResponseEntity.status(HttpStatus.OK).body(response);
                    }
                }else {
                    // when a user not available for given credentials.
                    customAPIResponse<userLoginResponseDTO> response = new customAPIResponse<>(
                            false,
                            "No user available for provided credentials!",
                            null
                    );
                    return ResponseEntity.status(HttpStatus.OK).body(response);
                }

            }else{
                // when user is not in active status.(User is already deleted)
                if(loginUser.getUserActiveStatus() == 0){
                    customAPIResponse<userLoginResponseDTO> response = new customAPIResponse<>(
                            false,
                            "User is not in active or User has been removed!",
                            null
                    );
                    return ResponseEntity.status(HttpStatus.OK).body(response);
                }else{
                    //Check whether the user login attempts are exceeded;
                    if(loginUser.getLoginAttempts() >= 3){
                        customAPIResponse<userLoginResponseDTO> response = new customAPIResponse<>(
                                false,
                                "Login attempt exceeded. Your account is locked!",
                                null
                        );
                        return ResponseEntity.status(HttpStatus.OK).body(response);
                    }else {
                        // When the User is in the Password Reset stage;
                        if(loginUser.getIsFirstLogin() == 1){
                            customAPIResponse<userLoginResponseDTO> response = new customAPIResponse<>(
                                    false,
                                    "Please Reset the temporary password using Reset option!",
                                    null
                            );
                            return ResponseEntity.status(HttpStatus.OK).body(response);
                        }else {
                            //Set login attempt as 0;
                            userRepository.updateLoginAttempt(loginUser.getUserEmail(), loginUser.getUserPassword());
                            // when an active user is available.
                            session.setAttribute("userId",loginUser.getUserId());

                            // Get the authorized function IDs for the user
                            String Sql = "SELECT function_id FROM user_function WHERE user_id = ?";
                            List<String> functionIds = template.queryForList(Sql, String.class, loginUser.getUserId());

                            customAPIResponse<userLoginResponseDTO> response = new customAPIResponse<>(
                                    true,
                                    null,
                                    new userLoginResponseDTO(
                                            loginUser.getUserTitle(),
                                            loginUser.getUserFirstName(),
                                            loginUser.getUserLastName(),
                                            loginUser.getUserLevel(),
                                            functionIds
                                    )// Set values for userLoginResponseDTO;
                            );
                            return ResponseEntity.status(HttpStatus.OK).body(response);
                        }
                    }
                }
            }
        }catch (Exception e){
            System.out.println(e.getMessage());
            customAPIResponse<userLoginResponseDTO> response = new customAPIResponse<>(
                    false,
                    "Un-expected error occurred while login to the system. Please contact administrator!",
                    null
            );
            return ResponseEntity.status(HttpStatus.OK).body(response);
        }
    }

    //Below method handle User Registration functionality
    @Transactional
    @Override
    public ResponseEntity<customAPIResponse<String>> userRegister(userRegisterDTO userRegister) {
            try{
                String newUserId;
                //Check the last user_id in the database.
                String lastUserId = userRepository.getLastUserId();
                if(lastUserId == null){
                    //When last user_id is null
                    newUserId = "USR-01";
                }else{
                    //When a last user_id is available
                    int newNumericUserId = Integer.parseInt(lastUserId.replace("USR-","")) + 1;
                    newUserId = String.format("USR-%02d", newNumericUserId);
                }

                // Generate 6-digit random temporary password
                Random random = new Random();
                String tempPassword = String.format("%06d", new Random().nextInt(999999));

                // Encrypt the password
                String encryptedPassword = PasswordUtil.encryptPassword(tempPassword);

                //Create new user object using constructor in order to save in the database
                User registerUserObj = new User(
                        newUserId,
                        userRegister.getUserTitle(),
                        userRegister.getUserFirstName(),
                        userRegister.getUserLastName(),
                        userRegister.getDepartment(),
                        userRegister.getSection(),
                        userRegister.getUserPosition(),
                        userRegister.getUserEpf(),
                        userRegister.getUserEmail(),
                        encryptedPassword,
                        1,
                        1,
                        0,
                        userRegister.getUserLevel(),
                        LocalDateTime.now(),
                        userRepository.findById(session.getAttribute("userId").toString()).get(),
                        userRegister.getUserSignature().getBytes(),
                        null
                );

                //After the successful registration, the system should check whether the user is an admin.
                //If user is an admin, update the DB with granting authorities for all system functionalities.
                if(userRegister.getUserLevel() == 1){
                    // Get all functions from the database
                    List<subFunction> allFunctions = SubFunctionRepository.findAll();
                    // Set the functions to the user object
                    registerUserObj.setSubFunctions(allFunctions);
                }
                userRepository.save(registerUserObj);

                // Send email with temporary password
                emailService.sendTemporaryPasswordEmail(
                        userRegister.getUserEmail(),
                        newUserId,
                        userRegister.getUserFirstName(),
                        userRegister.getUserLastName(),
                        tempPassword
                );
                customAPIResponse<String> response = new customAPIResponse<>(
                        true,
                        "User successfully registered with User ID: " + newUserId + "." + " " + "Temporary password has been sent to the user's email.",
                        null
                );
                return ResponseEntity.status(HttpStatus.OK).body(response);

            }catch (MailException e){
                customAPIResponse<String> response = new customAPIResponse<>(
                        false,
                        e.getMessage(),
                        null
                );
                return ResponseEntity.status(HttpStatus.OK).body(response);
            }catch (Exception e){
                //return "Unexpected error occurred while registering the user. Please contact administrator!";
                customAPIResponse<String> response = new customAPIResponse<>(
                        false,
                        e.getMessage(),
                        null
                );
                return ResponseEntity.status(HttpStatus.OK).body(response);
            }

    }

    @Override
    public ResponseEntity<customAPIResponse<searchUserDTO>> searchUser(String userId) {

            try{
                String Sql = "SELECT Usr1.user_id, Usr1.user_first_name, Usr1.user_last_name, Usr1.user_epf, Usr1.user_email, CASE WHEN Usr1.user_active_status = '0' THEN 'In-Active' WHEN Usr1.user_active_status = '1' THEN 'Active' END AS 'user_active_status', CASE WHEN Usr1.is_admin = '0' THEN 'General User' WHEN Usr1.is_admin = '1' THEN 'Administrator'END AS 'user_level', Usr1.user_created_date, Usr2.user_first_name AS 'user_created_by', Usr1.user_position AS 'user_position', Usr1.department AS 'department', Usr1.section AS 'section' FROM user Usr1 LEFT JOIN user Usr2 ON Usr1.user_created_by = Usr2.user_id WHERE Usr1.user_id = ?";
                List<searchUserDTO> searchedUser = template.query(Sql, new Object[]{userId}, new userSearchMapper());
                if(searchedUser.isEmpty()){
                    customAPIResponse<searchUserDTO> response = new customAPIResponse<>(
                            false,
                            "User not found for provided User ID!",
                            null
                    );
                    return ResponseEntity.status(HttpStatus.OK).body(response);
                }else{
                    //Check whether user is already deleted or not;
                    if(searchedUser.get(0).getUserActiveStatus().equals("In-Active")){
                        customAPIResponse<searchUserDTO> response = new customAPIResponse<>(
                                false,
                                "User ID: " + userId + " is already deleted!",
                                null
                        );
                        return ResponseEntity.status(HttpStatus.OK).body(response);
                    }else{
                        searchUserDTO userObj = searchedUser.get(0);
                        customAPIResponse<searchUserDTO> response = new customAPIResponse<>(
                                true,
                                null,
                                userObj
                        );
                        return ResponseEntity.status(HttpStatus.OK).body(response);
                    }
                }
            }catch (Exception e){
                customAPIResponse<searchUserDTO> response = new customAPIResponse<>(
                        false,
                        "An exception occurred while getting User details. Please contact administrator!",
                        null
                );
                return ResponseEntity.status(HttpStatus.OK).body(response);
            }

    }

    @Transactional
    @Override
    public ResponseEntity<customAPIResponse<updateUserDTO>> updateUser(updateUserDTO updatedUser) {
        System.out.println();
            try{
                //Getting existing user record relevant to the User ID and update the values of the record.
                User existingUser = userRepository.findById(updatedUser.getUserId()).get();
                existingUser.setUserFirstName(updatedUser.getUserFirstName());
                existingUser.setUserLastName(updatedUser.getUserLastName());
                existingUser.setUserEpf(updatedUser.getUserEpf());
                existingUser.setUserEmail(updatedUser.getUserEmail());
                existingUser.setUserPosition(updatedUser.getUserPosition());
                // Only update signature if it's not null AND not empty
                if (updatedUser.getUserSignature() != null &&
                        !updatedUser.getUserSignature().isEmpty()) {
                    existingUser.setUserSignature(updatedUser.getUserSignature().getBytes());
                }
                //Updated to the Database;
                userRepository.save(existingUser);
                //Define CustomApiResponse;
                customAPIResponse<updateUserDTO> response = new customAPIResponse<>(
                        true,
                        "User ID: " + updatedUser.getUserId() + " updated successfully!",
                        null
                );
                return ResponseEntity.status(HttpStatus.OK).body(response);
            }catch (Exception e){
                customAPIResponse<updateUserDTO> response = new customAPIResponse<>(
                        false,
                        "Un-expected error occurred while updating the User ID " + updatedUser.getUserId() + "." + " Please contact the administrator!",
                        null
                );
                return ResponseEntity.status(HttpStatus.OK).body(response);
            }

    }

    @Transactional
    @Override
    public ResponseEntity<customAPIResponse<String>> deleteUser(String userId) {

            try{
                if(session.getAttribute("userId").toString().equals(userId)){
                    //Check whether the deleting User ID is equal to the session User ID;
                    customAPIResponse<String> response = new customAPIResponse<>(
                            false,
                            "Unable to delete this user account, while you are login with same user account. Please login with another user account!",
                            null
                    );
                    return ResponseEntity.status(HttpStatus.OK).body(response);
                }else{
                    int affectedDeleteRow = userRepository.deleteUser(session.getAttribute("userId").toString(),userId);
                    customAPIResponse<String> response = new customAPIResponse<>(
                            true,
                            "User: " + userId + " deleted successfully. This User have no further authority to use this User Account!",
                            null
                    );
                    return ResponseEntity.status(HttpStatus.OK).body(response);
                }
            }catch (Exception e){
                customAPIResponse<String> response = new customAPIResponse<>(
                        false,
                        "Un-expected error occurred while deleting the User ID " + userId + " Please contact the administrator!",
                        null
                );
                return ResponseEntity.status(HttpStatus.OK).body(response);
            }

    }

    @Transactional
    @Override
    public ResponseEntity<customAPIResponse<String>> passwordReset(userPasswordReset resetData) {
        try{
            // Check whether any User is available for provided Email;
            User availableUser = userRepository.getPasswordResetUser(resetData.getUserEmail());
            //Check whether encrypted password and user provided temporary password is matched.
            boolean passwordMatchingStatus = PasswordUtil.verifyPassword(resetData.getTemporaryPassword(), userRepository.getEncryptedPassword(resetData.getUserEmail()));

            if(availableUser == null || !passwordMatchingStatus){
                customAPIResponse<String> response = new customAPIResponse<>(
                        false,
                        "No User available for provided Email. Please re-check the credentials!",
                        null
                );
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
            }else {
                //Encrypt user changed password;
                String encryptedPassword = PasswordUtil.encryptPassword(resetData.getChangedPassword());
                int affectedRow = userRepository.passwordChange(encryptedPassword, resetData.getUserEmail());
                if(affectedRow == 0){
                    customAPIResponse<String> response = new customAPIResponse<>(
                            false,
                            "Could not changed the password. Please contact administrator!",
                            null
                    );
                    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
                }else {
                    customAPIResponse<String> response = new customAPIResponse<>(
                            true,
                            "Password changed successfully. Please use new password for login!",
                            null
                    );
                    return ResponseEntity.status(HttpStatus.OK).body(response);
                }
            }
        }catch (Exception e){
            customAPIResponse<String> response = new customAPIResponse<>(
                    false,
                    "Un-expected error occurred. Please contact administrator!",
                    null
            );
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    @Override
    public ResponseEntity<customAPIResponse<List<usersForFunctionAuthorityDTO>>> userListForFunctionAuthority() {
        try{
            String Sql = "SELECT user_id, user_epf, user_first_name, user_last_name FROM user WHERE user_active_status = 1";
            List<usersForFunctionAuthorityDTO> userList = template.query(Sql, new userListForFunctionAuthorityMapper());
            if(userList.isEmpty()){
                return ResponseEntity.status(HttpStatus.CONFLICT).body(new customAPIResponse<>(
                        false,
                        "No Users available!",
                        null
                ));
            }else {
                return ResponseEntity.status(HttpStatus.OK).body(new customAPIResponse<>(
                        true,
                        null,
                        userList
                ));
            }
        }catch (Exception e){
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new customAPIResponse<>(
                    false,
                    "Un-expected error occurred. Please contact administrator!",
                    null
            ));
        }
    }

    @Override
    public ResponseEntity<customAPIResponse<List<userListForPasswordResetDTO>>> userListForPasswordReset() {
        try{
            String Sql = "SELECT user_id, user_epf, user_first_name, user_last_name, user_email FROM user WHERE user_active_status = 1";
            List<userListForPasswordResetDTO> userList = template.query(Sql, new userListForPasswordResetMapper());
            if(userList.isEmpty()){
                return ResponseEntity.status(HttpStatus.CONFLICT).body(new customAPIResponse<>(
                        false,
                        "No Users available!",
                        null
                ));
            }else {
                return ResponseEntity.status(HttpStatus.OK).body(new customAPIResponse<>(
                        true,
                        null,
                        userList
                ));
            }
        }catch (Exception e){
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new customAPIResponse<>(
                    false,
                    "Un-expected error occurred. Please contact administrator!",
                    null
            ));
        }
    }
}
