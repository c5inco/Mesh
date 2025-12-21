package des.c5inco.mesh.data

import data.MeshDocument
import data.MeshState
import kotlinx.serialization.json.Json
import model.MeshPoint
import java.io.File
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter

object DocumentManager {
    private val documentsDir = File(System.getProperty("user.home") + File.separator + ".mesh" + File.separator + "documents")
    private val json = Json { prettyPrint = true }

    init {
        // Ensure documents directory exists
        if (!documentsDir.exists()) {
            documentsDir.mkdirs()
        }
    }

    /**
     * Creates a new empty document with default settings
     */
    fun createNewDocument(): MeshDocument {
        return MeshDocument(
            name = "Untitled",
            meshState = MeshState(),
            meshPoints = emptyList(),
            canvasBackgroundColor = -1L
        )
    }

    /**
     * Saves a document to the specified file path
     */
    fun saveDocument(document: MeshDocument, file: File): Boolean {
        return try {
            val jsonString = json.encodeToString(MeshDocument.serializer(), document)
            file.writeText(jsonString)
            println("Saved document to: ${file.absolutePath}")
            true
        } catch (e: Exception) {
            println("Error saving document: ${e.message}")
            e.printStackTrace()
            false
        }
    }

    /**
     * Loads a document from the specified file path
     */
    fun loadDocument(file: File): MeshDocument? {
        return try {
            if (!file.exists()) return null
            val jsonString = file.readText()
            json.decodeFromString(MeshDocument.serializer(), jsonString)
        } catch (e: Exception) {
            println("Error loading document: ${e.message}")
            e.printStackTrace()
            null
        }
    }

    /**
     * Shows a file chooser dialog for saving a document
     * Returns the selected file or null if cancelled
     */
    fun showSaveDialog(currentName: String = "Untitled"): File? {
        val fileChooser = JFileChooser(documentsDir).apply {
            dialogTitle = "Save Mesh Document"
            fileFilter = FileNameExtensionFilter("Mesh Documents (*.mesh)", "mesh")
            selectedFile = File(documentsDir, "$currentName.mesh")
        }

        return if (fileChooser.showSaveDialog(null) == JFileChooser.APPROVE_OPTION) {
            var file = fileChooser.selectedFile
            // Ensure .mesh extension
            if (!file.name.endsWith(".mesh")) {
                file = File(file.parentFile, "${file.name}.mesh")
            }
            file
        } else {
            null
        }
    }

    /**
     * Shows a file chooser dialog for opening a document
     * Returns the selected file or null if cancelled
     */
    fun showOpenDialog(): File? {
        val fileChooser = JFileChooser(documentsDir).apply {
            dialogTitle = "Open Mesh Document"
            fileFilter = FileNameExtensionFilter("Mesh Documents (*.mesh)", "mesh")
        }

        return if (fileChooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
            fileChooser.selectedFile
        } else {
            null
        }
    }

    /**
     * Gets the default untitled document file path
     */
    fun getDefaultDocumentFile(): File {
        return File(documentsDir, "untitled.mesh")
    }

    /**
     * Lists all mesh documents in the documents directory
     */
    fun listDocuments(): List<File> {
        return documentsDir.listFiles { file -> file.extension == "mesh" }?.toList() ?: emptyList()
    }
}
