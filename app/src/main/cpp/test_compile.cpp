#include <opencv2/stitching.hpp>
#include <opencv2/features2d.hpp>

void test() {
    cv::Ptr<cv::Stitcher> stitcher = cv::Stitcher::create(cv::Stitcher::PANORAMA);
    stitcher->setFeaturesFinder(cv::ORB::create(300));
}
