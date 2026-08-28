package com.ivanfranchin.orderapi.rest;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ivanfranchin.orderapi.product.DuplicateSkuException;
import com.ivanfranchin.orderapi.product.Product;
import com.ivanfranchin.orderapi.product.ProductNotFoundException;
import com.ivanfranchin.orderapi.product.ProductService;
import com.ivanfranchin.orderapi.rest.dto.CreateProductRequest;
import com.ivanfranchin.orderapi.rest.dto.UpdateProductRequest;
import com.ivanfranchin.orderapi.security.SecurityConfig;
import com.ivanfranchin.orderapi.security.TokenProvider;
import java.math.BigDecimal;
import java.time.Instant;
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

@WebMvcTest(ProductController.class)
@Import(SecurityConfig.class)
class ProductControllerTest {

  @Autowired private MockMvc mockMvc;

  @Autowired private JsonMapper jsonMapper;

  @MockitoBean private ProductService productService;

  @MockitoBean private UserDetailsService userDetailsService;

  @MockitoBean private TokenProvider tokenProvider;

  @Test
  @WithMockUser(authorities = "USER")
  void getProducts_returnsDtosAsUser() throws Exception {
    when(productService.getProducts()).thenReturn(List.of(product()));

    mockMvc
        .perform(get("/api/products"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].id").value("product-id"))
        .andExpect(jsonPath("$[0].sku").value("SKU-1"))
        .andExpect(jsonPath("$[0].active").value(true));
  }

  @Test
  @WithMockUser(authorities = "ADMIN")
  void getProduct_returnsDtoAsAdmin() throws Exception {
    when(productService.validateAndGetProduct("product-id")).thenReturn(product());

    mockMvc
        .perform(get("/api/products/product-id"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Keyboard"))
        .andExpect(jsonPath("$.price").value(89.95));
  }

  @Test
  @WithMockUser(authorities = "USER")
  void getProduct_returns404WhenMissing() throws Exception {
    when(productService.validateAndGetProduct("missing"))
        .thenThrow(new ProductNotFoundException("Product with id missing not found"));

    mockMvc.perform(get("/api/products/missing")).andExpect(status().isNotFound());
  }

  @Test
  void getProducts_returns401WhenUnauthenticated() throws Exception {
    mockMvc.perform(get("/api/products")).andExpect(status().isUnauthorized());
  }

  @Test
  @WithMockUser(authorities = "ADMIN")
  void createProduct_returns201AsAdmin() throws Exception {
    CreateProductRequest request = createRequest();
    when(productService.createProduct(any(Product.class))).thenReturn(product());

    mockMvc
        .perform(
            post("/api/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(request)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.sku").value("SKU-1"));
  }

  @Test
  @WithMockUser(authorities = "ADMIN")
  void createProduct_returns409ForDuplicateSku() throws Exception {
    when(productService.createProduct(any(Product.class)))
        .thenThrow(new DuplicateSkuException("Product with SKU SKU-1 already exists"));

    mockMvc
        .perform(
            post("/api/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(createRequest())))
        .andExpect(status().isConflict());
  }

  @Test
  @WithMockUser(authorities = "USER")
  void createProduct_returns403AsUser() throws Exception {
    mockMvc
        .perform(
            post("/api/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(createRequest())))
        .andExpect(status().isForbidden());
  }

  @Test
  void createProduct_returns401WhenUnauthenticated() throws Exception {
    mockMvc
        .perform(
            post("/api/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(createRequest())))
        .andExpect(status().isUnauthorized());
  }

  @Test
  @WithMockUser(authorities = "ADMIN")
  void createProduct_rejectsInvalidFields() throws Exception {
    CreateProductRequest request = new CreateProductRequest(" ", "", new BigDecimal("-0.01"), -1);

    mockMvc
        .perform(
            post("/api/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(request)))
        .andExpect(status().isBadRequest());
  }

  @Test
  @WithMockUser(authorities = "ADMIN")
  void createProduct_rejectsMissingNumericFields() throws Exception {
    mockMvc
        .perform(
            post("/api/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"sku\":\"SKU-1\",\"name\":\"Keyboard\"}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  @WithMockUser(authorities = "ADMIN")
  void createProduct_rejectsPriceWithMoreThanTwoFractionalDigits() throws Exception {
    CreateProductRequest request =
        new CreateProductRequest("SKU-1", "Keyboard", new BigDecimal("12.999"), 10);

    mockMvc
        .perform(
            post("/api/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(request)))
        .andExpect(status().isBadRequest());
    verifyNoInteractions(productService);
  }

  @Test
  @WithMockUser(authorities = "ADMIN")
  void createProduct_rejectsOversizedSkuAndName() throws Exception {
    CreateProductRequest request =
        new CreateProductRequest(
            "s".repeat(Product.SKU_MAX_LENGTH + 1),
            "n".repeat(Product.NAME_MAX_LENGTH + 1),
            new BigDecimal("12.99"),
            10);

    mockMvc
        .perform(
            post("/api/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(request)))
        .andExpect(status().isBadRequest());
    verifyNoInteractions(productService);
  }

  @Test
  @WithMockUser(authorities = "ADMIN")
  void createProduct_acceptsPaddedSkuWhenNormalizedLengthIs64() throws Exception {
    String paddedSku = "  " + "a".repeat(Product.SKU_MAX_LENGTH) + "  ";
    CreateProductRequest request =
        new CreateProductRequest(paddedSku, "Keyboard", new BigDecimal("12.99"), 10);
    when(productService.createProduct(any(Product.class))).thenReturn(product());

    mockMvc
        .perform(
            post("/api/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(request)))
        .andExpect(status().isCreated());
  }

  @Test
  @WithMockUser(authorities = "ADMIN")
  void unicodeBoundaryWhitespaceIsAcceptedForCreateAndUpdate() throws Exception {
    when(productService.createProduct(any(Product.class))).thenReturn(product());
    when(productService.updateProduct(
            anyString(), anyString(), anyString(), any(BigDecimal.class), anyInt(), anyBoolean()))
        .thenReturn(product());

    for (String whitespace : List.of("\u2003", "\u00a0")) {
      String paddedSku = whitespace + "a".repeat(Product.SKU_MAX_LENGTH) + whitespace;
      CreateProductRequest createRequest =
          new CreateProductRequest(paddedSku, "Keyboard", new BigDecimal("12.99"), 10);
      UpdateProductRequest updateRequest =
          new UpdateProductRequest(paddedSku, "Keyboard", new BigDecimal("12.99"), 10, true);

      mockMvc
          .perform(
              post("/api/products")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(jsonMapper.writeValueAsString(createRequest)))
          .andExpect(status().isCreated());
      mockMvc
          .perform(
              put("/api/products/product-id")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(jsonMapper.writeValueAsString(updateRequest)))
          .andExpect(status().isOk());
    }
  }

  @Test
  @WithMockUser(authorities = "ADMIN")
  void supplementaryCodePointLimitAppliesEquallyToCreateAndUpdate() throws Exception {
    when(productService.createProduct(any(Product.class))).thenReturn(product());
    when(productService.updateProduct(
            anyString(), anyString(), anyString(), any(BigDecimal.class), anyInt(), anyBoolean()))
        .thenReturn(product());

    assertCreateAndUpdateSkuStatus("\ud83d\udce6".repeat(Product.SKU_MAX_LENGTH), 201, 200);
    assertCreateAndUpdateSkuStatus("\ud83d\udce6".repeat(Product.SKU_MAX_LENGTH + 1), 400, 400);
  }

  @Test
  @WithMockUser(authorities = "ADMIN")
  void mixedBmpAndSupplementarySkuIsCountedByCodePoint() throws Exception {
    when(productService.createProduct(any(Product.class))).thenReturn(product());
    when(productService.updateProduct(
            anyString(), anyString(), anyString(), any(BigDecimal.class), anyInt(), anyBoolean()))
        .thenReturn(product());

    assertCreateAndUpdateSkuStatus(
        "a".repeat(Product.SKU_MAX_LENGTH - 1) + "\ud83d\udce6", 201, 200);
    assertCreateAndUpdateSkuStatus("a".repeat(Product.SKU_MAX_LENGTH) + "\ud83d\udce6", 400, 400);
  }

  @Test
  @WithMockUser(authorities = "ADMIN")
  void createProduct_rejectsSkuWhenUppercaseNormalizedLengthExceeds64() throws Exception {
    String expandingSku = "a".repeat(Product.SKU_MAX_LENGTH - 1) + "ß";
    CreateProductRequest request =
        new CreateProductRequest(expandingSku, "Keyboard", new BigDecimal("12.99"), 10);

    mockMvc
        .perform(
            post("/api/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(request)))
        .andExpect(status().isBadRequest());
    verifyNoInteractions(productService);
  }

  @Test
  @WithMockUser(authorities = "ADMIN")
  void updateProduct_returns200AsAdmin() throws Exception {
    UpdateProductRequest request = updateRequest();
    Product updated = product();
    updated.setName("Updated Keyboard");
    when(productService.updateProduct(
            "product-id", "SKU-1", "Updated Keyboard", new BigDecimal("99.95"), 5, true))
        .thenReturn(updated);

    mockMvc
        .perform(
            put("/api/products/product-id")
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(request)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Updated Keyboard"));
  }

  @Test
  @WithMockUser(authorities = "USER")
  void updateProduct_returns403AsUser() throws Exception {
    mockMvc
        .perform(
            put("/api/products/product-id")
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(updateRequest())))
        .andExpect(status().isForbidden());
  }

  @Test
  @WithMockUser(authorities = "ADMIN")
  void updateProduct_rejectsMissingActive() throws Exception {
    String content = "{\"sku\":\"SKU-1\",\"name\":\"Keyboard\",\"price\":1,\"reorderPoint\":1}";

    mockMvc
        .perform(
            put("/api/products/product-id")
                .contentType(MediaType.APPLICATION_JSON)
                .content(content))
        .andExpect(status().isBadRequest());
  }

  @Test
  @WithMockUser(authorities = "ADMIN")
  void updateProduct_rejectsInvalidFields() throws Exception {
    UpdateProductRequest request =
        new UpdateProductRequest("", " ", new BigDecimal("-1.00"), -1, true);

    mockMvc
        .perform(
            put("/api/products/product-id")
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(request)))
        .andExpect(status().isBadRequest());
  }

  @Test
  @WithMockUser(authorities = "ADMIN")
  void updateProduct_rejectsPriceWithMoreThanTwoFractionalDigits() throws Exception {
    UpdateProductRequest request =
        new UpdateProductRequest("SKU-1", "Keyboard", new BigDecimal("12.999"), 10, true);

    mockMvc
        .perform(
            put("/api/products/product-id")
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(request)))
        .andExpect(status().isBadRequest());
    verifyNoInteractions(productService);
  }

  @Test
  @WithMockUser(authorities = "ADMIN")
  void updateProduct_rejectsOversizedSkuAndName() throws Exception {
    UpdateProductRequest request =
        new UpdateProductRequest(
            "s".repeat(Product.SKU_MAX_LENGTH + 1),
            "n".repeat(Product.NAME_MAX_LENGTH + 1),
            new BigDecimal("12.99"),
            10,
            true);

    mockMvc
        .perform(
            put("/api/products/product-id")
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(request)))
        .andExpect(status().isBadRequest());
    verifyNoInteractions(productService);
  }

  @Test
  @WithMockUser(authorities = "ADMIN")
  void updateProduct_acceptsPaddedSkuWhenNormalizedLengthIs64() throws Exception {
    String paddedSku = "  " + "a".repeat(Product.SKU_MAX_LENGTH) + "  ";
    UpdateProductRequest request =
        new UpdateProductRequest(paddedSku, "Keyboard", new BigDecimal("12.99"), 10, true);
    when(productService.updateProduct(
            anyString(), anyString(), anyString(), any(BigDecimal.class), anyInt(), anyBoolean()))
        .thenReturn(product());

    mockMvc
        .perform(
            put("/api/products/product-id")
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(request)))
        .andExpect(status().isOk());
  }

  @Test
  @WithMockUser(authorities = "ADMIN")
  void updateProduct_rejectsSkuWhenUppercaseNormalizedLengthExceeds64() throws Exception {
    String expandingSku = "a".repeat(Product.SKU_MAX_LENGTH - 1) + "ß";
    UpdateProductRequest request =
        new UpdateProductRequest(expandingSku, "Keyboard", new BigDecimal("12.99"), 10, true);

    mockMvc
        .perform(
            put("/api/products/product-id")
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(request)))
        .andExpect(status().isBadRequest());
    verifyNoInteractions(productService);
  }

  @Test
  @WithMockUser(authorities = "ADMIN")
  void deactivateProduct_returns204AsAdmin() throws Exception {
    mockMvc.perform(delete("/api/products/product-id")).andExpect(status().isNoContent());
  }

  @Test
  @WithMockUser(authorities = "USER")
  void deactivateProduct_returns403AsUser() throws Exception {
    mockMvc.perform(delete("/api/products/product-id")).andExpect(status().isForbidden());
  }

  @Test
  void deactivateProduct_returns401WhenUnauthenticated() throws Exception {
    mockMvc.perform(delete("/api/products/product-id")).andExpect(status().isUnauthorized());
  }

  private Product product() {
    Product product = new Product("SKU-1", "Keyboard", new BigDecimal("89.95"), 10);
    product.setId("product-id");
    product.setCreatedAt(Instant.parse("2026-01-01T00:00:00Z"));
    product.setUpdatedAt(Instant.parse("2026-01-01T00:00:00Z"));
    return product;
  }

  private void assertCreateAndUpdateSkuStatus(String sku, int createStatus, int updateStatus)
      throws Exception {
    CreateProductRequest createRequest =
        new CreateProductRequest(sku, "Keyboard", new BigDecimal("12.99"), 10);
    UpdateProductRequest updateRequest =
        new UpdateProductRequest(sku, "Keyboard", new BigDecimal("12.99"), 10, true);

    mockMvc
        .perform(
            post("/api/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(createRequest)))
        .andExpect(status().is(createStatus));
    mockMvc
        .perform(
            put("/api/products/product-id")
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(updateRequest)))
        .andExpect(status().is(updateStatus));
  }

  private CreateProductRequest createRequest() {
    return new CreateProductRequest("SKU-1", "Keyboard", new BigDecimal("89.95"), 10);
  }

  private UpdateProductRequest updateRequest() {
    return new UpdateProductRequest("SKU-1", "Updated Keyboard", new BigDecimal("99.95"), 5, true);
  }
}
