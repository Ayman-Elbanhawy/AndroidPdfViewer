package com.aymanelbanhawy.editor.core

import android.content.Context
import androidx.room.Room
import androidx.work.WorkManager
import com.aymanelbanhawy.editor.core.data.PdfWorkspaceDatabase
import com.aymanelbanhawy.editor.core.forms.DefaultFormSupportRepository
import com.aymanelbanhawy.editor.core.forms.FormSupportRepository
import com.aymanelbanhawy.editor.core.organize.DefaultPageThumbnailRepository
import com.aymanelbanhawy.editor.core.organize.PageThumbnailRepository
import com.aymanelbanhawy.editor.core.repository.DefaultDocumentRepository
import com.aymanelbanhawy.editor.core.repository.DocumentRepository
import com.aymanelbanhawy.editor.core.session.DefaultEditorSession
import com.aymanelbanhawy.editor.core.session.EditorSession
import com.aymanelbanhawy.editor.core.work.CleanupExportsWorker
import com.aymanelbanhawy.editor.core.work.WorkManagerAutosaveScheduler

class EditorCoreContainer(
    context: Context,
) {
    val appContext: Context = context.applicationContext
    private val database: PdfWorkspaceDatabase = Room.databaseBuilder(
        appContext,
        PdfWorkspaceDatabase::class.java,
        DATABASE_NAME,
    ).fallbackToDestructiveMigration().build()
    private val workManager: WorkManager = WorkManager.getInstance(appContext)
    val documentRepository: DocumentRepository = DefaultDocumentRepository(
        context = appContext,
        recentDocumentDao = database.recentDocumentDao(),
        draftDao = database.draftDao(),
        editHistoryMetadataDao = database.editHistoryMetadataDao(),
    )
    val pageThumbnailRepository: PageThumbnailRepository = DefaultPageThumbnailRepository(appContext)
    val formSupportRepository: FormSupportRepository = DefaultFormSupportRepository(
        context = appContext,
        profileDao = database.formProfileDao(),
        savedSignatureDao = database.savedSignatureDao(),
    )
    private val autosaveScheduler = WorkManagerAutosaveScheduler(documentRepository, workManager)

    init {
        CleanupExportsWorker.enqueue(workManager)
    }

    fun newSession(): EditorSession = DefaultEditorSession(documentRepository, autosaveScheduler)

    companion object {
        const val DATABASE_NAME: String = "enterprise-editor.db"
    }
}
