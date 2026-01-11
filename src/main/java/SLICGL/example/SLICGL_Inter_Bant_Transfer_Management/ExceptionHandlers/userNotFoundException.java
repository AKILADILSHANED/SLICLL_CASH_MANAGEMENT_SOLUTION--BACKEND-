package SLICGL.example.SLICGL_Inter_Bant_Transfer_Management.ExceptionHandlers;

public class userNotFoundException extends RuntimeException{
    public userNotFoundException(String message){
        super(message);
    }
}
