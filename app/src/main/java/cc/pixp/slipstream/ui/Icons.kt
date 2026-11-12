package cc.pixp.slipstream.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

enum class Symbol { Library, Live, Creators, Saved, Search, Play, Close, Back, Check, Arrow, Logo, Menu, Refresh, Down, Lock, ThumbUp, ThumbDown, Download }

@Composable
fun Glyph(symbol: Symbol, modifier: Modifier = Modifier, color: Color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface) {
    Canvas(modifier.size(24.dp)) {
        val s = size.width/24; val stroke = Stroke(1.8f*s, cap=StrokeCap.Round, join=StrokeJoin.Round)
        fun line(x:Float,y:Float,x2:Float,y2:Float) = drawLine(color, Offset(x*s,y*s),Offset(x2*s,y2*s),1.8f*s,StrokeCap.Round)
        fun path(vararg points: Pair<Float,Float>) { drawPath(Path().apply { points.forEachIndexed { i,p -> if(i==0) moveTo(p.first*s,p.second*s) else lineTo(p.first*s,p.second*s) } },color,style=stroke) }
        when(symbol) {
            Symbol.Download -> {line(12f,3f,12f,16f);path(6f to 10f,12f to 16f,18f to 10f);path(4f to 16f,4f to 21f,20f to 21f,20f to 16f)}
            Symbol.ThumbUp, Symbol.ThumbDown -> {
                val flip=if(symbol==Symbol.ThumbDown) -1f else 1f
                fun y(value:Float)=if(flip<0) 24-value else value
                path(3f to y(10f),6f to y(10f),6f to y(21f),3f to y(21f),3f to y(10f))
                path(6f to y(11f),12f to y(3f),14f to y(3f),14f to y(10f),21f to y(10f),21f to y(14f),18f to y(21f),6f to y(21f))
            }
            Symbol.Menu -> { line(4f,6f,20f,6f);line(4f,12f,20f,12f);line(4f,18f,20f,18f) }
            Symbol.Refresh -> { drawArc(color,40f,290f,false,Offset(4*s,4*s),Size(16*s,16*s),style=stroke);path(20f to 4f,20f to 10f,14f to 10f) }
            Symbol.Down -> path(6f to 9f,12f to 15f,18f to 9f)
            Symbol.Lock -> {drawRoundRect(color,Offset(5*s,10*s),Size(14*s,11*s),CornerRadius(2*s),style=stroke);drawArc(color,180f,180f,false,Offset(8*s,3*s),Size(8*s,14*s),style=stroke)}
            Symbol.Library -> {
                drawRoundRect(color,Offset(6*s,8*s),Size(16*s,13*s),CornerRadius(2*s),style=stroke)
                path(3f to 15f,2f to 15f,2f to 3f,18f to 3f,18f to 5f)
                drawPath(Path().apply {moveTo(12*s,11*s);lineTo(18*s,14.5f*s);lineTo(12*s,18*s);close()},color)
            }
            Symbol.Live -> { drawCircle(color,3*s,Offset(12*s,12*s)); drawArc(color,135f,90f,false,Offset(4*s,4*s),Size(16*s,16*s),style=stroke); drawArc(color,-45f,90f,false,Offset(4*s,4*s),Size(16*s,16*s),style=stroke) }
            Symbol.Creators -> { drawCircle(color,3.5f*s,Offset(12*s,7*s),style=stroke); drawArc(color,180f,180f,false,Offset(4*s,13*s),Size(16*s,13*s),style=stroke) }
            Symbol.Saved -> path(6f to 3f,18f to 3f,18f to 21f,12f to 17f,6f to 21f,6f to 3f)
            Symbol.Search -> { drawCircle(color,6.5f*s,Offset(10*s,10*s),style=stroke); line(15f,15f,21f,21f) }
            Symbol.Play -> drawPath(Path().apply { moveTo(8*s,4*s); lineTo(21*s,12*s); lineTo(8*s,20*s); close() },color)
            Symbol.Close -> { line(6f,6f,18f,18f); line(18f,6f,6f,18f) }
            Symbol.Back -> { line(20f,12f,4f,12f); path(10f to 5f,3f to 12f,10f to 19f) }
            Symbol.Check -> path(5f to 12f,10f to 17f,20f to 7f)
            Symbol.Arrow -> { line(4f,12f,20f,12f); path(14f to 6f,20f to 12f,14f to 18f) }
            Symbol.Logo -> for(i in 0..2) {
                drawPath(Path().apply {
                    moveTo(3*s,(6+i*6)*s)
                    cubicTo(7*s,(2+i*6)*s,8*s,(10+i*6)*s,12*s,(6+i*6)*s)
                    cubicTo(16*s,(2+i*6)*s,17*s,(10+i*6)*s,21*s,(6+i*6)*s)
                },color,style=stroke)
            }
        }
    }
}
