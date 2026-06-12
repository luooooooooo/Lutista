package cn.alittlecookie.lut2photo_n.watermark.manager

import cn.alittlecookie.lut2photo_n.watermark.model.BaseImageElement
import cn.alittlecookie.lut2photo_n.watermark.model.Scene
import cn.alittlecookie.lut2photo_n.watermark.model.TextElement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class HistoryManagerTest {

    private lateinit var historyManager: HistoryManager

    @Before
    fun setup() {
        historyManager = HistoryManager()
    }

    private fun createScene(text: String): Scene {
        return Scene(
            baseImage = BaseImageElement(imageUri = "/test.jpg"),
            elements = listOf(TextElement(id = "1", content = text, fontSize = 10f))
        )
    }

    private fun getFirstTextContent(scene: Scene): String {
        return (scene.elements[0] as TextElement).content
    }

    @Test
    fun `initial state has no undo or redo`() {
        assertFalse(historyManager.canUndo())
        assertFalse(historyManager.canRedo())
    }

    @Test
    fun `saveState allows undo`() {
        val scene1 = createScene("A")
        val scene2 = createScene("B")

        historyManager.saveState(scene1)
        historyManager.saveState(scene2)

        assertTrue(historyManager.canUndo())
        assertFalse(historyManager.canRedo())
    }

    @Test
    fun `undo returns previous state`() {
        val scene1 = createScene("A")
        val scene2 = createScene("B")

        historyManager.saveState(scene1)
        historyManager.saveState(scene2)

        val undone = historyManager.undo()
        assertNotNull(undone)
        assertEquals("A", getFirstTextContent(undone!!))
    }

    @Test
    fun `redo returns next state`() {
        val scene1 = createScene("A")
        val scene2 = createScene("B")

        historyManager.saveState(scene1)
        historyManager.saveState(scene2)
        historyManager.undo()

        assertTrue(historyManager.canRedo())
        val redone = historyManager.redo()
        assertNotNull(redone)
        assertEquals("B", getFirstTextContent(redone!!))
    }

    @Test
    fun `undo on empty stack returns null`() {
        assertNull(historyManager.undo())
    }

    @Test
    fun `redo on empty stack returns null`() {
        assertNull(historyManager.redo())
    }

    @Test
    fun `saveState clears redo stack`() {
        val scene1 = createScene("A")
        val scene2 = createScene("B")
        val scene3 = createScene("C")

        historyManager.saveState(scene1)
        historyManager.saveState(scene2)
        historyManager.undo()
        assertTrue(historyManager.canRedo())

        historyManager.saveState(scene3)
        assertFalse(historyManager.canRedo())
    }

    @Test
    fun `clear resets everything`() {
        val scene1 = createScene("A")
        val scene2 = createScene("B")

        historyManager.saveState(scene1)
        historyManager.saveState(scene2)
        historyManager.undo()

        historyManager.clear()

        assertFalse(historyManager.canUndo())
        assertFalse(historyManager.canRedo())
        assertNull(historyManager.undo())
    }

    @Test
    fun `multiple undo and redo works correctly`() {
        val scenes = (1..5).map { createScene(it.toString()) }

        scenes.forEach { historyManager.saveState(it) }

        assertEquals("4", getFirstTextContent(historyManager.undo()!!))
        assertEquals("3", getFirstTextContent(historyManager.undo()!!))
        assertEquals("2", getFirstTextContent(historyManager.undo()!!))

        assertEquals("3", getFirstTextContent(historyManager.redo()!!))
        assertEquals("4", getFirstTextContent(historyManager.redo()!!))
        assertEquals("5", getFirstTextContent(historyManager.redo()!!))

        assertNull(historyManager.redo())
    }
}
