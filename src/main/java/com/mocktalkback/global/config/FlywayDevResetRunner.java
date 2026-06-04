package com.mocktalkback.global.config;

import org.flywaydb.core.Flyway;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
@Profile("dev") // 로컬에서만
@ConditionalOnBean(Flyway.class)
public class FlywayDevResetRunner implements ApplicationRunner {

    private final Flyway flyway;
    private final Environment env;

    public FlywayDevResetRunner(Flyway flyway, Environment env) {
        this.flyway = flyway;
        this.env = env;
    }

    @Override
    public void run(ApplicationArguments args) {
        boolean reset = env.getProperty("app.flyway.reset", Boolean.class, false);
        if (!reset) return;

        flyway.clean();
        flyway.migrate();
    }
}
