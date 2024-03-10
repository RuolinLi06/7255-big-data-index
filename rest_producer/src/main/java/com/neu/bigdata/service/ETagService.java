package com.neu.bigdata.service;

import com.neu.bigdata.dao.RedisDao;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

/**
 * @author Ruolin Li
 * @Date 2023-09-25
 */
@Service
public class ETagService {
    @Autowired
    private RedisDao redisDao;

    private final static Logger logger = LoggerFactory.getLogger(ETagService.class);

    public String generateETag(JSONObject jsonObject) {
        //Use Hash as ETag
        String eTag = null;
        String key = jsonObject.getString("objectType") + "_" + jsonObject.getString("objectId");
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(jsonObject.toString().getBytes(StandardCharsets.UTF_8));
            eTag = Base64.getEncoder().encodeToString(hash);
            logger.info("key "+key +" eTag "+eTag);
            redisDao.setHash(key,"eTag",eTag);
        }catch (Exception e){
            logger.error("Error when generating ETag "+e.getMessage());
        }
        return eTag;
    }
    public String getEtag(String key){
        return redisDao.getHash(key,"eTag");
    }
}
