package cn.alittlecookie.lutista.watermark.manager

import cn.alittlecookie.lutista.watermark.model.Scene
import java.util.Stack

class HistoryManager {
    private val undoStack = Stack<Scene>()
    private val redoStack = Stack<Scene>()
    private var currentScene: Scene? = null

    fun saveState(scene: Scene) {
        currentScene?.let { undoStack.push(it) }
        currentScene = scene
        redoStack.clear()
    }

    fun undo(): Scene? {
        if (undoStack.isEmpty()) return null
        currentScene?.let { redoStack.push(it) }
        currentScene = undoStack.pop()
        return currentScene
    }

    fun redo(): Scene? {
        if (redoStack.isEmpty()) return null
        currentScene?.let { undoStack.push(it) }
        currentScene = redoStack.pop()
        return currentScene
    }

    fun canUndo(): Boolean = undoStack.isNotEmpty()
    fun canRedo(): Boolean = redoStack.isNotEmpty()

    fun clear() {
        undoStack.clear()
        redoStack.clear()
        currentScene = null
    }
}
