package com.wulong.dict.domain.usecase

import com.wulong.dict.domain.model.Suggestion
import com.wulong.dict.domain.repository.DictionaryRepository

class GetSuggestionsUseCase(private val repository: DictionaryRepository) {

    suspend operator fun invoke(prefix: String, limit: Int = 20): List<Suggestion> {
        if (prefix.isBlank()) return emptyList()
        return repository.getSuggestions(prefix.trim(), limit)
    }
}
