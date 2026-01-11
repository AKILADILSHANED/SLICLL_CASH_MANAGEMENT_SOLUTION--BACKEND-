package SLICGL.example.SLICGL_Inter_Bant_Transfer_Management.Controller;
import SLICGL.example.SLICGL_Inter_Bant_Transfer_Management.APIResponse.customAPIResponse;
import SLICGL.example.SLICGL_Inter_Bant_Transfer_Management.DTO.*;
import SLICGL.example.SLICGL_Inter_Bant_Transfer_Management.Service.UserLoginIMPL;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping(value = "/api/v1/user")
@CrossOrigin(value = "${frontend.url}", allowCredentials = "true")
public class UserController {
    @Autowired
    UserLoginIMPL userLogin;

    @PostMapping(value = "/user-login")
    public ResponseEntity<customAPIResponse<userLoginResponseDTO>> userLogin(@RequestBody userLoginDTO login){
        return userLogin.userLogin(login);
    }

    @PostMapping(value = "/user-register", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<customAPIResponse<String>> userRegister(@ModelAttribute userRegisterDTO userRegister){
    return userLogin.userRegister(userRegister);
    }

    @GetMapping(value = "/user-search")
    public ResponseEntity<customAPIResponse<searchUserDTO>> searchUser(@RequestParam String userId){
        return userLogin.searchUser(userId);
    }

    @PutMapping(value = "/user-update", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<customAPIResponse<updateUserDTO>> updateUser(@ModelAttribute updateUserDTO updatedUser){
        return userLogin.updateUser(updatedUser);
    }

    @PutMapping(value = "/user-delete")
    public ResponseEntity<customAPIResponse<String>> deleteUser(@RequestParam String userId){
        return userLogin.deleteUser(userId);
    }

    @PutMapping(value = "/password-reset")
    public ResponseEntity<customAPIResponse<String>> passwordReset(@RequestBody userPasswordReset resetData){
        return userLogin.passwordReset(resetData);
    }

    @GetMapping(value = "/userList")
    public ResponseEntity<customAPIResponse<List<usersForFunctionAuthorityDTO>>> userListForFunctionAuthority(){
        return userLogin.userListForFunctionAuthority();
    }

    @GetMapping(value = "/userList-password-reset")
    public ResponseEntity<customAPIResponse<List<userListForPasswordResetDTO>>> userListForPasswordReset(){
        return userLogin.userListForPasswordReset();
    }
}
