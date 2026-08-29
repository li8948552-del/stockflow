package com.ivanfranchin.orderapi;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

class DatabaseMigrationConfigurationTest {
  @Test
  void productionConfigurationUsesFlywayAndHibernateValidation() throws Exception {
    List<PropertySource<?>> sources =
        new YamlPropertySourceLoader()
            .load("application", new ClassPathResource("application.yml"));
    PropertySource<?> source = sources.get(0);
    assertThat(source.getProperty("spring.jpa.hibernate.ddl-auto")).isEqualTo("validate");
    assertThat(source.getProperty("spring.flyway.enabled")).isEqualTo(true);
    assertThat(source.getProperty("spring.flyway.clean-disabled")).isEqualTo(true);
    assertThat(source.getProperty("spring.flyway.baseline-on-migrate")).isEqualTo(false);
  }
}
