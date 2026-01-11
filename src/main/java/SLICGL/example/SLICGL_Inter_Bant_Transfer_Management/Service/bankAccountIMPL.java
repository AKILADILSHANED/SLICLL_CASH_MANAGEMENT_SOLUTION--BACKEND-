package SLICGL.example.SLICGL_Inter_Bant_Transfer_Management.Service;

import SLICGL.example.SLICGL_Inter_Bant_Transfer_Management.APIResponse.customAPIResponse;
import SLICGL.example.SLICGL_Inter_Bant_Transfer_Management.DTO.*;
import SLICGL.example.SLICGL_Inter_Bant_Transfer_Management.Entity.bankAccount;
import SLICGL.example.SLICGL_Inter_Bant_Transfer_Management.Repositoriy.UserRepo;
import SLICGL.example.SLICGL_Inter_Bant_Transfer_Management.Repositoriy.bankAccountRepo;
import SLICGL.example.SLICGL_Inter_Bant_Transfer_Management.Repositoriy.bankRepo;
import SLICGL.example.SLICGL_Inter_Bant_Transfer_Management.Repositoriy.repoRepository;
import SLICGL.example.SLICGL_Inter_Bant_Transfer_Management.Security.RequiresPermission;
import SLICGL.example.SLICGL_Inter_Bant_Transfer_Management.SqlMappers.accountSearch;
import SLICGL.example.SLICGL_Inter_Bant_Transfer_Management.SqlMappers.searchAccountUpdate;
import jakarta.servlet.http.HttpSession;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

@Service
public class bankAccountIMPL implements bankAccountService{
    @Autowired
    bankAccountRepo bankAccountRepository;
    @Autowired
    HttpSession session;
    @Autowired
    UserRepo userRepository;
    @Autowired
    bankRepo bankRepository;
    @Autowired
    JdbcTemplate template;
    @Autowired
    repoRepository repoRepository;

    @Transactional
    @Override
    @RequiresPermission(value = "FUNC-005")
    public ResponseEntity<customAPIResponse<String>> accountRegister(bankAccountRegisterDTO registerAccount) {

        try{
            //Create new account id;
            String newAccountId;
            String lastAccountId = bankAccountRepository.getLastAccountId();
            //Check whether an account id is already available in the bank account table;
            if(lastAccountId == null){
                //If not account id available;
                newAccountId = "ACC-001";
            }else{
                //If account id is available;
                int newNumericID = Integer.parseInt(lastAccountId.replace("ACC-", "")) + 1;
                newAccountId = String.format("ACC-%03d", newNumericID);
            }
            //Create new bank account entity to save to database;
            bankAccount account = new bankAccount(
                    newAccountId,
                    registerAccount.getBankBranch(),
                    registerAccount.getAccountType(),
                    registerAccount.getCurrency(),
                    bankRepository.findById(registerAccount.getBank()).get(),
                    registerAccount.getGlCode(),
                    registerAccount.getAccountNumber(),
                    0,
                    null,
                    LocalDateTime.now(),
                    userRepository.findById(session.getAttribute("userId").toString()).get()
            );
            bankAccountRepository.save(account);
            customAPIResponse<String> response = new customAPIResponse<>(
                    true,
                    "Bank Account registered successfully with Account ID: " + newAccountId + "!",
                    null
            );
            return ResponseEntity.status(HttpStatus.OK).body(response);

        }catch (Exception e){
            customAPIResponse<String> response = new customAPIResponse<>(
                    false,
                    "Un-expected error occurred while registering the bank account. Please contact administrator!",
                    null
            );
            System.out.println(e.getMessage());
            return ResponseEntity.status(HttpStatus.OK).body(response);
        }
    }

    @Override
    @RequiresPermission(value = "FUNC-006")
    public ResponseEntity<customAPIResponse<searchAccountDTO>> searchAccountDetails(String accountId) {
        try{
            String Sql = "SELECT ACC.account_id, bnk.bank_name, ACC.bank_branch, ACC.account_number, ACC.delete_status, CASE WHEN ACC.account_type = '1' THEN 'Current Account' WHEN ACC.account_type = '2' THEN 'Saving Account' END AS 'accountType', ACC.currency, ACC.gl_code, ACC.registered_date, USR.user_first_name FROM bank_account ACC LEFT JOIN user USR ON ACC.registered_by = USR.user_id LEFT JOIN bank BNK ON ACC.bank = BNK.bank_id WHERE ACC.account_id = ?";
            List<searchAccountDTO> searchedAccount = template.query(Sql, new Object[]{accountId}, new accountSearch());
            //Check whether any account available for user provided account id;
            if(searchedAccount.isEmpty()){
                customAPIResponse<searchAccountDTO> accountAvailability = new customAPIResponse<>(
                            false,
                            "Provided Account ID is invalid. Please provide valid Account ID!",
                            null
                );
                return ResponseEntity.status(HttpStatus.OK).body(accountAvailability);
            }else{
                //Check whether the existing record is already deleted or not;
                if(searchedAccount.get(0).getDeleteStatus() == 0){
                    customAPIResponse<searchAccountDTO> accountAvailability = new customAPIResponse<>(
                            true,
                            null,
                            searchedAccount.get(0)
                    );
                    return ResponseEntity.status(HttpStatus.OK).body(accountAvailability);
                }else{
                    customAPIResponse<searchAccountDTO> accountAvailability = new customAPIResponse<>(
                            false,
                            "This Account ID: " + searchedAccount.get(0).getAccountId() + " is already deleted!",
                            null
                    );
                    return ResponseEntity.status(HttpStatus.OK).body(accountAvailability);
                }
            }

        }catch (Exception e){
            customAPIResponse<searchAccountDTO> accountAvailability = new customAPIResponse<>(
                    false,
                    e.getMessage() + ":" + " Un-expected error occurred. Please contact administrator!",
                    null
            );
            return ResponseEntity.status(HttpStatus.OK).body(accountAvailability);
        }
    }

    @Override
    public ResponseEntity<customAPIResponse<searchAccountUpdateDTO>> searchAccountDetailsForUpdate(String accountId) {
        try{
            String Sql = "SELECT ACC.account_id, bnk.bank_id, bnk.bank_name, ACC.bank_branch, ACC.account_type, ACC.currency, ACC.gl_code, ACC.account_number, ACC.registered_date, USR.user_first_name, ACC.delete_status FROM bank_account ACC LEFT JOIN user USR ON ACC.registered_by = USR.user_id LEFT JOIN bank BNK ON ACC.bank = BNK.bank_id WHERE ACC.account_id = ?";
            List<searchAccountUpdateDTO> searchedAccount = template.query(Sql, new Object[]{accountId}, new searchAccountUpdate());
            //Check whether any account available for user provided account id;
            if(searchedAccount.isEmpty()){
                customAPIResponse<searchAccountUpdateDTO> accountAvailability = new customAPIResponse<>(
                        false,
                        "Provided Account ID is invalid. Please provide valid Account ID!",
                        null
                );
                return ResponseEntity.status(HttpStatus.OK).body(accountAvailability);
            }else{
                //Check whether the existing record is already deleted or not;
                if(searchedAccount.get(0).getDeleteStatus() == 0){
                    customAPIResponse<searchAccountUpdateDTO> accountAvailability = new customAPIResponse<>(
                            true,
                            null,
                            searchedAccount.get(0)
                    );
                    return ResponseEntity.status(HttpStatus.OK).body(accountAvailability);
                }else{
                    customAPIResponse<searchAccountUpdateDTO> accountAvailability = new customAPIResponse<>(
                            false,
                            "This Account ID: " + searchedAccount.get(0).getAccountId() + " is already deleted!",
                            null
                    );
                    return ResponseEntity.status(HttpStatus.OK).body(accountAvailability);
                }
            }

        }catch (Exception e){
            customAPIResponse<searchAccountUpdateDTO> accountAvailability = new customAPIResponse<>(
                    false,
                    e.getMessage() + ":" + " Un-expected error occurred. Please contact administrator!",
                    null
            );
            return ResponseEntity.status(HttpStatus.OK).body(accountAvailability);
        }
    }

    @Transactional
    @Override
    @RequiresPermission(value = "FUNC-007")
    public ResponseEntity<customAPIResponse<String>> updateAccount(accountUpdateDTO updateAccount) {
        try{
            int affected_rows = bankAccountRepository.updateAccount(
                    updateAccount.getBank(),
                    updateAccount.getBankBranch(),
                    updateAccount.getAccountNumber(),
                    updateAccount.getAccountType(),
                    updateAccount.getCurrency(),
                    updateAccount.getGlCode(),
                    updateAccount.getAccountId()
            );
            if(affected_rows > 0){
                customAPIResponse<String> response = new customAPIResponse<>(
                        true,
                        "Account ID: " + updateAccount.getAccountId() + " updated successfully!",
                        null
                );
                return ResponseEntity.status(HttpStatus.OK).body(response);
            }else{
                customAPIResponse<String> response = new customAPIResponse<>(
                        false,
                        "Unable to update Account Details. Please contact administrator!",
                        null
                );
                return ResponseEntity.status(HttpStatus.OK).body(response);
            }

        }catch (Exception e){
            customAPIResponse<String> response = new customAPIResponse<>(
                    false,
                    "Un-expected error occurred while updating the Account Details. Please contact administrator!",
                    null
            );
            return ResponseEntity.status(HttpStatus.OK).body(response);
        }
    }

    @Transactional
    @Override
    @RequiresPermission(value = "FUNC-008")
    public ResponseEntity<customAPIResponse<String>> deleteAccount(String accountId) {

        try{
            int affectedRows = bankAccountRepository.deleteAccount(session.getAttribute("userId").toString(), accountId);

            if(affectedRows == 0){
                customAPIResponse<String> response = new customAPIResponse<>(
                        false,
                        "No Account number found for provided Account ID: " + accountId,
                        null
                );
                return ResponseEntity.status(HttpStatus.OK).body(response);
            }else{
                customAPIResponse<String> response = new customAPIResponse<>(
                        true,
                        "Account ID: " + accountId + " deleted successfully!",
                        null
                );
                return ResponseEntity.status(HttpStatus.OK).body(response);
            }
        }catch (Exception e){
            customAPIResponse<String> response = new customAPIResponse<>(
                    false,
                    e.getMessage() +" Un-expected error occurred while deleting bank account. Please contact administrator!",
                    null
            );
            return ResponseEntity.status(HttpStatus.OK).body(response);
        }
    }

    @Override
    public ResponseEntity<customAPIResponse<List<getBankAccountsForFundRequestDTO>>> getBankAccountsForFundRequest() {
        try{
            List<getBankAccountsForFundRequestDTO> accountList = bankAccountRepository.getBankAccountsForFundRequest();
            if(!accountList.isEmpty()){
                customAPIResponse<List<getBankAccountsForFundRequestDTO>> response = new customAPIResponse<>(
                        true,
                        null,
                        accountList
                );
                return ResponseEntity.status(HttpStatus.OK).body(response);
            }else {
                customAPIResponse<List<getBankAccountsForFundRequestDTO>> response = new customAPIResponse<>(
                        false,
                        "No Bank Accounts found!",
                        null
                );
                return ResponseEntity.status(HttpStatus.OK).body(response);
            }

        }catch (Exception e){;
            customAPIResponse<List<getBankAccountsForFundRequestDTO>> response = new customAPIResponse<>(
                    false,
                    "Un-expected error occurred. Please contact administrator!",
                    null
            );
            return ResponseEntity.status(HttpStatus.OK).body(response);
        }
    }

    @Override
    public ResponseEntity<customAPIResponse<Boolean>> checkBankAccountForNewRepo(String newRepoAccountId, String fromRepoId) {
        try{
            //Check whether from repo account id is equal with new repo account id;
            if(repoRepository.getFromRepoBankAccountId(fromRepoId).equals(newRepoAccountId) || repoRepository.getFromRepoBankAccountId(fromRepoId) == null || Objects.equals(newRepoAccountId, "")){
                customAPIResponse<Boolean> response = new customAPIResponse<>(
                        false,
                        null,
                        true
                );
                return ResponseEntity.status(HttpStatus.OK).body(response);
            }else {
                customAPIResponse<Boolean> response = new customAPIResponse<>(
                        false,
                        null,
                        false
                );
                return ResponseEntity.status(HttpStatus.OK).body(response);
            }
        }catch (Exception e){
            customAPIResponse<Boolean> response = new customAPIResponse<>(
                    false,
                    "Un-expected error occurred. Please contact administrator!",
                    null
            );
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    @Override
    public ResponseEntity<customAPIResponse<Boolean>> checkBankAccountForExistingRepo(String toRepoId, String fromRepoId) {
        try{
            //Check whether from repo account id is equal with to repo account id;
            if(repoRepository.getFromRepoBankAccountId(fromRepoId).equals(repoRepository.getFromRepoBankAccountId(toRepoId)) || Objects.equals(toRepoId, "")){
                customAPIResponse<Boolean> response = new customAPIResponse<>(
                        false,
                        null,
                        true
                );
                return ResponseEntity.status(HttpStatus.OK).body(response);
            }else {
                customAPIResponse<Boolean> response = new customAPIResponse<>(
                        false,
                        null,
                        false
                );
                return ResponseEntity.status(HttpStatus.OK).body(response);
            }
        }catch (Exception e){
            customAPIResponse<Boolean> response = new customAPIResponse<>(
                    false,
                    "Un-expected error occurred. Please contact administrator!",
                    null
            );
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
}
