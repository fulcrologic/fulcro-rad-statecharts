# Statechart Engine Notes

## View Mode

View mode (`view-mode?`, `view-action`, `create-action`, `edit-action`) was intentionally removed. Nothing in the
statechart engine ever set the `:action` key to `"view"`, making `view-mode?` always return false. The constants
`view-action`, `create-action`, `edit-action` were unused. If view-only forms are needed in the future, implement
them via `fo/read-only?` component option or a new statechart state, not by resurrecting the dead code.
