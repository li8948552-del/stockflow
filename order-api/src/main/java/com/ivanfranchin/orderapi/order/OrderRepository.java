package com.ivanfranchin.orderapi.order;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrderRepository extends JpaRepository<Order, String> {
  @EntityGraph(attributePaths = {"user", "warehouse", "items", "items.product"})
  @Query(
      """
      select distinct o from Order o
      where (:username is null or o.user.username = :username)
        and (:userId is null or o.user.id = :userId)
        and (:status is null or o.status = :status)
        and (:warehouseId is null or o.warehouse.id = :warehouseId)
      order by o.createdAt desc, o.id desc
      """)
  List<Order> findOrders(
      @Param("username") String username,
      @Param("userId") Long userId,
      @Param("status") OrderStatus status,
      @Param("warehouseId") String warehouseId);

  @EntityGraph(attributePaths = {"user", "warehouse", "items", "items.product"})
  @Query("select o from Order o where o.id = :id")
  Optional<Order> findDetailedById(@Param("id") String id);

  boolean existsByUserId(Long userId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select o from Order o where o.id = :id")
  Optional<Order> findByIdForUpdate(@Param("id") String id);

  @Query(
      "select o.id from Order o where o.status = :status and o.expiresAt <= :now order by o.expiresAt asc, o.id asc")
  List<String> findExpiredIds(
      @Param("status") OrderStatus status, @Param("now") Instant now, Pageable pageable);
}
