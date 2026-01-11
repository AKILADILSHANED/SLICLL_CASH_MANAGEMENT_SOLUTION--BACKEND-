package SLICGL.example.SLICGL_Inter_Bant_Transfer_Management.Service;

import SLICGL.example.SLICGL_Inter_Bant_Transfer_Management.APIResponse.customAPIResponse;
import SLICGL.example.SLICGL_Inter_Bant_Transfer_Management.DTO.getFunctionsForGrantDTO;
import SLICGL.example.SLICGL_Inter_Bant_Transfer_Management.DTO.getUserModuleDTO;
import SLICGL.example.SLICGL_Inter_Bant_Transfer_Management.Repositoriy.userModuleRepo;
import SLICGL.example.SLICGL_Inter_Bant_Transfer_Management.SqlMappers.GetAllSubFunctionsMapper;
import SLICGL.example.SLICGL_Inter_Bant_Transfer_Management.SqlMappers.GetAllUserFunctionsMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.Iterator;
import java.util.List;
@Service
public class userModuleIMPL implements userModuleService{
    @Autowired
    userModuleRepo userModuleRepository;
    @Autowired
    JdbcTemplate template;

    @Override
    public ResponseEntity<customAPIResponse<List<getUserModuleDTO>>> getUserModule() {
        try{
            List<getUserModuleDTO> moduleList = userModuleRepository.getModuleList();
            if(moduleList.isEmpty()){
                return ResponseEntity.status(HttpStatus.CONFLICT).body(
                        new customAPIResponse<>(
                                false,
                                "No User Modules identified!",
                                null
                        )
                );
            }else {
                return ResponseEntity.status(HttpStatus.OK).body(
                        new customAPIResponse<>(
                                true,
                                null,
                                moduleList
                        )
                );
            }
        }catch (Exception e){
            return ResponseEntity.status(HttpStatus.CONFLICT).body(
                    new customAPIResponse<>(
                            false,
                            "Un-expected error occurred. Please contact administrator!",
                            null
                    )
            );
        }
    }

    @Override
    public ResponseEntity<customAPIResponse<List<getFunctionsForGrantDTO>>> getFunctionsForGrant(String userId, String moduleId) {
        try{
            //Get all sub functions related to provided module;
            String SqlGetAllSubFunctions = "SELECT SUB.function_id, MODULE.module_name, SUB.function_name FROM sub_function SUB LEFT JOIN user_module MODULE ON SUB.module = MODULE.module_id WHERE SUB.module = ? ORDER BY SUB.function_id ASC";
            List<getFunctionsForGrantDTO> allSubFunctionList = template.query(SqlGetAllSubFunctions, new GetAllSubFunctionsMapper(), moduleId);

            //Get all user available functions related;
            String SqlGetAllUserFunctions = "SELECT function_id FROM user_function WHERE user_id = ?";
            List<String> allUserFunctionList = template.query(SqlGetAllUserFunctions, new GetAllUserFunctionsMapper(), userId);

            Iterator<getFunctionsForGrantDTO> iterator = allSubFunctionList.iterator();
            while (iterator.hasNext()) {
                getFunctionsForGrantDTO subFunc = iterator.next();
                if (allUserFunctionList.contains(subFunc.getFunctionId().trim())) {
                    iterator.remove();
                }
            }

            if(allSubFunctionList.isEmpty()){
                return ResponseEntity.status(HttpStatus.CONFLICT).body(
                        new customAPIResponse<>(
                                false,
                                "No functions available for grant!",
                                null
                        )
                );
            }else {
                return ResponseEntity.status(HttpStatus.OK).body(
                        new customAPIResponse<>(
                                true,
                                null,
                                allSubFunctionList
                        )
                );
            }
        }catch (Exception e){
            System.out.println(e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                    new customAPIResponse<>(
                            false,
                            "Un-expected error occurred. Please contact administrator!",
                            null
                    )
            );
        }
    }

    @Override
    public ResponseEntity<customAPIResponse<List<getFunctionsForGrantDTO>>> getFunctionsForRevoke(String userId, String moduleId) {
        try{
            //Get all sub functions related to provided module;
            String SqlGetAllSubFunctions = "SELECT SUB.function_id, MODULE.module_name, SUB.function_name FROM sub_function SUB LEFT JOIN user_module MODULE ON SUB.module = MODULE.module_id WHERE SUB.module = ? ORDER BY SUB.function_id ASC";
            List<getFunctionsForGrantDTO> allSubFunctionList = template.query(SqlGetAllSubFunctions, new GetAllSubFunctionsMapper(), moduleId);

            //Get all user available functions related;
            String SqlGetAllUserFunctions = "SELECT function_id FROM user_function WHERE user_id = ?";
            List<String> allUserFunctionList = template.query(SqlGetAllUserFunctions, new GetAllUserFunctionsMapper(), userId);

            Iterator<getFunctionsForGrantDTO> iterator = allSubFunctionList.iterator();
            while (iterator.hasNext()) {
                getFunctionsForGrantDTO subFunc = iterator.next();
                if (!allUserFunctionList.contains(subFunc.getFunctionId().trim())) {
                    iterator.remove();
                }
            }

            if(allSubFunctionList.isEmpty()){
                return ResponseEntity.status(HttpStatus.CONFLICT).body(
                        new customAPIResponse<>(
                                false,
                                "No functions available for revoke!",
                                null
                        )
                );
            }else {
                return ResponseEntity.status(HttpStatus.OK).body(
                        new customAPIResponse<>(
                                true,
                                null,
                                allSubFunctionList
                        )
                );
            }
        }catch (Exception e){
            System.out.println(e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                    new customAPIResponse<>(
                            false,
                            "Un-expected error occurred. Please contact administrator!",
                            null
                    )
            );
        }
    }
}
