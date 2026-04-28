package org.example.config;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import org.apache.http.HttpHost;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestClientBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ElasticsearchConfig {

    @Bean(destroyMethod = "close")
    public RestClient elasticsearchRestClient(
            @Value("${rag.es.url:http://localhost:9200}") String esUrl,
            @Value("${rag.es.timeout-ms:3000}") int timeoutMs) {
        return RestClient.builder(HttpHost.create(esUrl))
                .setRequestConfigCallback(requestConfigBuilder -> requestConfigBuilder
                        .setConnectTimeout(timeoutMs)
                        .setSocketTimeout(timeoutMs))
                .build();
    }

    @Bean
    public ElasticsearchClient elasticsearchClient(RestClient elasticsearchRestClient) {
        RestClientTransport transport = new RestClientTransport(
                elasticsearchRestClient,
                new JacksonJsonpMapper()
        );
        return new ElasticsearchClient(transport);
    }
}
