#include <jni.h>
#include <string>
#include <vector>
#include <opencv2/opencv.hpp>
#include <opencv2/stitching.hpp>
#include <android/log.h>

#define TAG "Sary360Native"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

extern "C" JNIEXPORT jint JNICALL
Java_com_mandreshope_sary360_stitching_NativeStitcher_stitchImages(
        JNIEnv* env,
        jobject /* this */,
        jobjectArray imagePaths,
        jstring outputPath,
        jfloat downscaleFactor) {

    int numImages = env->GetArrayLength(imagePaths);
    std::vector<cv::Mat> imgs;

    for (int i = 0; i < numImages; ++i) {
        jstring pathStr = (jstring)env->GetObjectArrayElement(imagePaths, i);
        const char* path = env->GetStringUTFChars(pathStr, nullptr);
        
        cv::Mat img = cv::imread(path);
        if (img.empty()) {
            LOGE("Could not read image: %s", path);
            env->ReleaseStringUTFChars(pathStr, path);
            continue;
        }

        // Downscale for performance during feature matching
        if (downscaleFactor < 1.0f && downscaleFactor > 0.0f) {
            cv::resize(img, img, cv::Size(), downscaleFactor, downscaleFactor);
        }

        imgs.push_back(img);
        env->ReleaseStringUTFChars(pathStr, path);
    }

    if (imgs.size() < 2) {
        return -1; // Not enough images to stitch
    }

    cv::Mat pano;
    cv::Ptr<cv::Stitcher> stitcher = cv::Stitcher::create(cv::Stitcher::PANORAMA);
    
    // Configure stitcher for spherical mode (default for PANORAMA often works, 
    // but we can be explicit if using the detailed API)
    
    LOGD("Starting stitching of %zu images...", imgs.size());
    cv::Stitcher::Status status = stitcher->stitch(imgs, pano);

    if (status != cv::Stitcher::OK) {
        LOGE("Stitching failed with status: %d", static_cast<int>(status));
        return static_cast<int>(status);
    }

    const char* outPath = env->GetStringUTFChars(outputPath, nullptr);
    bool success = cv::imwrite(outPath, pano);
    env->ReleaseStringUTFChars(outputPath, outPath);

    return success ? 0 : -2; // -2 if save fails
}
