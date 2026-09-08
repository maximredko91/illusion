package com.illusion.app.ui.common

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

/**
 * Форма выпадающих меню. Material3 по умолчанию скругляет их на 4dp (extraSmall), из-за чего
 * список выглядел прямоугольной коробкой под скруглённой «капсулой» кнопки, которая его открывает.
 * Одно значение на всё приложение, чтобы меню в настройках, фильтрах библиотеки и плеере не
 * разъезжались по стилю.
 */
val MenuShape = RoundedCornerShape(20.dp)
