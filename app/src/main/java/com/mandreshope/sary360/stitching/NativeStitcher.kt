package com.mandreshope.sary360.stitching

class NativeStitcher {
    
    interface StitchingCallback {
        fun onProgress(progress: Int)
        fun onError(message: String)
        fun onSuccess(outputPath: String)
    }

    /**
     * Stitches multiple images into a spherical panorama.
     * @param imagePaths Array of absolute paths to images.
     * @param outputPath Path where the final equirectangular image will be saved.
     * @param downscaleFactor Factor to downscale images for feature matching (e.g., 0.5).
     * @return 0 on success, error code otherwise.
     */
    external fun stitchImages(
        imagePaths: Array<String>,
        outputPath: String,
        downscaleFactor: Float = 0.5f
    ): Int

    companion object {
        init {
            System.loadLibrary("sary360_native")
        }
    }
}
