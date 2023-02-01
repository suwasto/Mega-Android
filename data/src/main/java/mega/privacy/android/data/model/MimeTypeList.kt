package mega.privacy.android.data.model

import android.webkit.MimeTypeMap
import mega.privacy.android.data.mapper.getMimeType
import java.util.Locale

/**
 * Mime type list
 *
 * @property type
 * @property extension
 */
data class MimeTypeList(
    val type: String,
    val extension: String,
) {
    companion object {
        /**
         * Type for name
         *
         * @param name
         */
        fun typeForName(name: String?): MimeTypeList {
            val newName = name ?: ""
            val fixedName = newName.trim().lowercase(Locale.getDefault())
            var extension = ""
            val index = fixedName.lastIndexOf(".")
            if (index >= 0 && index + 1 < fixedName.length) {
                extension = fixedName.substring(index + 1)
            }
            val detectedType = getMimeType(
                extension
            ) {
                MimeTypeMap.getSingleton()
                    .getMimeTypeFromExtension(extension)
            }
            return MimeTypeList(detectedType, extension)
        }
    }

    /**
     * Check if MimeType is Video Reproducible
     */
    fun isVideoReproducible() =
        type.startsWith("video/") || extension == "mkv" || extension == "flv" || extension == "vob" || extension == "avi" || extension == "wmv" || extension == "mpg" || extension == "mts"


    /**
     * Check if MimeType is Mp4 or Video
     */
    fun isMp4Video() =
        type.startsWith("video/") || extension == "mp4"


    /**
     * Check if MimeType is Zip
     */
    fun isZip() =
        type.startsWith("application/zip") || type.startsWith("multipart/x-zip")


    /**
     * Check if MimeType is image
     */
    fun isImage() =
        type.startsWith("image/")

}