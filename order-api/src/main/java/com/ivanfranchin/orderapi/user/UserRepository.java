package com.ivanfranchin.orderapi.user;

import com.ivanfranchin.orderapi.security.Role;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, Long> {

  List<User> findAllByOrderByUsernameAsc();

  Optional<User> findByUsername(String username);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select user from User user where user.username = :username")
  Optional<User> findByUsernameForUpdate(@Param("username") String username);

  boolean existsByUsername(String username);

  boolean existsByEmail(String email);

  long countByRole(Role role);
}
