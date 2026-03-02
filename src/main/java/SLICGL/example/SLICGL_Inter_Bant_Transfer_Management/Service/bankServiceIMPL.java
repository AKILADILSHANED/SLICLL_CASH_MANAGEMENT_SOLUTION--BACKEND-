package SLICGL.example.SLICGL_Inter_Bant_Transfer_Management.Service;

import SLICGL.example.SLICGL_Inter_Bant_Transfer_Management.APIResponse.customAPIResponse;
import SLICGL.example.SLICGL_Inter_Bant_Transfer_Management.Entity.bank;
import SLICGL.example.SLICGL_Inter_Bant_Transfer_Management.Repositoriy.bankRepo;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class bankServiceIMPL implements bankService{
    @Autowired
    bankRepo bankRepository;
    @Autowired
    HttpSession session;

    private static final Logger logger = LoggerFactory.getLogger(bankServiceIMPL.class);

    @Override
    public ResponseEntity<customAPIResponse<List<bank>>> getAllBankList() {
        if(session.getAttribute("userId") == null){
            throw new SecurityException("Session Expired. Please Re-login to the system.");
        }else {
            logger.info("User: {} | Bank details fetching process started.", session.getAttribute("userId").toString());
            List<bank> bankList = bankRepository.findAll();
            if(bankList.isEmpty()){
                logger.warn("User: {} | No registered Banks found.", session.getAttribute("userId").toString());
                customAPIResponse<List<bank>> response = new customAPIResponse<>(
                        false,
                        "No registered bank details found.",
                        null
                );
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
            }else {
                logger.info("User: {} | Bank details fetched and displayed to the User successfully. | [{} of Banks found]", session.getAttribute("userId").toString(), bankList.size());
                customAPIResponse<List<bank>> response = new customAPIResponse<>(
                        true,
                        null,
                        bankList
                );
                return ResponseEntity.status(HttpStatus.OK).body(response);
            }
        }
    }
}
