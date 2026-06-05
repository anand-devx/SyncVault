package com.syncvault.server.service; // MUST be exactly this!

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

@Service
public class UserService {

    @Value("${syncvault.users.file:users.json}")
    private String usersFile;

    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    public boolean register(String username, String password) throws Exception {
        Map<String, String> users = loadUsers();
        if (users.containsKey(username)) return false; // already exists
        users.put(username, encoder.encode(password));
        saveUsers(users);
        return true;
    }

    public boolean validate(String username, String password) throws Exception {
        Map<String, String> users = loadUsers();
        String hash = users.get(username);
        if (hash == null) return false;
        return encoder.matches(password, hash);
    }

    private Map<String, String> loadUsers() throws Exception {
        File f = new File(usersFile);
        if (!f.exists()) return new HashMap<>();
        return new com.fasterxml.jackson.databind.ObjectMapper()
            .readValue(f, new com.fasterxml.jackson.core.type.TypeReference<Map<String, String>>() {});
    }

    private void saveUsers(Map<String, String> users) throws Exception {
        new com.fasterxml.jackson.databind.ObjectMapper()
            .writeValue(new File(usersFile), users);
    }
}