package SLICGL.example.SLICGL_Inter_Bant_Transfer_Management.Service;

import SLICGL.example.SLICGL_Inter_Bant_Transfer_Management.APIResponse.customAPIResponse;
import SLICGL.example.SLICGL_Inter_Bant_Transfer_Management.DTO.*;
import SLICGL.example.SLICGL_Inter_Bant_Transfer_Management.Entity.accountBalance;
import SLICGL.example.SLICGL_Inter_Bant_Transfer_Management.Repositoriy.UserRepo;
import SLICGL.example.SLICGL_Inter_Bant_Transfer_Management.Repositoriy.accountBalanceAdjustmentsRepo;
import SLICGL.example.SLICGL_Inter_Bant_Transfer_Management.Repositoriy.accountBalanceRepo;
import SLICGL.example.SLICGL_Inter_Bant_Transfer_Management.Repositoriy.bankAccountRepo;
import SLICGL.example.SLICGL_Inter_Bant_Transfer_Management.SqlMappers.*;
import jakarta.servlet.http.HttpSession;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Service
public class accountBalanceIMPL implements accountBalanceService{
    @Autowired
    accountBalanceRepo accountBalanceRepository;

    @Autowired
    bankAccountRepo bankAccountRepository;

    @Autowired
    UserRepo userRepository;

    @Autowired
    HttpSession session;

    @Autowired
    JdbcTemplate template;

    @Autowired
    accountBalanceAdjustmentsRepo accountBalanceAdjustmentsRepository;

    @Override
    public ResponseEntity<customAPIResponse<List<balanceEnterDTO>>> getAvailableAccounts() {
        List<balanceEnterDTO> newBalanceList = new ArrayList<>();
        try{
            //Get all available bank accounts;
            List<balanceEnterDTO> listOfAccounts = bankAccountRepository.getAllBankAccounts();
            //Loop each account_ids and check whether any balance is entered already for current date;
            for (balanceEnterDTO currentAccountObj : listOfAccounts){
                String availableBalanceId = accountBalanceRepository.getBalanceIdForBalanceEnter(currentAccountObj.getAccountId(), LocalDate.now());
                if(availableBalanceId == null){
                    newBalanceList.add(currentAccountObj);
                }else{
                    continue;
                }
            }
            if(newBalanceList.isEmpty()){
                customAPIResponse<List<balanceEnterDTO>> response = new customAPIResponse<>(
                        false,
                        "No Bank Accounts available for enter balances under current date!",
                        null
                );
                return ResponseEntity.status(HttpStatus.OK).body(response);
            }else{
                customAPIResponse<List<balanceEnterDTO>> response = new customAPIResponse<>(
                        true,
                        null,
                        newBalanceList
                );
                return ResponseEntity.status(HttpStatus.OK).body(response);
            }
        }catch (Exception e){
            customAPIResponse<List<balanceEnterDTO>> response = new customAPIResponse<>(
                    false,
                    "Un-expected error occurred while fetching Bank Accounts. Please contact administrator!",
                    null
            );
            return ResponseEntity.status(HttpStatus.OK).body(response);
        }
    }

    @Transactional
    @Override
    public ResponseEntity<customAPIResponse<String>> saveBalance(String accountId, float balanceAmount) {
        String newBalanceId;
        try {
            //Create variable for store current year
            String currentYear = String.valueOf(LocalDate.now().getYear());
            //Create variable for store current month;
            String currentMonth = LocalDate.now().format(DateTimeFormatter.ofPattern("MM"));

            //Check whether any account balance record is available in account balance table;
            String lastBalanceId = accountBalanceRepository.getLastBalanceId();
            if(lastBalanceId == null){
                newBalanceId = "BAL-" + currentYear + currentMonth + "-0001";
            }else{
                //Get year of the last balance id;
                String lastYear = lastBalanceId.substring(4,8);
                //Get month of the last balance id;
                String lastMonth = lastBalanceId.substring(8,10);
                //Check whether the year and month of the last balance id is equal to the  current year and month;
                if(lastYear.equals(currentYear) && lastMonth.equals(currentMonth)){
                    //Get last four characters in last balance_id;
                    int numericPart = Integer.parseInt(lastBalanceId.substring(lastBalanceId.length() - 4)) + 1;
                    String newNumericPart = String.format("%04d", numericPart);
                    newBalanceId = "BAL-" + currentYear + currentMonth + "-" + newNumericPart;
                }else {
                    newBalanceId = "BAL-" + currentYear + currentMonth + "-0001";
                }

            }
            //Create new account balance object;

            accountBalanceRepository.save(new accountBalance(
                    newBalanceId,
                    LocalDate.now(),
                    balanceAmount,
                    0,
                    userRepository.findById(session.getAttribute("userId").toString()).get(),
                    bankAccountRepository.findById(accountId).get(),
                    null
            ));
            customAPIResponse<String> response = new customAPIResponse<>(
                    true,
                    "Account Balance saved successfully with Balance ID: " + newBalanceId,
                    null
            );
            return ResponseEntity.status(HttpStatus.OK).body(response);

        }catch (Exception e){
            customAPIResponse<String> response = new customAPIResponse<>(
                    false,
                    e.getMessage() + ": " + "Un-expected error occurred while saving Account Balance. Please contact administrator!",
                    null
            );
            return ResponseEntity.status(HttpStatus.OK).body(response);
        }
    }

    @Override
    public ResponseEntity<customAPIResponse<List<getAllBalancesDTO>>> getAllBalances(LocalDate balanceDate) {
        try{
            String Sql = "SELECT BAL.balance_id, BNK.bank_name, ACC.account_number, BAL.balance_date, BAL.balance_amount, CASE WHEN BAL.delete_status = 0 THEN 'Active' WHEN BAL.delete_status = 1 THEN 'Deleted' END AS 'delete_status', CASE WHEN BAL.deleted_by IS NULL THEN \"N/A\" ELSE USRDEL.user_first_name END as deletedBy, USR.user_first_name FROM account_balance BAL LEFT JOIN bank_account ACC ON BAL.account_id = ACC.account_id LEFT JOIN bank BNK ON ACC.bank = BNK.bank_id LEFT JOIN user USR ON BAL.entered_by = USR.user_id LEFT JOIN user USRDEL ON USR.user_id = USRDEL.user_id WHERE BAL.balance_date = ?";
            List<getAllBalancesDTO> balanceList = template.query(Sql, new Object[]{balanceDate}, new accountBalanceMapper());
            //Check whether any account balances are available for user provided balance date;
            if(balanceList.isEmpty()){
                customAPIResponse<List<getAllBalancesDTO>> response = new customAPIResponse<>(
                        false,
                        "No Account Balances found for selected date!",
                        null
                );
                return ResponseEntity.status(HttpStatus.OK).body(response);
            }else{
                customAPIResponse<List<getAllBalancesDTO>> response = new customAPIResponse<>(
                        true,
                        null,
                        balanceList
                );
                return ResponseEntity.status(HttpStatus.OK).body(response);
            }
        }catch(Exception e){
            customAPIResponse<List<getAllBalancesDTO>> response = new customAPIResponse<>(
                    false,
                    e.getMessage() + ": " + "Un-expected error occurred while fetching Account Balances. Please contact administrator!",
                    null
            );
            return ResponseEntity.status(HttpStatus.OK).body(response);
        }
    }

    @Override
    public ResponseEntity<customAPIResponse<getBalanceForUpdateDTO>> getBalanceForUpdate(String balanceId) {
        try{
            String Sql = "SELECT BAL.balance_id, BNK.bank_name, ACC.account_number, BAL.balance_date, BAL.balance_amount, BAL.delete_status FROM account_balance BAL LEFT JOIN bank_account ACC ON BAL.account_id = ACC.account_id LEFT JOIN bank BNK ON ACC.bank = BNK.bank_id WHERE BAL.balance_id = ?";
            List<getBalanceForUpdateDTO> balanceObject = template.query(Sql, new Object[]{balanceId}, new updateBalanceMapper());
            //Check whether any balance is available for provided Balance ID;
            if(balanceObject.isEmpty()){
                customAPIResponse<getBalanceForUpdateDTO> response = new customAPIResponse<>(
                        false,
                        "No balance found for provided Balance ID!",
                        null
                );
                return ResponseEntity.status(HttpStatus.OK).body(response);
            }else {
                //Check whether the provided balance id is already deleted or not;
                if(balanceObject.get(0).getDeleteStatus() == 1){
                    customAPIResponse<getBalanceForUpdateDTO> response = new customAPIResponse<>(
                            false,
                            "This Balance ID is already deleted. No update required!",
                            null
                    );
                    return ResponseEntity.status(HttpStatus.OK).body(response);
                }else {
                    //Check whether the updating balance is related to a previous day balance or not;
                    if(balanceObject.get(0).getBalanceDate().equals(LocalDate.now())){
                        customAPIResponse<getBalanceForUpdateDTO> response = new customAPIResponse<>(
                                true,
                                null,
                                balanceObject.get(0)
                        );
                        return ResponseEntity.status(HttpStatus.OK).body(response);
                    }else {
                        customAPIResponse<getBalanceForUpdateDTO> response = new customAPIResponse<>(
                                false,
                                "You are not authorized to update previous day balance data!",
                                null
                        );
                        return ResponseEntity.status(HttpStatus.OK).body(response);
                    }
                }
            }
        }catch (Exception e){
            customAPIResponse<getBalanceForUpdateDTO> response = new customAPIResponse<>(
                    false,
                    e.getMessage() + ": " + "Un-expected error occurred while fetching Account Balances. Please contact administrator!",
                   null
            );
            return ResponseEntity.status(HttpStatus.OK).body(response);
        }
    }

    @Transactional
    @Override
    public ResponseEntity<customAPIResponse<balanceUpdateDTO>> updateBalance(balanceUpdateDTO updatedBalance) {
        try {
            float newBalance;
            float newOutstandingBalance;
            //Check whether updating balance is a + action or - action;
            if(updatedBalance.getAction().equals("+")){
                newBalance = updatedBalance.getBalanceAmount() + updatedBalance.getAdjustmentAmount();
                int affectedRow = accountBalanceRepository.updateAccountBalance(newBalance, updatedBalance.getBalanceId());
                if(affectedRow > 0){
                    customAPIResponse<balanceUpdateDTO> response = new customAPIResponse<>(
                            true,
                            "Balance ID: " + updatedBalance.getBalanceId() + " updated successfully!",
                            null
                    );
                    return ResponseEntity.status(HttpStatus.OK).body(response);
                }else {
                    customAPIResponse<balanceUpdateDTO> response = new customAPIResponse<>(
                            false,
                            "Un-expected error occurred while saving Account Balances. Please contact administrator!",
                            null
                    );
                    return ResponseEntity.status(HttpStatus.OK).body(response);
                }
            }else {
                newBalance = updatedBalance.getBalanceAmount() - updatedBalance.getAdjustmentAmount();
                int affectedRow = accountBalanceRepository.updateAccountBalance(newBalance, updatedBalance.getBalanceId());
                if(affectedRow > 0){
                    customAPIResponse<balanceUpdateDTO> response = new customAPIResponse<>(
                            true,
                            "Balance ID: " + updatedBalance.getBalanceId() + " updated successfully!",
                            null
                    );
                    return ResponseEntity.status(HttpStatus.OK).body(response);
                }else {
                    customAPIResponse<balanceUpdateDTO> response = new customAPIResponse<>(
                            false,
                            "Un-expected error occurred while saving Account Balances. Please contact administrator!",
                            null
                    );
                    return ResponseEntity.status(HttpStatus.OK).body(response);
                }
            }
        }catch (Exception e){
            customAPIResponse<balanceUpdateDTO> response = new customAPIResponse<>(
                    false,
                    "Un-expected error occurred while saving Account Balances. Please contact administrator!",
                    null
            );
            return ResponseEntity.status(HttpStatus.OK).body(response);
        }
    }

    @Override
    public ResponseEntity<customAPIResponse<getBalanceForDeleteDTO>> getBalanceForDelete(String balanceId) {
        try{
            String Sql = "SELECT BAL.balance_id, BNK.bank_name, ACC.account_number, BAL.balance_date, BAL.balance_amount, BAL.delete_status FROM account_balance BAL LEFT JOIN bank_account ACC ON BAL.account_id = ACC.account_id LEFT JOIN bank BNK ON ACC.bank = BNK.bank_id WHERE BAL.balance_id = ?";
            List<getBalanceForDeleteDTO> deleteBalanceObj = template.query(Sql, new Object[]{balanceId}, new deleteBalanceMapper());
            //Check whether any balance record is available for provided Balance ID;
            if(deleteBalanceObj.isEmpty()){
                return ResponseEntity.status(HttpStatus.OK).body(new customAPIResponse<>(
                        false,
                        "No balance record found for provided Balance ID!",
                        null
                ));
            }else {
                //Check whether the balance record is a previous day balance record;
                if(!deleteBalanceObj.get(0).getBalanceDate().equals(LocalDate.now())){
                    return ResponseEntity.status(HttpStatus.OK).body(new customAPIResponse<>(
                            false,
                            "You are not authorized to delete a previous day balance record." + " [Balance Date: " + deleteBalanceObj.get(0).getBalanceDate() + "]",
                            null
                    ));
                }else {
                    //Check whether the balance record is already deleted or not;
                    if(deleteBalanceObj.get(0).getDeleteStatus() == 1){
                        return ResponseEntity.status(HttpStatus.OK).body(new customAPIResponse<>(
                                false,
                                "This balance record is already deleted. No further deletion is required!",
                                null
                        ));
                    }else {
                        return ResponseEntity.status(HttpStatus.OK).body(new customAPIResponse<>(
                                true,
                                null,
                                deleteBalanceObj.get(0)
                        ));
                    }
                }
            }
        }catch (Exception e){
            return ResponseEntity.status(HttpStatus.OK).body(new customAPIResponse<>(
                    false,
                    e.getMessage() + "Un-expected error occurred while fetching balance data. Please contact administrator!",
                    null
            ));
        }
    }

    @Transactional
    @Override
    public ResponseEntity<customAPIResponse<String>> deleteBalance(String balanceId) {
        //Check whether any adjustments have been initiated under this balance.
        List<String> adjustmentList = accountBalanceAdjustmentsRepository.getAvailableAdjustments(balanceId);
        if(!adjustmentList.isEmpty()){
            return ResponseEntity.status(HttpStatus.CONFLICT).body(new customAPIResponse<>(
                    false,
                    "Several adjustments have been initiated for this balance. Please reverse the adjustments first, before delete the balance!",
                    null
            ));
        }else {
            try{
                int affectedRow = accountBalanceRepository.deleteBalance(session.getAttribute("userId").toString(), balanceId);
                if(affectedRow > 0){
                    return ResponseEntity.status(HttpStatus.OK).body(new customAPIResponse<>(
                            true,
                            "Account Balance " + balanceId + " deleted successfully!",
                            null
                    ));
                }else {
                    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new customAPIResponse<>(
                            false,
                            "Not found account balance!",
                            null
                    ));
                }
            }catch (Exception e){
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new customAPIResponse<>(
                        false,
                        "Un-expected error occurred while deleting balance data. Please contact administrator!",
                        null
                ));
            }
        }
    }

    @Override
    public ResponseEntity<customAPIResponse<List<balanceAdjustmentsDTO>>> getAdjustments(String balanceId) {
        try{
            String Sql = "SELECT adj.adjustment_id AS 'adjustment_id', crsadj.cross_adjustment_id, adj.adjustment_amount AS 'adjustment_amount', adj.adjustment_remark AS 'remark', bal.balance_amount AS 'opening_balance', (COALESCE((SELECT COALESCE(SUM(balAdj.adjustment_amount), 0) FROM account_balance_adjustments balAdj LEFT JOIN cross_adjustment crsadj1 ON balAdj.cross_adjustment_id = crsadj1.cross_adjustment_id WHERE crsadj1.is_reversed = 0 AND balAdj.adjustment_balance = ?)+bal.balance_amount,0)) AS 'closing_balance', bal.balance_id, acc.account_number FROM account_balance_adjustments adj LEFT JOIN account_balance bal ON bal.balance_id = adj.adjustment_balance LEFT JOIN bank_account acc ON acc.account_id = bal.account_id LEFT JOIN cross_adjustment crsadj ON crsadj.cross_adjustment_id = adj.cross_adjustment_id WHERE crsadj.is_reversed = 0 AND adj.adjustment_balance = ?";
            List<balanceAdjustmentsDTO> adjustmentList = template.query(Sql, new balanceAdjustmentsMapper(), balanceId, balanceId);
            if(!adjustmentList.isEmpty()){
                return ResponseEntity.status(HttpStatus.OK).body(new customAPIResponse<>(
                        true,
                        null,
                        adjustmentList
                ));
            }else {
                return ResponseEntity.status(HttpStatus.CONFLICT).body(new customAPIResponse<>(
                        false,
                        "No adjustment can be found for provided Account Balance!",
                        null
                ));
            }
        }catch (Exception e){
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new customAPIResponse<>(
                    false,
                    "Un-expected error occurred while getting adjustment data. Please contact administrator!",
                    null
            ));
        }
    }

    //Implemented a method to update the Outstanding Account Balance;
    @Transactional
    public void updateOutstandingBalance(String balanceId, float updateAmount, int adjustmentType){
        //AdjustmentType 0 = + , 1 = -
        //Get existing balance amount
        float existingBalance = accountBalanceRepository.existingBalanceAmount(balanceId);
        if(adjustmentType == 0){
            accountBalanceRepository.updateOutstandingBalance(existingBalance + updateAmount, balanceId);
        }else {
            accountBalanceRepository.updateOutstandingBalance(existingBalance - updateAmount, balanceId);
        }
    }

    @Override
    public ResponseEntity<customAPIResponse<List<getAccountBalancesDTO>>> getAccountBalances(LocalDate balanceDate) {
        try{
            String Sql = "SELECT acc.account_number, accbal.balance_amount FROM account_balance accbal LEFT JOIN bank_account acc ON acc.account_id = accbal.account_id WHERE accbal.balance_date = ? AND accbal.delete_status = 0 AND accbal.balance_amount > 0";
            List<getAccountBalancesDTO> balanceList = template.query(Sql, new getAccountBalancesMapper(), balanceDate);
            return ResponseEntity.status(HttpStatus.OK).body(new customAPIResponse<>(
                    true,
                    null,
                    balanceList
            ));
        }catch (Exception e){
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new customAPIResponse<>(
                    false,
                    "No Account Balances found!",
                    null
            ));
        }
    }

    @Override
    public ResponseEntity<customAPIResponse<List<getOverdraftBalancesDTO>>> getOverdraftBalances(LocalDate balanceDate) {
        try{
            String Sql = "SELECT accbal.balance_id AS 'Balance ID', acc.account_number AS 'Account Number', accbal.balance_amount AS 'Balance Amount' FROM account_balance accbal LEFT JOIN bank_account acc ON acc.account_id = accbal.account_id WHERE accbal.balance_amount < 0 AND accbal.delete_status = 0 AND accbal.balance_date = ?";
            List<getOverdraftBalancesDTO> overdraftBalances = template.query(Sql, new getOverdraftBalancesMapper(), balanceDate);
            return ResponseEntity.status(HttpStatus.OK).body(new customAPIResponse<>(
                    true,
                    null,
                    overdraftBalances
            ));
        }catch (Exception e){
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new customAPIResponse<>(
                    false,
                    "No Overdraft Balances found!",
                    null
            ));
        }
    }

}
