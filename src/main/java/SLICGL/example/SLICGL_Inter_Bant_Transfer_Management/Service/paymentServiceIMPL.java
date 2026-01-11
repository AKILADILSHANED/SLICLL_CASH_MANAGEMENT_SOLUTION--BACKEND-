package SLICGL.example.SLICGL_Inter_Bant_Transfer_Management.Service;

import SLICGL.example.SLICGL_Inter_Bant_Transfer_Management.APIResponse.customAPIResponse;
import SLICGL.example.SLICGL_Inter_Bant_Transfer_Management.DTO.getPaymentsForFundRequestDTO;
import SLICGL.example.SLICGL_Inter_Bant_Transfer_Management.DTO.paymentRegisterDTO;
import SLICGL.example.SLICGL_Inter_Bant_Transfer_Management.DTO.paymentSearchDTO;
import SLICGL.example.SLICGL_Inter_Bant_Transfer_Management.Entity.payment;
import SLICGL.example.SLICGL_Inter_Bant_Transfer_Management.Repositoriy.UserRepo;
import SLICGL.example.SLICGL_Inter_Bant_Transfer_Management.Repositoriy.paymentRepo;
import SLICGL.example.SLICGL_Inter_Bant_Transfer_Management.SqlMappers.paymentSearchMapper;
import jakarta.servlet.http.HttpSession;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class paymentServiceIMPL implements paymentService{
    @Autowired
    paymentRepo paymentRepository;

    @Autowired
    UserRepo userRepository;

    @Autowired
    HttpSession session;

    @Autowired
    JdbcTemplate template;

    @Transactional
    @Override
    public ResponseEntity<customAPIResponse<paymentRegisterDTO>> paymentRegister(String paymentType) {
        String newPaymentId;
        String currentYear = String.valueOf(LocalDate.now().getYear());
        try{
            //Creating new payment id;
            //Get last payment id from payment table;
            String lastPaymentId = paymentRepository.getLastPaymentId();
            if(lastPaymentId == null){
                newPaymentId = "PAY-" + currentYear + "-001";
            }else{
                //If any last id is available, then check the year of last is equal with current year;
                if(!lastPaymentId.substring(4,8).equals(currentYear)){
                    newPaymentId = "PAY-" + currentYear + "-001";
                }else {
                    int newNumericPartOfId = Integer.parseInt(lastPaymentId.substring(9,12)) + 1;
                    newPaymentId = "PAY-" + currentYear + "-" + String.format("%03d",newNumericPartOfId);
                }
            }
            //Register payment to payment table;
            payment paymentObject = new payment(
                    newPaymentId,
                    paymentType,
                    LocalDateTime.now(),
                    userRepository.findById(session.getAttribute("userId").toString()).get(),
                    0,
                    null
            );
            paymentRepository.save(paymentObject);

            paymentRegisterDTO registerObject = new paymentRegisterDTO(
                    newPaymentId,
                    paymentType,
                    LocalDateTime.now(),
                    userRepository.findById(session.getAttribute("userId").toString()).get().getUserFirstName()
            );
            customAPIResponse<paymentRegisterDTO> response = new customAPIResponse(
                    true,
                    "Payment registered successfully with Payment ID: " + newPaymentId,
                    registerObject
            );
            return ResponseEntity.status(HttpStatus.OK).body(response);
        }catch (Exception e){
            System.out.println(e.getMessage());
            customAPIResponse<paymentRegisterDTO> response = new customAPIResponse(
                    false,
                    "Un-expected error occurred while registering the payment. Please contact administrator!",
                    null
            );
            return ResponseEntity.status(HttpStatus.OK).body(response);
        }
    }

    @Override
    public ResponseEntity<customAPIResponse<paymentSearchDTO>> searchPayment(String paymentId) {
        try{
            String Sql = "SELECT PAY.payment_id, PAY.payment_type, PAY.registered_date, USR.user_first_name, PAY.is_deleted FROM payment PAY LEFT JOIN user USR ON PAY.registered_by = USR.user_id WHERE PAY.payment_id = ?";
            List<paymentSearchDTO> searchedPaymentObj = template.query(Sql, new Object[]{paymentId}, new paymentSearchMapper());
            //Check whether any record is available for provided Payment ID;
            if(searchedPaymentObj.isEmpty()){
                customAPIResponse<paymentSearchDTO> response = new customAPIResponse(
                        false,
                        "No payment found for provided Payment ID!",
                        null
                );
                return ResponseEntity.status(HttpStatus.OK).body(response);
            }else {
                //Check whether the payment is already deleted or not;
                if(searchedPaymentObj.get(0).getDeleteStatus() == 1){
                    customAPIResponse<paymentSearchDTO> response = new customAPIResponse(
                            false,
                            "This Payment ID is already deleted!",
                            null
                    );
                    return ResponseEntity.status(HttpStatus.OK).body(response);
                }else {
                    customAPIResponse<paymentSearchDTO> response = new customAPIResponse(
                            true,
                            null,
                            searchedPaymentObj.get(0)
                    );
                    return ResponseEntity.status(HttpStatus.OK).body(response);
                }
            }

        }catch (Exception e){
            customAPIResponse<paymentSearchDTO> response = new customAPIResponse(
                    false,
                    "Un-expected error occurred while fetching payment data. Please contact administrator!",
                    null
            );
            return ResponseEntity.status(HttpStatus.OK).body(response);
        }
    }

    @Transactional
    @Override
    public ResponseEntity<customAPIResponse<String>> updatePayment(@RequestParam String paymentType, @RequestParam String paymentId) {
        try{
            int affectedRow = paymentRepository.updatePayment(paymentType, paymentId);
            if(affectedRow == 0){
                customAPIResponse<String> response = new customAPIResponse(
                        false,
                        "No payment record found for provided Payment ID!",
                        null
                );
                return ResponseEntity.status(HttpStatus.OK).body(response);
            }else {
                customAPIResponse<String> response = new customAPIResponse(
                        true,
                        "Payment " + paymentId + " updated successfully!",
                        null
                );
                return ResponseEntity.status(HttpStatus.OK).body(response);
            }
        }catch (Exception e){
            customAPIResponse<String> response = new customAPIResponse(
                    false,
                    "Un-expected error occurred while fetching payment data. Please contact administrator!",
                    null
            );
            return ResponseEntity.status(HttpStatus.OK).body(response);
        }
    }

    @Transactional
    @Override
    public ResponseEntity<customAPIResponse<String>> deletePayment(String paymentId) {
        try{
            int affectedRow = paymentRepository.deletePayment(session.getAttribute("userId").toString(), paymentId);
            if(affectedRow == 0){
                customAPIResponse<String> response = new customAPIResponse(
                        false,
                        "No payment record found for provided Payment ID!",
                        null
                );
                return ResponseEntity.status(HttpStatus.OK).body(response);
            }else {
                customAPIResponse<String> response = new customAPIResponse(
                        true,
                        "Payment " + paymentId + " deleted successfully!",
                        null
                );
                return ResponseEntity.status(HttpStatus.OK).body(response);
            }
        }catch (Exception e){
            customAPIResponse<String> response = new customAPIResponse(
                    false,
                    "Un-expected error occurred while deleting payment data. Please contact administrator!",
                    null
            );
            return ResponseEntity.status(HttpStatus.OK).body(response);
        }
    }

    @Override
    public ResponseEntity<customAPIResponse<List<getPaymentsForFundRequestDTO>>> getPaymentsForFundRequest() {
        try{
            List<getPaymentsForFundRequestDTO> paymentList = paymentRepository.paymentListForFundRequest();
            customAPIResponse<List<getPaymentsForFundRequestDTO>> response = new customAPIResponse(
                    true,
                    null,
                    paymentList
            );
            return ResponseEntity.status(HttpStatus.OK).body(response);
        }catch(Exception e){
            customAPIResponse<List<getPaymentsForFundRequestDTO>> response = new customAPIResponse(
                    false,
                    "Un-expected error occurred. Please contact administrator!",
                    null
            );
            return ResponseEntity.status(HttpStatus.OK).body(response);
        }
    }
}
