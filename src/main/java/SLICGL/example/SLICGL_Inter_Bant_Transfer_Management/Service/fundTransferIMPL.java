package SLICGL.example.SLICGL_Inter_Bant_Transfer_Management.Service;

import SLICGL.example.SLICGL_Inter_Bant_Transfer_Management.APIResponse.customAPIResponse;
import SLICGL.example.SLICGL_Inter_Bant_Transfer_Management.DTO.*;
import SLICGL.example.SLICGL_Inter_Bant_Transfer_Management.Entity.Repos;
import SLICGL.example.SLICGL_Inter_Bant_Transfer_Management.Entity.transfers;
import SLICGL.example.SLICGL_Inter_Bant_Transfer_Management.Repositoriy.*;
import SLICGL.example.SLICGL_Inter_Bant_Transfer_Management.SqlMappers.*;
import SLICGL.example.SLICGL_Inter_Bant_Transfer_Management.TransferIdGenerator;
import jakarta.servlet.http.HttpSession;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;

@Service
public class fundTransferIMPL implements fundTransferService{

    @Autowired
    transfersRepo transferRepository;

    @Autowired
    HttpSession session;

    @Autowired
    UserRepo userRepository;

    @Autowired
    bankAccountRepo accountRepository;

    @Autowired
    transferChannelRepo channelRepository;

    @Autowired
    JdbcTemplate template;

    @Autowired
    transferChannelRepo transferChannelRepository;

    @Autowired
    accountBalanceAdjustmentsIMPL accountBalanceAdjustments;

    @Autowired
    transferOptionRepository transferOptionRepository;

    @Autowired
    repoRepository repoRepository;

    @Autowired
    repoAdjustmentIMPL repoAdjustment;

    @Autowired
    accountBalanceAdjustmentsRepo accountBalanceAdjustmentsRepository;

    @Autowired
    repoAdjustmentRepo repoAdjustmentRepository;

    @Autowired
    fundRequestRepo fundRequestRepository;

    @Autowired
    crossAdjustmentIMPL crossAdjustmentIMPL;

    @Autowired
    crossAdjustmentRepo crossAdjustmentRepository;

    @Autowired
    accountBalanceRepo accountBalanceRepository;

    //This method has been implemented to initiate a new transfer;
    @Transactional
    public String newTransfer(String newTransferId, String fromAccount, String toAccount, String channel, BigDecimal amount, String fromRepo, String toRepo, String crossAdjustment){
        Repos fromRepoEntity = (fromRepo != null) ? repoRepository.findById(fromRepo).orElse(null) : null;
        Repos toRepoEntity = (toRepo != null) ? repoRepository.findById(toRepo).orElse(null) : null;

        try{
            transferRepository.save(
                    new transfers(
                            newTransferId,
                            LocalDateTime.now(),
                            amount,
                            0,
                            null,
                            0,
                            null,
                            null,
                            0,
                            null,
                            null,
                            userRepository.findById(session.getAttribute("userId").toString()).get(),
                            accountRepository.findById(fromAccount).get(),
                            accountRepository.findById(toAccount).get(),
                            channelRepository.findById(channel).get(),
                            fromRepoEntity,
                            toRepoEntity,
                            crossAdjustmentRepository.findById(crossAdjustment).get()
                    )
            );
            return newTransferId;
        }catch (Exception e){
            throw new RuntimeException("Transfer creation failed");
        }
    }

    @Override
    @Transactional
    public ResponseEntity<customAPIResponse<String>> initiateTransfers() {
        try{
            //Get last transfer id by calling for generateTransferId method;
            String generatedLastTransferId = transferRepository.getLastTransferId();
            TransferIdGenerator idGenerator = new TransferIdGenerator(generatedLastTransferId);

            //Check whether any repo has been created, which facilitate for fund transferring;
            int result = transferRepository.checkFundTransferableRepo(LocalDate.now());
            if(result != 1){
                return ResponseEntity.status(HttpStatus.CONFLICT).body(
                        new customAPIResponse<>(
                                false,
                                "Please create a REPO, which have facility for fund transfers, before initiate the transfers!",
                                null
                        )
                );
            }else {
                //Check whether the account balances has been entered for all bank accounts, which is funds are already requested;
                List<String> nullBalanceList = fundRequestRepository.nullBalanceRequest(LocalDate.now(), LocalDate.now());
                if(!nullBalanceList.isEmpty()){
                    return ResponseEntity.status(HttpStatus.CONFLICT).body(
                            new customAPIResponse<>(
                                    false,
                                    "Identified null balances for fund requested bank accounts. Please check corresponding account balances for each fund requests before initiate the transfers!",
                                    null
                            )
                    );
                }else {
                    //Create two array lists to store fundReceivable accounts and fundTransferable accounts;
                    List<accountBalanceListDTO> fundTransferableAccounts = new ArrayList<>();
                    List<accountBalanceListDTO> fundReceivableAccounts = new ArrayList<>();

                    String Sql = "SELECT bnk.account_id, bnk.account_number, COALESCE(account_balances.balance_id,0) AS balance_id, COALESCE(COALESCE(account_balances.balance_amount,0) + COALESCE(account_balance_adjustments.net_adjustment,0)-COALESCE(fund_request.total_request,0),0) AS final_balance FROM bank_account bnk LEFT JOIN (SELECT bal.account_id, bal.balance_id, bal.balance_amount FROM account_balance bal WHERE bal.delete_status = 0 AND DATE(bal.balance_date) = DATE(?)) AS account_balances ON bnk.account_id = account_balances.account_id LEFT JOIN (SELECT adj.adjustment_balance, COALESCE(SUM(adj.adjustment_amount),0) AS 'net_adjustment' FROM account_balance_adjustments adj LEFT JOIN cross_adjustment crsadj ON crsadj.cross_adjustment_id = adj.cross_adjustment_id WHERE crsadj.is_reversed = 0 AND DATE(adj.adjustment_date) = DATE(?) GROUP BY adj.adjustment_balance) AS account_balance_adjustments ON account_balances.balance_id = account_balance_adjustments.adjustment_balance LEFT JOIN (SELECT req.bank_account, COALESCE(SUM(req.request_amount),0) AS total_request FROM fund_request req WHERE req.approve_status = 1 AND req.delete_status = 0 AND DATE(req.required_date) = DATE(?) GROUP BY req.bank_account) AS fund_request ON bnk.account_id = fund_request.bank_account WHERE bnk.delete_status = 0";
                    List<accountBalanceListDTO> accountList = template.query(Sql, new accountBalanceListMapper(), LocalDate.now(), LocalDate.now(), LocalDate.now());

                    //Loop accountList and segregate the bank accounts into above defined two array lists;
                    //If final balance = 0, then ignored;
                    //If final balance  > 0, then it is a fund transferable account;
                    //If final balance  < 0, then it is a fund receivable account;
                    for(accountBalanceListDTO account: accountList){
                        if(account.getFinalBalance().compareTo(BigDecimal.ZERO) == 0) {
                            //Ignored;
                        }else if(account.getFinalBalance().compareTo(BigDecimal.ZERO) > 0){
                            fundTransferableAccounts.add(account);
                        }else{
                            fundReceivableAccounts.add(account);
                        }
                    }
                    //Loop each transfer channels under the priority levels;
                    List<String> channelList = transferChannelRepository.channelsWithPriorities();
                    for(String channel: channelList){
                        //Initiate transfers under each channel types;
                        //Loop each fund receivable bank accounts and initiate transfers from fund transferable channels;
                        Iterator<accountBalanceListDTO> receivableIterator = fundReceivableAccounts.iterator();
                        while (receivableIterator.hasNext()){
                            accountBalanceListDTO receivableAccount = receivableIterator.next();
                            //Loop each fund transferable accounts and, check the possibility of initiating the transfers to the current receivable account;
                            Iterator<accountBalanceListDTO> transferableIterator = fundTransferableAccounts.iterator();
                            while (transferableIterator.hasNext()){
                                accountBalanceListDTO transferableAccount = transferableIterator.next();
                                //Checking possibility of initiating the transfers;
                                int isAvailable = transferOptionRepository.optionIsAvailable(transferableAccount.getAccountId(), receivableAccount.getAccountId(), channel);
                                if(isAvailable == 1){
                                    //If we can initiate transfer, check the balances of both receivable and transferable accounts to initiate transfers;
                                    BigDecimal transferableBalance = transferableAccount.getFinalBalance();
                                    BigDecimal receivableBalance = receivableAccount.getFinalBalance();
                                    BigDecimal differenceBalance = transferableBalance.subtract(receivableBalance.multiply(BigDecimal.valueOf(-1)));
                                    if(differenceBalance.compareTo(BigDecimal.ZERO) > 0){
                                        //Create new cross adjustment;
                                        String crossAdjustment = crossAdjustmentIMPL.saveNewCrossAdjustment();
                                        //In this level mean, it can be recovered full request of receivable account and there is an additional balance in transferable account after initiating the transfers;
                                        //Initiate transfer;
                                        String transferId = this.newTransfer(idGenerator.getNextId(), transferableAccount.getAccountId(), receivableAccount.getAccountId(), channel, receivableBalance.multiply(BigDecimal.valueOf(-1)), null, null, crossAdjustment);
                                        //Identify the adjustments of both from account and to account balances;
                                        //Adjustment for from account;
                                        accountBalanceAdjustments.saveNewAdjustment(receivableAccount.getAccountNumber(), receivableBalance, transferableAccount.getBalanceId(), transferId, crossAdjustment);
                                        //Adjustment for to account;
                                        accountBalanceAdjustments.saveNewAdjustment(transferableAccount.getAccountNumber(), receivableBalance.multiply(BigDecimal.valueOf(-1)), receivableAccount.getBalanceId(), transferId, crossAdjustment);
                                        //Since fully recovered the total request, the relevant receivable account should remove from the fundReceivableAccounts array list;
                                        receivableIterator.remove();
                                        //Then update the new balance of the transferable account after the initiation of transfer;
                                        transferableAccount.setFinalBalance(differenceBalance);
                                        break;
                                    }else if(differenceBalance.compareTo(BigDecimal.ZERO) < 0){
                                        //Create new cross adjustment;
                                        String crossAdjustment = crossAdjustmentIMPL.saveNewCrossAdjustment();
                                        //In this level mean, it can be transferred full amount of transferable account balance. However, there may have outstanding amount for further recovering the receivable balance;
                                        //Initiate transfer;
                                        String transferId = this.newTransfer(idGenerator.getNextId(), transferableAccount.getAccountId(), receivableAccount.getAccountId(), channel, transferableBalance, null, null, crossAdjustment);
                                        //Identify the adjustments of both from account and to account balances;
                                        //Adjustment for from account;
                                        accountBalanceAdjustments.saveNewAdjustment(receivableAccount.getAccountNumber(), transferableBalance.multiply(BigDecimal.valueOf(-1)), transferableAccount.getBalanceId(), transferId, crossAdjustment);
                                        //Adjustment for to account;
                                        accountBalanceAdjustments.saveNewAdjustment(transferableAccount.getAccountNumber(), transferableBalance, receivableAccount.getBalanceId(), transferId, crossAdjustment);
                                        //Since the total balance of the transferable account become zero, the relevant account removed from the array list;
                                        transferableIterator.remove();
                                        //Then update the new balance of the receivable account after the initiation of transfer;
                                        receivableAccount.setFinalBalance(differenceBalance);
                                    }else {
                                        //Create new cross adjustment;
                                        String crossAdjustment = crossAdjustmentIMPL.saveNewCrossAdjustment();
                                        //In this level mean, it can be recovered full request of receivable account and there is no additional balance in transferable account after initiating the transfers;
                                        //Initiate transfer;
                                        String transferId = this.newTransfer(idGenerator.getNextId(), transferableAccount.getAccountId(), receivableAccount.getAccountId(), channel, receivableBalance.multiply(BigDecimal.valueOf(-1)), null, null, crossAdjustment);
                                        //Identify the adjustments of both from account and to account balances;
                                        //Adjustment for from account;
                                        accountBalanceAdjustments.saveNewAdjustment(receivableAccount.getAccountNumber(), receivableBalance, transferableAccount.getBalanceId(), transferId, crossAdjustment);
                                        //Adjustment for to account;
                                        accountBalanceAdjustments.saveNewAdjustment(transferableAccount.getAccountNumber(), receivableBalance.multiply(BigDecimal.valueOf(-1)), receivableAccount.getBalanceId(), transferId, crossAdjustment);
                                        //Since fully recovered the total request and the balance of transferable account become zero, it can be removed both receivable and transferable account from both array list;
                                        receivableIterator.remove();
                                        transferableIterator.remove();
                                        break;
                                    }
                                }else {
                                    //Since no transfer option is available, check for another account;
                                }
                            }
                        }
                    }
                    //Initiate transfers to receivable bank accounts from repo account;
                    //Get existing fund transferable repo;
                    String sql = "SELECT rpo.repo_id, rpo.bank_account, COALESCE(COALESCE(rpo.repo_value,0) + COALESCE(repo_adjustment.total_adjustment,0), 0) AS final_value FROM repos rpo LEFT JOIN (SELECT rpoadj.adjusted_repo,SUM(rpoadj.adjustment_amount) AS total_adjustment FROM repo_adjustment rpoadj LEFT JOIN cross_adjustment crsadj ON crsadj.cross_adjustment_id = rpoadj.cross_adjustment_id WHERE crsadj.is_reversed = 0 AND DATE(rpoadj.adjustment_date) = ? GROUP BY rpoadj.adjusted_repo) AS repo_adjustment ON repo_adjustment.adjusted_repo = rpo.repo_id WHERE rpo.transfer_eligibility = 1 AND rpo.is_deleted = 0 AND rpo.is_invested = 0 AND DATE(rpo.created_date) = ?";
                    List<repoDetailsForTransferDTO> repo = template.query(sql, new repoDetailsForTransferMapper(), LocalDate.now(), LocalDate.now());
                    if(!repo.isEmpty()){
                        //Create new repo array for add the repo account list;
                        List<repoDetailsForTransferDTO> repoAccounts = new ArrayList<>();
                        //Loop each repo and check the repo final value and if there is any transferable value, add to the repoAccounts array;
                        for(repoDetailsForTransferDTO repoAccount: repo){
                            if(repoAccount.getFinalRepoValue().compareTo(BigDecimal.ZERO)==0){
                                //Ignored;
                            }else if(repoAccount.getFinalRepoValue().compareTo(BigDecimal.ZERO) > 0){
                                repoAccounts.add(repoAccount);
                            }else{
                                //Ignored;
                            }
                        }
                        //Loop each transfer channels under the priority levels;
                        List<String> channelListForRepoTransfer = transferChannelRepository.channelsWithPriorities();
                        for(String channel: channelListForRepoTransfer){
                            //Initiate transfers under each channel types;
                            //Loop each fund receivable bank accounts and initiate transfers from fund transferable channels;
                            Iterator<accountBalanceListDTO> receivableIterator = fundReceivableAccounts.iterator();
                            while (receivableIterator.hasNext()){
                                accountBalanceListDTO receivableAccount = receivableIterator.next();
                                //Loop each REPO accounts and, check the possibility of initiating the transfers to the current receivable account;
                                Iterator<repoDetailsForTransferDTO> repoIterator = repoAccounts.iterator();
                                while (repoIterator.hasNext()) {
                                    repoDetailsForTransferDTO repoAccount = repoIterator.next();
                                    //Before initiate the transfer, we need to check whether the repo account id and receivable account id is same or not;
                                    if(repoAccount.getAccountId().equals(receivableAccount.getAccountId())){
                                        //If both bank accounts are same, no transfer is required. But the adjustment to the relevant repo is needed;
                                        //Adjustment for from repo account;
                                        //If we can initiate transfer, check the balances of both receivable and repo accounts to initiate transfers;
                                        BigDecimal repoBalance = repoAccount.getFinalRepoValue();
                                        BigDecimal receivableBalance = receivableAccount.getFinalBalance();
                                        BigDecimal differenceBalance = repoBalance.subtract(receivableBalance.multiply(BigDecimal.valueOf(-1)));
                                        if(differenceBalance.compareTo(BigDecimal.ZERO) > 0){
                                            //Create new cross adjustment;
                                            String crossAdjustment = crossAdjustmentIMPL.saveNewCrossAdjustment();
                                            //In this level mean, it can be recovered full request of receivable account and there is an additional balance in repo account after initiating the adjustment;
                                            repoAdjustment.saveNewAdjustment(receivableAccount.getAccountNumber(), receivableBalance, repoAccount.getRepoId(), null, crossAdjustment);
                                            //Adjustment for to account;
                                            accountBalanceAdjustments.saveNewAdjustment(repoAccount.getRepoId(), receivableBalance.multiply(BigDecimal.valueOf(-1)), receivableAccount.getBalanceId(), null, crossAdjustment);
                                            //Since fully recovered the total request, the relevant receivable account should remove from the fundReceivableAccounts array list;
                                            receivableIterator.remove();
                                            //Then update the new balance of the transferable account after the initiation of transfer;
                                            repoAccount.setFinalRepoValue(differenceBalance);
                                            break;
                                        }else if(differenceBalance.compareTo(BigDecimal.ZERO) < 0){
                                            //Create new cross adjustment;
                                            String crossAdjustment = crossAdjustmentIMPL.saveNewCrossAdjustment();
                                            //In this level mean, it can be adjusted full amount of repo account balance. However, there may have outstanding amount for further recovering the receivable balance;
                                            repoAdjustment.saveNewAdjustment(receivableAccount.getAccountNumber(), repoBalance.multiply(BigDecimal.valueOf(-1)), repoAccount.getRepoId(), null, crossAdjustment);
                                            //Adjustment for to account;
                                            accountBalanceAdjustments.saveNewAdjustment(repoAccount.getRepoId(), repoBalance, receivableAccount.getBalanceId(), null, crossAdjustment);
                                            //Since the total balance of the transferable account become zero, the relevant repo account removed from the array list;
                                            repoIterator.remove();
                                            //Then update the new balance of the receivable account after the initiation of transfer;
                                            receivableAccount.setFinalBalance(differenceBalance);
                                        }else {
                                            //Create new cross adjustment;
                                            String crossAdjustment = crossAdjustmentIMPL.saveNewCrossAdjustment();
                                            //In this level mean, it can be recovered full request of receivable account and there is no additional balance in repo account after initiating the adjustment;
                                            repoAdjustment.saveNewAdjustment(receivableAccount.getAccountNumber(), receivableBalance, repoAccount.getRepoId(), null, crossAdjustment);
                                            //Adjustment for to account;
                                            accountBalanceAdjustments.saveNewAdjustment(repoAccount.getRepoId(), receivableBalance.multiply(BigDecimal.valueOf(-1)), receivableAccount.getBalanceId(), null, crossAdjustment);
                                            //Since fully recovered the total request and the balance of transferable account become zero, it can be removed both receivable and transferable account from both array list;
                                            receivableIterator.remove();
                                            repoIterator.remove();
                                        }
                                    }else {
                                        //Checking possibility of initiating the transfers;
                                        int isAvailable = transferOptionRepository.optionIsAvailable(repoAccount.getAccountId(), receivableAccount.getAccountId(), channel);
                                        if(isAvailable == 1){
                                            //If we can initiate transfer, check the balances of both receivable and repo accounts to initiate transfers;
                                            BigDecimal repoBalance = repoAccount.getFinalRepoValue();
                                            BigDecimal receivableBalance = receivableAccount.getFinalBalance();
                                            BigDecimal differenceBalance = repoBalance.subtract(receivableBalance.multiply(BigDecimal.valueOf(-1)));
                                            if(differenceBalance.compareTo(BigDecimal.ZERO) > 0){
                                                //Create new cross adjustment;
                                                String crossAdjustment = crossAdjustmentIMPL.saveNewCrossAdjustment();
                                                //In this level mean, it can be recovered full request of receivable account and there is an additional balance in repo account after initiating the transfers;
                                                //Initiate transfer;
                                                String transferId = this.newTransfer(idGenerator.getNextId(), repoAccount.getAccountId(), receivableAccount.getAccountId(), channel, receivableBalance.multiply(BigDecimal.valueOf(-1)), repoAccount.getRepoId(), null, crossAdjustment);
                                                //Identify the adjustments of both from account and to account balances;
                                                //Adjustment for from account;
                                                repoAdjustment.saveNewAdjustment(receivableAccount.getAccountNumber(), receivableBalance, repoAccount.getRepoId(), transferId, crossAdjustment);
                                                //Adjustment for to account;
                                                accountBalanceAdjustments.saveNewAdjustment(repoAccount.getRepoId(), receivableBalance.multiply(BigDecimal.valueOf(-1)), receivableAccount.getBalanceId(), transferId, crossAdjustment);
                                                //Since fully recovered the total request, the relevant receivable account should remove from the fundReceivableAccounts array list;
                                                receivableIterator.remove();
                                                //Then update the new balance of the transferable account after the initiation of transfer;
                                                repoAccount.setFinalRepoValue(differenceBalance);
                                                break;
                                            }else if(differenceBalance.compareTo(BigDecimal.ZERO) < 0){
                                                //Create new cross adjustment;
                                                String crossAdjustment = crossAdjustmentIMPL.saveNewCrossAdjustment();
                                                //In this level mean, it can be transferred full amount of repo account balance. However, there may have outstanding amount for further recovering the receivable balance;
                                                //Initiate transfer;
                                                String transferId = this.newTransfer(idGenerator.getNextId(), repoAccount.getAccountId(), receivableAccount.getAccountId(), channel, repoBalance, repoAccount.getRepoId(), null, crossAdjustment);
                                                //Identify the adjustments of both from account and to account balances;
                                                //Adjustment for from account;
                                                repoAdjustment.saveNewAdjustment(receivableAccount.getAccountNumber(), repoBalance.multiply(BigDecimal.valueOf(-1)), repoAccount.getRepoId(), transferId, crossAdjustment);
                                                //Adjustment for to account;
                                                accountBalanceAdjustments.saveNewAdjustment(repoAccount.getRepoId(), repoBalance, receivableAccount.getBalanceId(), transferId, crossAdjustment);
                                                //Since the total balance of the transferable account become zero, the relevant repo account removed from the array list;
                                                repoIterator.remove();
                                                //Then update the new balance of the receivable account after the initiation of transfer;
                                                receivableAccount.setFinalBalance(differenceBalance);
                                            }else {
                                                //Create new cross adjustment;
                                                String crossAdjustment = crossAdjustmentIMPL.saveNewCrossAdjustment();
                                                //In this level mean, it can be recovered full request of receivable account and there is no additional balance in repo account after initiating the transfers;
                                                //Initiate transfer;
                                                String transferId = this.newTransfer(idGenerator.getNextId(), repoAccount.getAccountId(), receivableAccount.getAccountId(), channel, receivableBalance.multiply(BigDecimal.valueOf(-1)), repoAccount.getRepoId(), null, crossAdjustment);
                                                //Identify the adjustments of both from account and to account balances;
                                                //Adjustment for from account;
                                                repoAdjustment.saveNewAdjustment(receivableAccount.getAccountNumber(), receivableBalance, repoAccount.getRepoId(), transferId, crossAdjustment);
                                                //Adjustment for to account;
                                                accountBalanceAdjustments.saveNewAdjustment(repoAccount.getRepoId(), receivableBalance.multiply(BigDecimal.valueOf(-1)), receivableAccount.getBalanceId(), transferId, crossAdjustment);
                                                //Since fully recovered the total request and the balance of transferable account become zero, it can be removed both receivable and transferable account from both array list;
                                                receivableIterator.remove();
                                                repoIterator.remove();
                                            }
                                        }else {
                                            //Since no transfer option is available, check for another account;
                                        }
                                    }
                                }
                            }
                        }
                    }else {
                        //Ignored;
                    }
                    //Initiate transfers from transferable bank accounts to repo account;
                    //Get existing fund transferable repo;
                    String sqlRepo = "SELECT rpo.repo_id, rpo.bank_account, COALESCE(COALESCE(rpo.repo_value,0) + COALESCE(repo_adjustment.total_adjustment,0), 0) AS final_value FROM repos rpo LEFT JOIN (SELECT rpoadj.adjusted_repo,SUM(rpoadj.adjustment_amount) AS total_adjustment FROM repo_adjustment rpoadj LEFT JOIN cross_adjustment crsadj ON crsadj.cross_adjustment_id = rpoadj.cross_adjustment_id WHERE crsadj.is_reversed = 0 AND DATE(rpoadj.adjustment_date) = ? GROUP BY rpoadj.adjusted_repo) AS repo_adjustment ON repo_adjustment.adjusted_repo = rpo.repo_id WHERE rpo.transfer_eligibility = 1 AND rpo.is_deleted = 0 AND rpo.is_invested = 0 AND DATE(rpo.created_date) = ?";
                    List<repoDetailsForTransferDTO> toRepo = template.query(sql, new repoDetailsForTransferMapper(), LocalDate.now(), LocalDate.now());
                    if(!toRepo.isEmpty()){
                        //Loop each transfer channels under the priority levels;
                        List<String> channelListForRepoTransfer = transferChannelRepository.channelsWithPriorities();
                        for(String channel: channelListForRepoTransfer){
                            //Initiate transfers under each channel types;
                            //Loop each repo accounts and initiate transfers from fund transferable accounts;
                            Iterator<repoDetailsForTransferDTO> repoIterator = toRepo.iterator();
                            while (repoIterator.hasNext()){
                                repoDetailsForTransferDTO repoAccount = repoIterator.next();
                                //Loop each transferable accounts and, check the possibility of initiating the transfers to the repo account;
                                Iterator<accountBalanceListDTO> transferableIterator = fundTransferableAccounts.iterator();
                                while (transferableIterator.hasNext()) {
                                    accountBalanceListDTO transferableAccount = transferableIterator.next();
                                    //Checking possibility of initiating the transfers;
                                    int isAvailable = transferOptionRepository.optionIsAvailable(transferableAccount.getAccountId(), repoAccount.getAccountId(), channel);
                                    if(isAvailable == 1){
                                        //Create new cross adjustment;
                                        String crossAdjustment = crossAdjustmentIMPL.saveNewCrossAdjustment();
                                        //Initiate transfer;
                                        String transferId = this.newTransfer(idGenerator.getNextId(), transferableAccount.getAccountId(), repoAccount.getAccountId(), channel, transferableAccount.getFinalBalance(), null, repoAccount.getRepoId(), crossAdjustment);
                                        //Identify the adjustments of both from account and to account balances;
                                        //Adjustment for from account;
                                        accountBalanceAdjustments.saveNewAdjustment(repoAccount.getRepoId(), transferableAccount.getFinalBalance().multiply(BigDecimal.valueOf(-1)), transferableAccount.getBalanceId(), transferId, crossAdjustment);
                                        //Adjustment for to account;
                                        repoAdjustment.saveNewAdjustment(transferableAccount.getAccountNumber(), transferableAccount.getFinalBalance(), repoAccount.getRepoId(), transferId, crossAdjustment);
                                        //Since fully recovered the total request, the relevant receivable account should remove from the fundReceivableAccounts array list;
                                        transferableIterator.remove();
                                    }else {
                                        //Since no transfer option is available, check for another account;
                                    }
                                }
                            }
                        }
                    }else {
                        //Ignored;
                    }
                    return ResponseEntity.status(HttpStatus.OK).body(
                            new customAPIResponse<>(
                                    true,
                                    "Transfers successfully initiated!",
                                    null
                            )
                    );
                }
            }
        }catch (Exception e){
            throw new RuntimeException("Failed to initiate transfers", e);
        }
    }

    @Override
    public ResponseEntity<customAPIResponse<getTransferForTransferIdDTO>> getTransferForTransferId(String transferId) {
        try{
            if(transferId != null){
                String Sql = "SELECT TFR.transfer_id, ACCFROM.account_number AS 'from_account', ACCTO.account_number AS 'to_account', TFR.transfer_amount, CHNL.channel_type, CASE WHEN FROMREPO.repo_type IS NULL THEN 'N/A' ELSE FROMREPO.repo_id END as 'from_repo', CASE WHEN TOREPO.repo_type IS NULL THEN 'N/A' ELSE TOREPO.repo_id END as 'to_repo', CASE WHEN TFR.is_approved = 0 THEN 'Not-Approved' ELSE 'Approved' END AS 'approve_status', USR.user_first_name AS 'initiated_by', TFR.is_reversed AS 'reversed_status', TFR.cross_adjustment AS 'cross_adjustment' FROM transfers TFR LEFT JOIN bank_account ACCFROM ON TFR.from_account = ACCFROM.account_id LEFT JOIN bank_account ACCTO ON TFR.to_account = ACCTO.account_id LEFT JOIN transfer_channel CHNL ON TFR.chanel = CHNL.channel_id LEFT JOIN repos FROMREPO ON TFR.from_repo = FROMREPO.repo_id LEFT JOIN repos TOREPO ON TFR.to_repo = TOREPO.repo_id LEFT JOIN user USR ON USR.user_id = TFR.initiated_by WHERE transfer_id = ?";
                List<getTransferForTransferIdDTO> transferDetails = template.query(Sql, new getTransferForTransferIdMapper(), transferId);
                //Check whether any transfer details are available for provided Transfer ID;
                if(!transferDetails.isEmpty()){
                    //Check whether relevant record is already deleted or not;
                    if(transferDetails.get(0).getIsReversed() == 0){
                        return ResponseEntity.status(HttpStatus.OK).body(
                                new customAPIResponse<>(
                                        true,
                                        null,
                                        transferDetails.get(0)
                                )
                        );
                    }else {
                        return ResponseEntity.status(HttpStatus.CONFLICT).body(
                                new customAPIResponse<>(
                                        false,
                                        "This Transfer ID is already reversed!",
                                        null
                                )
                        );
                    }
                }else {
                    return ResponseEntity.status(HttpStatus.CONFLICT).body(
                            new customAPIResponse<>(
                                    false,
                                    "No record found for provided Transfer ID!",
                                    null
                            )
                    );
                }
            }else {
                return ResponseEntity.status(HttpStatus.CONFLICT).body(
                        new customAPIResponse<>(
                                false,
                                "Please provide valid Transfer ID!",
                                null
                        )
                );
            }

        }catch (Exception e){
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                    new customAPIResponse<>(
                            false,
                            "Un-expected error occurred while fetching transfer details. Please contact the administrator!",
                            null
                    )
            );
        }
    }

    @Override
    public ResponseEntity<customAPIResponse<List<getAllTransfersDTO>>> getAllTransfers(LocalDate transferDate) {
        try{
            String SQL = "SELECT TFR.transfer_id, ACCFROM.account_number AS 'from_account', ACCTO.account_number AS 'to_account', TFR.transfer_amount, CHNL.channel_type, CASE WHEN FROMREPO.repo_type IS NULL THEN 'N/A' ELSE FROMREPO.repo_id END as 'from_repo', CASE WHEN TOREPO.repo_type IS NULL THEN 'N/A' ELSE TOREPO.repo_id END as 'to_repo', CASE WHEN TFR.is_approved = 0 THEN 'PENDING' ELSE 'APPROVED' END AS 'approve_status', CASE WHEN TFR.approved_by IS NULL THEN 'N/A' ELSE APPROVEUSR.user_first_name END AS 'approved_by',CASE WHEN TFR.approved_date IS NULL THEN 'N/A' ELSE TFR.approved_date END AS 'Approved Date', USR.user_first_name AS 'initiated_by', TFR.transfer_date AS 'Initiated Date', CASE WHEN TFR.is_checked = 0 THEN 'PENDING' ELSE 'CHECKED' END AS 'checked_status', CASE WHEN TFR.checked_by IS NULL THEN 'N/A' ELSE CHECKEDUSER.user_first_name END AS 'checked_by', CASE WHEN TFR.checked_date IS NULL THEN 'N/A' ELSE TFR.checked_date END AS 'Checked Date', CASE WHEN  TFR.is_reversed = 0 THEN 'Not-Reversed' ELSE 'Reversed' END AS'is_reversed', CASE WHEN TFR.reversed_by IS NULL THEN 'N/A' ELSE REVERSEDUSR.user_first_name END AS 'reversed_by', TFR.cross_adjustment AS 'cross_adjustment' FROM transfers TFR LEFT JOIN bank_account ACCFROM ON TFR.from_account = ACCFROM.account_id LEFT JOIN bank_account ACCTO ON TFR.to_account = ACCTO.account_id LEFT JOIN transfer_channel CHNL ON TFR.chanel = CHNL.channel_id LEFT JOIN repos FROMREPO ON TFR.from_repo = FROMREPO.repo_id LEFT JOIN repos TOREPO ON TFR.to_repo = TOREPO.repo_id LEFT JOIN user USR ON USR.user_id = TFR.initiated_by LEFT JOIN user APPROVEUSR ON TFR.approved_by = APPROVEUSR.user_id LEFT JOIN user REVERSEDUSR ON TFR.reversed_by = REVERSEDUSR.user_id LEFT JOIN user CHECKEDUSER ON tfr.checked_by = CHECKEDUSER.user_id WHERE DATE(TFR.transfer_date)= ?";
            List <getAllTransfersDTO> transferDetails = template.query(SQL, new getAllTransfersMapper(), transferDate);
            if(!transferDetails.isEmpty()){
                return ResponseEntity.status(HttpStatus.OK).body(
                        new customAPIResponse<>(
                                true,
                                null,
                                transferDetails
                        )
                );
            }else {
                return ResponseEntity.status(HttpStatus.CONFLICT).body(
                        new customAPIResponse<>(
                                false,
                                "No Transfer Data available on provided date!",
                                null
                        )
                );
            }
        }catch (Exception e){
            System.out.println(e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                    new customAPIResponse<>(
                            false,
                            "Un-expected error occurred while fetching transfer details. Please contact the administrator!",
                            null
                    )
            );
        }
    }

    @Override
    public ResponseEntity<customAPIResponse<List<getAllTransferForApproveDTO>>> getAllTransferForApprove() {
        try{
            String Sql = "SELECT tfr.transfer_id, DATE(tfr.transfer_date) AS 'transfer_date', fromacc.account_number AS 'from account', toacc.account_number AS 'to account', CASE WHEN tfr.from_repo IS NULL THEN 'N/A' ELSE tfr.from_repo END AS 'from repo', CASE WHEN tfr.to_repo IS NULL THEN 'N/A' ELSE tfr.to_repo END AS 'to repo', tfr.transfer_amount FROM transfers tfr LEFT JOIN bank_account fromacc ON tfr.from_account = fromacc.account_id LEFT JOIn bank_account toacc ON tfr.to_account = toacc.account_id WHERE DATE(tfr.transfer_date) = ? AND tfr.is_approved = 0 AND tfr.is_checked = 1 AND tfr.is_reversed = 0";
            List<getAllTransferForApproveDTO> approveList = template.query(Sql, new getAllTransferForApproveMapper(), LocalDate.now());
            if(!approveList.isEmpty()){
                return ResponseEntity.status(HttpStatus.OK).body(
                        new customAPIResponse<>(
                                true,
                                null,
                                approveList
                        )
                );
            }else {
                return ResponseEntity.status(HttpStatus.CONFLICT).body(
                        new customAPIResponse<>(
                                false,
                                "No transfer data found for approving!",
                                null
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

    @Transactional
    @Override
    public ResponseEntity<customAPIResponse<String>> approveTransfer(String transferId) {
        try{
            //Check whether the transaction has been already initiated or approved by same user;
            if(Objects.equals(session.getAttribute("userId").toString(), transferRepository.checkUserForApprove(transferId).getCheckedBy()) || Objects.equals(session.getAttribute("userId").toString(), transferRepository.checkUserForApprove(transferId).getInitiatedBy())){
                return ResponseEntity.status(HttpStatus.CONFLICT).body(
                        new customAPIResponse<>(
                                false,
                                "You have already initiated or checked this transfer. You have no authority to approve this transaction. Please approve with another user!",
                                null
                        )
                );
            }else {
                int affectedRow = transferRepository.approveTransfer(session.getAttribute("userId").toString(), LocalDateTime.now(), transferId);
                if(affectedRow > 0){
                    return ResponseEntity.status(HttpStatus.OK).body(
                            new customAPIResponse<>(
                                    true,
                                    "Transfer approved successfully!",
                                    null
                            )
                    );
                }else {
                    return ResponseEntity.status(HttpStatus.CONFLICT).body(
                            new customAPIResponse<>(
                                    false,
                                    "Un-expected error occurred while approving transfer. Please contact administrator!",
                                    null
                            )
                    );
                }
            }
        }catch (Exception e){
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                    new customAPIResponse<>(
                            false,
                            "Un-expected error occurred while approving transfer. Please contact administrator!",
                            null
                    )
            );
        }
    }

    @Override
    public ResponseEntity<customAPIResponse<List<rejectTransfersDTO>>> rejectTransfers(LocalDate transferDate) {
        try{
            String Sql = "SELECT TFR.transfer_id, FROMACC.account_number AS 'from_account', TOACC.account_number AS 'to_account', TFR.transfer_amount, CHNL.channel_type AS 'channel', CASE WHEN FROMREPO.repo_id IS NULL THEN 'N/A' ELSE FROMREPO.repo_id END as 'from_repo', CASE WHEN TOREPO.repo_id IS NULL THEN 'N/A' ELSE TOREPO.repo_id END AS 'to_repo', USR.user_first_name AS 'initiated_by', CASE WHEN TFR.is_checked = 1 THEN 'Checked' ELSE 'Not-Checked' END AS 'checked status', CASE WHEN TFR.is_approved = 1 THEN 'Approved' ELSE 'Not-Approved' END AS 'Approve Status', TFR.is_reversed FROM transfers TFR LEFT JOIN bank_account FROMACC ON TFR.from_account = FROMACC.account_id LEFT JOIN bank_account TOACC ON TFR.to_account = TOACC.account_id LEFT JOIN transfer_channel CHNL ON TFR.chanel = CHNL.channel_id LEFT JOIN repos FROMREPO ON FROMREPO.repo_id = TFR.from_repo LEFT JOIN repos TOREPO ON TOREPO.repo_id = TFR.to_repo LEFT JOIN user USR ON USR.user_id = TFR.initiated_by WHERE DATE(TFR.transfer_date) = ? AND TFR.is_reversed = 0 AND (TFR.is_approved = 1 OR TFR.is_checked = 1)";
            List<rejectTransfersDTO> transferList = template.query(Sql, new rejectTransfersMapper(), transferDate);
            if(!transferList.isEmpty()){
                return ResponseEntity.status(HttpStatus.OK).body(
                        new customAPIResponse<>(
                                true,
                                null,
                                transferList
                        )
                );
            }else {
                return ResponseEntity.status(HttpStatus.CONFLICT).body(
                        new customAPIResponse<>(
                                false,
                                "No Transfer Data available to reject on provided date!",
                                null
                        )
                );
            }
        }catch (Exception e){
            System.out.println(e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                    new customAPIResponse<>(
                            false,
                            "Un-expected error occurred while fetching transfer details. Please contact the administrator!",
                            null
                    )
            );
        }
    }

    @Transactional
    @Override
    public ResponseEntity<customAPIResponse<String>> saveCheckRejection(String transferId) {
        try{
            String Sql = "SELECT TFR.is_checked AS 'checked status', TFR.is_approved AS 'approved status', usr.user_id FROM transfers TFR LEFT JOIN user usr ON usr.user_id = tfr.checked_by WHERE TFR.transfer_id = ?";
            transferStatusDTO status = template.query(Sql, new transferStatusMapper(), transferId).get(0);
            //Check whether the transaction is already in un-checked status;
            if(status.getCheckedStatus().equals("0")){
                return ResponseEntity.status(HttpStatus.CONFLICT).body(
                        new customAPIResponse<>(
                                false,
                                "This Transfer ID: " + transferId + " is already in Un-checked status. No further reversal is required!",
                                null
                        )
                );
            }else {
                //Check whether the transfer is already approved or not. If it is already approved, the approval should be reversed first;
                if(!status.getApproveStatus().equals("0")){
                    return ResponseEntity.status(HttpStatus.CONFLICT).body(
                            new customAPIResponse<>(
                                    false,
                                    "Please reverse the approval first, before reverse the checking!",
                                    null
                            )
                    );
                }else {
                    //Check whether the transfer has been already checked by session user. If not, session user has no authority for rejection of checking;
                    if (!status.getUserId().equals(session.getAttribute("userId").toString())) {
                        return ResponseEntity.status(HttpStatus.CONFLICT).body(
                                new customAPIResponse<>(
                                        false,
                                        "You have no authority to reverse the checking status. Please reverse from checked user account!",
                                        null
                                )
                        );
                    }else {
                        //Update un-check status of the transfer;
                        int affectedRows = transferRepository.saveCheckRejection(transferId);
                        if(affectedRows > 0){
                            return ResponseEntity.status(HttpStatus.OK).body(
                                    new customAPIResponse<>(
                                            true,
                                            "Transfer ID: " + transferId + " is Un-checked successfully!",
                                            null
                                    )
                            );
                        }else {
                            return ResponseEntity.status(HttpStatus.CONFLICT).body(
                                    new customAPIResponse<>(
                                            false,
                                            "Un-expected error occurred. Please contact administrator!",
                                            null
                                    )
                            );
                        }
                    }

                }
            }
        }catch (Exception e) {
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
    public ResponseEntity<customAPIResponse<List<reverseTransfersDTO>>> reverseTransfers(LocalDate transferDate) {
        try{
            String Sql = "SELECT TFR.transfer_id, FROMACC.account_number AS 'from_account', TOACC.account_number AS 'to_account', TFR.transfer_amount, CHNL.channel_type AS 'channel', CASE WHEN FROMREPO.repo_id IS NULL THEN 'N/A' ELSE FROMREPO.repo_id END as 'from_repo', CASE WHEN TOREPO.repo_id IS NULL THEN 'N/A' ELSE TOREPO.repo_id END AS 'to_repo', USR.user_first_name AS 'initiated_by', TFR.is_approved, TFR.is_reversed FROM transfers TFR LEFT JOIN bank_account FROMACC ON TFR.from_account = FROMACC.account_id LEFT JOIN bank_account TOACC ON TFR.to_account = TOACC.account_id LEFT JOIN transfer_channel CHNL ON TFR.chanel = CHNL.channel_id LEFT JOIN repos FROMREPO ON FROMREPO.repo_id = TFR.from_repo LEFT JOIN repos TOREPO ON TOREPO.repo_id = TFR.to_repo LEFT JOIN user USR ON USR.user_id = TFR.initiated_by WHERE DATE(TFR.transfer_date) = ? AND TFR.is_reversed = 0";
            List<reverseTransfersDTO> transferList = template.query(Sql, new reverseTransfersMapper(), transferDate);
            if(!transferList.isEmpty()){
                return ResponseEntity.status(HttpStatus.OK).body(
                        new customAPIResponse<>(
                                true,
                                null,
                                transferList
                        )
                );
            }else {
                return ResponseEntity.status(HttpStatus.CONFLICT).body(
                        new customAPIResponse<>(
                                false,
                                "No Transfer Data available to reverse on provided date!",
                                null
                        )
                );
            }
        }catch (Exception e){
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                    new customAPIResponse<>(
                            false,
                            "Un-expected error occurred while fetching transfer details. Please contact the administrator!",
                            null
                    )
            );
        }
    }

    @Transactional
    @Override
    public ResponseEntity<customAPIResponse<String>> saveReversal(String transferId) {
        try{
            //Get transfer data for provided transfer id;
            String sql = "SELECT DATE(TFR.transfer_date), TFR.cross_adjustment, TFR.is_checked, TFR.is_approved FROM transfers TFR WHERE TFR.transfer_id = ?";
            List<getTransferDataForReversalDTO> transferData = template.query(sql, new getTransferDataForReversalMapper(), transferId);
            if(!transferData.isEmpty()){
                //Check whether the transaction is a previous day transaction;
                if(transferData.get(0).getTransferDate().equals(LocalDate.now())){
                    //Check whether the transfer is already approved or checked;
                    if(transferData.get(0).getCheckedStatus() == 1 || transferData.get(0).getApproveStatus() == 1){
                        return ResponseEntity.status(HttpStatus.CONFLICT).body(
                                new customAPIResponse<>(
                                        false,
                                        "This transfer is already checked or approved. Please reverse the check status and approval status before delete!",
                                        null
                                )
                        );
                    }else {
                        //Reverse transfer data for provided transferId;
                        int affectedTransferRow = transferRepository.updateReversal(userRepository.findById(session.getAttribute("userId").toString()).get(), transferId);
                        //Reverse relevant cross adjustment id;
                        int affectedRows = crossAdjustmentRepository.crossAdjustmentDeletion(session.getAttribute("userId").toString(), transferData.get(0).getCrossAdjustment());
                        if(affectedRows > 0 && affectedTransferRow > 0){
                            return ResponseEntity.status(HttpStatus.OK).body(
                                    new customAPIResponse<>(
                                            true,
                                            "Transfer ID: " + transferId + " reversed successfully!",
                                            null
                                    )
                            );
                        }else {
                            return null;
                        }
                    }
                }else {
                    return ResponseEntity.status(HttpStatus.CONFLICT).body(
                            new customAPIResponse<>(
                                    false,
                                    "You are not authorized to reverse previous day transfers!",
                                    null
                            )
                    );
                }
            }else {
                return ResponseEntity.status(HttpStatus.CONFLICT).body(
                        new customAPIResponse<>(
                                false,
                                "No Transfer Data available for provided Transfer ID!",
                                null
                        )
                );
            }
        }catch (Exception e){
            System.out.println(e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                    new customAPIResponse<>(
                            false,
                            "Un-expected error occurred while reversing transfers. Please contact the administrator!",
                            null
                    )
            );
        }
    }

    @Transactional
    @Override
    public ResponseEntity<customAPIResponse<String>> reverseAll(LocalDate transferDate) {
        try{
            //Check whether the transaction is a previous day transaction;
            if(transferDate.equals(LocalDate.now())){
                //Get the list of transfers for provided transfer date;
                String sql = "SELECT TFR.transfer_id AS 'transfer_id', DATE_FORMAT(TFR.transfer_date, '%Y-%m-%d') AS 'transfer_date', TFR.cross_adjustment AS 'cross_adjustment',  TFR.is_checked AS 'is_checked', TFR.is_approved AS 'is_approved' FROM transfers TFR WHERE TFR.is_reversed = 0     AND DATE(TFR.transfer_date) = ?";
                List<getTransferDataForReverseAllDTO> transferData = template.query(sql, new getTransferDataForReverseAllMapper(), transferDate);
                //Loop each data in above list and check whether any transfer is available with already approved or checked;
                for(getTransferDataForReverseAllDTO transferRecord: transferData){
                    if(transferRecord.getCheckedStatus() == 1 || transferRecord.getApproveStatus() == 1){
                        continue;
                    }else {
                        //Delete relevant transfers;
                        transferRepository.updateReversal(userRepository.findById(session.getAttribute("userId").toString()).get(), transferRecord.getTransferId());
                        //Reverse relevant cross adjustment;
                        int affectedRows = crossAdjustmentRepository.crossAdjustmentDeletion(session.getAttribute("userId").toString(), transferRecord.getCrossAdjustment());
                    }
                }
                return ResponseEntity.status(HttpStatus.OK).body(
                        new customAPIResponse<>(
                                true,
                                "All transfers have been reversed successfully. Please note that, already checked and approved transfers will not be reversed. If you need to delete, please reverse the check status and approve status of particular transfer!",
                                null
                        )
                );
            }else {
                return ResponseEntity.status(HttpStatus.CONFLICT).body(
                        new customAPIResponse<>(
                                false,
                                "You are not authorized to reverse previous day transfers!",
                                null
                        )
                );
            }
        }catch (Exception e){
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                    new customAPIResponse<>(
                            false,
                            "Un-expected error occurred while reversing transfers. Please contact the administrator!",
                            null
                    )
            );
        }
    }

    @Override
    public ResponseEntity<customAPIResponse<List<getAllTransfersForCheckDTO>>> getAllTransfersForCheck() {
        try{
            String sql = "SELECT tfr.transfer_id, DATE(tfr.transfer_date) AS 'transfer_date', fromacc.account_number AS 'from account', toacc.account_number AS 'to account', CASE WHEN tfr.from_repo IS NULL THEN 'N/A' ELSE tfr.from_repo END AS 'from repo', CASE WHEN tfr.to_repo IS NULL THEN 'N/A' ELSE tfr.to_repo END AS 'to repo', tfr.transfer_amount FROM transfers tfr LEFT JOIN bank_account fromacc ON tfr.from_account = fromacc.account_id LEFT JOIn bank_account toacc ON tfr.to_account = toacc.account_id WHERE DATE(tfr.transfer_date) = ? AND tfr.is_approved = 0 AND tfr.is_checked = 0 AND tfr.is_reversed = 0";
            List<getAllTransfersForCheckDTO> checkList = template.query(sql, new getAllTransfersForCheckMapper(), LocalDate.now());
            if(!checkList.isEmpty()){
                return ResponseEntity.status(HttpStatus.OK).body(
                        new customAPIResponse<>(
                                true,
                                null,
                                checkList
                        )
                );
            }else {
                return ResponseEntity.status(HttpStatus.CONFLICT).body(
                        new customAPIResponse<>(
                                false,
                                "No transfer data found for checking!",
                                null
                        )
                );
            }
        }catch (Exception e){
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                    new customAPIResponse<>(
                            false,
                            "Un-expected error occurred. Please contact administrator!",
                            null
                    )
            );
        }
    }

    @Transactional
    @Override
    public ResponseEntity<customAPIResponse<String>> checkTransfer(String transferId) {
        try{
            //Check whether the transaction has been already initiated or approved by same user;
            if(Objects.equals(session.getAttribute("userId").toString(), transferRepository.checkUserForCheck(transferId))){
                return ResponseEntity.status(HttpStatus.CONFLICT).body(
                        new customAPIResponse<>(
                                true,
                                "You have already initiated this transfer. Please check with another user!",
                                null
                        )
                );
            }else {
                int affectedRow = transferRepository.checkTransfer(session.getAttribute("userId").toString(), LocalDateTime.now(), transferId);
                if(affectedRow > 0){
                    return ResponseEntity.status(HttpStatus.OK).body(
                            new customAPIResponse<>(
                                    true,
                                    "Transfer checked successfully!",
                                    null
                            )
                    );
                }else {
                    return ResponseEntity.status(HttpStatus.CONFLICT).body(
                            new customAPIResponse<>(
                                    false,
                                    "Un-expected error occurred while checking transfer. Please contact administrator!",
                                    null
                            )
                    );
                }
            }
        }catch (Exception e){
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                    new customAPIResponse<>(
                            false,
                            "Un-expected error occurred while checking transfer. Please contact administrator!",
                            null
                    )
            );
        }
    }

    @Transactional
    @Override
    public ResponseEntity<customAPIResponse<String>> saveApprovalRejection(String transferId) {
        try{
            String Sql = "SELECT TFR.is_checked AS 'checked status', TFR.is_approved AS 'approved status', usr.user_id FROM transfers TFR LEFT JOIN user usr ON usr.user_id = tfr.approved_by WHERE TFR.transfer_id = ?";
            transferStatusDTO status = template.query(Sql, new transferStatusMapper(), transferId).get(0);
            //Check whether the transaction is already in approved status;
            if(status.getApproveStatus().equals("0")){
                return ResponseEntity.status(HttpStatus.CONFLICT).body(
                        new customAPIResponse<>(
                                false,
                                "This Transfer ID: " + transferId + " is already in Un-approved status. No further approval reversal is required!",
                                null
                        )
                );
            }else {
                //Check whether the transfer has been already approved by session user. If not, session user has no authority for rejection of approval;
                if (!status.getUserId().equals(session.getAttribute("userId").toString())) {
                    return ResponseEntity.status(HttpStatus.CONFLICT).body(
                            new customAPIResponse<>(
                                    false,
                                    "You have no authority to reverse the approval. Please reverse from approved user account!",
                                    null
                            )
                    );
                }else {
                    //Update un-approved status of the transfer;
                    int affectedRows = transferRepository.saveApprovalRejection(transferId);
                    if(affectedRows > 0){
                        return ResponseEntity.status(HttpStatus.OK).body(
                                new customAPIResponse<>(
                                        true,
                                        "Approval successfully reversed for Transfer ID: " + transferId + " !",
                                        null
                                )
                        );
                    }else {
                        return ResponseEntity.status(HttpStatus.CONFLICT).body(
                                new customAPIResponse<>(
                                        false,
                                        "Un-expected error occurred. Please contact administrator!",
                                        null
                                )
                        );
                    }
                }
            }
        }catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                    new customAPIResponse<>(
                            false,
                            "Un-expected error occurred. Please contact administrator!",
                            null
                    )
            );
        }
    }
    
    public String configureTransferType(String selectedFromBankAccount, String selectedFromRepoAccount, String selectedToBankAccount, String selectedToRepoAccount){
        boolean fromBank = !selectedFromBankAccount.isEmpty();
        boolean fromRepo = !selectedFromRepoAccount.isEmpty();
        boolean toBank = !selectedToBankAccount.isEmpty();
        boolean toRepo = !selectedToRepoAccount.isEmpty();

        if (fromBank && toBank) {
            return "BANK_TO_BANK";
        } else if (fromBank && toRepo) {
            return "BANK_TO_REPO";
        } else if (fromRepo && toBank) {
            return "REPO_TO_BANK";
        } else if (fromRepo && toRepo) {
            return "REPO_TO_REPO";
        } else {
            return "INVALID";
        }
    }

    @Transactional
    @Override
    public ResponseEntity<customAPIResponse<String>> initiateManualTransfers(String selectedFromBankAccount, String selectedFromAccountNumber, String selectedFromRepoAccount, String selectedToBankAccount, String selectedToAccountNumber, String selectedToRepoAccount, BigDecimal amount, String transferChannel) {
        try{
            String transferType = this.configureTransferType(selectedFromBankAccount, selectedFromRepoAccount, selectedToBankAccount, selectedToRepoAccount);
            switch (transferType){
                //Case where fund transfer from bank account to another bank account;
                case "BANK_TO_BANK":
                        //Get last transfer id by calling for generateTransferId method;
                        String generatedLastTransferIdBANK_TO_BANK = transferRepository.getLastTransferId();
                        TransferIdGenerator idGeneratorBANK_TO_BANK = new TransferIdGenerator(generatedLastTransferIdBANK_TO_BANK);
                        //create new cross adjustment;
                        String crossAdjustmentBANK_TO_BANK = crossAdjustmentIMPL.saveNewCrossAdjustment();
                        //Initiate transfer;
                        String transferIdBANK_TO_BANK = this.newTransfer(idGeneratorBANK_TO_BANK.getNextId(), selectedFromBankAccount, selectedToBankAccount, transferChannel, amount, null, null, crossAdjustmentBANK_TO_BANK);
                        //Adjustment for from account;
                        accountBalanceAdjustments.saveNewAdjustment(selectedToAccountNumber, amount.multiply(BigDecimal.valueOf(-1)), accountBalanceRepository.getBalanceId(selectedFromBankAccount, LocalDate.now()), transferIdBANK_TO_BANK, crossAdjustmentBANK_TO_BANK);
                        //Adjustment for to account;
                        accountBalanceAdjustments.saveNewAdjustment(selectedFromAccountNumber, amount, accountBalanceRepository.getBalanceId(selectedToBankAccount, LocalDate.now()), transferIdBANK_TO_BANK, crossAdjustmentBANK_TO_BANK);

                        //Return successful message to the browser;
                        return ResponseEntity.status(HttpStatus.OK).body(
                                new customAPIResponse<>(
                                        true,
                                        "Transfer initiated successfully!",
                                        null
                                )
                        );

                case "REPO_TO_REPO":
                    //Get last transfer id by calling for generateTransferId method;
                    String generatedLastTransferIdREPO_TO_REPO = transferRepository.getLastTransferId();
                    TransferIdGenerator idGeneratorREPO_TO_REPO = new TransferIdGenerator(generatedLastTransferIdREPO_TO_REPO);
                    //create new cross adjustment;
                    String crossAdjustmentREPO_TO_REPO = crossAdjustmentIMPL.saveNewCrossAdjustment();
                    //Initiate transfer;
                    String transferIdREPO_TO_REPO = this.newTransfer(idGeneratorREPO_TO_REPO.getNextId(), selectedFromBankAccount, selectedToBankAccount, transferChannel, amount, selectedFromRepoAccount, selectedToRepoAccount, crossAdjustmentREPO_TO_REPO);
                    //Adjustment for from repo account;
                    repoAdjustment.saveNewAdjustment(selectedToRepoAccount, amount.multiply(BigDecimal.valueOf(-1)), selectedFromRepoAccount, transferIdREPO_TO_REPO, crossAdjustmentREPO_TO_REPO);
                    //Adjustment for to repo account;
                    repoAdjustment.saveNewAdjustment(selectedFromRepoAccount, amount, selectedToRepoAccount, transferIdREPO_TO_REPO, crossAdjustmentREPO_TO_REPO);

            }
            return null;
        }catch (Exception e){
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
