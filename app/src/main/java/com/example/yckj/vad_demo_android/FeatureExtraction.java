package com.example.yckj.vad_demo_android;

abstract class FeatureExtraction {
    public abstract void SetData(short[] data, long[] dim);
    public abstract float[] GetFeature();
    public abstract  long[] GetDim();
}
