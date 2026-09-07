package com.householdsplitter.data.entity;

/**
 * Where a draft order stopped, so tapping it on Home resumes it there.
 *
 * <p>SPEC 7.3.4 and 7.9.14 both require this and SPEC 5.3 gives no column for it, so the
 * column was added. SPEC 11.14 makes the same demand for process death at any point.
 */
public enum DraftStep {
    IMPORT,
    REVIEW,
    DETAILS,
    PARTICIPANTS,
    ASSIGN,
    SUMMARY
}
