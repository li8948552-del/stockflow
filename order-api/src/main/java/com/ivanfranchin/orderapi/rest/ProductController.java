package com.ivanfranchin.orderapi.rest;

import static com.ivanfranchin.orderapi.config.SwaggerConfig.BEARER_KEY_SECURITY_SCHEME;

import com.ivanfranchin.orderapi.product.Product;
import com.ivanfranchin.orderapi.product.ProductService;
import com.ivanfranchin.orderapi.rest.dto.CreateProductRequest;
import com.ivanfranchin.orderapi.rest.dto.ProductDto;
import com.ivanfranchin.orderapi.rest.dto.UpdateProductRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/products")
public class ProductController {

  private final ProductService productService;

  @Operation(security = {@SecurityRequirement(name = BEARER_KEY_SECURITY_SCHEME)})
  @GetMapping
  public List<ProductDto> getProducts() {
    return productService.getProducts().stream().map(ProductDto::from).toList();
  }

  @Operation(security = {@SecurityRequirement(name = BEARER_KEY_SECURITY_SCHEME)})
  @GetMapping("/{id}")
  public ProductDto getProduct(@PathVariable String id) {
    return ProductDto.from(productService.validateAndGetProduct(id));
  }

  @Operation(security = {@SecurityRequirement(name = BEARER_KEY_SECURITY_SCHEME)})
  @ResponseStatus(HttpStatus.CREATED)
  @PostMapping
  public ProductDto createProduct(@Valid @RequestBody CreateProductRequest request) {
    return ProductDto.from(productService.createProduct(request.toDomain()));
  }

  @Operation(security = {@SecurityRequirement(name = BEARER_KEY_SECURITY_SCHEME)})
  @PutMapping("/{id}")
  public ProductDto updateProduct(
      @PathVariable String id, @Valid @RequestBody UpdateProductRequest request) {
    Product product =
        productService.updateProduct(
            id,
            request.sku(),
            request.name(),
            request.price(),
            request.reorderPoint(),
            request.active());
    return ProductDto.from(product);
  }

  @Operation(security = {@SecurityRequirement(name = BEARER_KEY_SECURITY_SCHEME)})
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @DeleteMapping("/{id}")
  public void deactivateProduct(@PathVariable String id) {
    productService.deactivateProduct(id);
  }
}
