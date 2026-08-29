package com.ivanfranchin.orderapi.rest.dto;

import com.ivanfranchin.orderapi.security.Role;
import com.ivanfranchin.orderapi.user.User;

public record UserDto(Long id, String username, String name, String email, Role role) {

  public static UserDto from(User user) {
    return new UserDto(
        user.getId(), user.getUsername(), user.getName(), user.getEmail(), user.getRole());
  }
}
