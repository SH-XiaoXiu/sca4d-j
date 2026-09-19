package com.sca4d.auth.totp.enrollment;

/** TOTP enrollment 在 Redis 中的单向生命周期状态。 */
enum TotpEnrollmentStatus {
    PENDING,
    CONSUMED
}
