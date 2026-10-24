package com.example.poemchecker.app

import android.app.Application
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.poemchecker.data.local.PoemDatabase
import com.example.poemchecker.data.repository.RoomPoemRepository
import com.example.poemchecker.domain.training.FinishRecitation
import com.example.poemchecker.speech.createAndroidSpeechRecognitionClient
import com.example.poemchecker.ui.editor.EditorViewModel
import com.example.poemchecker.ui.recitation.RecitationViewModel

class PoemApplication : Application() {
    val container by lazy { AppContainer(this) }
}

class AppContainer(application: Application) {
    private val repository = RoomPoemRepository(PoemDatabase.getInstance(application))

    val editorFactory = viewModelFactory {
        initializer { EditorViewModel(repository, createSavedStateHandle()) }
    }
    val recitationFactory = viewModelFactory {
        initializer {
            RecitationViewModel(createAndroidSpeechRecognitionClient(application), FinishRecitation(repository))
        }
    }
}
