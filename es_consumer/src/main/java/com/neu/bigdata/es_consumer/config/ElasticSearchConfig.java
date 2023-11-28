package com.neu.bigdata.es_consumer.config;

import org.apache.http.HttpHost;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.client.RestHighLevelClientBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @author Ruolin Li
 * @Date 2023-11-25
 */
@Configuration
public class ElasticSearchConfig {

    @Value("${elasticsearch.host}")
    private String host;

    @Value("${elasticsearch.port}")
    private int port;

    @Bean(destroyMethod = "close", name = "client")
    public RestHighLevelClient initRestClient() {

        RestClient httpClient = RestClient.builder(new HttpHost(host, port)).build();
        RestHighLevelClient esClient = new RestHighLevelClientBuilder(httpClient)
                .setApiCompatibilityMode(true)
                .build();
        return esClient;
    }

}
