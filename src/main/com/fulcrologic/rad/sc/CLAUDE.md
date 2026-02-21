# RAD Statecharts Session Convention

## Session ID Namespace

All ident-derived session IDs use the keyword namespace `"com.fulcrologic.rad.sc"`.

## Encoding Format

`ident->session-id` encodes `[:ns/name value]` as `:com.fulcrologic.rad.sc/ns_name--<str-of-value>`.

- The ident key's namespace and name are joined with `_`
- The value is separated by `--`
- The `--` separator is unambiguous because `_` cannot appear at the start of a keyword name

## Value Type Round-Tripping

`session-id->ident` uses `parse-id-value` to recover the original type:
- UUID strings -> parsed via `ids/new-uuid`
- Numeric strings -> parsed via `ids/id-string->id :int`
- Strings starting with `:` -> parsed as keywords
- Everything else -> kept as string

## Spec Deviation: report/container helpers

The spec shows `report-session-id` and `container-session-id` with two 1-arity overloads (instance vs class), which is impossible in Clojure. These were implemented as:
- 1-arity: takes component instance
- 2-arity: takes component class + props map (use `{}` for singletons)

## Auth Session ID

`auth-session-id` is `::auth-session` (i.e., `:com.fulcrologic.rad.sc.session/auth-session`). It's a well-known keyword, not derived from an ident.
