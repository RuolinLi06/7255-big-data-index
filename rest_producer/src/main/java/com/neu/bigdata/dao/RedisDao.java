package com.neu.bigdata.dao;

import org.springframework.stereotype.Repository;
import redis.clients.jedis.Jedis;

import java.util.Map;
import java.util.Set;

/**
 * @author Ruolin Li
 * @Date 2023-09-25
 */
@Repository
public class RedisDao {
    private final Jedis jedis;

    public RedisDao(Jedis jedis) {
        this.jedis = jedis;
    }
    public boolean existsKey(String key){
        return jedis.exists(key);
    }
    public String add(String key, String value){
        return jedis.set(key,value);
    }

    public Long addMembers(String key, String value ){
        return jedis.sadd(key,value);
    }
    public String getValue(String key){
        return jedis.get(key);
    }
    public Long deleteKey(String key){
        return jedis.del(key);
    }

    public Long deleteKeys(String[] keys){
        return jedis.del(keys);
    }
    public Long setHash(String key, String field, String value){
        return jedis.hset(key,field , value);
    }
    public String getHash(String key,  String field){
        return jedis.hget(key,field);
    }

    public Set<String> getKeys(String pattern){
        return jedis.keys(pattern);
    }

    public Map<String,String> getAllValuesByKey(String key) {
        return jedis.hgetAll(key);
    }

    public Set<String> sMembers(String key) {
        return jedis.smembers(key);
    }

}
