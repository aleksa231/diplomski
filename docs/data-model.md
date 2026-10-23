# Data Model & Schema Design: NoSQL vs Relational

This document outlines the domain schema, design decisions, and trade-offs between MongoDB (Document model) and PostgreSQL (Normalized EAV and JSONB models).

---

## 1. Product Schema & Embedding vs. Referencing

### MongoDB Document Model
In MongoDB, each product is stored as a single cohesive document in the `products` collection:
```json
{
  "_id": "9f1c...-uuid",
  "sku": "LAP-000123",
  "name": "Aurora 15 Pro",
  "category": "laptops",
  "price": 1299.90,
  "stock": 42,
  "attributes": {
    "cpu": "8-core",
    "ramGb": 16,
    "storageGb": 512,
    "gpu": "integrated"
  },
  "reviews": [
    { "rating": 5, "text": "Superb performance", "createdAt": "2026-01-02T10:00:00Z" }
  ],
  "reviewCount": 7,
  "ratingAvg": 4.3,
  "createdAt": "2026-01-01T00:00:00Z",
  "updatedAt": "2026-01-02T10:00:00Z"
}
```

#### Why Embed the 20 Most Recent Reviews?
1. **Locality of Reference**: 99% of e-commerce product detail page views display the product details alongside the top/most recent reviews. Embedding avoids an expensive second query or join.
2. **BSON 16MB Document Limit & Unbounded Growth**: Storing an unbounded array of reviews inside a product document risks document growth beyond the 16MB limit, causes frequent document reallocation/fragmentation on disk, and degrades read latency as review counts grow into the thousands.
3. **The Hybrid Capped Pattern**: By embedding only the `20` most recent reviews (using `$slice: -20` during updates) and keeping `reviewCount` and `ratingAvg` denormalized at the root document level, we achieve single-read low-latency performance while bounding document size to a predictable, small footprint (< 4 KB).
4. **Historical Reviews**: If review history beyond the latest 20 is requested, an overflow `reviews` collection can be referenced by `productId`.

---

## 2. Dynamic Attribute Flexibility

### Five Target Categories & Attributes
1. **laptops**: `cpu`, `ramGb` (number), `storageGb` (number), `screenInch` (number), `gpu`, `weightKg` (number)
2. **phones**: `os`, `ramGb` (number), `storageGb` (number), `batteryMah` (number), `cameraMp` (number), `fiveG` (boolean)
3. **clothing**: `size`, `color`, `material`, `gender`, `season`
4. **groceries**: `weightG` (number), `organic` (boolean), `expiryDays` (number), `origin`, `allergens` (array of strings)
5. **books**: `author`, `pages` (number), `language`, `isbn`, `genre`

### Document vs. Relational Approaches
- **MongoDB**: Attributes reside as a nested JSON object (`attributes: { ... }`). Dynamic filtering is native (`attributes.ramGb: 16`, `attributes.organic: true`). Adding new categories or attributes requires zero schema alterations or downtime.
- **PostgreSQL Normalized (EAV)**: Uses `attribute_definition` and `product_attribute_value` tables. Preserves strict relational integrity and type validation, but queries with multiple attribute filters require multiple joins or `GROUP BY ... HAVING` intersections, substantially increasing query complexity and query planner overhead.
- **PostgreSQL JSONB**: Stores attributes in a `JSONB` column with a GIN index (`CREATE INDEX idx_products_jsonb_attrs ON product_jsonb USING gin (attributes)`). Provides document-like schema flexibility within PostgreSQL, queryable via `@>` and `->>`.

---

## 3. Order Model & Atomicity

### MongoDB
Orders embed line items with immutable price snapshots:
```json
{
  "_id": "order-uuid",
  "customerRef": "cust-8472",
  "items": [
    { "productId": "9f1c...", "productName": "Aurora 15 Pro", "price": 1299.90, "quantity": 1 }
  ],
  "total": 1299.90,
  "status": "CREATED",
  "statusHistory": [
    { "status": "CREATED", "timestamp": "2026-01-02T10:15:00Z" }
  ],
  "createdAt": "2026-01-02T10:15:00Z"
}
```
- Stock decrement uses conditional update: `findAndModify({ _id: productId, stock: { $gte: qty } }, { $inc: { stock: -qty } })`.
- When `app.orders.transactions=true`, MongoDB multi-document transactions coordinate the order creation and stock decrements atomically across replica set nodes.

### PostgreSQL
Normalized tables `orders`, `order_item`, and `order_status_history` enforced via standard foreign keys and ACID transactions (`READ COMMITTED` or `REPEATABLE READ`). Stock decrement is executed via conditional update `UPDATE product SET stock = stock - ? WHERE id = ? AND stock >= ?`.
