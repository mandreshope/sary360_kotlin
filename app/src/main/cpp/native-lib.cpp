#include <android/log.h>
#include <jni.h>
#include <opencv2/core/ocl.hpp>
#include <opencv2/opencv.hpp>
#include <opencv2/stitching.hpp>
#include <opencv2/stitching/detail/blenders.hpp>
#include <opencv2/stitching/detail/exposure_compensate.hpp>
#include <opencv2/stitching/detail/seam_finders.hpp>
#include <string>
#include <vector>

#define TAG "Sary360Native"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

extern "C" {

JNIEXPORT void JNICALL
Java_com_mandreshope_sary360_renderer_SphereRenderer_drawFrame(JNIEnv *env,
                                                               jobject thiz) {
  // TODO: Implement rendering logic here
}

JNIEXPORT void JNICALL
Java_com_mandreshope_sary360_renderer_SphereRenderer_surfaceChanged(
    JNIEnv *env, jobject thiz, jint width, jint height) {
  // TODO: Implement surface changed logic here
}

JNIEXPORT void JNICALL
Java_com_mandreshope_sary360_renderer_SphereRenderer_surfaceCreated(
    JNIEnv *env, jobject thiz) {
  // TODO: Implement surface created logic here
}

JNIEXPORT jint JNICALL
Java_com_mandreshope_sary360_stitching_NativeStitcher_stitchImages(
    JNIEnv *env, jobject /* this */, jobjectArray imagePaths,
    jstring outputPath, jfloat downscaleFactor) {

  int numImages = env->GetArrayLength(imagePaths);
  std::vector<cv::Mat> imgs;

  cv::Ptr<cv::ORB> orb = cv::ORB::create();

  for (int i = 0; i < numImages; ++i) {
    jstring pathStr = (jstring)env->GetObjectArrayElement(imagePaths, i);
    const char *path = env->GetStringUTFChars(pathStr, nullptr);

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

    std::vector<cv::KeyPoint> keypoints;
    orb->detect(img, keypoints);
    if (keypoints.size() < 10) {
      LOGE("Skipping image %s: too few features (%zu). Causes FLANN crash.",
           path, keypoints.size());
      env->ReleaseStringUTFChars(pathStr, path);
      continue;
    }

    imgs.push_back(img);
    env->ReleaseStringUTFChars(pathStr, path);
  }

  if (imgs.size() < 2) {
    return -1; // Not enough images to stitch
  }

  // Disable OpenCL to avoid driver hangs on Android
  cv::ocl::setUseOpenCL(false);

  cv::Mat pano;
  cv::Ptr<cv::Stitcher> stitcher = cv::Stitcher::create(cv::Stitcher::PANORAMA);

  // Use SIFT with a low cap on features. OpenCV's Stitcher matcher expects
  // floating-point descriptors (SIFT/SURF). Using ORB (binary) causes Flann
  // matcher crashes/hangs on some devices.
  auto finder = cv::SIFT::create();
  finder->setNFeatures(
      300); // Cap features to avoid N^2 matching explosion with 60 images
  stitcher->setFeaturesFinder(finder);

  // Configure stitcher for extreme performance (mandatory for 50+ images on
  // Android)
  stitcher->setRegistrationResol(0.2); // Very low for fast feature matching
  stitcher->setSeamEstimationResol(0.1);
  stitcher->setCompositingResol(0.6); // Compress final panorama to ~0.6
                                      // Megapixels (~900x600) max internally
  stitcher->setPanoConfidenceThresh(0.3); // High tolerance for missed linkages
  stitcher->setWaveCorrection(false);     // MUST remain false, wave correction
                                          // hangs on 50+ images on mobile

  // Massive speedups for many images (bypasses slow GraphCut processing):
  stitcher->setSeamFinder(cv::makePtr<cv::detail::VoronoiSeamFinder>());
  stitcher->setExposureCompensator(
      cv::makePtr<cv::detail::NoExposureCompensator>());
  stitcher->setBlender(
      cv::makePtr<cv::detail::FeatherBlender>()); // Simpler blending

  LOGD("Starting stitching of %zu images...", imgs.size());
  cv::Stitcher::Status status = stitcher->stitch(imgs, pano);

  if (status != cv::Stitcher::OK) {
    LOGE("Stitching failed with status: %d", static_cast<int>(status));
    return static_cast<int>(status);
  }

  // Force 2:1 aspect ratio (equirectangular format) so OpenGL sphere doesn't
  // stretch it vertically
  int requiredHeight = pano.cols / 2;
  if (pano.rows < requiredHeight) {
    int paddingTotal = requiredHeight - pano.rows;
    int paddingTop = paddingTotal / 2;
    int paddingBottom = paddingTotal - paddingTop;
    cv::copyMakeBorder(pano, pano, paddingTop, paddingBottom, 0, 0,
                       cv::BORDER_CONSTANT, cv::Scalar(0, 0, 0));
    LOGD("Padded image vertically by %d pixels to reach 2:1 eq-rectangular "
         "ratio.",
         paddingTotal);
  } else if (pano.rows > requiredHeight) {
    // Very rare, but if it's too tall, crop it
    int extra = pano.rows - requiredHeight;
    cv::Rect roi(0, extra / 2, pano.cols, requiredHeight);
    pano = pano(roi);
  }

  const char *outPath = env->GetStringUTFChars(outputPath, nullptr);
  bool success = cv::imwrite(outPath, pano);
  env->ReleaseStringUTFChars(outputPath, outPath);

  return success ? 0 : -2; // -2 if save fails
}
}
