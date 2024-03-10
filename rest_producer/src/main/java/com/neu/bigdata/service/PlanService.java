package com.neu.bigdata.service;

import com.neu.bigdata.dao.RedisDao;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * @author Ruolin Li
 * @Date 2023-09-24
 */
@Service
public class PlanService {
    @Autowired
    private RedisDao redisDao;

    @Autowired
    private  ETagService eTagService;

    @Autowired
    private KafkaPub kafkaPub;

    private final static Logger logger = LoggerFactory.getLogger(PlanService.class);

    private Map<String,String> relationMap = new HashMap<>();

    /**
     * @param key
     * @return return true if key exists in Redis; otherwise false
     */
    public boolean existsKey(String key){
        return redisDao.existsKey(key);
    }

    public void addPlan(String key, JSONObject plan){
        jsonToMap(plan);
        indexQueue(plan, plan.getString("objectId"));
    }

    public Map<String, Object> getPlan(String key){
        Map<String, Object> result = new HashMap<>();
        getOrDelete(key, result, false);
        return result;
    }

    public void deletePlan(String key){
        getOrDelete(key, null, true);
    }

    public Map<String, Map<String, Object>> jsonToMap(JSONObject jsonObject) {
        Map<String, Map<String, Object>> map = new HashMap<>();
        Map<String, Object> contentMap = new HashMap<>();

        for (String key : jsonObject.keySet()) {
            String redisKey = jsonObject.get("objectType") + "_" + jsonObject.get("objectId");
            Object value = jsonObject.get(key);

            if (value instanceof JSONObject) {
                value = jsonToMap((JSONObject) value);
                redisDao.addMembers(redisKey + "_" + key, ((Map<String, Map<String, Object>>) value).entrySet().iterator().next().getKey());
            } else if (value instanceof JSONArray) {
                value = jsonToList((JSONArray) value);
                ((List<Map<String, Map<String, Object>>>) value)
                        .forEach((entry) -> {
                            entry.keySet()
                                    .forEach((listKey) -> {
                                        redisDao.addMembers(redisKey + "_" + key, listKey);
                                    });
                        });
            } else {
                redisDao.setHash(redisKey, key, value.toString());
                contentMap.put(key, value);
                map.put(redisKey, contentMap);
            }
        }
        return map;
    }

    public List<Object> jsonToList(JSONArray jsonArray) {
        List<Object> result = new ArrayList<>();
        for (Object value : jsonArray) {
            if (value instanceof JSONArray) value = jsonToList((JSONArray) value);
            else if (value instanceof JSONObject) value = jsonToMap((JSONObject) value);
            result.add(value);
        }
        return result;
    }

    private Map<String, Object> getOrDelete(String redisKey, Map<String, Object> resultMap, boolean isDelete) {
        Set<String> keys = redisDao.getKeys(redisKey + "_*");
        keys.add(redisKey);

        for (String key : keys) {
            if (key.equals(redisKey)) {
                if (isDelete) redisDao.deleteKeys((new String[]{key}));
                else {
                    Map<String, String> object =redisDao.getAllValuesByKey(key);
                    for (String attrKey : object.keySet()) {
                        if (!attrKey.equalsIgnoreCase("eTag")) {
                            resultMap.put(attrKey, isInteger(object.get(attrKey)) ? Integer.parseInt(object.get(attrKey)) : object.get(attrKey));
                        }
                    }
                }
            } else {
                String newKey = key.substring((redisKey + "_").length());
                Set<String> members = redisDao.sMembers(key);
                if (members.size() > 1 || newKey.equals("linkedPlanServices")) {
                    List<Object> listObj = new ArrayList<>();
                    for (String member : members) {
                        if (isDelete) {
                            getOrDelete(member, null, true);
                        } else {
                            Map<String, Object> listMap = new HashMap<>();
                            listObj.add(getOrDelete(member, listMap, false));
                        }
                    }
                    if (isDelete) redisDao.deleteKeys((new String[]{key}));
                    else resultMap.put(newKey, listObj);
                } else {
                    if (isDelete) {
                        redisDao.deleteKeys(new String[]{members.iterator().next(), key});
                    } else {
                        Map<String, String> object = redisDao.getAllValuesByKey(members.iterator().next());
                        Map<String, Object> nestedMap = new HashMap<>();
                        for (String attrKey : object.keySet()) {
                            nestedMap.put(attrKey,
                                    isInteger(object.get(attrKey)) ? Integer.parseInt(object.get(attrKey)) : object.get(attrKey));
                        }
                        resultMap.put(newKey, nestedMap);
                    }
                }
            }
        }
        return resultMap;
    }

    private boolean isInteger(String str) {
        try {
            Integer.parseInt(str);
        } catch (Exception e) {
            return false;
        }
        return true;
    }

    private void indexQueue(JSONObject jsonObject, String uuid) {

        Map<String, String> simpleMap = new HashMap<>();

        for (Object key : jsonObject.keySet()) {
            String attributeKey = String.valueOf(key);
            Object attributeVal = jsonObject.get(String.valueOf(key));
            String edge = attributeKey;

            if (attributeVal instanceof JSONObject) {
                JSONObject embdObject = (JSONObject) attributeVal;

                JSONObject joinObj = new JSONObject();
                if (edge.equals("planserviceCostShares") && embdObject.getString("objectType").equals("membercostshare")) {
                    joinObj.put("name", "planservice_membercostshare");
                } else {
                    joinObj.put("name", embdObject.getString("objectType"));
                }

                joinObj.put("parent", uuid);
                embdObject.put("plan_service", joinObj);
                embdObject.put("parent_id", uuid);
                System.out.println(embdObject.toString());
//                    messageQueueService.addToMessageQueue(embdObject.toString(), false);
                kafkaPub.publish("index", embdObject.toString());

            } else if (attributeVal instanceof JSONArray) {

                JSONArray jsonArray = (JSONArray) attributeVal;
                Iterator<Object> jsonIterator = jsonArray.iterator();

                while (jsonIterator.hasNext()) {
                    JSONObject embdObject = (JSONObject) jsonIterator.next();
                    embdObject.put("parent_id", uuid);
                    System.out.println(embdObject.toString());

                    String embd_uuid = embdObject.getString("objectId");
                    relationMap.put(embd_uuid, uuid);

                    indexQueue(embdObject, embd_uuid);
                }

            } else {
                simpleMap.put(attributeKey, String.valueOf(attributeVal));
            }
        }

        JSONObject joinObj = new JSONObject();
        joinObj.put("name", simpleMap.get("objectType"));

        if (!simpleMap.containsKey("planType")) {
            joinObj.put("parent", relationMap.get(uuid));
        }

        JSONObject obj1 = new JSONObject(simpleMap);
        obj1.put("plan_service", joinObj);
        obj1.put("parent_id", relationMap.get(uuid));
        System.out.println(obj1.toString());
//            messageQueueService.addToMessageQueue(obj1.toString(), false);
        kafkaPub.publish("index", obj1.toString());

    }


}