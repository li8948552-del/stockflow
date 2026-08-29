package com.ivanfranchin.orderapi.rest;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ivanfranchin.orderapi.order.Order;
import com.ivanfranchin.orderapi.order.OrderService;
import com.ivanfranchin.orderapi.product.Product;
import com.ivanfranchin.orderapi.rest.dto.CreateOrderItemRequest;
import com.ivanfranchin.orderapi.rest.dto.CreateOrderRequest;
import com.ivanfranchin.orderapi.security.CustomUserDetails;
import com.ivanfranchin.orderapi.security.Role;
import com.ivanfranchin.orderapi.security.SecurityConfig;
import com.ivanfranchin.orderapi.security.TokenProvider;
import com.ivanfranchin.orderapi.user.User;
import com.ivanfranchin.orderapi.warehouse.Warehouse;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;

@WebMvcTest(OrderController.class)
@Import(SecurityConfig.class)
class OrderControllerTest {
  @Autowired private MockMvc mockMvc;
  @Autowired private JsonMapper jsonMapper;
  @MockitoBean private OrderService orderService;
  @MockitoBean private UserDetailsService userDetailsService;
  @MockitoBean private TokenProvider tokenProvider;

  @Test
  void authenticatedUserCanCreateAndDtoDoesNotLeakPassword() throws Exception {
    Order order = order("order-id", "alice");
    when(orderService.createOrder(any(CreateOrderRequest.class), eq("alice"))).thenReturn(order);
    CreateOrderRequest request =
        new CreateOrderRequest(
            "warehouse-id", List.of(new CreateOrderItemRequest("product-id", 2L)));

    mockMvc
        .perform(
            post("/api/orders")
                .with(user(principal("alice", Role.USER)))
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(request)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.status").value("RESERVED"))
        .andExpect(jsonPath("$.items[0].lineNumber").value(1))
        .andExpect(jsonPath("$.items[0].unitPrice").value("12.50"))
        .andExpect(jsonPath("$.items[0].lineTotal").value("25.00"))
        .andExpect(jsonPath("$.totalAmount").value("25.00"))
        .andExpect(jsonPath("$.user.username").value("alice"))
        .andExpect(jsonPath("$.user.password").doesNotExist());
  }

  @Test
  void clientLineNumberCannotOverrideServerGeneratedValue() throws Exception {
    Order order = order("order-id", "alice");
    when(orderService.createOrder(any(CreateOrderRequest.class), eq("alice"))).thenReturn(order);

    mockMvc
        .perform(
            post("/api/orders")
                .with(user(principal("alice", Role.USER)))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"warehouseId":"warehouse-id","items":[{
                      "productId":"product-id","quantity":2,"lineNumber":99
                    }]}
                    """))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.items[0].lineNumber").value(1));
  }

  @Test
  void createValidatesEmptyItemsAndNonpositiveQuantity() throws Exception {
    mockMvc
        .perform(
            post("/api/orders")
                .with(user(principal("alice", Role.USER)))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"warehouseId\":\"warehouse-id\",\"items\":[]}"))
        .andExpect(status().isBadRequest());
    mockMvc
        .perform(
            post("/api/orders")
                .with(user(principal("alice", Role.USER)))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"warehouseId\":\"warehouse-id\",\"items\":[{\"productId\":\"product-id\",\"quantity\":0}]}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void userCanReadListAndSingleOrder() throws Exception {
    Order order = order("order-id", "alice");
    when(orderService.getOrders("alice", Role.USER, null, null, null)).thenReturn(List.of(order));
    when(orderService.getOrder("order-id", "alice", Role.USER)).thenReturn(order);

    mockMvc
        .perform(get("/api/orders").with(user(principal("alice", Role.USER))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].id").value("order-id"));
    mockMvc
        .perform(get("/api/orders/order-id").with(user(principal("alice", Role.USER))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value("order-id"));
  }

  @Test
  void adminFiltersAndCancelsAnyOrder() throws Exception {
    Order order = order("order-id", "alice");
    when(orderService.getOrders("admin", Role.ADMIN, 1L, null, "warehouse-id"))
        .thenReturn(List.of(order));
    order.cancel();
    when(orderService.cancelOrder("order-id", "admin", Role.ADMIN)).thenReturn(order);

    mockMvc
        .perform(
            get("/api/orders")
                .with(user(principal("admin", Role.ADMIN)))
                .param("userId", "1")
                .param("warehouseId", "warehouse-id"))
        .andExpect(status().isOk());
    mockMvc
        .perform(post("/api/orders/order-id/cancel").with(user(principal("admin", Role.ADMIN))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("CANCELLED"));
  }

  @Test
  void anonymousRequestsReturn401AndDeleteIsNotExposed() throws Exception {
    mockMvc.perform(get("/api/orders")).andExpect(status().isUnauthorized());
    mockMvc
        .perform(
            post("/api/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"warehouseId\":\"warehouse-id\",\"items\":[]}"))
        .andExpect(status().isUnauthorized());
    mockMvc
        .perform(delete("/api/orders/order-id").with(user(principal("admin", Role.ADMIN))))
        .andExpect(status().isMethodNotAllowed());
  }

  private CustomUserDetails principal(String username, Role role) {
    return new CustomUserDetails(
        1L,
        username,
        "secret",
        username,
        username + "@example.com",
        List.of(new SimpleGrantedAuthority(role.name())));
  }

  private Order order(String id, String username) {
    User owner = new User(username, "secret", username, username + "@example.com", Role.USER);
    owner.setId(1L);
    Warehouse warehouse = new Warehouse("WH-1", "Main", "Sydney");
    ReflectionTestUtils.setField(warehouse, "id", "warehouse-id");
    Product product = new Product("SKU-1", "Widget", new BigDecimal("12.50"), 5);
    ReflectionTestUtils.setField(product, "id", "product-id");
    Order order = new Order(id, owner, warehouse, Instant.now().plusSeconds(1800));
    order.addItem(product, 2, 1);
    ReflectionTestUtils.setField(order, "createdAt", Instant.now());
    ReflectionTestUtils.setField(order, "updatedAt", Instant.now());
    ReflectionTestUtils.setField(order, "version", 0L);
    ReflectionTestUtils.setField(order.getItems().getFirst(), "id", "item-id");
    return order;
  }
}
