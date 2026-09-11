package com.delivery.fdp.service;

import com.delivery.fdp.repository.ManagedProjectRepository;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class ManagedSqlExecutionService {
    private final ManagedProjectRepository repository;
    private final DataSource dataSource;

    public ManagedSqlExecutionService(ManagedProjectRepository repository, DataSource dataSource) {
        this.repository = repository;
        this.dataSource = dataSource;
    }

    public Map<String, Object> execute(Long projectId, String sql) {
        ManagedProjectRepository.Project project = repository.findById(projectId)
                .orElseThrow(() -> new IllegalArgumentException("项目不存在: " + projectId));
        if (!StringUtils.hasText(sql)) throw new IllegalArgumentException("SQL 不能为空");

        int statements = 0;
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                connection.setCatalog(project.databaseName());
                for (String fragment : splitSql(sql)) {
                    if (!StringUtils.hasText(fragment)) continue;
                    try (Statement statement = connection.createStatement()) {
                        statement.execute(fragment);
                        statements++;
                    }
                }
                connection.commit();
            } catch (Exception error) {
                try {
                    connection.rollback();
                } catch (Exception rollbackError) {
                    error.addSuppressed(rollbackError);
                }
                throw error;
            }
        } catch (Exception error) {
            throw new IllegalStateException("SQL 执行失败，事务已回滚: " + message(error), error);
        }

        return Map.of(
                "projectId", projectId,
                "database", project.databaseName(),
                "statements", statements,
                "transactional", true,
                "success", true);
    }

    private List<String> splitSql(String sql) {
        List<String> result = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean single = false;
        boolean dbl = false;
        for (int i = 0; i < sql.length(); i++) {
            char c = sql.charAt(i);
            if (c == '\'' && !dbl) single = !single;
            if (c == '"' && !single) dbl = !dbl;
            if (c == ';' && !single && !dbl) {
                result.add(current.toString().trim());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        if (!current.toString().isBlank()) result.add(current.toString().trim());
        return result;
    }

    private String message(Throwable error) {
        return error == null || error.getMessage() == null ? String.valueOf(error) : error.getMessage();
    }
}
