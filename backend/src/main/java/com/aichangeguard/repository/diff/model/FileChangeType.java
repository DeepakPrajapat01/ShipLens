package com.aichangeguard.repository.diff.model;

/**
 * Classification of changes applied to a file between Git revisions.
 */
public enum FileChangeType {
    ADDED,
    MODIFIED,
    DELETED,
    RENAMED,
    COPIED,
    TYPE_CHANGED,
    UNKNOWN
}
