package com.ivanfranchin.orderapi.runner;

import com.ivanfranchin.orderapi.security.Role;
import com.ivanfranchin.orderapi.user.User;
import com.ivanfranchin.orderapi.user.UserService;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Slf4j
@Component
public class DatabaseInitializer implements CommandLineRunner {

  private final UserService userService;
  private final PasswordEncoder passwordEncoder;
  private final JdbcTemplate jdbcTemplate;
  private final PlatformTransactionManager transactionManager;

  @Autowired
  public DatabaseInitializer(
      UserService userService,
      PasswordEncoder passwordEncoder,
      JdbcTemplate jdbcTemplate,
      PlatformTransactionManager transactionManager) {
    this.userService = userService;
    this.passwordEncoder = passwordEncoder;
    this.jdbcTemplate = jdbcTemplate;
    this.transactionManager = transactionManager;
  }

  /** Constructor retained for isolated unit tests that do not start a transaction manager. */
  public DatabaseInitializer(
      UserService userService, PasswordEncoder passwordEncoder, JdbcTemplate jdbcTemplate) {
    this(userService, passwordEncoder, jdbcTemplate, null);
  }

  @Value("${app.database.initialize-demo-data:false}")
  private boolean initializeDemoData;

  @Value("${DEMO_ADMIN_PASSWORD:}")
  private String demoAdminPassword;

  @Value("${DEMO_USER_PASSWORD:}")
  private String demoUserPassword;

  @Override
  public void run(String... args) {
    if (!initializeDemoData) {
      return;
    }
    if (transactionManager == null) {
      initializeWithinTransaction();
    } else {
      new TransactionTemplate(transactionManager)
          .executeWithoutResult(status -> initializeWithinTransaction());
    }
  }

  void initializeWithinTransaction() {
    validatePasswords();
    List<User> users = getUsers();
    if (isPostgres()) {
      insertUsersAtomically(users);
    } else {
      users.stream()
          .filter(user -> !userService.hasUserWithUsername(user.getUsername()))
          .forEach(
              user -> {
                user.setPassword(passwordEncoder.encode(user.getPassword()));
                userService.saveUser(user);
              });
    }
    log.info("Database initialized");
  }

  private void validatePasswords() {
    if (demoAdminPassword == null
        || demoAdminPassword.isBlank()
        || demoUserPassword == null
        || demoUserPassword.isBlank()) {
      throw new IllegalStateException(
          "Demo data initialization requires non-blank DEMO_ADMIN_PASSWORD and DEMO_USER_PASSWORD");
    }
  }

  private List<User> getUsers() {
    return List.of(
        new User("admin", demoAdminPassword, "Admin", "admin@mycompany.com", Role.ADMIN),
        new User("user", demoUserPassword, "User", "user@mycompany.com", Role.USER));
  }

  private void insertUsersAtomically(List<User> users) {
    String sql =
        "INSERT INTO users (username, password, name, email, role) VALUES (?, ?, ?, ?, ?), (?, ?, ?, ?, ?) ON CONFLICT DO NOTHING";
    User admin = users.get(0);
    User user = users.get(1);
    jdbcTemplate.update(
        sql,
        admin.getUsername(),
        passwordEncoder.encode(demoAdminPassword),
        admin.getName(),
        admin.getEmail(),
        admin.getRole().name(),
        user.getUsername(),
        passwordEncoder.encode(demoUserPassword),
        user.getName(),
        user.getEmail(),
        user.getRole().name());
  }

  private boolean isPostgres() {
    return jdbcTemplate.execute(
        (org.springframework.jdbc.core.ConnectionCallback<Boolean>)
            connection ->
                connection.getMetaData().getDatabaseProductName().equalsIgnoreCase("PostgreSQL"));
  }
}
