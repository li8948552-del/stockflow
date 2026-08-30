package com.ivanfranchin.orderapi.procurement;

import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GoodsReceiptRepository extends JpaRepository<GoodsReceipt, String> {
  @EntityGraph(
      attributePaths = {"items", "items.purchaseOrderItem", "items.purchaseOrderItem.product"})
  Optional<GoodsReceipt> findByPurchaseOrderIdAndClientRequestId(String poId, String key);
}
