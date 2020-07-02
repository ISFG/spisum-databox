package cz.isfgroup.sslspisumdatabox;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.client.RestTemplate;

import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;

@Slf4j
@EnableScheduling
@EnableCaching
@Configuration
public class SslSpisumDataboxConfiguration {

    @Value("${databox.thread.count:5}")
    private int databoxThreadCount;

    @Value("${uploader.thread.count:20}")
    private int uploaderThreadCount;

    @Bean
    public ThreadPoolExecutor databoxExecutor() {
        return (ThreadPoolExecutor) Executors.newFixedThreadPool(databoxThreadCount, new ThreadFactoryBuilder()
            .setNameFormat("databox-thread-%s")
            .build());
    }

    @Bean
    public ThreadPoolExecutor uploaderExecutor() {
        return (ThreadPoolExecutor) Executors.newFixedThreadPool(uploaderThreadCount, new ThreadFactoryBuilder()
            .setNameFormat("uploader-thread-%s")
            .build());
    }

    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper()
            .setSerializationInclusion(JsonInclude.Include.NON_NULL)
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }

}
