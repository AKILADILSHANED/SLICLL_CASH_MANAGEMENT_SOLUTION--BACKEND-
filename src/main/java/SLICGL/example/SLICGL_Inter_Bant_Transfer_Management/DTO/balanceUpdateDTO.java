package SLICGL.example.SLICGL_Inter_Bant_Transfer_Management.DTO;

import java.time.LocalDate;

public class balanceUpdateDTO {
    private String balanceId;
    private float balanceAmount;
    private float outstandingBalance;
    private String action;
    private float adjustmentAmount;

    public balanceUpdateDTO() {
    }

    public balanceUpdateDTO(String balanceId, float balanceAmount, float outstandingBalance, String action, float adjustmentAmount) {
        this.balanceId = balanceId;
        this.balanceAmount = balanceAmount;
        this.outstandingBalance = outstandingBalance;
        this.action = action;
        this.adjustmentAmount = adjustmentAmount;
    }

    public String getBalanceId() {
        return balanceId;
    }

    public void setBalanceId(String balanceId) {
        this.balanceId = balanceId;
    }

    public float getBalanceAmount() {
        return balanceAmount;
    }

    public void setBalanceAmount(float balanceAmount) {
        this.balanceAmount = balanceAmount;
    }

    public float getOutstandingBalance() {
        return outstandingBalance;
    }

    public void setOutstandingBalance(float outstandingBalance) {
        this.outstandingBalance = outstandingBalance;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public float getAdjustmentAmount() {
        return adjustmentAmount;
    }

    public void setAdjustmentAmount(float adjustmentAmount) {
        this.adjustmentAmount = adjustmentAmount;
    }
}
