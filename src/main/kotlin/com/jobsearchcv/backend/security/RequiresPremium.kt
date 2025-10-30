package com.jobsearchcv.backend.security

import org.springframework.security.access.prepost.PreAuthorize

@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@PreAuthorize("@subscriptionService.checkPremiumAccess(authentication.name)")
annotation class RequiresPremium