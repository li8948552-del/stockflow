package com.ivanfranchin.orderapi.rest;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ivanfranchin.orderapi.rest.dto.CreateSupplierRequest;
import com.ivanfranchin.orderapi.rest.dto.UpdateSupplierRequest;
import com.ivanfranchin.orderapi.security.SecurityConfig;
import com.ivanfranchin.orderapi.security.TokenProvider;
import com.ivanfranchin.orderapi.supplier.Supplier;
import com.ivanfranchin.orderapi.supplier.SupplierService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;

@WebMvcTest(SupplierController.class)
@Import(SecurityConfig.class)
class SupplierControllerTest {
  @Autowired MockMvc mockMvc;
  @Autowired JsonMapper jsonMapper;
  @MockitoBean SupplierService service;
  @MockitoBean UserDetailsService userDetailsService;
  @MockitoBean TokenProvider tokenProvider;

  @Test
  @WithMockUser(authorities = "USER")
  void authenticatedUserCanRead() throws Exception {
    when(service.getSuppliers()).thenReturn(List.of(savedSupplier()));
    mockMvc
        .perform(get("/api/suppliers"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].supplierCode").value("SUPPLIER-1"));
  }

  @Test
  void unauthenticatedReadReturns401() throws Exception {
    mockMvc.perform(get("/api/suppliers")).andExpect(status().isUnauthorized());
  }

  @Test
  @WithMockUser(authorities = "ADMIN")
  void adminCanCreateUpdateAndDeactivate() throws Exception {
    when(service.createSupplier(any(Supplier.class))).thenReturn(savedSupplier());
    when(service.updateSupplier(anyString(), any(Supplier.class))).thenReturn(savedSupplier());
    mockMvc
        .perform(
            post("/api/suppliers")
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(createRequest("supplier-1", 0))))
        .andExpect(status().isCreated());
    mockMvc
        .perform(
            put("/api/suppliers/id")
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(updateRequest("supplier-1", 3650))))
        .andExpect(status().isOk());
    mockMvc.perform(delete("/api/suppliers/id")).andExpect(status().isNoContent());
    verify(service).deactivateSupplier("id");
  }

  @Test
  @WithMockUser(authorities = "USER")
  void userCannotWrite() throws Exception {
    mockMvc
        .perform(
            post("/api/suppliers")
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(createRequest("supplier-1", 1))))
        .andExpect(status().isForbidden());
    mockMvc
        .perform(
            put("/api/suppliers/id")
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(updateRequest("supplier-1", 1))))
        .andExpect(status().isForbidden());
    mockMvc.perform(delete("/api/suppliers/id")).andExpect(status().isForbidden());
  }

  @Test
  @WithMockUser(authorities = "ADMIN")
  void invalidEmailAndLeadTimeReturn400() throws Exception {
    CreateSupplierRequest request =
        new CreateSupplierRequest("supplier-1", "Supplier", "invalid", null, 3651);
    mockMvc
        .perform(
            post("/api/suppliers")
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(request)))
        .andExpect(status().isBadRequest());
    verifyNoInteractions(service);
  }

  @Test
  @WithMockUser(authorities = "ADMIN")
  void optionalEmailMayBeNullAndLeadTimeBoundariesAreAccepted() throws Exception {
    when(service.createSupplier(any(Supplier.class))).thenReturn(savedSupplier());
    mockMvc
        .perform(
            post("/api/suppliers")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    jsonMapper.writeValueAsString(
                        new CreateSupplierRequest("supplier-1", "Supplier", null, null, 0))))
        .andExpect(status().isCreated());
  }

  @Test
  @WithMockUser(authorities = "ADMIN")
  void normalizedCodeRulesApplyToCreateAndUpdate() throws Exception {
    String paddedValid = "  " + "a".repeat(64) + "  ";
    when(service.createSupplier(any(Supplier.class))).thenReturn(savedSupplier());
    when(service.updateSupplier(anyString(), any(Supplier.class))).thenReturn(savedSupplier());
    mockMvc
        .perform(
            post("/api/suppliers")
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(createRequest(paddedValid, 1))))
        .andExpect(status().isCreated());
    mockMvc
        .perform(
            put("/api/suppliers/id")
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(updateRequest(paddedValid, 1))))
        .andExpect(status().isOk());
  }

  @Test
  @WithMockUser(authorities = "ADMIN")
  void unicodeBoundaryWhitespaceIsAcceptedForCreateAndUpdate() throws Exception {
    when(service.createSupplier(any(Supplier.class))).thenReturn(savedSupplier());
    when(service.updateSupplier(anyString(), any(Supplier.class))).thenReturn(savedSupplier());

    for (String whitespace : List.of("\u2003", "\u00a0")) {
      String paddedCode = whitespace + "a".repeat(Supplier.CODE_MAX_LENGTH) + whitespace;
      mockMvc
          .perform(
              post("/api/suppliers")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(jsonMapper.writeValueAsString(createRequest(paddedCode, 1))))
          .andExpect(status().isCreated());
      mockMvc
          .perform(
              put("/api/suppliers/id")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(jsonMapper.writeValueAsString(updateRequest(paddedCode, 1))))
          .andExpect(status().isOk());
    }
  }

  @Test
  @WithMockUser(authorities = "ADMIN")
  void supplementaryCodePointLimitAppliesEquallyToCreateAndUpdate() throws Exception {
    when(service.createSupplier(any(Supplier.class))).thenReturn(savedSupplier());
    when(service.updateSupplier(anyString(), any(Supplier.class))).thenReturn(savedSupplier());

    assertCreateAndUpdateCodeStatus("\ud83d\udce6".repeat(Supplier.CODE_MAX_LENGTH), 201, 200);
    assertCreateAndUpdateCodeStatus("\ud83d\udce6".repeat(Supplier.CODE_MAX_LENGTH + 1), 400, 400);
  }

  @Test
  @WithMockUser(authorities = "ADMIN")
  void mixedBmpAndSupplementaryCodeIsCountedByCodePoint() throws Exception {
    when(service.createSupplier(any(Supplier.class))).thenReturn(savedSupplier());
    when(service.updateSupplier(anyString(), any(Supplier.class))).thenReturn(savedSupplier());

    assertCreateAndUpdateCodeStatus(
        "a".repeat(Supplier.CODE_MAX_LENGTH - 1) + "\ud83d\udce6", 201, 200);
    assertCreateAndUpdateCodeStatus(
        "a".repeat(Supplier.CODE_MAX_LENGTH) + "\ud83d\udce6", 400, 400);
  }

  @Test
  @WithMockUser(authorities = "ADMIN")
  void unicodeExpansionReturns400ForCreateAndUpdate() throws Exception {
    String expanding = "a".repeat(63) + "ß";
    mockMvc
        .perform(
            post("/api/suppliers")
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(createRequest(expanding, 1))))
        .andExpect(status().isBadRequest());
    mockMvc
        .perform(
            put("/api/suppliers/id")
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(updateRequest(expanding, 1))))
        .andExpect(status().isBadRequest());
    verifyNoInteractions(service);
  }

  private CreateSupplierRequest createRequest(String code, int leadTime) {
    return new CreateSupplierRequest(code, "Supplier", "a@example.com", "+61", leadTime);
  }

  private void assertCreateAndUpdateCodeStatus(String code, int createStatus, int updateStatus)
      throws Exception {
    mockMvc
        .perform(
            post("/api/suppliers")
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(createRequest(code, 1))))
        .andExpect(status().is(createStatus));
    mockMvc
        .perform(
            put("/api/suppliers/id")
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(updateRequest(code, 1))))
        .andExpect(status().is(updateStatus));
  }

  private UpdateSupplierRequest updateRequest(String code, int leadTime) {
    return new UpdateSupplierRequest(code, "Supplier", "a@example.com", "+61", leadTime, true);
  }

  private Supplier savedSupplier() {
    Supplier supplier = new Supplier("SUPPLIER-1", "Supplier", "a@example.com", "+61", 1);
    supplier.setId("id");
    supplier.onPrePersist();
    return supplier;
  }
}
