package com.example.meetingroom.repository;

import com.example.meetingroom.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 【Repository】UserRepository
 */
@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    // 找出所有審核者
    List<User> findByRole(String role);
}
