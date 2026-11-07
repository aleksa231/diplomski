# S7 (b) - Referential Integrity: Category Deletion

Scenario S7 has two halves. Half (a) is the timed load comparison of the reporting queries
(`GET /api/reports/category-monthly-summary` and friends) driven by
[monthly-summary.js](../loadtests/s7/monthly-summary.js). Half (b), described here, is *not* a
performance property - it is a correctness property of the storage engines, so it is verified by
the Java test suite instead of by k6. There is nothing meaningful for a load generator to measure
about whether a database refuses an operation.

## The property under test

> Can the database itself refuse to remove a category while products still reference it?

This is the classic "no dangling reference" guarantee. It is enforced by the storage engine in one
case and left entirely to the application in the other.

## PostgreSQL (normalized): enforced by the database

The schema declares the reference as a restricting foreign key in
[V1__init_schema.sql](../relational/src/main/resources/db/migration/V1__init_schema.sql#L15):

```sql
category_id VARCHAR(36) NOT NULL REFERENCES category(id) ON DELETE RESTRICT
```

A `category` row is a real entity. As long as at least one `product` row points at it, PostgreSQL
rejects the delete with a `DataIntegrityViolationException`; once the referencing product is
removed, the same delete succeeds. The application never has to remember to check - the database
cannot be talked into an inconsistent state.

Asserted by
[RelationalReferentialIntegrityTest.java](../relational/src/test/java/com/benchmark/relational/RelationalReferentialIntegrityTest.java):

1. create a product in a fresh category;
2. `DELETE FROM category WHERE id = ?` throws `DataIntegrityViolationException` (FK is `RESTRICT`);
3. delete the product, then the very same `DELETE` affects 1 row.

## MongoDB: not expressible, integrity is application-owned

There is no `category` collection. The category is a plain denormalized string on each product
document, and the only structural check MongoDB offers is a per-document `$jsonSchema` validator in
[init-rs.sh](../infra/mongo-init/init-rs.sh) - it validates the *shape of one document* and cannot
say anything about other documents. Consequently:

* a product may reference a category that does not exist anywhere (a "phantom" category);
* "deleting a category" is not a database operation at all - it is just clearing a string field, and
  the database can never reject it;
* removing the last product that used a category makes the category disappear implicitly, with no
  constraint ever firing.

Referential integrity is therefore a discipline the application must impose (see the hybrid
embedding/referencing discussion in [data-model.md](data-model.md)).

Asserted by
[NosqlReferentialIntegrityTest.java](../nosql-blocking/src/test/java/com/benchmark/nosqlblocking/NosqlReferentialIntegrityTest.java):

1. a created product's category lives only as a field on the `products` document - no `category`
   collection exists;
2. dropping a (non-existent) category collection never throws and leaves the product untouched,
   i.e. there is no guard equivalent to `ON DELETE RESTRICT`;
3. deleting the last product in the category drops the reference count to zero with no constraint
   involved.

## Summary

| Aspect | PostgreSQL (normalized) | MongoDB |
| --- | --- | --- |
| Category representation | dedicated `category` entity/table | denormalized string on each product |
| Guard mechanism | `FOREIGN KEY ... ON DELETE RESTRICT` | none in the database |
| Deleting a category in use | rejected by the engine | not even an engine operation |
| Who owns integrity | the database | the application |

**Conclusion.** Relational engines give "no dangling references" for free as a declarative schema
constraint; a document store pushes the same responsibility into application code, where it is
easy to get wrong. This is the honest trade-off: MongoDB buys schema flexibility and single-read
locality, PostgreSQL buys enforceable integrity - and S7 is the scenario where that shows up as a
functional (not merely performance) difference.

## Running the tests

```powershell
mvn -pl relational test -Dtest=RelationalReferentialIntegrityTest
mvn -pl nosql-blocking test -Dtest=NosqlReferentialIntegrityTest
```

Both use Testcontainers (`postgres:16-alpine` / `mongo:7.0` + `redis:7.2-alpine`), so a working
Docker daemon is required and no local database has to be running.
