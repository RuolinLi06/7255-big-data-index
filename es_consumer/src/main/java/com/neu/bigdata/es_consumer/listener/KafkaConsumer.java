package com.neu.bigdata.es_consumer.listener;

import com.neu.bigdata.es_consumer.constant.Constant;
import com.neu.bigdata.es_consumer.dao.ElasticSearchDao;
import org.springframework.stereotype.Service;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import java.io.IOException;
/**
 * @author Ruolin Li
 * @Date 2023-11-25
 */
@Service
public class KafkaConsumer {
    Logger logger = LoggerFactory.getLogger(this.getClass());

    @Autowired
    ElasticSearchDao elasticSearchDao;

    @KafkaListener(topics = "info7255")
    public void OpsSub(ConsumerRecord<String, String> record) {
        logger.info("Consumed Message - {} ", record);
        logger.info("Consumed message: \nKey: " + record.key().toString() + " \nValue: " + record.value().toString());

        // Send Message to elastic search
        if (record.key().toString().equals(Constant.ES_INDEX)) {
            logger.info("CONSUMER " + record.key() + " : " + record.value());

            JSONObject planJson = new JSONObject(record.value().toString());

            try {
                elasticSearchDao.postDocument(planJson);
            } catch (IOException e) {
                e.printStackTrace();
            }

        } else if (record.key().toString().equals(Constant.ES_DELETE)) {
            logger.info("CONSUMER " + record.key() + " : " + record.value());

            String objId = record.value().toString();

            elasticSearchDao.deletePlanDoc(objId);
        }
    }

}
