# db_scripts

Table definitions, FK relationships, and full-text indexes in `schema.json` are an RMB requirement 
— cross-record queries are only supported for tables declared there.


The SQL scripts are needed to allow Liquibase to modify them.
[change_function_owner.sql](change_function_owner.sql)
[change_table_owner.sql](change_table_owner.sql)
[create_isbn_functions.sql](create_isbn_functions.sql)


> **Note:** All schema modifications must be done via Liquibase migrations. Editing `schema.json` directly is only permitted in exceptional cases (e.g. adding cross-record query support for a new table).
