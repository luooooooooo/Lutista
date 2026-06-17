package cn.alittlecookie.lutista.watermark.util

import cn.alittlecookie.lutista.watermark.model.Anchor
import cn.alittlecookie.lutista.watermark.model.BaseImageElement
import cn.alittlecookie.lutista.watermark.model.BorderWidth
import cn.alittlecookie.lutista.watermark.model.ContainerElement
import cn.alittlecookie.lutista.watermark.model.ContainerMode
import cn.alittlecookie.lutista.watermark.model.CrossAxisAlign
import cn.alittlecookie.lutista.watermark.model.Element
import cn.alittlecookie.lutista.watermark.model.FontWeight
import cn.alittlecookie.lutista.watermark.model.ImageElement
import cn.alittlecookie.lutista.watermark.model.MainAxisAlign
import cn.alittlecookie.lutista.watermark.model.Margin
import cn.alittlecookie.lutista.watermark.model.Padding
import cn.alittlecookie.lutista.watermark.model.Scene
import cn.alittlecookie.lutista.watermark.model.SizeMode
import cn.alittlecookie.lutista.watermark.model.TextAlign
import cn.alittlecookie.lutista.watermark.model.TextElement
import org.json.JSONArray
import org.json.JSONObject

object SceneSerializer {

    fun toJson(scene: Scene): String {
        val json = JSONObject()
        json.put("baseImage", baseImageToJson(scene.baseImage))
        json.put("elements", JSONArray().apply {
            scene.elements.forEach { put(elementToJson(it)) }
        })
        return json.toString()
    }

    fun fromJson(jsonString: String): Scene {
        val json = JSONObject(jsonString)
        val baseImageJson = json.getJSONObject("baseImage")
        val baseImage = BaseImageElement(
            imageUri = baseImageJson.optString("imageUri", ""),
            borderWidth = parseBorderWidth(baseImageJson.optJSONObject("borderWidth")),
            borderColor = baseImageJson.optInt("borderColor", -1)
        )
        val elements = parseElements(json.optJSONArray("elements"))
        return Scene(baseImage = baseImage, elements = elements)
    }

    private fun baseImageToJson(base: BaseImageElement): JSONObject {
        return JSONObject().apply {
            put("imageUri", base.imageUri)
            put("borderWidth", borderWidthToJson(base.borderWidth))
            put("borderColor", base.borderColor)
        }
    }

    private fun borderWidthToJson(bw: BorderWidth): JSONObject {
        return JSONObject().apply {
            put("top", bw.top.toDouble())
            put("right", bw.right.toDouble())
            put("bottom", bw.bottom.toDouble())
            put("left", bw.left.toDouble())
        }
    }

    private fun parseBorderWidth(json: JSONObject?): BorderWidth {
        if (json == null) return BorderWidth.ZERO
        return BorderWidth(
            top = json.optDouble("top", 0.0).toFloat(),
            right = json.optDouble("right", 0.0).toFloat(),
            bottom = json.optDouble("bottom", 0.0).toFloat(),
            left = json.optDouble("left", 0.0).toFloat()
        )
    }

    private fun elementToJson(element: Element): JSONObject {
        val json = JSONObject()
        json.put("id", element.id)
        json.put("margin", marginToJson(element.margin))
        json.put("opacity", element.opacity.toDouble())
        element.anchor?.let { json.put("anchor", it.name) }
        element.offsetX?.let { json.put("offsetX", it.toDouble()) }
        element.offsetY?.let { json.put("offsetY", it.toDouble()) }

        when (element) {
            is TextElement -> {
                json.put("type", "text")
                json.put("content", element.content)
                element.fontUri?.let { json.put("fontUri", it) }
                json.put("fontWeight", element.fontWeight.name)
                json.put("color", element.color)
                json.put("fontSize", element.fontSize.toDouble())
                json.put("letterSpacing", element.letterSpacing.toDouble())
                json.put("lineHeight", element.lineHeight.toDouble())
                json.put("textAlign", element.textAlign.name)
            }
            is ImageElement -> {
                json.put("type", "image")
                json.put("imageUri", element.imageUri)
                element.width?.let { json.put("width", it.toDouble()) }
                element.height?.let { json.put("height", it.toDouble()) }
                json.put("maintainAspectRatio", element.maintainAspectRatio)
                if (element.imageSizeRef != 0) json.put("imageSizeRef", element.imageSizeRef)
            }
            is ContainerElement -> {
                json.put("type", "container")
                json.put("mode", element.mode.name)
                json.put("mainAxisAlign", element.mainAxisAlign.name)
                json.put("crossAxisAlign", element.crossAxisAlign.name)
                json.put("widthMode", element.widthMode.name)
                element.width?.let { json.put("width", it.toDouble()) }
                json.put("heightMode", element.heightMode.name)
                element.height?.let { json.put("height", it.toDouble()) }
                json.put("padding", paddingToJson(element.padding))
                json.put("children", JSONArray().apply {
                    element.children.forEach { put(elementToJson(it)) }
                })
            }
        }
        return json
    }

    private fun marginToJson(m: Margin): JSONObject {
        return JSONObject().apply {
            put("top", m.top.toDouble())
            put("right", m.right.toDouble())
            put("bottom", m.bottom.toDouble())
            put("left", m.left.toDouble())
        }
    }

    private fun paddingToJson(p: Padding): JSONObject {
        return JSONObject().apply {
            put("top", p.top.toDouble())
            put("right", p.right.toDouble())
            put("bottom", p.bottom.toDouble())
            put("left", p.left.toDouble())
        }
    }

    private fun parseElements(array: JSONArray?): List<Element> {
        if (array == null) return emptyList()
        val elements = mutableListOf<Element>()
        for (i in 0 until array.length()) {
            parseElement(array.getJSONObject(i))?.let { elements.add(it) }
        }
        return elements
    }

    private fun parseElement(json: JSONObject): Element? {
        val type = json.optString("type") ?: return null
        val id = json.optString("id", "")
        val margin = parseMargin(json.optJSONObject("margin"))
        val opacity = json.optDouble("opacity", 1.0).toFloat()
        val anchor = if (json.has("anchor")) {
            try { Anchor.valueOf(json.getString("anchor")) } catch (_: Exception) { null }
        } else null
        val offsetX = if (json.has("offsetX")) json.optDouble("offsetX").toFloat() else null
        val offsetY = if (json.has("offsetY")) json.optDouble("offsetY").toFloat() else null

        return when (type) {
            "text" -> TextElement(
                id = id,
                margin = margin,
                opacity = opacity,
                anchor = anchor,
                offsetX = offsetX,
                offsetY = offsetY,
                content = json.optString("content", ""),
                fontUri = json.optString("fontUri", "").ifEmpty { null },
                fontWeight = try { FontWeight.valueOf(json.optString("fontWeight", "NORMAL")) } catch (_: Exception) { FontWeight.NORMAL },
                color = json.optInt("color", -16777216),
                fontSize = json.optDouble("fontSize", 10.0).toFloat(),
                letterSpacing = json.optDouble("letterSpacing", 0.0).toFloat(),
                lineHeight = json.optDouble("lineHeight", 1.2).toFloat(),
                textAlign = try { TextAlign.valueOf(json.optString("textAlign", "LEFT")) } catch (_: Exception) { TextAlign.LEFT }
            )
            "image" -> ImageElement(
                id = id,
                margin = margin,
                opacity = opacity,
                anchor = anchor,
                offsetX = offsetX,
                offsetY = offsetY,
                imageUri = json.optString("imageUri", ""),
                width = if (json.has("width")) json.optDouble("width").toFloat() else null,
                height = if (json.has("height")) json.optDouble("height").toFloat() else null,
                maintainAspectRatio = json.optBoolean("maintainAspectRatio", true),
                imageSizeRef = json.optInt("imageSizeRef", 0)
            )
            "container" -> ContainerElement(
                id = id,
                margin = margin,
                opacity = opacity,
                anchor = anchor,
                offsetX = offsetX,
                offsetY = offsetY,
                children = parseElements(json.optJSONArray("children")),
                mode = try { ContainerMode.valueOf(json.optString("mode", "LINEAR_H")) } catch (_: Exception) { ContainerMode.LINEAR_H },
                mainAxisAlign = try { MainAxisAlign.valueOf(json.optString("mainAxisAlign", "START")) } catch (_: Exception) { MainAxisAlign.START },
                crossAxisAlign = try { CrossAxisAlign.valueOf(json.optString("crossAxisAlign", "START")) } catch (_: Exception) { CrossAxisAlign.START },
                widthMode = try { SizeMode.valueOf(json.optString("widthMode", "WRAP_CONTENT")) } catch (_: Exception) { SizeMode.WRAP_CONTENT },
                width = if (json.has("width")) json.optDouble("width").toFloat() else null,
                heightMode = try { SizeMode.valueOf(json.optString("heightMode", "WRAP_CONTENT")) } catch (_: Exception) { SizeMode.WRAP_CONTENT },
                height = if (json.has("height")) json.optDouble("height").toFloat() else null,
                padding = parsePadding(json.optJSONObject("padding"))
            )
            else -> null
        }
    }

    private fun parseMargin(json: JSONObject?): Margin {
        if (json == null) return Margin.ZERO
        return Margin(
            top = json.optDouble("top", 0.0).toFloat(),
            right = json.optDouble("right", 0.0).toFloat(),
            bottom = json.optDouble("bottom", 0.0).toFloat(),
            left = json.optDouble("left", 0.0).toFloat()
        )
    }

    private fun parsePadding(json: JSONObject?): Padding {
        if (json == null) return Padding.ZERO
        return Padding(
            top = json.optDouble("top", 0.0).toFloat(),
            right = json.optDouble("right", 0.0).toFloat(),
            bottom = json.optDouble("bottom", 0.0).toFloat(),
            left = json.optDouble("left", 0.0).toFloat()
        )
    }
}
