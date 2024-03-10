package com.neu.bigdata.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.neu.bigdata.service.ETagService;
import com.neu.bigdata.service.KafkaPub;
import com.neu.bigdata.service.PlanService;
import com.neu.bigdata.util.JsonSchemaValidator;
import com.neu.bigdata.util.JwtTokenUtil;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.HashMap;

/**
 * @author Ruolin Li
 * @Date 2023-09-24
 */
@RestController
@RequestMapping("/v1")
public class MedicalPlanController {
    @Autowired
    private PlanService planService;

    @Autowired
    private ETagService eTagService;

    @Autowired
    private JwtTokenUtil jwtTokenUtil;

    @Autowired
    private KafkaPub kafkaPub;

    private final static Logger logger = LoggerFactory.getLogger(MedicalPlanController.class);

    @GetMapping(value = "/token", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> getToken(){
        String token = jwtTokenUtil.generateToken();
        return ResponseEntity.status(HttpStatus.CREATED).body(new JSONObject().put("Token: ", token).toString());
    }
    /**
     * @param planPayload
     * @param type
     * @return return with Etag if created successfully
     */
    @PostMapping(value = "/{type}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> addPlan(@RequestHeader HttpHeaders headers, @RequestBody String planPayload,@PathVariable String type){
        JSONObject plan = new JSONObject(planPayload);
        // json schema validation
        try {
            JsonSchemaValidator.validateJson(plan);
        }catch (Exception e){
            logger.error("Validation failed: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
                    new JSONObject().put("Error","Validation failed: " + e.getMessage()).toString());
        }
        // generate key
        String key = plan.getString("objectType") + "_" + plan.getString("objectId");
        if(planService.existsKey(key)){
            return ResponseEntity.status(HttpStatus.CONFLICT).body(
                    new JSONObject().put("Error","Exists the same resource").toString());
        }
        //save into key-value store
        planService.addPlan(key,plan);
        //generate eTag
        String eTag = eTagService.generateETag(plan);
        logger.info("Start creating " + type + " key: " + key + " eTag: " + eTag);
        return ResponseEntity.status(HttpStatus.CREATED).eTag(eTag)
                .body(new JSONObject().put("Message", "Create data key: " + key).toString());
    }

    /**
     * @param headers IF NONE MATCH(optional)
     * @param objectId
     * @param objectType
     * @return return NOT MODIFIED if request with IF NONE MATCH headers eTag matched.
     * return OK with data when not match or without IF NONE MATCH headers.
     */
    @GetMapping(value = "/{objectType}/{objectId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> getPlan(@RequestHeader HttpHeaders headers, @PathVariable String objectId, @PathVariable String objectType) {

        String key = objectType + "_" + objectId;
        String eTag;
        if (!planService.existsKey(key)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new JSONObject().put("Error", objectType+" "+objectId+" not exist").toString());
        }
        Map<String, Object> plan = planService.getPlan(key);
        eTag = eTagService.getEtag(key);
        String ifNoneMatch = headers.getFirst(HttpHeaders.IF_NONE_MATCH);
        if(ifNoneMatch!=null && ifNoneMatch.equals(eTag)){
            //with "if none match" and match
            return ResponseEntity.status(HttpStatus.NOT_MODIFIED).eTag(eTag).build();
        } else {
            //without headers "if none match" or not match the header, return data
            return ResponseEntity.ok().eTag(eTag).body(new JSONObject(plan).toString());
        }

    }

    /**
     *
     * @param headers IF MATCH(optional)
     * @param objectId
     * @param objectType
     * @param planPayload
     * @return return PRECONDITION FAILED if request have with IF MATCH headers but not match.
     *      update and return NO CONTENT when without or match IF MATCH headers.
     */

    @PutMapping(value = "/{objectType}/{objectId}",produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> updatePlan(@RequestHeader HttpHeaders headers, @PathVariable String objectId, @PathVariable String objectType,@RequestBody(required = false) String planPayload) throws JsonProcessingException {
        JSONObject plan = new JSONObject(planPayload);
        // json schema validation
        try {
            JsonSchemaValidator.validateJson(plan);
        } catch (Exception e) {
            logger.error("Validation failed: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
                    new JSONObject().put("Error", "Validation failed: " + e.getMessage()).toString());
        }
        String key = objectType + "_" + objectId;
        if (!planService.existsKey(key)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new JSONObject().put("Error", "Object not exist").toString());
        }
        String ifMatch = headers.getFirst(HttpHeaders.IF_MATCH);
        String eTag = eTagService.getEtag(key);
        if (ifMatch != null && !ifMatch.equals(eTag)) {
            //with "if match" but not match, refuse to update
            return ResponseEntity.status(HttpStatus.PRECONDITION_FAILED).eTag(eTag)
                    .body(new JSONObject().put("Error", objectType + " " + objectId + " modified").toString());
        } else {
            //update and generate new eTag
            String new_eTag = eTagService.generateETag(plan);
            deleteES(objectType,objectId);
            planService.deletePlan(key);
            planService.addPlan(key, plan);
            return ResponseEntity.status(HttpStatus.NO_CONTENT).eTag(new_eTag).build();
        }
    }


    /**
     *
     * @param headers IF MATCH(optional)
     * @param objectId
     * @param objectType
     * @param planObject
     * @return return PRECONDITION FAILED if request have with IF MATCH headers but not match.
     *      update and return NO CONTENT when without or match IF MATCH headers.
     */
    @PatchMapping(value = "/{objectType}/{objectId}",produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> patchUpdatePlan(@RequestHeader HttpHeaders headers, @PathVariable String objectId, @PathVariable String objectType,@RequestBody(required = false) String planObject) {
        String key = objectType + "_" + objectId;
        JSONObject plan = new JSONObject(planObject);
        if (!planService.existsKey(key)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new JSONObject().put("Error", "Object not exist").toString());
        }
        String ifMatch = headers.getFirst(HttpHeaders.IF_MATCH);
        String eTag = eTagService.getEtag(key);
        if (ifMatch != null && !ifMatch.equals(eTag)) {
            //with "if match" but not match, refuse to update
            return ResponseEntity.status(HttpStatus.PRECONDITION_FAILED).eTag(eTag)
                    .body(new JSONObject().put("Error", objectType + " " + objectId + " modified").toString());
        } else {
            //generate eTag
            String new_eTag = eTagService.generateETag(plan);
            planService.addPlan(key, plan);
            return ResponseEntity.status(HttpStatus.NO_CONTENT).eTag(new_eTag).build();

        }
    }
    /**
     * @param headers IF MATCH(optional)
     * @param objectId
     * @param objectType
     * @return return PRECONDITION FAILED if request have with IF MATCH headers but not match.
     * deleted and return NO CONTENT when without or match IF MATCH headers.
     */
    @DeleteMapping(value = "/{objectType}/{objectId}",produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> deletePlan(@RequestHeader HttpHeaders headers, @PathVariable String objectId, @PathVariable String objectType) throws JsonProcessingException {
        String key = objectType + "_" + objectId;
        String eTag = "";
        if (!planService.existsKey(key)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new JSONObject().put("Error", objectType + " " + objectId + " not exist").toString());
        }
        Map<String, Object> plan = planService.getPlan(key);
        eTag = eTagService.getEtag(key);
        String ifMatch = headers.getFirst(HttpHeaders.IF_MATCH);
        if (ifMatch != null && !ifMatch.equals(eTag)) {
            //with "if match" and not match, refuse to delete
            return ResponseEntity.status(HttpStatus.PRECONDITION_FAILED).eTag(eTag)
                    .body(new JSONObject().put("Error", objectType + " " + objectId + " modified").toString());
        } else {
            //without headers "if match" or match the header, delete
            deleteES(objectType,objectId);
            planService.deletePlan(key);
            return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
        }
    }

    public void deleteES(String objectType,String objectId) throws JsonProcessingException {
        String key = objectType + "_" + objectId;
        Map<String, Object> plan = planService.getPlan(key);

        logger.info(plan.toString());
        ObjectMapper objectMapper = new ObjectMapper();
        String jsonString = objectMapper.writeValueAsString(plan);

        JSONObject deleteJsonObject = new JSONObject(jsonString);
        kafkaPub.publish("delete", objectId);
        JSONObject planCostSharesObject = deleteJsonObject.getJSONObject("planCostShares");
        kafkaPub.publish("delete", planCostSharesObject.getString("objectId"));
        JSONArray linkedPlanServices = deleteJsonObject.getJSONArray("linkedPlanServices");
        for (int i = 0; i < linkedPlanServices.length(); i++) {
            JSONObject linkedPlanServicesObject = linkedPlanServices.getJSONObject(i);
            kafkaPub.publish("delete", linkedPlanServicesObject.getString("objectId"));
            JSONObject linkedServiceObject = linkedPlanServicesObject.getJSONObject("linkedService");
            kafkaPub.publish("delete", linkedServiceObject.getString("objectId"));
            JSONObject planserviceCostSharesObject = linkedPlanServicesObject.getJSONObject("planserviceCostShares");
            kafkaPub.publish("delete", planserviceCostSharesObject.getString("objectId"));
        }
        return;
    }


}
