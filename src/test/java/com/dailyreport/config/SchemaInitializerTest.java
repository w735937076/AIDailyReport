package com.dailyreport.config;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SchemaInitializerTest {

    @Test
    void splitStatements_keepsDdlAfterLeadingComment() {
        String sql = """
                -- section title

                CREATE TABLE IF NOT EXISTS performance_review (
                    id BIGINT PRIMARY KEY
                );

                CREATE INDEX idx_perf ON performance_review(id);
                """;
        List<String> stmts = SchemaInitializer.splitStatements(sql);
        assertEquals(2, stmts.size());
        assertTrue(stmts.get(0).startsWith("CREATE TABLE IF NOT EXISTS performance_review"));
        assertTrue(stmts.get(1).startsWith("CREATE INDEX idx_perf"));
    }

    @Test
    void splitStatements_dropsCommentOnlyChunks() {
        String sql = "-- only a comment\n;\nCREATE TABLE t (id INT);\n";
        List<String> stmts = SchemaInitializer.splitStatements(sql);
        assertEquals(1, stmts.size());
        assertTrue(stmts.get(0).startsWith("CREATE TABLE t"));
    }
}
