package com.wulong.dict.domain.usecase

import com.wulong.dict.domain.model.SearchHistory
import com.wulong.dict.domain.repository.HistoryRepository
import kotlinx.coroutines.flow.Flow

class GetSearchHistoryUseCase(private val historyRepository: HistoryRepository) {
    operator fun invoke(): Flow<List<SearchHistory>> {
        return historyRepository.getHistoryFlow()
    }
}
