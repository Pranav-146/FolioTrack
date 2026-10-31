package com.foliotrack;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@OpenAPIDefinition(info = @Info(
        title = "FolioTrack API",
        version = "v1",
        description = "Portfolio and trade management platform"))
public class FolioTrackApplication {

    public static void main(String[] args) {
        SpringApplication.run(FolioTrackApplication.class, args);
    }
}
