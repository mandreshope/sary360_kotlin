#include <opencv2/stitching.hpp>
#include <iostream>

void test() {
    cv::Ptr<cv::Stitcher> stitcher = cv::Stitcher::create(cv::Stitcher::PANORAMA);
    stitcher->setRegistrationResol(0.3);
    stitcher->setSeamEstimationResol(0.1);
    stitcher->setCompositingResol(0.8);
    stitcher->setPanoConfidenceThresh(0.5);
    stitcher->setWaveCorrection(false);
}
