package com.wulong.dict.domain.usecase

import com.wulong.dict.domain.repository.HistoryRepository

class DeleteHistoryItemUseCase(private val historyRepository: HistoryRepository) {
    suspend operator fun invoke(id: Long) {
        historyRepository.deleteById(id)
    }
}
