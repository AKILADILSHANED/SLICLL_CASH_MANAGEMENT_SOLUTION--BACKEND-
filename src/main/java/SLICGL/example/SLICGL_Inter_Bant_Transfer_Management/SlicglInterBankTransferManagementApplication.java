package SLICGL.example.SLICGL_Inter_Bant_Transfer_Management;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;


@SpringBootApplication(exclude = {SecurityAutoConfiguration.class})
public class SlicglInterBankTransferManagementApplication {

	public static void main(String[] args) {
		SpringApplication.run(SlicglInterBankTransferManagementApplication.class, args);
	}

}
