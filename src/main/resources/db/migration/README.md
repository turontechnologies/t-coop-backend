# Database migrations

Flyway-managed schema changes live here, named `V{number}__{description}.sql`
(e.g. `V2__add_loan_repayments_table.sql`). They run automatically, in order,
every time the app starts — nobody edits the database by hand.

- **`V1__init_schema.sql`** — the full baseline schema. See
  [`documentation/schema-design.md`](../../../../documentation/schema-design.md)
  for the reasoning behind each table.

Once `V1` has run against the shared database, **never edit it** — every
future change is a new `V2`, `V3`, ... file, even for something small.
