package com.springtest.product_store.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.i18n.AcceptHeaderLocaleResolver;

import java.util.List;
import java.util.Locale;

// Message language comes from the Accept-Language header: "ar" gets Arabic, anything else
// (including no header or an unsupported language) gets English, never the server's locale.
@Configuration
public class LocaleConfig {

    public static final Locale ARABIC = Locale.forLanguageTag("ar");

    @Bean
    public LocaleResolver localeResolver() {
        AcceptHeaderLocaleResolver resolver = new AcceptHeaderLocaleResolver();
        resolver.setSupportedLocales(List.of(Locale.ENGLISH, ARABIC));
        resolver.setDefaultLocale(Locale.ENGLISH);
        return resolver;
    }
}
