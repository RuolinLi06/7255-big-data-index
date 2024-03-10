package com.neu.bigdata.util;

import com.neu.bigdata.constant.Constant;
import org.everit.json.schema.Schema;
import org.everit.json.schema.loader.SchemaLoader;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;

/**
 * @author Ruolin Li
 * @Date 2023-09-24
 */
public class JsonSchemaValidator {

    private final static Logger logger = LoggerFactory.getLogger(JsonSchemaValidator.class);
    private static Schema schema ;

    public static void loadSchema() {
        if(schema == null) {
            try {
                File schemaFile = new File(Constant.SCHEMA_FILE);
                //InputStream inputStream = getClass().getResourceAsStream("/schema.json"); //uses the class loader to load the resource
                InputStream inputStream = new FileInputStream(schemaFile);
                JSONObject jsonSchema = new JSONObject(new JSONTokener(inputStream)); //parse JSON source strings
                schema = SchemaLoader.load(jsonSchema);
            } catch (Exception e) {
                logger.error("Error when loading the schema file " + e.getMessage());
            }
        }
    }
    public static void validateJson(JSONObject jsonObject) throws Exception{
        if(schema == null){
            logger.error("JSON Schema has not been loaded");
            throw new IllegalStateException("JSON Schema has not been loaded");
        }
        schema.validate(jsonObject);
    }
}
