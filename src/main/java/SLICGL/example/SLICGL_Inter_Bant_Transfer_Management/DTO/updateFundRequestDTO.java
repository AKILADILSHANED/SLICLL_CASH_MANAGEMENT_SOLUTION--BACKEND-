package SLICGL.example.SLICGL_Inter_Bant_Transfer_Management.DTO;

public class updateFundRequestDTO {
    private String requestId;
    private String accountId;
    private float requestAmount;
    private float outstandingAmount;
    private String paymentType;
    private String adjustmentType;
    private float adjustmentAmount;

    public updateFundRequestDTO() {
    }

    public updateFundRequestDTO(String requestId, String accountId, float requestAmount, float outstandingAmount, String paymentType, String adjustmentType, float adjustmentAmount) {
        this.requestId = requestId;
        this.accountId = accountId;
        this.requestAmount = requestAmount;
        this.outstandingAmount = outstandingAmount;
        this.paymentType = paymentType;
        this.adjustmentType = adjustmentType;
        this.adjustmentAmount = adjustmentAmount;
    }

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public String getAccountId() {
        return accountId;
    }

    public void setAccountId(String accountId) {
        this.accountId = accountId;
    }

    public String getPaymentType() {
        return paymentType;
    }

    public void setPaymentType(String paymentType) {
        this.paymentType = paymentType;
    }

    public String getAdjustmentType() {
        return adjustmentType;
    }

    public void setAdjustmentType(String adjustmentType) {
        this.adjustmentType = adjustmentType;
    }

    public float getAdjustmentAmount() {
        return adjustmentAmount;
    }

    public void setAdjustmentAmount(float adjustmentAmount) {
        this.adjustmentAmount = adjustmentAmount;
    }

    public float getRequestAmount() {
        return requestAmount;
    }

    public void setRequestAmount(float requestAmount) {
        this.requestAmount = requestAmount;
    }

    public float getOutstandingAmount() {
        return outstandingAmount;
    }

    public void setOutstandingAmount(float outstandingAmount) {
        this.outstandingAmount = outstandingAmount;
    }
}
