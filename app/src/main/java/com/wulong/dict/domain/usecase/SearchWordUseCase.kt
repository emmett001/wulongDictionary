package com.wulong.dict.domain.usecase

import com.wulong.dict.domain.model.DictionaryEntry
import com.wulong.dict.domain.repository.DictionaryRepository

class SearchWordUseCase(private val repository: DictionaryRepository) {

    suspend operator fun invoke(word: String): List<DictionaryEntry> {
        if (word.isBlank()) return emptyList()
        return repository.searchWord(word.trim())
    }
}
