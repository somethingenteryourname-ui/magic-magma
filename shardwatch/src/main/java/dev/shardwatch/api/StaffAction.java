package dev.shardwatch.api;

/** Every staff action Shardwatch knows about. Used for the audit log, Lustre rewards and effects. */
public enum StaffAction {
    CHIP, HUSH, EJECT, ENCASE, PETRIFY, UNPETRIFY, REVOKE,
    FLARE_CREATE, FLARE_CLAIM, FLARE_RESOLVE, FLARE_DISMISS,
    REWIND, REWIND_UNDO, ECHO_INSPECT,
    GLINT_ALERT, GLINT_HANDLE,
    FACET_SET, VEIL_ON, VEIL_OFF,
    REFINE, KEEPSAKE_CLAIM, GIVE, RELOAD
}
