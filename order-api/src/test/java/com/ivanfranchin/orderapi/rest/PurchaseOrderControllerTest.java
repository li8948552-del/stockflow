package com.ivanfranchin.orderapi.rest;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ivanfranchin.orderapi.procurement.DuplicateReceiptException;
import com.ivanfranchin.orderapi.procurement.GoodsReceipt;
import com.ivanfranchin.orderapi.procurement.InvalidPurchaseOrderStateException;
import com.ivanfranchin.orderapi.procurement.OverReceiptException;
import com.ivanfranchin.orderapi.procurement.PurchaseOrder;
import com.ivanfranchin.orderapi.procurement.PurchaseOrderService;
import com.ivanfranchin.orderapi.procurement.PurchaseOrderStatus;
import com.ivanfranchin.orderapi.product.Product;
import com.ivanfranchin.orderapi.security.CustomUserDetails;
import com.ivanfranchin.orderapi.security.Role;
import com.ivanfranchin.orderapi.security.SecurityConfig;
import com.ivanfranchin.orderapi.security.TokenProvider;
import com.ivanfranchin.orderapi.supplier.Supplier;
import com.ivanfranchin.orderapi.warehouse.Warehouse;
import java.math.BigDecimal;
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

@WebMvcTest(PurchaseOrderController.class)
@Import(SecurityConfig.class)
class PurchaseOrderControllerTest {
  @Autowired private MockMvc mockMvc;
  @Autowired private JsonMapper jsonMapper;
  @MockitoBean private PurchaseOrderService service;
  @MockitoBean private UserDetailsService userDetailsService;
  @MockitoBean private TokenProvider tokenProvider;

  @Test
  void adminCanReadAndFilterPurchaseOrders() throws Exception {
    when(service.findAll("supplier-id", "warehouse-id", PurchaseOrderStatus.SUBMITTED))
        .thenReturn(List.of(order()));
    mockMvc
        .perform(
            get("/api/purchase-orders")
                .with(user(principal("admin", Role.ADMIN)))
                .param("supplierId", "supplier-id")
                .param("warehouseId", "warehouse-id")
                .param("status", "SUBMITTED"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].totalAmount").value("0.10"));
    verify(service).findAll("supplier-id", "warehouse-id", PurchaseOrderStatus.SUBMITTED);
  }

  @Test
  void adminCanReadDetailCreateSubmitReceiveAndCancel() throws Exception {
    PurchaseOrder po = order();
    GoodsReceipt receipt = new GoodsReceipt("receipt-id", po, "key", "admin", "hash");
    when(service.find("po-id")).thenReturn(po);
    when(service.create(any())).thenReturn(po);
    when(service.submit("po-id")).thenReturn(po);
    when(service.receive(eq("po-id"), any(), eq("admin"))).thenReturn(receipt);
    when(service.cancel("po-id")).thenReturn(po);
    mockMvc
        .perform(get("/api/purchase-orders/po-id").with(user(principal("admin", Role.ADMIN))))
        .andExpect(status().isOk());
    mockMvc
        .perform(
            post("/api/purchase-orders")
                .with(user(principal("admin", Role.ADMIN)))
                .contentType(MediaType.APPLICATION_JSON)
                .content(createJson()))
        .andExpect(status().isCreated());
    mockMvc
        .perform(
            post("/api/purchase-orders/po-id/submit").with(user(principal("admin", Role.ADMIN))))
        .andExpect(status().isOk());
    mockMvc
        .perform(
            post("/api/purchase-orders/po-id/receipts")
                .with(user(principal("admin", Role.ADMIN)))
                .contentType(MediaType.APPLICATION_JSON)
                .content(receiptJson()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.clientRequestId").value("key"));
    mockMvc
        .perform(
            post("/api/purchase-orders/po-id/cancel").with(user(principal("admin", Role.ADMIN))))
        .andExpect(status().isOk());
  }

  @Test
  void userAndAnonymousCannotAccessAnyPurchaseWriteEndpoint() throws Exception {
    String[] paths = {
      "/api/purchase-orders",
      "/api/purchase-orders/po-id/submit",
      "/api/purchase-orders/po-id/receipts",
      "/api/purchase-orders/po-id/cancel"
    };
    for (String path : paths) {
      mockMvc
          .perform(
              post(path)
                  .with(user(principal("user", Role.USER)))
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(createJson()))
          .andExpect(status().isForbidden());
      mockMvc
          .perform(post(path).contentType(MediaType.APPLICATION_JSON).content(createJson()))
          .andExpect(status().isUnauthorized());
    }
  }

  @Test
  void userAndAnonymousCannotReadPurchaseOrders() throws Exception {
    mockMvc
        .perform(get("/api/purchase-orders").with(user(principal("user", Role.USER))))
        .andExpect(status().isForbidden());
    mockMvc.perform(get("/api/purchase-orders")).andExpect(status().isUnauthorized());
  }

  @Test
  void invalidStatusAndRequestFieldsReturnBadRequest() throws Exception {
    mockMvc
        .perform(
            get("/api/purchase-orders")
                .with(user(principal("admin", Role.ADMIN)))
                .param("status", "UNKNOWN"))
        .andExpect(status().isBadRequest());
    mockMvc
        .perform(
            post("/api/purchase-orders")
                .with(user(principal("admin", Role.ADMIN)))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"supplierId\":\"s\",\"warehouseId\":\"w\",\"items\":[]}"))
        .andExpect(status().isBadRequest());
    mockMvc
        .perform(
            post("/api/purchase-orders/po-id/receipts")
                .with(user(principal("admin", Role.ADMIN)))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"clientRequestId\":\"\",\"items\":[]}"))
        .andExpect(status().isBadRequest());
    verifyNoInteractions(service);
  }

  @Test
  void clientCannotOverrideCalculatedFields() throws Exception {
    when(service.create(any())).thenReturn(order());
    mockMvc
        .perform(
            post("/api/purchase-orders")
                .with(user(principal("admin", Role.ADMIN)))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"supplierId\":\"s\",\"warehouseId\":\"w\",\"totalAmount\":\"999.99\",\"items\":[{\"productId\":\"p\",\"quantity\":1,\"unitCost\":\"0.10\",\"lineTotal\":\"999.99\",\"lineNumber\":99,\"receivedQuantity\":99}]}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.totalAmount").value("0.10"));
  }

  @Test
  void procurementExceptionsMapToExpectedStatuses() throws Exception {
    when(service.find("missing"))
        .thenThrow(
            new com.ivanfranchin.orderapi.procurement.PurchaseOrderNotFoundException("missing"));
    when(service.submit("po-id")).thenThrow(new InvalidPurchaseOrderStateException("bad state"));
    when(service.cancel("po-id")).thenThrow(new OverReceiptException("too much"));
    when(service.receive(eq("po-id"), any(), eq("admin")))
        .thenThrow(new DuplicateReceiptException("duplicate"));
    mockMvc
        .perform(get("/api/purchase-orders/missing").with(user(principal("admin", Role.ADMIN))))
        .andExpect(status().isNotFound());
    mockMvc
        .perform(
            post("/api/purchase-orders/po-id/submit").with(user(principal("admin", Role.ADMIN))))
        .andExpect(status().isConflict());
    mockMvc
        .perform(
            post("/api/purchase-orders/po-id/cancel").with(user(principal("admin", Role.ADMIN))))
        .andExpect(status().isConflict());
    mockMvc
        .perform(
            post("/api/purchase-orders/po-id/receipts")
                .with(user(principal("admin", Role.ADMIN)))
                .contentType(MediaType.APPLICATION_JSON)
                .content(receiptJson()))
        .andExpect(status().isConflict());
  }

  @Test
  void unsupportedPutPatchDeleteDoNotInvokeService() throws Exception {
    mockMvc
        .perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put(
                    "/api/purchase-orders/po-id")
                .with(user(principal("admin", Role.ADMIN))))
        .andExpect(status().isMethodNotAllowed());
    mockMvc
        .perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch(
                    "/api/purchase-orders/po-id")
                .with(user(principal("admin", Role.ADMIN))))
        .andExpect(status().isMethodNotAllowed());
    mockMvc
        .perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete(
                    "/api/purchase-orders/po-id")
                .with(user(principal("admin", Role.ADMIN))))
        .andExpect(status().isMethodNotAllowed());
    verifyNoInteractions(service);
  }

  private String createJson() {
    return "{\"supplierId\":\"supplier-id\",\"warehouseId\":\"warehouse-id\",\"items\":[{\"productId\":\"product-id\",\"quantity\":1,\"unitCost\":\"0.10\"}]}";
  }

  private String receiptJson() {
    return "{\"clientRequestId\":\"key\",\"items\":[{\"purchaseOrderItemId\":\"item-id\",\"quantity\":1}]}";
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

  private PurchaseOrder order() {
    Supplier s = new Supplier("SUP-1", "Supplier", null, null, 2);
    ReflectionTestUtils.setField(s, "id", "supplier-id");
    Warehouse w = new Warehouse("WH-1", "Main", "Sydney");
    ReflectionTestUtils.setField(w, "id", "warehouse-id");
    Product p = new Product("SKU-1", "Widget", new BigDecimal("1.00"), 1);
    ReflectionTestUtils.setField(p, "id", "product-id");
    PurchaseOrder po = new PurchaseOrder("po-id", s, w, null);
    po.addItem(p, 1, new BigDecimal("0.10"), 1);
    ReflectionTestUtils.setField(po, "createdAt", java.time.Instant.parse("2026-01-01T00:00:00Z"));
    ReflectionTestUtils.setField(po, "updatedAt", java.time.Instant.parse("2026-01-01T00:00:00Z"));
    ReflectionTestUtils.setField(po, "version", 0L);
    return po;
  }
}
