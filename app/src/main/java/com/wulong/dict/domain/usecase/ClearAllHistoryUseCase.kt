package com.wulong.dict.domain.usecase

import com.wulong.dict.domain.repository.HistoryRepository

class ClearAllHistoryUseCase(private val historyRepository: HistoryRepository) {
    suspend operator fun invoke() {
        historyRepository.clearAll()
    }
}
