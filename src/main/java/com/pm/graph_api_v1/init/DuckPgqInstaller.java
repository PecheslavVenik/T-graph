package com.pm.graph_api_v1.init;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;

@Component
public class DuckPgqInstaller implements ApplicationRunner {

    private final DataSource dataSource;

    public DuckPgqInstaller(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("INSTALL duckpgq FROM community");
            stmt.execute("LOAD duckpgq");
        }
    }
}