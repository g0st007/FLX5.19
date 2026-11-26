package app.flux.downloader.engine

const val OPEN_IN_BROWSER_HINT =
    "No se encontró un video descargable en este enlace. Ábrelo en el navegador de Flux, reproduce el video y toca el aviso \"Video detectado\"."

/** ¿El fallo es un corte de red (y no el servidor rechazando)? Si es así lo ya descargado se conserva para reanudar. */
fun isNetworkDrop(e: Throwable): Boolean {
    var t: Throwable? = e
    var depth = 0
    while (t != null && depth < 4) {
        if (t is java.net.UnknownHostException || t is java.net.SocketException || t is java.net.SocketTimeoutException ||
            t is java.net.ConnectException || t is javax.net.ssl.SSLException || t is java.io.EOFException ||
            t is java.net.ProtocolException
        ) return true
        val m = t.message.orEmpty()
        if (m.contains("se cortó", true) || m.contains("unexpected end of stream", true) ||
            m.contains("Unable to resolve", true) || m.contains("timed out", true) ||
            m.contains("Connection reset", true) || m.contains("Network is unreachable", true)
        ) return true
        t = t.cause
        depth++
    }
    return false
}

fun friendlyError(e: Exception): String {
    val msg = e.message.orEmpty()
    return when {
        isNetworkDrop(e) -> "Se cortó la conexión. Toca Reintentar para seguir donde iba."
        msg.contains("Unsupported URL", true) || msg.contains("Unable to extract", true) ||
            msg.contains("[generic]", true) || msg.contains("No video formats", true) -> OPEN_IN_BROWSER_HINT
        msg.contains("HTTP Error 403", true) -> "El sitio rechazó la descarga (403). Ábrelo en el navegador de Flux para detectar el video con tu sesión."
        msg.contains("HTTP Error 404", true) -> "El video ya no existe en ese enlace (404)."
        msg.contains("private", true) || msg.contains("login", true) || msg.contains("sign in", true) ->
            "El contenido es privado o requiere iniciar sesión."
        msg.contains("Unable to resolve", true) || msg.contains("timed out", true) || msg.contains("network", true) ->
            "Error de red. Revisa tu conexión e inténtalo de nuevo."
        msg.contains("DRM", true) -> "Este contenido está protegido con DRM y no se puede descargar."
        else -> msg.lineSequence().lastOrNull { it.isNotBlank() }?.take(160) ?: "Ocurrió un error inesperado."
    }
}
