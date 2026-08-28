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

import com.ivanfranchin.orderapi.rest.dto.CreateWarehouseRequest;
import com.ivanfranchin.orderapi.rest.dto.UpdateWarehouseRequest;
import com.ivanfranchin.orderapi.security.SecurityConfig;
import com.ivanfranchin.orderapi.security.TokenProvider;
import com.ivanfranchin.orderapi.warehouse.Warehouse;
import com.ivanfranchin.orderapi.warehouse.WarehouseService;
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

@WebMvcTest(WarehouseController.class)
@Import(SecurityConfig.class)
class WarehouseControllerTest {
  @Autowired MockMvc mockMvc;
  @Autowired JsonMapper jsonMapper;
  @MockitoBean WarehouseService service;
  @MockitoBean UserDetailsService userDetailsService;
  @MockitoBean TokenProvider tokenProvider;

  @Test
  @WithMockUser(authorities = "USER")
  void authenticatedUserCanRead() throws Exception {
    when(service.getWarehouses()).thenReturn(List.of(savedWarehouse()));
    mockMvc
        .perform(get("/api/warehouses"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].warehouseCode").value("WAREHOUSE-1"));
  }

  @Test
  void unauthenticatedReadReturns401() throws Exception {
    mockMvc.perform(get("/api/warehouses")).andExpect(status().isUnauthorized());
  }

  @Test
  @WithMockUser(authorities = "ADMIN")
  void adminCanCreateUpdateAndDeactivate() throws Exception {
    when(service.createWarehouse(any(Warehouse.class))).thenReturn(savedWarehouse());
    when(service.updateWarehouse(anyString(), any(Warehouse.class))).thenReturn(savedWarehouse());
    mockMvc
        .perform(
            post("/api/warehouses")
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(createRequest("warehouse-1", "Melbourne"))))
        .andExpect(status().isCreated());
    mockMvc
        .perform(
            put("/api/warehouses/id")
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(updateRequest("warehouse-1", "Sydney"))))
        .andExpect(status().isOk());
    mockMvc.perform(delete("/api/warehouses/id")).andExpect(status().isNoContent());
    verify(service).deactivateWarehouse("id");
  }

  @Test
  @WithMockUser(authorities = "USER")
  void userCannotWrite() throws Exception {
    mockMvc
        .perform(
            post("/api/warehouses")
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(createRequest("warehouse-1", "Melbourne"))))
        .andExpect(status().isForbidden());
    mockMvc
        .perform(
            put("/api/warehouses/id")
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(updateRequest("warehouse-1", "Melbourne"))))
        .andExpect(status().isForbidden());
    mockMvc.perform(delete("/api/warehouses/id")).andExpect(status().isForbidden());
  }

  @Test
  @WithMockUser(authorities = "ADMIN")
  void locationBoundaryIsEnforcedForCreateAndUpdate() throws Exception {
    String oversized = "x".repeat(Warehouse.LOCATION_MAX_LENGTH + 1);
    mockMvc
        .perform(
            post("/api/warehouses")
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(createRequest("warehouse-1", oversized))))
        .andExpect(status().isBadRequest());
    mockMvc
        .perform(
            put("/api/warehouses/id")
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(updateRequest("warehouse-1", oversized))))
        .andExpect(status().isBadRequest());
    verifyNoInteractions(service);
  }

  @Test
  @WithMockUser(authorities = "ADMIN")
  void normalizedCodeRulesApplyToCreateAndUpdate() throws Exception {
    String paddedValid = "  " + "a".repeat(64) + "  ";
    when(service.createWarehouse(any(Warehouse.class))).thenReturn(savedWarehouse());
    when(service.updateWarehouse(anyString(), any(Warehouse.class))).thenReturn(savedWarehouse());
    mockMvc
        .perform(
            post("/api/warehouses")
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(createRequest(paddedValid, "Melbourne"))))
        .andExpect(status().isCreated());
    mockMvc
        .perform(
            put("/api/warehouses/id")
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(updateRequest(paddedValid, "Melbourne"))))
        .andExpect(status().isOk());
  }

  @Test
  @WithMockUser(authorities = "ADMIN")
  void unicodeBoundaryWhitespaceIsAcceptedForCreateAndUpdate() throws Exception {
    when(service.createWarehouse(any(Warehouse.class))).thenReturn(savedWarehouse());
    when(service.updateWarehouse(anyString(), any(Warehouse.class))).thenReturn(savedWarehouse());

    for (String whitespace : List.of("\u2003", "\u00a0")) {
      String paddedCode = whitespace + "a".repeat(Warehouse.CODE_MAX_LENGTH) + whitespace;
      mockMvc
          .perform(
              post("/api/warehouses")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(jsonMapper.writeValueAsString(createRequest(paddedCode, "Melbourne"))))
          .andExpect(status().isCreated());
      mockMvc
          .perform(
              put("/api/warehouses/id")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(jsonMapper.writeValueAsString(updateRequest(paddedCode, "Melbourne"))))
          .andExpect(status().isOk());
    }
  }

  @Test
  @WithMockUser(authorities = "ADMIN")
  void supplementaryCodePointLimitAppliesEquallyToCreateAndUpdate() throws Exception {
    when(service.createWarehouse(any(Warehouse.class))).thenReturn(savedWarehouse());
    when(service.updateWarehouse(anyString(), any(Warehouse.class))).thenReturn(savedWarehouse());

    assertCreateAndUpdateCodeStatus("\ud83d\udce6".repeat(Warehouse.CODE_MAX_LENGTH), 201, 200);
    assertCreateAndUpdateCodeStatus("\ud83d\udce6".repeat(Warehouse.CODE_MAX_LENGTH + 1), 400, 400);
  }

  @Test
  @WithMockUser(authorities = "ADMIN")
  void mixedBmpAndSupplementaryCodeIsCountedByCodePoint() throws Exception {
    when(service.createWarehouse(any(Warehouse.class))).thenReturn(savedWarehouse());
    when(service.updateWarehouse(anyString(), any(Warehouse.class))).thenReturn(savedWarehouse());

    assertCreateAndUpdateCodeStatus(
        "a".repeat(Warehouse.CODE_MAX_LENGTH - 1) + "\ud83d\udce6", 201, 200);
    assertCreateAndUpdateCodeStatus(
        "a".repeat(Warehouse.CODE_MAX_LENGTH) + "\ud83d\udce6", 400, 400);
  }

  @Test
  @WithMockUser(authorities = "ADMIN")
  void unicodeExpansionReturns400ForCreateAndUpdate() throws Exception {
    String expanding = "a".repeat(63) + "ß";
    mockMvc
        .perform(
            post("/api/warehouses")
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(createRequest(expanding, "Melbourne"))))
        .andExpect(status().isBadRequest());
    mockMvc
        .perform(
            put("/api/warehouses/id")
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(updateRequest(expanding, "Melbourne"))))
        .andExpect(status().isBadRequest());
    verifyNoInteractions(service);
  }

  private CreateWarehouseRequest createRequest(String code, String location) {
    return new CreateWarehouseRequest(code, "Warehouse", location);
  }

  private void assertCreateAndUpdateCodeStatus(String code, int createStatus, int updateStatus)
      throws Exception {
    mockMvc
        .perform(
            post("/api/warehouses")
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(createRequest(code, "Melbourne"))))
        .andExpect(status().is(createStatus));
    mockMvc
        .perform(
            put("/api/warehouses/id")
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(updateRequest(code, "Melbourne"))))
        .andExpect(status().is(updateStatus));
  }

  private UpdateWarehouseRequest updateRequest(String code, String location) {
    return new UpdateWarehouseRequest(code, "Warehouse", location, true);
  }

  private Warehouse savedWarehouse() {
    Warehouse warehouse = new Warehouse("WAREHOUSE-1", "Warehouse", "Melbourne");
    warehouse.setId("id");
    warehouse.onPrePersist();
    return warehouse;
  }
}
