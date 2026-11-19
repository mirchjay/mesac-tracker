package com.mesac.web;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

/**
 * Connects to an online PostgreSQL database when the DATABASE_URL setting exists (on Render).
 * Without it (on your own computer) this class does nothing and the app uses the H2 file database.
 *
 * DATABASE_URL can be pasted exactly as Neon or Render give it:
 *   postgresql://user:password@host/dbname?sslmode=require
 * or as a JDBC link (jdbc:postgresql://host/dbname) with DATABASE_USERNAME and DATABASE_PASSWORD set too.
 */
@Configuration
@ConditionalOnProperty(name = "DATABASE_URL")
public class DatabaseConfig {

    @Bean
    public DataSource dataSource(@Value("${DATABASE_URL}") String databaseUrl,
                                 @Value("${DATABASE_USERNAME:}") String username,
                                 @Value("${DATABASE_PASSWORD:}") String password) {
        String url = databaseUrl.trim();
        String jdbcUrl;
        String user = username;
        String pass = password;

        if (url.startsWith("jdbc:")) {
            jdbcUrl = url;
        } else if (url.startsWith("postgres://") || url.startsWith("postgresql://")) {
            URI uri = URI.create(url);
            String info = uri.getRawUserInfo();
            if (info != null && !info.isEmpty()) {
                int colon = info.indexOf(':');
                user = decode(colon < 0 ? info : info.substring(0, colon));
                pass = colon < 0 ? "" : decode(info.substring(colon + 1));
            }
            StringBuilder b = new StringBuilder("jdbc:postgresql://").append(uri.getHost());
            if (uri.getPort() > 0) b.append(':').append(uri.getPort());
            b.append(uri.getRawPath() == null || uri.getRawPath().isEmpty() ? "/postgres" : uri.getRawPath());
            String query = keepSupported(uri.getRawQuery());
            if (!query.isEmpty()) b.append('?').append(query);
            jdbcUrl = b.toString();
        } else {
            throw new IllegalStateException("DATABASE_URL must start with postgresql://, postgres:// or jdbc:");
        }

        HikariDataSource ds = new HikariDataSource();
        ds.setJdbcUrl(jdbcUrl);
        ds.setUsername(user);
        ds.setPassword(pass);
        ds.setMaximumPoolSize(5);   // free databases allow only a few connections
        System.out.println(">>> Using the online PostgreSQL database at " + jdbcUrl.replaceAll("\\?.*$", ""));
        return ds;
    }

    /** Keeps the link's settings except channel_binding, which older drivers don't understand. */
    private static String keepSupported(String query) {
        if (query == null || query.isEmpty()) return "";
        StringBuilder out = new StringBuilder();
        for (String part : query.split("&")) {
            if (part.isEmpty() || part.startsWith("channel_binding=")) continue;
            if (out.length() > 0) out.append('&');
            out.append(part);
        }
        return out.toString();
    }

    private static String decode(String s) {
        return URLDecoder.decode(s, StandardCharsets.UTF_8);
    }
}
