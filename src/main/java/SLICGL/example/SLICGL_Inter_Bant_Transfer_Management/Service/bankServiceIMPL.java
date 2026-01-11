package SLICGL.example.SLICGL_Inter_Bant_Transfer_Management.Service;

import SLICGL.example.SLICGL_Inter_Bant_Transfer_Management.APIResponse.customAPIResponse;
import SLICGL.example.SLICGL_Inter_Bant_Transfer_Management.Entity.bank;
import SLICGL.example.SLICGL_Inter_Bant_Transfer_Management.Repositoriy.bankRepo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class bankServiceIMPL implements bankService{
    @Autowired
    bankRepo bankRepository;
    @Override
    public ResponseEntity<customAPIResponse<List<bank>>> getAllBankList() {
        try{
            List<bank> bankList = bankRepository.findAll();
            if(bankList.isEmpty()){
                customAPIResponse<List<bank>> response = new customAPIResponse<>(
                        false,
                        "No bank details found!",
                        null
                );
                return ResponseEntity.status(HttpStatus.OK).body(response);
            }else {
                customAPIResponse<List<bank>> response = new customAPIResponse<>(
                        true,
                        null,
                        bankList
                );
                return ResponseEntity.status(HttpStatus.OK).body(response);
            }
        }catch (Exception e){
            customAPIResponse<List<bank>> response = new customAPIResponse<>(
                    true,
                    "Un-expected error occurred while fetching bank details. Please contact administrator!",
                    null
            );
            return ResponseEntity.status(HttpStatus.OK).body(response);
        }
    }
}
