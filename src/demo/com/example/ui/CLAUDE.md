# Demo UI Layer

## Porting Decisions

### account_forms.cljc
- **Removed** from original: blob/avatar fields, FileForm subform, TagForm, timezone/zone-id field, `fo/query-inclusion` for blob status keys, semantic-ui rendering options (`suo/*`)
- **Kept**: AccountForm with address subforms (primary + additional), BriefAccountForm (simplified view)
- `::form/autocreate-on-load?` used on primary-address subform to auto-create when parent loads without one
- No custom rendering body — uses default `render-layout` via headless plugin multimethods

### address_forms.cljc
- Minimal subform: id, street, city, state (enum), zip
- `fo/route-prefix` kept for standalone editing capability
- `fo/cancel-route` removed (original had `["landing-page"]`) — statechart routing handles navigation

### General Patterns
- All files are CLJC — no reader conditionals needed for pure form declarations
- No `install-ui-controls!` — headless plugin auto-registers via multimethods
- No UISM/`uism/` references — forms use statechart lifecycle via `fo/statechart` (defaulted by macro)
- Model attributes imported from `com.example.model.*` (already ported)
- `comp/props` used in subform can-add?/can-delete? lambdas to inspect parent state
