package com.gitanalytics.auth.repository;

import com.gitanalytics.auth.entity.UserPreferences;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserPreferencesRepository extends JpaRepository<UserPreferences, UUID> {
    Optional<UserPreferences> findByUserId(UUID userId);

    @Modifying
    @Query("DELETE FROM UserPreferences p WHERE p.user.id = :userId")
    void deleteByUserId(UUID userId);
}
