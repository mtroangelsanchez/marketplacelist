package com.app.marketlist.domain.usecase

import com.app.marketlist.domain.model.AppError

/**
 * Wrapper that allows AppError to be used with Result.failure().
 * Since Result requires a Throwable, this exception carries the typed AppError.
 */
class AppErrorException(val appError: AppError) : Exception(appError.toString())
