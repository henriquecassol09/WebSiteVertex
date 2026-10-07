package com.empresa.backend;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class DatabaseConnectionTest {

    @Autowired
    private DataSource dataSource;

    @Test
    void testConnection() throws Exception {
        assertNotNull(dataSource);
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT version(), current_database(), current_user")) {
            assertTrue(rs.next());
            System.out.println("==================================================");
            System.out.println("CONEXÃO COM O NEON ESTABELECIDA COM SUCESSO!");
            System.out.println("PostgreSQL: " + rs.getString(1));
            System.out.println("Database:   " + rs.getString(2));
            System.out.println("User:       " + rs.getString(3));
            System.out.println("==================================================");
        }
    }
}
