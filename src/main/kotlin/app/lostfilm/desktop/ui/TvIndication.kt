package app.lostfilm.desktop.ui

import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.interaction.FocusInteraction
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/**
 * Единая подсветка фокуса: бирюзовая рамка вокруг любого кликабельного
 * элемента, до которого добрались клавиатурой (Tab/стрелки).
 */
class TvIndication : IndicationNodeFactory {
    override fun create(interactionSource: InteractionSource): DelegatableNode =
        FocusBorderNode(interactionSource)

    override fun equals(other: Any?): Boolean = other is TvIndication

    override fun hashCode(): Int = TvIndication::class.hashCode()

    private class FocusBorderNode(
        private val interactionSource: InteractionSource,
    ) : Modifier.Node(), DrawModifierNode {
        private var focused by mutableStateOf(false)

        override fun onAttach() {
            coroutineScope.launch {
                interactionSource.interactions.collect { interaction ->
                    focused = interaction is FocusInteraction.Focus
                }
            }
        }

        override fun ContentDrawScope.draw() {
            drawContent()
            if (!focused) return
            val stroke = 2.dp.toPx()
            drawRoundRect(
                color = Color(0xFF4FC3F7),
                topLeft = Offset(stroke / 2f, stroke / 2f),
                size = Size(size.width - stroke, size.height - stroke),
                cornerRadius = CornerRadius(8.dp.toPx()),
                style = Stroke(width = stroke),
            )
        }
    }
}
