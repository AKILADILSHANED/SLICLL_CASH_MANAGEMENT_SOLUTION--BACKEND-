package SLICGL.example.SLICGL_Inter_Bant_Transfer_Management.Service;

import SLICGL.example.SLICGL_Inter_Bant_Transfer_Management.APIResponse.customAPIResponse;
import SLICGL.example.SLICGL_Inter_Bant_Transfer_Management.DTO.*;
import SLICGL.example.SLICGL_Inter_Bant_Transfer_Management.Entity.User;
import SLICGL.example.SLICGL_Inter_Bant_Transfer_Management.Entity.subFunction;
import SLICGL.example.SLICGL_Inter_Bant_Transfer_Management.ExceptionHandlers.*;
import SLICGL.example.SLICGL_Inter_Bant_Transfer_Management.ExceptionHandlers.UserExceptions.*;
import SLICGL.example.SLICGL_Inter_Bant_Transfer_Management.ExceptionHandlers.UserExceptions.PasswordResetException;
import SLICGL.example.SLICGL_Inter_Bant_Transfer_Management.Logs.LogActivity;
import SLICGL.example.SLICGL_Inter_Bant_Transfer_Management.PasswordUtil.PasswordUtil;
import SLICGL.example.SLICGL_Inter_Bant_Transfer_Management.Repositoriy.SubFunctionRepo;
import SLICGL.example.SLICGL_Inter_Bant_Transfer_Management.Repositoriy.UserRepo;
import SLICGL.example.SLICGL_Inter_Bant_Transfer_Management.Security.RequiresPermission;
import SLICGL.example.SLICGL_Inter_Bant_Transfer_Management.SqlMappers.userListForFunctionAuthorityMapper;
import SLICGL.example.SLICGL_Inter_Bant_Transfer_Management.SqlMappers.userListForPasswordResetMapper;
import SLICGL.example.SLICGL_Inter_Bant_Transfer_Management.SqlMappers.userSearchMapper;
import jakarta.servlet.http.HttpSession;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.MailException;
import org.springframework.stereotype.Service;

import java.io.IOException;
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
    SubFunctionRepo SubFunctionRepository;

    private static final Logger logger = LoggerFactory.getLogger(UserLoginIMPL.class);

    //Below method handle User Login functionality
    @Transactional
    @Override
    public ResponseEntity<customAPIResponse<userLoginResponseDTO>> userLogin(userLoginDTO login) {
        logger.info("Email: {} | User login process started.", login.getUserEmail());
        //Check whether user provided an email and password;
        if (login.getUserEmail() == null || login.getUserPassword() == null || login.getUserEmail().isEmpty() || login.getUserPassword().isEmpty()) {
            //User not provided values for email and password or both fields;
            logger.error("Email: {} | Login was unsuccessful. User did not provided either Email Address or Password or both for a successful login.", login.getUserEmail());
            throw new InvalidLoginDataException("Please provide both valid Email Address and Password for successful login.");
        } else {
            //Check whether any active user is available for provided Email ID.
            User loginUser = userRepository.getUserFromEmailAndPassword(login.getUserEmail());
            String encryptedPassword = userRepository.getEncryptedPassword(login.getUserEmail());
            boolean passwordMatching = PasswordUtil.verifyPassword(login.getUserPassword(), encryptedPassword);
            if (loginUser == null || !passwordMatching) {
                //Check again whether the provided email is valid while password is invalid;
                String validEmail = userRepository.getUserFromEmail(login.getUserEmail());
                if (validEmail != null) {
                    //Increase login attempt by 1
                    int currentAttempt = userRepository.getCurrentAttempt(login.getUserEmail());
                    if (currentAttempt > 2) {
                        logger.error("Email: {} | User's login attempts have been already exceeded. Unable to login. | [Current Attempts: {}]", login.getUserEmail(), currentAttempt);
                        throw new LoginAttemptExceedException("Your User Account has been locked due to exceeded login attempts. Please contact administrator to reset your account.");
                    } else {
                        int updatedAttempt = currentAttempt + 1;
                        int remainingAttempts = 3 - updatedAttempt;
                        userRepository.updateLoginAttemptForEmail(updatedAttempt, login.getUserEmail());
                        logger.warn("Email: {} | User's login was unsuccessful. | Current Attempt: {} | Remaining Attempts {}", login.getUserEmail(), updatedAttempt, remainingAttempts);
                        // when a user not available for given credentials.
                        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(
                                new customAPIResponse<>(
                                        false,
                                        "Login Un-successful. You have only " + remainingAttempts + " attempts. If exceeded, your account will be locked.",
                                        null
                                )
                        );
                    }
                } else {
                    //When a user not available for given credentials.
                    logger.info("Email: {} | User was not available for provided credentials.", login.getUserEmail());
                    throw new InvalidLoginDataException("No user available for provided credentials. Please provide valid credentials.");
                }
            } else {
                // when user is not in active status.(User is already deleted)
                if (loginUser.getUserActiveStatus() == 0) {
                    logger.info("Email: {} | User was not in active status or User has been removed.", login.getUserEmail());
                    throw new InactiveUserException("User is not in active or User has been removed.");
                } else {
                    //Check whether the user login attempts are exceeded;
                    if (loginUser.getLoginAttempts() >= 3) {
                        logger.info("Email: {} | User's login attempt was exceeded. Account has been locked. | [Current Attempt: {}]", login.getUserEmail(), loginUser.getLoginAttempts());
                        throw new LoginAttemptExceedException("Login attempt exceeded. Your account is locked.");
                    } else {
                        // When the User is in the Password Reset stage;
                        if (loginUser.getIsFirstLogin() == 1) {
                            logger.info("Email: {} | User was in Password Reset stage. User needed to reset new password for successful login.", login.getUserEmail());
                            throw new PasswordResetException("You need to reset your password before the login");
                        } else {
                            //Set login attempt as 0;
                            userRepository.updateLoginAttempt(loginUser.getUserEmail(), loginUser.getUserPassword());
                            // when an active user is available.
                            session.setAttribute("userId", loginUser.getUserId());
                            logger.info("Email: {} | User Id successfully stored in the session. | [Session User ID: {}]", login.getUserEmail(), session.getAttribute("userId").toString());

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
                            logger.info("User ID: {} | User successfully redirected to the Dashboard.", session.getAttribute("userId").toString());
                            return ResponseEntity.status(HttpStatus.OK).body(response);
                        }
                    }
                }
            }
        }
    }

    //Below method handle User Registration functionality
    @Transactional
    @Override
    @RequiresPermission("FUNC-001")
    @LogActivity(methodDescription = "This method will register a new user")
    public ResponseEntity<customAPIResponse<String>> userRegister(userRegisterDTO userRegister) {
        String sessionUser = session.getAttribute("userId").toString();
        //Check whether user provided data is empty or not
        if (userRegister.getUserTitle().isEmpty() || userRegister.getUserLevel() == null || userRegister.getUserFirstName().isEmpty() || userRegister.getUserLastName().isEmpty() || userRegister.getDepartment().isEmpty() || userRegister.getSection().isEmpty() || userRegister.getUserPosition().isEmpty() || userRegister.getUserEmail().isEmpty() || userRegister.getUserEpf().isEmpty()) {
            throw new UserInputDataViolationException("Please provide required information for a successful User Registration.");
        } else {
            try {
                // Generate new user ID
                String lastUserId = userRepository.getLastUserId();
                String newUserId;
                if (lastUserId == null) {
                    newUserId = "USR-01";
                } else {
                    int newNumericUserId = Integer.parseInt(lastUserId.replace("USR-", "")) + 1;
                    newUserId = String.format("USR-%02d", newNumericUserId);
                }
                // Generate and encrypt temporary password
                String tempPassword = String.format("%06d", new Random().nextInt(999999));
                String encryptedPassword = PasswordUtil.encryptPassword(tempPassword);
                // Create user object
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
                        1, 1, 0,
                        userRegister.getUserLevel(),
                        LocalDateTime.now(),
                        userRepository.findById(sessionUser).get(),
                        userRegister.getUserSignature().getBytes(),
                        null
                );
                // Grant admin privileges if needed
                if (userRegister.getUserLevel() == 1) {
                    List<subFunction> allFunctions = SubFunctionRepository.findAll();
                    registerUserObj.setSubFunctions(allFunctions);
                }
                // Save user
                userRepository.save(registerUserObj);
                // Send email
                emailService.sendTemporaryPasswordEmail(
                        userRegister.getUserEmail(),
                        newUserId,
                        userRegister.getUserFirstName(),
                        userRegister.getUserLastName(),
                        tempPassword
                );
                customAPIResponse<String> response = new customAPIResponse<>(
                        true,
                        "User successfully registered with User ID: " + newUserId + ". Temporary password has been sent to the user's email.",
                        null
                );
                return ResponseEntity.status(HttpStatus.OK).body(response);
            } catch (MailException e) {
                customAPIResponse<String> response = new customAPIResponse<>(
                        false,
                        "User registered successfully. But failed to send email. Please contact administrator.",
                        null
                );
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
            } catch (Exception e) {
                customAPIResponse<String> response = new customAPIResponse<>(
                        false,
                        "Unexpected error occurred. Please contact administrator." + e.getMessage(),
                        null
                );
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
            }
        }
    }

    @RequiresPermission("FUNC-002")
    @Override
    @LogActivity(methodDescription = "This method will display user details")
    public ResponseEntity<customAPIResponse<searchUserDTO>> searchUser(String userId) {
        //Check whether user has provided a User ID to check user details
        if (userId == null || userId.isEmpty()) {
            throw new UserInputDataViolationException("Please provide a User ID for successful searching.");
        } else {
            String Sql = "SELECT Usr1.user_id, Usr1.user_first_name, Usr1.user_last_name, Usr1.user_epf, Usr1.user_email, CASE WHEN Usr1.user_active_status = '0' THEN 'In-Active' WHEN Usr1.user_active_status = '1' THEN 'Active' END AS 'user_active_status', CASE WHEN Usr1.is_admin = '0' THEN 'General User' WHEN Usr1.is_admin = '1' THEN 'Administrator'END AS 'user_level', Usr1.user_created_date, Usr2.user_first_name AS 'user_created_by', Usr1.user_position AS 'user_position', Usr1.department AS 'department', Usr1.section AS 'section' FROM user Usr1 LEFT JOIN user Usr2 ON Usr1.user_created_by = Usr2.user_id WHERE Usr1.user_id = ?";
            List<searchUserDTO> searchedUser = template.query(Sql, new Object[]{userId}, new userSearchMapper());
            if (searchedUser.isEmpty()) {
                //User not available for provided User ID
                throw new UserInputDataViolationException("No User found for provided User ID.");
            } else {
                //Check whether user is already deleted or not
                if (searchedUser.get(0).getUserActiveStatus().equals("In-Active")) {
                    return ResponseEntity.status(HttpStatus.CONFLICT).body(
                            new customAPIResponse<>(
                                    false,
                                    "User ID: " + userId + " is already deleted.",
                                    null
                            )
                    );
                } else {
                    searchUserDTO userObj = searchedUser.get(0);
                    return ResponseEntity.status(HttpStatus.OK).body(
                            new customAPIResponse<>(
                                    true,
                                    null,
                                    userObj
                            )
                    );
                }
            }
        }
    }

    @RequiresPermission("FUNC-003")
    @Override
    @LogActivity(methodDescription = "This method will display user details for update")
    public ResponseEntity<customAPIResponse<searchUserDTO>> searchUserForUpdate(String userId) {
        //Check whether user has provided a User ID to check user details
        if (userId == null || userId.isEmpty()) {
            throw new UserInputDataViolationException("Please provide a User ID for successful searching.");
        } else {
            String Sql = "SELECT Usr1.user_id, Usr1.user_first_name, Usr1.user_last_name, Usr1.user_epf, Usr1.user_email, CASE WHEN Usr1.user_active_status = '0' THEN 'In-Active' WHEN Usr1.user_active_status = '1' THEN 'Active' END AS 'user_active_status', CASE WHEN Usr1.is_admin = '0' THEN 'General User' WHEN Usr1.is_admin = '1' THEN 'Administrator'END AS 'user_level', Usr1.user_created_date, Usr2.user_first_name AS 'user_created_by', Usr1.user_position AS 'user_position', Usr1.department AS 'department', Usr1.section AS 'section' FROM user Usr1 LEFT JOIN user Usr2 ON Usr1.user_created_by = Usr2.user_id WHERE Usr1.user_id = ?";
            List<searchUserDTO> searchedUser = template.query(Sql, new Object[]{userId}, new userSearchMapper());
            if (searchedUser.isEmpty()) {
                //User not available for provided User ID
                throw new UserInputDataViolationException("No User found for provided User ID.");
            } else {
                //Check whether user is already deleted or not
                if (searchedUser.get(0).getUserActiveStatus().equals("In-Active")) {
                    return ResponseEntity.status(HttpStatus.CONFLICT).body(
                            new customAPIResponse<>(
                                    false,
                                    "User ID: " + userId + " is already deleted.",
                                    null
                            )
                    );
                } else {
                    searchUserDTO userObj = searchedUser.get(0);
                    return ResponseEntity.status(HttpStatus.OK).body(
                            new customAPIResponse<>(
                                    true,
                                    null,
                                    userObj
                            )
                    );
                }
            }
        }
    }

    @Transactional
    @Override
    @RequiresPermission("FUNC-003")
    @LogActivity(methodDescription = "This method will update user details")
    public ResponseEntity<customAPIResponse<updateUserDTO>> updateUser(updateUserDTO updatedUser) {
        //Check whether the user has provided updated details
        if (updatedUser.getUserFirstName().isEmpty() || updatedUser.getUserLastName().isEmpty() || updatedUser.getUserEpf().isEmpty() || updatedUser.getUserEmail().isEmpty() || updatedUser.getUserPosition().isEmpty()) {
            throw new UserInputDataViolationException("Please provide required user updating details for successful user updating.");
        } else {
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
                try {
                    existingUser.setUserSignature(updatedUser.getUserSignature().getBytes());
                } catch (IOException e) {
                    throw new RuntimeException("Unable to update User's Signature image. Please contact administrator");
                }
            }
            //Updated to the Database;
            userRepository.save(existingUser);
            //Define CustomApiResponse;
            return ResponseEntity.status(HttpStatus.OK).body(
                    new customAPIResponse<>(
                            true,
                            "User ID: " + updatedUser.getUserId() + " updated successfully.",
                            null
                    )
            );
        }
    }

    @RequiresPermission("FUNC-004")
    @Override
    @LogActivity(methodDescription = "This method will display user details for delete")
    public ResponseEntity<customAPIResponse<searchUserDTO>> searchUserForDelete(String userId) {
        //Check whether user has provided a User ID to check user details
        if (userId == null || userId.isEmpty()) {
            throw new UserInputDataViolationException("Please provide a User ID for successful searching.");
        } else {
            String Sql = "SELECT Usr1.user_id, Usr1.user_first_name, Usr1.user_last_name, Usr1.user_epf, Usr1.user_email, CASE WHEN Usr1.user_active_status = '0' THEN 'In-Active' WHEN Usr1.user_active_status = '1' THEN 'Active' END AS 'user_active_status', CASE WHEN Usr1.is_admin = '0' THEN 'General User' WHEN Usr1.is_admin = '1' THEN 'Administrator'END AS 'user_level', Usr1.user_created_date, Usr2.user_first_name AS 'user_created_by', Usr1.user_position AS 'user_position', Usr1.department AS 'department', Usr1.section AS 'section' FROM user Usr1 LEFT JOIN user Usr2 ON Usr1.user_created_by = Usr2.user_id WHERE Usr1.user_id = ?";
            List<searchUserDTO> searchedUser = template.query(Sql, new Object[]{userId}, new userSearchMapper());
            if (searchedUser.isEmpty()) {
                //User not available for provided User ID
                throw new UserInputDataViolationException("No User found for provided User ID.");
            } else {
                //Check whether user is already deleted or not
                if (searchedUser.get(0).getUserActiveStatus().equals("In-Active")) {
                    return ResponseEntity.status(HttpStatus.CONFLICT).body(
                            new customAPIResponse<>(
                                    false,
                                    "User ID: " + userId + " is already deleted.",
                                    null
                            )
                    );
                } else {
                    searchUserDTO userObj = searchedUser.get(0);
                    return ResponseEntity.status(HttpStatus.OK).body(
                            new customAPIResponse<>(
                                    true,
                                    null,
                                    userObj
                            )
                    );
                }
            }
        }
    }

    @Transactional
    @Override
    @RequiresPermission("FUNC-004")
    @LogActivity(methodDescription = "This method will delete user details")
    public ResponseEntity<customAPIResponse<String>> deleteUser(String userId) {
        //Check whether user has been provided a User ID
        if (userId == null || userId.isEmpty()) {
            throw new UserInputDataViolationException("Please provide a User ID for successful deletion.");
        } else {
            //Check whether the deleting User ID is equal to the session User ID;
            if (session.getAttribute("userId").toString().equals(userId)) {
                throw new SelfDeletionException("Unable to delete User Account, while you are login with same User Account.");
            } else {
                int affectedDeleteRow = userRepository.deleteUser(session.getAttribute("userId").toString(), userId);
                return ResponseEntity.status(HttpStatus.OK).body(
                        new customAPIResponse<>(
                                true,
                                "User: " + userId + " deleted successfully. This User have no further authority to use this User Account.",
                                null
                        )
                );
            }
        }
    }

    @Transactional
    @Override
    public ResponseEntity<customAPIResponse<String>> passwordReset(userPasswordReset resetData) {
        logger.info("Email: {} | Password resting process started.", resetData.getUserEmail());
        //Check whether user has been provided values for password re-setting
        if (resetData.getUserEmail() == null || resetData.getTemporaryPassword() == null || resetData.getChangedPassword() == null || resetData.getUserEmail().isEmpty() || resetData.getTemporaryPassword().isEmpty() || resetData.getChangedPassword().isEmpty()) {
            logger.error("Email: {} | Password reset was unsuccessful. Did not provided values for all required fields.", resetData.getUserEmail());
            throw new InvalidPasswordResetDataException("Please provide data for successful password reset");
        } else {
            // Check whether any User is available for provided Email;
            User availableUser = userRepository.getPasswordResetUser(resetData.getUserEmail());
            //Check whether encrypted password and user provided temporary password is matched.
            boolean passwordMatchingStatus = PasswordUtil.verifyPassword(resetData.getTemporaryPassword(), userRepository.getEncryptedPassword(resetData.getUserEmail()));
            if (availableUser == null || !passwordMatchingStatus) {
                logger.error("Email: {} | Email address or Temporary password or both credentials were incorrect. Login Password reset was unsuccessful", resetData.getUserEmail());
                throw new InvalidPasswordResetDataException("Please provide valid credentials for successful password reset. Email Address or Password or both incorrect");
            } else {
                //Encrypt user changed password;
                String encryptedPassword = PasswordUtil.encryptPassword(resetData.getChangedPassword());
                int affectedRow = userRepository.passwordChange(encryptedPassword, resetData.getUserEmail());
                if (affectedRow == 0) {
                    logger.error("Email: {} | Unable to encrypt user changed password", resetData.getUserEmail());
                    throw new PasswordEncryptException("Unable to encrypt user changed password");
                } else {
                    logger.info("Email: {} | User's password successfully changed", resetData.getUserEmail());
                    customAPIResponse<String> response = new customAPIResponse<>(
                            true,
                            "Password changed successfully. Please use new password for next login!",
                            null
                    );
                    return ResponseEntity.status(HttpStatus.OK).body(response);
                }
            }
        }
    }

    @Override
    public ResponseEntity<customAPIResponse<List<usersForFunctionAuthorityDTO>>> userListForFunctionAuthority() {
        logger.info("By User Id: {} started to get User List for functional authority", session.getAttribute("userId").toString());
        String Sql = "SELECT user_id, user_epf, user_first_name, user_last_name FROM user WHERE user_active_status = 1";
        List<usersForFunctionAuthorityDTO> userList = template.query(Sql, new userListForFunctionAuthorityMapper());
        if (userList.isEmpty()) {
            throw new DataNotFoundException("No Users available!");
        } else {
            logger.info("Found {} users for functional authorities", userList.size());
            return ResponseEntity.status(HttpStatus.OK).body(new customAPIResponse<>(
                    true,
                    null,
                    userList
            ));
        }
    }

    @Override
    public ResponseEntity<customAPIResponse<List<userListForPasswordResetDTO>>> userListForPasswordReset() {
        logger.info("By User Id: {} started to get User List for Password Reset", session.getAttribute("userId").toString());
        String Sql = "SELECT user_id, user_epf, user_first_name, user_last_name, user_email FROM user WHERE user_active_status = 1";
        List<userListForPasswordResetDTO> userList = template.query(Sql, new userListForPasswordResetMapper());
        if (userList.isEmpty()) {
            throw new DataNotFoundException("No Users available!");
        } else {
            logger.info("Found {} users for Password Reset", userList.size());
            return ResponseEntity.status(HttpStatus.OK).body(new customAPIResponse<>(
                    true,
                    null,
                    userList
            ));
        }
    }
}
