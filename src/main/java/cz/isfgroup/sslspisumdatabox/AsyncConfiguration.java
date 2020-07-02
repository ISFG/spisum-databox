package cz.isfgroup.sslspisumdatabox;

import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurerSupport;
import org.springframework.scheduling.annotation.EnableAsync;

@Slf4j
@EnableAsync
@Configuration
public class AsyncConfiguration extends AsyncConfigurerSupport {

    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return (throwable, method, obj) -> {
            log.warn("Exception message - {}", throwable.getMessage());
            for (Object param : obj) {
                log.warn("Parameter value - {}", param);
            }
        };
    }

}
