# Demo Server Components

## Key Decisions

- **Datomic adapter**: Uses `datomic-cloud` (client API via `datomic.client.api`), NOT `datomic` (peer API). The `:demo` alias deps include `com.datomic/local` and `com.datomic/client-cloud` which both use the client API.
- **All CLJC except database_queries.clj**: Server-only code wrapped in `#?(:clj ...)` reader conditionals. `database_queries.clj` stays CLJ-only because it directly uses Datomic query API.
- **No blob/authorization**: Removed blob_store, blob middleware from save_middleware, and all authorization references per spec.
- **Config location**: `config/dev.edn` at project root. Uses `ring.middleware/defaults-config` for Ring defaults.
- **Mount state dependencies**: config → datomic → auto_resolvers → parser → ring_middleware → server. Mount handles ordering.
- **Parser references model resolvers directly**: `account/resolvers` and `invoice/resolvers` are registered explicitly in the parser, not through `model/all-resolvers`.

## Namespace Mapping (original → ported)

| Original (fulcro-rad-demo) | Ported |
|---|---|
| `com.example.components.datomic` (.clj) | `.datomic` (.cljc) |
| `com.example.components.auto-resolvers` (.clj) | `.auto-resolvers` (.cljc) |
| `com.example.components.save-middleware` (.clj) | `.save-middleware` (.cljc) |
| `com.example.components.delete-middleware` (.clj) | `.delete-middleware` (.cljc) |
| `com.example.components.parser` (.clj) | `.parser` (.cljc) |
| `com.example.components.server` (.clj) | `.server` (.cljc) |
| `com.example.components.config` (.clj) | `.config` (.cljc) |
| `com.example.components.ring-middleware` (.clj) | `.ring-middleware` (.cljc) |
| `com.example.components.database-queries` (.clj) | `.database-queries` (.clj, kept CLJ) |
| `development` (.clj) | `com.example.development` (.cljc) |
