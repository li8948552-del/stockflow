package com.ivanfranchin.orderapi.rest;

import static com.ivanfranchin.orderapi.config.SwaggerConfig.BEARER_KEY_SECURITY_SCHEME;

import com.ivanfranchin.orderapi.order.Order;
import com.ivanfranchin.orderapi.order.OrderService;
import com.ivanfranchin.orderapi.order.OrderStatus;
import com.ivanfranchin.orderapi.rest.dto.CreateOrderRequest;
import com.ivanfranchin.orderapi.rest.dto.OrderDto;
import com.ivanfranchin.orderapi.security.CustomUserDetails;
import com.ivanfranchin.orderapi.security.Role;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/orders")
public class OrderController {
  private final OrderService orderService;

  @Operation(security = {@SecurityRequirement(name = BEARER_KEY_SECURITY_SCHEME)})
  @GetMapping
  public List<OrderDto> getOrders(
      @AuthenticationPrincipal CustomUserDetails currentUser,
      @RequestParam(required = false) Long userId,
      @RequestParam(required = false) OrderStatus status,
      @RequestParam(required = false) String warehouseId) {
    return orderService
        .getOrders(currentUser.getUsername(), roleOf(currentUser), userId, status, warehouseId)
        .stream()
        .map(OrderDto::from)
        .toList();
  }

  @Operation(security = {@SecurityRequirement(name = BEARER_KEY_SECURITY_SCHEME)})
  @GetMapping("/{id}")
  public OrderDto getOrder(
      @PathVariable String id, @AuthenticationPrincipal CustomUserDetails currentUser) {
    return OrderDto.from(orderService.getOrder(id, currentUser.getUsername(), roleOf(currentUser)));
  }

  @Operation(security = {@SecurityRequirement(name = BEARER_KEY_SECURITY_SCHEME)})
  @ResponseStatus(HttpStatus.CREATED)
  @PostMapping
  public OrderDto createOrder(
      @AuthenticationPrincipal CustomUserDetails currentUser,
      @Valid @RequestBody CreateOrderRequest request) {
    return OrderDto.from(orderService.createOrder(request, currentUser.getUsername()));
  }

  @Operation(security = {@SecurityRequirement(name = BEARER_KEY_SECURITY_SCHEME)})
  @PostMapping("/{id}/cancel")
  public OrderDto cancelOrder(
      @PathVariable String id, @AuthenticationPrincipal CustomUserDetails currentUser) {
    Order order = orderService.cancelOrder(id, currentUser.getUsername(), roleOf(currentUser));
    return OrderDto.from(order);
  }

  private Role roleOf(CustomUserDetails user) {
    return user.getAuthorities().stream()
        .map(authority -> Role.valueOf(authority.getAuthority()))
        .filter(role -> role == Role.ADMIN)
        .findFirst()
        .orElse(Role.USER);
  }
}
