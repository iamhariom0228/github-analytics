package com.gitanalytics.auth.dao;

import com.gitanalytics.auth.entity.User;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserDao {
    Optional<User> findById(UUID id);
    Optional<User> findByGithubId(Long githubId);
    List<User> findAll();
    User save(User user);
}
