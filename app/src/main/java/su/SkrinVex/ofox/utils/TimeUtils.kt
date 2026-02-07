package su.SkrinVex.ofox.utils

fun formatTime(timestamp: Long): String {
    val diff = System.currentTimeMillis() - timestamp
    val seconds = diff / 1000
    val minutes = seconds / 60
    val hours = minutes / 60
    val days = hours / 24
    
    return when {
        seconds < 5 -> "только что"
        seconds < 60 -> "$seconds сек назад"
        minutes < 60 -> "$minutes мин назад"
        hours < 24 -> "$hours ч назад"
        else -> "$days дн назад"
    }
}
