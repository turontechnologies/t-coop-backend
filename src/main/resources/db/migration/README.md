# Database migrations

Flyway-managed schema changes live here, named `V{number}__{description}.sql`
(e.g. `V1__create_cooperatives_table.sql`). They run automatically, in order,
every time the app starts — nobody edits the database by hand.

No migrations yet — schema design is the next step after this scaffold is
confirmed to connect to the real database.
