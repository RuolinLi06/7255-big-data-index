package com.neu.bigdata;

import com.neu.bigdata.util.JsonSchemaValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class Application {
    private final static Logger logger = LoggerFactory.getLogger(Application.class);

    public static void main(String[] args) {
        JsonSchemaValidator.loadSchema();

        SpringApplication.run(Application.class, args);
    }


}
