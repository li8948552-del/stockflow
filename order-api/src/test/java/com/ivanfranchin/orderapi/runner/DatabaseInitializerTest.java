package com.ivanfranchin.orderapi.runner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.ivanfranchin.orderapi.security.Role;
import com.ivanfranchin.orderapi.user.User;
import com.ivanfranchin.orderapi.user.UserService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

class DatabaseInitializerTest {

  private final UserService userService = mock(UserService.class);
  private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
  private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);

  @Test
  void missingConfigurationDoesNotCreateDemoUsers() {
    DatabaseInitializer initializer =
        new DatabaseInitializer(userService, passwordEncoder, jdbcTemplate);

    initializer.run();

    verifyNoInteractions(userService, passwordEncoder);
  }

  @Test
  void disabledConfigurationDoesNotCreateDemoUsers() {
    DatabaseInitializer initializer = initializerWithDemoData(false);

    initializer.run();

    verifyNoInteractions(userService, passwordEncoder);
  }

  @Test
  void enabledConfigurationCreatesOnlyExpectedDemoUsers() {
    when(userService.countUsers()).thenReturn(0L);
    when(userService.hasUserWithUsername("admin")).thenReturn(false);
    when(userService.hasUserWithUsername("user")).thenReturn(false);
    when(jdbcTemplate.execute(org.mockito.ArgumentMatchers.any(ConnectionCallback.class)))
        .thenReturn(false);
    when(passwordEncoder.encode("admin-password")).thenReturn("encoded-admin");
    when(passwordEncoder.encode("user-password")).thenReturn("encoded-user");
    DatabaseInitializer initializer = initializerWithDemoData(true);

    initializer.run();

    var captor = org.mockito.ArgumentCaptor.forClass(User.class);
    verify(userService, org.mockito.Mockito.times(2)).saveUser(captor.capture());
    List<User> users = captor.getAllValues();
    assertThat(users).extracting(User::getUsername).containsExactlyInAnyOrder("admin", "user");
    assertThat(users)
        .extracting(User::getPassword)
        .containsExactlyInAnyOrder("encoded-admin", "encoded-user");
    assertThat(users).extracting(User::getRole).containsExactlyInAnyOrder(Role.ADMIN, Role.USER);
  }

  @Test
  void existingUsersAreNeverOverwritten() {
    when(jdbcTemplate.execute(org.mockito.ArgumentMatchers.any(ConnectionCallback.class)))
        .thenReturn(false);
    when(userService.hasUserWithUsername("admin")).thenReturn(true);
    when(userService.hasUserWithUsername("user")).thenReturn(true);
    DatabaseInitializer initializer = initializerWithDemoData(true);

    initializer.run();

    verifyNoInteractions(passwordEncoder);
    org.mockito.Mockito.verify(userService, org.mockito.Mockito.never())
        .saveUser(org.mockito.ArgumentMatchers.any());
  }

  @Test
  void enabledConfigurationWithoutPasswordsFailsBeforeAnyWrite() {
    DatabaseInitializer initializer = initializerWithDemoData(true);
    ReflectionTestUtils.setField(initializer, "demoAdminPassword", " ");
    ReflectionTestUtils.setField(initializer, "demoUserPassword", null);

    org.assertj.core.api.Assertions.assertThatThrownBy(initializer::run)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("DEMO_ADMIN_PASSWORD")
        .hasMessageContaining("DEMO_USER_PASSWORD");

    verifyNoInteractions(userService, passwordEncoder, jdbcTemplate);
  }

  @Test
  void postgresInitializationUsesAtomicConflictSafeInsert() {
    when(jdbcTemplate.execute(org.mockito.ArgumentMatchers.any(ConnectionCallback.class)))
        .thenReturn(true);
    when(passwordEncoder.encode("admin-password")).thenReturn("encoded-admin");
    when(passwordEncoder.encode("user-password")).thenReturn("encoded-user");
    DatabaseInitializer initializer = initializerWithDemoData(true);

    initializer.run();

    verify(jdbcTemplate)
        .update(
            contains("ON CONFLICT DO NOTHING"), org.mockito.ArgumentMatchers.any(Object[].class));
    verifyNoInteractions(userService);
  }

  private DatabaseInitializer initializerWithDemoData(boolean enabled) {
    DatabaseInitializer initializer =
        new DatabaseInitializer(userService, passwordEncoder, jdbcTemplate);
    ReflectionTestUtils.setField(initializer, "initializeDemoData", enabled);
    ReflectionTestUtils.setField(initializer, "demoAdminPassword", "admin-password");
    ReflectionTestUtils.setField(initializer, "demoUserPassword", "user-password");
    return initializer;
  }
}
