package com.citacita;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.citacita.mapper")
public class CitacitaBackendApplication {
	
	public static void main(String[] args) {
		SpringApplication.run(CitacitaBackendApplication.class, args);
	}
}
