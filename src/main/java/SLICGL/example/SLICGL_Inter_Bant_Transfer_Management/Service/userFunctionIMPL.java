package SLICGL.example.SLICGL_Inter_Bant_Transfer_Management.Service;

import SLICGL.example.SLICGL_Inter_Bant_Transfer_Management.APIResponse.customAPIResponse;
import SLICGL.example.SLICGL_Inter_Bant_Transfer_Management.DTO.grantFunctionDTO;
import SLICGL.example.SLICGL_Inter_Bant_Transfer_Management.DTO.revokeFunctionDTO;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class userFunctionIMPL implements userFunctionService{
    @Autowired
    JdbcTemplate template;

    @Override
    @Transactional
    public ResponseEntity<customAPIResponse<String>> grantFunction(grantFunctionDTO grantFunctionDTO){
        try{
            String sql = "INSERT INTO user_function (user_id, function_id) VALUES (?, ?)";
            int rows = template.update(sql, grantFunctionDTO.getUserId(), grantFunctionDTO.getFunctionId());
            if (rows > 0) {
                return ResponseEntity.status(HttpStatus.OK).body(
                        new customAPIResponse<>(
                                true,
                                "Function granted successfully for user: " + grantFunctionDTO.getUserId(),
                                null
                        )
                );
            } else {
                return ResponseEntity.status(HttpStatus.CONFLICT).body(
                        new customAPIResponse<>(
                                false,
                                "Unable to grant the function. Please contact administrator!",
                                null
                        )
                );
            }
        }catch (Exception e){
            System.out.println(e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                    new customAPIResponse<>(
                            false,
                            "Un-expected error occurred while granting the function. Please contact administrator!",
                            null
                    )
            );
        }
    }

    @Override
    public ResponseEntity<customAPIResponse<String>> revokeFunction(revokeFunctionDTO revokeFunctionDTO) {
        try{
            String sql = "DELETE FROM user_function WHERE user_id = ? AND function_id = ?";
            int rows = template.update(sql, revokeFunctionDTO.getUserId(), revokeFunctionDTO.getFunctionId());
            if (rows > 0) {
                return ResponseEntity.status(HttpStatus.OK).body(
                        new customAPIResponse<>(
                                true,
                                "Function revoked successfully for user: " + revokeFunctionDTO.getUserId(),
                                null
                        )
                );
            } else {
                return ResponseEntity.status(HttpStatus.CONFLICT).body(
                        new customAPIResponse<>(
                                false,
                                "Unable to revoke the function. Please contact administrator!",
                                null
                        )
                );
            }
        }catch (Exception e){
            System.out.println(e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                    new customAPIResponse<>(
                            false,
                            "Un-expected error occurred while revoking the function. Please contact administrator!",
                            null
                    )
            );
        }
    }
}
