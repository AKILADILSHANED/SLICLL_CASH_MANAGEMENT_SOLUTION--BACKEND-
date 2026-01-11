package SLICGL.example.SLICGL_Inter_Bant_Transfer_Management.DTO;

import java.time.LocalDate;

public class newFundRequestDTO {
    private String accountId;
    private String paymentId;
    private float requestAmount;
    private int requestType; // 0 = Actual Fund Request && 1 = Forecasted Fund Request
    private LocalDate requiredDate;

    public newFundRequestDTO() {
    }

    public newFundRequestDTO(String accountId, String paymentId, float requestAmount, int requestType, LocalDate requiredDate) {
        this.accountId = accountId;
        this.paymentId = paymentId;
        this.requestAmount = requestAmount;
        this.requestType = requestType;
        this.requiredDate = requiredDate;
    }

    public String getAccountId() {
        return accountId;
    }

    public void setAccountId(String accountId) {
        this.accountId = accountId;
    }

    public String getPaymentId() {
        return paymentId;
    }

    public void setPaymentId(String paymentId) {
        this.paymentId = paymentId;
    }

    public float getRequestAmount() {
        return requestAmount;
    }

    public void setRequestAmount(float requestAmount) {
        this.requestAmount = requestAmount;
    }

    public int getRequestType() {
        return requestType;
    }

    public void setRequestType(int requestType) {
        this.requestType = requestType;
    }

    public LocalDate getRequiredDate() {
        return requiredDate;
    }

    public void setRequiredDate(LocalDate requiredDate) {
        this.requiredDate = requiredDate;
    }
}
