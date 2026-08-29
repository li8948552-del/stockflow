package com.ivanfranchin.orderapi.user;

import com.ivanfranchin.orderapi.order.OrderRepository;
import com.ivanfranchin.orderapi.security.Role;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Service
public class UserService {

  private final UserRepository userRepository;
  private final OrderRepository orderRepository;

  public List<User> getUsers() {
    return userRepository.findAllByOrderByUsernameAsc();
  }

  public long countUsers() {
    return userRepository.count();
  }

  public long countAdmins() {
    return userRepository.countByRole(Role.ADMIN);
  }

  public Optional<User> getUserByUsername(String username) {
    return userRepository.findByUsername(username);
  }

  public boolean hasUserWithUsername(String username) {
    return userRepository.existsByUsername(username);
  }

  public boolean hasUserWithEmail(String email) {
    return userRepository.existsByEmail(email);
  }

  public User validateAndGetUserByUsername(String username) {
    return getUserByUsername(username)
        .orElseThrow(
            () -> new UserNotFoundException("User with username %s not found".formatted(username)));
  }

  public User validateAndGetUserByUsernameForUpdate(String username) {
    return userRepository
        .findByUsernameForUpdate(username)
        .orElseThrow(
            () -> new UserNotFoundException("User with username %s not found".formatted(username)));
  }

  public User saveUser(User user) {
    return userRepository.save(user);
  }

  @Transactional
  public void deleteUser(String username, String currentUsername) {
    User user = validateAndGetUserByUsernameForUpdate(username);
    if (currentUsername.equals(username)) {
      throw new UserDeletionNotAllowedException("You cannot delete your own account");
    }
    if (Role.ADMIN.equals(user.getRole()) && userRepository.countByRole(Role.ADMIN) == 1) {
      throw new UserDeletionNotAllowedException("Cannot delete the last admin account");
    }
    if (orderRepository.existsByUserId(user.getId())) {
      throw new UserHasOrdersException(
          "User %s cannot be deleted because order history exists".formatted(username));
    }
    userRepository.delete(user);
    userRepository.flush();
  }
}
