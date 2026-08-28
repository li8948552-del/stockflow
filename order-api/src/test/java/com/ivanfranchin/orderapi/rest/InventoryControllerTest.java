package com.ivanfranchin.orderapi.rest;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ivanfranchin.orderapi.inventory.Inventory;
import com.ivanfranchin.orderapi.inventory.InventoryConflictException;
import com.ivanfranchin.orderapi.inventory.InventoryNotFoundException;
import com.ivanfranchin.orderapi.inventory.InventoryOptimisticLockException;
import com.ivanfranchin.orderapi.inventory.InventoryService;
import com.ivanfranchin.orderapi.product.Product;
import com.ivanfranchin.orderapi.security.CustomUserDetails;
import com.ivanfranchin.orderapi.security.Role;
import com.ivanfranchin.orderapi.security.SecurityConfig;
import com.ivanfranchin.orderapi.security.TokenProvider;
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
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(InventoryController.class)
@Import(SecurityConfig.class)
class InventoryControllerTest {

  @Autowired private MockMvc mockMvc;
  @MockitoBean private InventoryService inventoryService;
  @MockitoBean private UserDetailsService userDetailsService;
  @MockitoBean private TokenProvider tokenProvider;

  @Test
  @WithMockUser(authorities = "USER")
  void authenticatedUserCanReadInventoryWithFilters() throws Exception {
    when(inventoryService.getInventory("product-id", "warehouse-id", true))
        .thenReturn(List.of(inventory()));

    mockMvc
        .perform(
            get("/api/inventory")
                .param("productId", "product-id")
                .param("warehouseId", "warehouse-id")
                .param("lowStock", "true"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].inventoryId").value("inventory-id"))
        .andExpect(jsonPath("$[0].productSku").value("SKU-1"))
        .andExpect(jsonPath("$[0].warehouseCode").value("WH-1"))
        .andExpect(jsonPath("$[0].onHand").value(100))
        .andExpect(jsonPath("$[0].reserved").value(20))
        .andExpect(jsonPath("$[0].available").value(80));
  }

  @Test
  @WithMockUser(authorities = "USER")
  void authenticatedUserCanReadOneInventoryAndMovements() throws Exception {
    when(inventoryService.getInventory("inventory-id")).thenReturn(inventory());
    when(inventoryService.getMovements("inventory-id")).thenReturn(List.of());

    mockMvc.perform(get("/api/inventory/inventory-id")).andExpect(status().isOk());
    mockMvc
        .perform(get("/api/inventory/inventory-id/movements"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(0));
  }

  @Test
  void unauthenticatedReadsReturn401() throws Exception {
    mockMvc.perform(get("/api/inventory")).andExpect(status().isUnauthorized());
    mockMvc.perform(get("/api/inventory/inventory-id")).andExpect(status().isUnauthorized());
    mockMvc
        .perform(get("/api/inventory/inventory-id/movements"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void unauthenticatedWritesReturn401() throws Exception {
    mockMvc
        .perform(
            post("/api/inventory/receipts")
                .contentType(MediaType.APPLICATION_JSON)
                .content(receiptJson(1)))
        .andExpect(status().isUnauthorized());
    mockMvc
        .perform(
            post("/api/inventory/inventory-id/adjustments")
                .contentType(MediaType.APPLICATION_JSON)
                .content(adjustmentJson(1, "Correction")))
        .andExpect(status().isUnauthorized());
  }

  @Test
  @WithMockUser(authorities = "USER")
  void userWritesReturn403() throws Exception {
    mockMvc
        .perform(
            post("/api/inventory/receipts")
                .contentType(MediaType.APPLICATION_JSON)
                .content(receiptJson(1)))
        .andExpect(status().isForbidden());
    mockMvc
        .perform(
            post("/api/inventory/inventory-id/adjustments")
                .contentType(MediaType.APPLICATION_JSON)
                .content(adjustmentJson(1, "Correction")))
        .andExpect(status().isForbidden());
    verifyNoInteractions(inventoryService);
  }

  @Test
  void adminCanReceiveAndAdjust() throws Exception {
    Inventory inventory = inventory();
    when(inventoryService.receive(
            eq("product-id"), eq("warehouse-id"), eq(10L), isNull(), eq("admin")))
        .thenReturn(inventory);
    when(inventoryService.adjust(eq("inventory-id"), eq(-5L), eq("Damage"), isNull(), eq("admin")))
        .thenReturn(inventory);

    mockMvc
        .perform(
            post("/api/inventory/receipts")
                .with(user(details("admin", Role.ADMIN)))
                .contentType(MediaType.APPLICATION_JSON)
                .content(receiptJson(10)))
        .andExpect(status().isCreated());
    mockMvc
        .perform(
            post("/api/inventory/inventory-id/adjustments")
                .with(user(details("admin", Role.ADMIN)))
                .contentType(MediaType.APPLICATION_JSON)
                .content(adjustmentJson(-5, "Damage")))
        .andExpect(status().isOk());
  }

  @Test
  void requestValidationRejectsInvalidQuantitiesAndReason() throws Exception {
    mockMvc
        .perform(
            post("/api/inventory/receipts")
                .with(user(details("admin", Role.ADMIN)))
                .contentType(MediaType.APPLICATION_JSON)
                .content(receiptJson(-1)))
        .andExpect(status().isBadRequest());
    mockMvc
        .perform(
            post("/api/inventory/inventory-id/adjustments")
                .with(user(details("admin", Role.ADMIN)))
                .contentType(MediaType.APPLICATION_JSON)
                .content(adjustmentJson(0, " ")))
        .andExpect(status().isBadRequest());
    verifyNoInteractions(inventoryService);
  }

  @Test
  void requestValidationUsesNormalizedUnicodeText() throws Exception {
    mockMvc
        .perform(
            post("/api/inventory/inventory-id/adjustments")
                .with(user(details("admin", Role.ADMIN)))
                .contentType(MediaType.APPLICATION_JSON)
                .content(adjustmentJson(1, "\u00a0\u2003")))
        .andExpect(status().isBadRequest());
    mockMvc
        .perform(
            post("/api/inventory/inventory-id/adjustments")
                .with(user(details("admin", Role.ADMIN)))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    adjustmentJson(
                        1,
                        "📦"
                            .repeat(
                                com.ivanfranchin.orderapi.inventory.InventoryMovement
                                        .REASON_MAX_LENGTH
                                    + 1))))
        .andExpect(status().isBadRequest());
    verifyNoInteractions(inventoryService);
  }

  @Test
  void requestValidationAcceptsNormalizedSupplementaryCodePointLimits() throws Exception {
    Inventory inventory = inventory();
    String reason = "\u00a0" + "📦".repeat(500) + "\u2003";
    String reference = "\u2003" + "📦".repeat(128) + "\u00a0";
    when(inventoryService.adjust(
            eq("inventory-id"), eq(1L), eq(reason), eq(reference), eq("admin")))
        .thenReturn(inventory);

    mockMvc
        .perform(
            post("/api/inventory/inventory-id/adjustments")
                .with(user(details("admin", Role.ADMIN)))
                .contentType(MediaType.APPLICATION_JSON)
                .content(adjustmentJson(1, reason, reference)))
        .andExpect(status().isOk());
  }

  @Test
  void inventoryAndOptimisticConflictsReturn409() throws Exception {
    when(inventoryService.receive(anyString(), anyString(), eq(1L), isNull(), eq("admin")))
        .thenThrow(new InventoryConflictException("Inventory already exists"));
    when(inventoryService.adjust(
            eq("inventory-id"), eq(1L), eq("Correction"), isNull(), eq("admin")))
        .thenThrow(
            new InventoryOptimisticLockException(
                "Inventory was updated by another transaction", new RuntimeException()));

    mockMvc
        .perform(
            post("/api/inventory/receipts")
                .with(user(details("admin", Role.ADMIN)))
                .contentType(MediaType.APPLICATION_JSON)
                .content(receiptJson(1)))
        .andExpect(status().isConflict());
    mockMvc
        .perform(
            post("/api/inventory/inventory-id/adjustments")
                .with(user(details("admin", Role.ADMIN)))
                .contentType(MediaType.APPLICATION_JSON)
                .content(adjustmentJson(1, "Correction")))
        .andExpect(status().isConflict());
  }

  @Test
  @WithMockUser(authorities = "USER")
  void notFoundUsesExistingErrorFormat() throws Exception {
    when(inventoryService.getInventory("missing"))
        .thenThrow(new InventoryNotFoundException("Inventory with id missing not found"));

    mockMvc.perform(get("/api/inventory/missing")).andExpect(status().isNotFound());
  }

  private Inventory inventory() {
    Product product = new Product("SKU-1", "Keyboard", BigDecimal.ONE, 10);
    Warehouse warehouse = new Warehouse("WH-1", "Main", "Sydney");
    ReflectionTestUtils.setField(product, "id", "product-id");
    ReflectionTestUtils.setField(warehouse, "id", "warehouse-id");
    Inventory inventory = new Inventory(product, warehouse, 100);
    inventory.setReserved(20);
    ReflectionTestUtils.setField(inventory, "id", "inventory-id");
    ReflectionTestUtils.setField(inventory, "version", 1L);
    ReflectionTestUtils.setField(inventory, "updatedAt", Instant.now());
    return inventory;
  }

  private CustomUserDetails details(String username, Role role) {
    return new CustomUserDetails(
        1L,
        username,
        "pass",
        "Test User",
        username + "@example.com",
        List.of(new SimpleGrantedAuthority(role.name())));
  }

  private String receiptJson(long quantity) {
    return """
        {"productId":"product-id","warehouseId":"warehouse-id","quantity":%d}
        """
        .formatted(quantity);
  }

  private String adjustmentJson(long delta, String reason) {
    return adjustmentJson(delta, reason, null);
  }

  private String adjustmentJson(long delta, String reason, String reference) {
    return """
        {"quantityDelta":%d,"reason":"%s","reference":%s}
        """
        .formatted(delta, reason, reference == null ? "null" : "\"" + reference + "\"");
  }
}
