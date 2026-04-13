# Sales Record Order Archive Design

**Goal:** Turn sales records into order-level archives that are auto-generated when an order is completed, while preserving full multi-product detail in a read-only detail dialog.

**Current State**
- The sales record page reads from `/api/sales/records`.
- `sales_records` currently stores one row per manually-created product sale.
- Order completion does not generate any sales record.
- Orders only persist `createTime` and `approveTime`; ship and completion timestamps exist only implicitly in logs.

**Problems**
- Completing an order does not populate the sales record page.
- Multi-product orders cannot be represented as one readable sales record without losing product detail.
- The sales record UI mixes manual CRUD with what should become a system-generated archive.

**Design**

## 1. Order Timeline Fields
- Keep `createTime` as the order date.
- Add `shipTime` to `Order`.
- Add `completeTime` to `Order`.
- Write `shipTime` during `shipOrder`.
- Write `completeTime` during `completeOrder`.

This makes order lifecycle dates explicit and reusable by both the order detail view and the sales archive.

## 2. Sales Record Data Model
- Keep using the `sales_records` collection.
- Upgrade each record from product-level storage to order-level storage.
- Add the following fields:
  - `orderId`
  - `orderNo`
  - `productCount`
  - `totalQuantity`
  - `orderDate`
  - `shipDate`
  - `completeDate`
  - `items`
- `items` is an embedded list that stores one object per order line:
  - `productId`
  - `productCode`
  - `productName`
  - `specification`
  - `quantity`
  - `unitPrice`
  - `amount`

Each completed order produces exactly one sales record that contains all product lines.

## 3. Auto-Generation Rule
- Trigger sales record generation inside `OrderServiceImpl.completeOrder`.
- Generate the sales record only after the order status successfully transitions from `SHIPPED` to `COMPLETED`.
- Prevent duplicates by checking whether a sales record already exists for the same `orderId`.

This keeps the sales archive aligned with fulfilled business outcomes rather than intermediate workflow states.

## 4. Sales Record UI
- Convert the sales record page into a read-only archive view.
- Remove manual create/edit/delete controls from the page.
- Keep the existing page structure and theme based on Element Plus cards, tables, spacing, and the current blue/white/gray admin palette.
- Change the list columns to order-level summary fields:
  - customer name
  - order number
  - product count
  - total quantity
  - total amount
  - order date
  - complete date
  - creator
  - action

The action column becomes a single `查看明细` entry.

## 5. Detail Dialog
- Open a dialog from the list page instead of navigating away.
- The dialog shows:
  - summary section for order number, customer, totals, order date, ship date, complete date, creator
  - a product detail table for all embedded `items`
- Styling should feel like an upgraded version of the current page:
  - clean summary blocks
  - highlighted amount values using the project’s existing warm accent treatment
  - consistent typography and spacing with the existing admin pages

## 6. Testing
- Add backend tests for:
  - completing an order writes `completeTime`
  - completing an order creates one sales record with all order items
  - completing the same order path does not generate duplicate sales records
- Keep frontend verification to build validation after the page refactor.

**Recommendation**
- Implement the archive as a focused extension of the existing `sales_records` module instead of introducing a parallel data source.
- Leave the backend sales CRUD endpoints untouched for now unless product rules later require hard enforcement at the API level.
