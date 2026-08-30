CREATE TABLE purchase_orders (
    id VARCHAR(255) PRIMARY KEY,
    supplier_id VARCHAR(255) NOT NULL,
    warehouse_id VARCHAR(255) NOT NULL,
    status VARCHAR(24) NOT NULL,
    total_amount NUMERIC(19,2) NOT NULL,
    expected_delivery_date DATE,
    submitted_at TIMESTAMP WITH TIME ZONE,
    cancelled_at TIMESTAMP WITH TIME ZONE,
    completed_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    version BIGINT NOT NULL,
    CONSTRAINT fk_purchase_orders_supplier FOREIGN KEY (supplier_id) REFERENCES suppliers(id),
    CONSTRAINT fk_purchase_orders_warehouse FOREIGN KEY (warehouse_id) REFERENCES warehouses(id),
    CONSTRAINT ck_purchase_orders_status CHECK (status IN ('DRAFT','SUBMITTED','PARTIALLY_RECEIVED','RECEIVED','CANCELLED')),
    CONSTRAINT ck_purchase_orders_total_nonnegative CHECK (total_amount >= 0)
);
CREATE TABLE purchase_order_items (
    id VARCHAR(255) PRIMARY KEY,
    purchase_order_id VARCHAR(255) NOT NULL,
    product_id VARCHAR(255) NOT NULL,
    ordered_quantity BIGINT NOT NULL,
    received_quantity BIGINT NOT NULL,
    unit_cost NUMERIC(19,2) NOT NULL,
    line_total NUMERIC(19,2) NOT NULL,
    line_number INTEGER NOT NULL,
    CONSTRAINT fk_purchase_items_order FOREIGN KEY (purchase_order_id) REFERENCES purchase_orders(id),
    CONSTRAINT fk_purchase_items_product FOREIGN KEY (product_id) REFERENCES products(id),
    CONSTRAINT uk_purchase_items_order_product UNIQUE (purchase_order_id,product_id),
    CONSTRAINT uk_purchase_items_order_line UNIQUE (purchase_order_id,line_number),
    CONSTRAINT ck_purchase_items_line_positive CHECK (line_number > 0),
    CONSTRAINT ck_purchase_items_ordered_positive CHECK (ordered_quantity > 0),
    CONSTRAINT ck_purchase_items_received_range CHECK (received_quantity >= 0 AND received_quantity <= ordered_quantity),
    CONSTRAINT ck_purchase_items_cost_nonnegative CHECK (unit_cost >= 0 AND line_total >= 0)
);
CREATE TABLE goods_receipts (
    id VARCHAR(255) PRIMARY KEY,
    purchase_order_id VARCHAR(255) NOT NULL,
    client_request_id VARCHAR(128) NOT NULL,
    received_by_username VARCHAR(255) NOT NULL,
    received_at TIMESTAMP WITH TIME ZONE NOT NULL,
    payload_hash VARCHAR(64) NOT NULL,
    CONSTRAINT fk_goods_receipts_order FOREIGN KEY (purchase_order_id) REFERENCES purchase_orders(id),
    CONSTRAINT uk_goods_receipts_order_request UNIQUE (purchase_order_id,client_request_id)
);
CREATE TABLE goods_receipt_items (
    id VARCHAR(255) PRIMARY KEY,
    goods_receipt_id VARCHAR(255) NOT NULL,
    purchase_order_item_id VARCHAR(255) NOT NULL,
    quantity BIGINT NOT NULL,
    before_on_hand BIGINT NOT NULL,
    after_on_hand BIGINT NOT NULL,
    line_number INTEGER NOT NULL,
    CONSTRAINT fk_goods_receipt_items_receipt FOREIGN KEY (goods_receipt_id) REFERENCES goods_receipts(id),
    CONSTRAINT fk_goods_receipt_items_order_item FOREIGN KEY (purchase_order_item_id) REFERENCES purchase_order_items(id),
    CONSTRAINT uk_goods_receipt_items_line UNIQUE (goods_receipt_id,line_number),
    CONSTRAINT ck_goods_receipt_items_quantity_positive CHECK (quantity > 0),
    CONSTRAINT ck_goods_receipt_items_on_hand_nonnegative CHECK (before_on_hand >= 0 AND after_on_hand >= 0)
);
CREATE INDEX ix_purchase_orders_supplier ON purchase_orders(supplier_id);
CREATE INDEX ix_purchase_orders_warehouse ON purchase_orders(warehouse_id);
CREATE INDEX ix_purchase_orders_status ON purchase_orders(status,created_at,id);
CREATE INDEX ix_purchase_items_order ON purchase_order_items(purchase_order_id);
CREATE INDEX ix_purchase_items_product ON purchase_order_items(product_id);
CREATE INDEX ix_goods_receipts_order_time ON goods_receipts(purchase_order_id,received_at,id);
CREATE INDEX ix_goods_receipts_client_key ON goods_receipts(client_request_id);
