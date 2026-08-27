package com.delivery.fdp.repository;

import com.delivery.fdp.model.SourceCredential;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.List;
import java.util.Optional;

@Repository
public class SourceCredentialRepository {
    private final JdbcTemplate jdbc;
    private final RowMapper<SourceCredential> mapper = (rs, rowNum) -> {
        SourceCredential credential = new SourceCredential();
        credential.setId(rs.getLong("id"));
        credential.setName(rs.getString("name"));
        credential.setProvider(rs.getString("provider"));
        credential.setCloneUsername(rs.getString("clone_username"));
        credential.setStatus(rs.getString("status"));
        credential.setLastTestMessage(rs.getString("last_test_message"));
        if (rs.getTimestamp("last_test_time") != null) credential.setLastTestTime(rs.getTimestamp("last_test_time").toLocalDateTime());
        if (rs.getTimestamp("create_time") != null) credential.setCreateTime(rs.getTimestamp("create_time").toLocalDateTime());
        if (rs.getTimestamp("update_time") != null) credential.setUpdateTime(rs.getTimestamp("update_time").toLocalDateTime());
        return credential;
    };

    public SourceCredentialRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<SourceCredential> findAll() {
        return jdbc.query("SELECT id,name,provider,clone_username,status,last_test_message,last_test_time,create_time,update_time FROM source_credential ORDER BY id DESC", mapper);
    }

    public Optional<SourceCredential> findById(Long id) {
        return jdbc.query("SELECT id,name,provider,clone_username,status,last_test_message,last_test_time,create_time,update_time FROM source_credential WHERE id=?", mapper, id).stream().findFirst();
    }

    public Optional<CredentialSecret> findSecretById(Long id) {
        return jdbc.query(
                "SELECT id,clone_username,secret_encrypted FROM source_credential WHERE id=?",
                (rs, rowNum) -> new CredentialSecret(rs.getLong("id"), rs.getString("clone_username"), rs.getString("secret_encrypted")),
                id
        ).stream().findFirst();
    }

    public long create(String name, String provider, String cloneUsername, String encryptedSecret) {
        KeyHolder key = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO source_credential(name,provider,clone_username,secret_encrypted,status) VALUES(?,?,?,?,'UNTESTED')",
                    Statement.RETURN_GENERATED_KEYS
            );
            ps.setString(1, name);
            ps.setString(2, provider);
            ps.setString(3, cloneUsername);
            ps.setString(4, encryptedSecret);
            return ps;
        }, key);
        if (key.getKey() == null) throw new IllegalStateException("Credential create failed");
        return key.getKey().longValue();
    }

    public void update(Long id, String name, String provider, String cloneUsername, String encryptedSecret) {
        if (encryptedSecret == null) {
            jdbc.update(
                    "UPDATE source_credential SET name=?,provider=?,clone_username=?,status='UNTESTED',last_test_message=NULL,last_test_time=NULL,update_time=NOW() WHERE id=?",
                    name, provider, cloneUsername, id
            );
        } else {
            jdbc.update(
                    "UPDATE source_credential SET name=?,provider=?,clone_username=?,secret_encrypted=?,status='UNTESTED',last_test_message=NULL,last_test_time=NULL,update_time=NOW() WHERE id=?",
                    name, provider, cloneUsername, encryptedSecret, id
            );
        }
    }

    public void updateTestResult(Long id, String status, String message) {
        jdbc.update(
                "UPDATE source_credential SET status=?,last_test_message=?,last_test_time=NOW(),update_time=NOW() WHERE id=?",
                status, truncate(message, 500), id
        );
    }

    public int countByName(String name, Long excludeId) {
        Integer count = excludeId == null
                ? jdbc.queryForObject("SELECT COUNT(*) FROM source_credential WHERE name=?", Integer.class, name)
                : jdbc.queryForObject("SELECT COUNT(*) FROM source_credential WHERE name=? AND id<>?", Integer.class, name, excludeId);
        return count == null ? 0 : count;
    }

    public int countProjectsUsing(Long id) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM delivery_project WHERE credential_id=?", Integer.class, id);
        return count == null ? 0 : count;
    }

    public void delete(Long id) {
        jdbc.update("DELETE FROM source_credential WHERE id=?", id);
    }

    private String truncate(String value, int maxLength) {
        if (value == null) return null;
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    public record CredentialSecret(Long id, String cloneUsername, String encryptedSecret) {}
}
