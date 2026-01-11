package SLICGL.example.SLICGL_Inter_Bant_Transfer_Management.DTO;


public class createNewRepoDTO {
    private String accountID;
    private int repoType;
    private String repoValue;
    private int eligibility;

    public createNewRepoDTO() {
    }

    public createNewRepoDTO(String accountID, int repoType, String repoValue, int eligibility) {
        this.accountID = accountID;
        this.repoType = repoType;
        this.repoValue = repoValue;
        this.eligibility = eligibility;
    }

    public String getAccountID() {
        return accountID;
    }

    public void setAccountID(String accountID) {
        this.accountID = accountID;
    }

    public int getRepoType() {
        return repoType;
    }

    public void setRepoType(int repoType) {
        this.repoType = repoType;
    }

    public String getRepoValue() {
        return repoValue;
    }

    public void setRepoValue(String repoValue) {
        this.repoValue = repoValue;
    }

    public int getEligibility() {
        return eligibility;
    }

    public void setEligibility(int eligibility) {
        this.eligibility = eligibility;
    }
}
