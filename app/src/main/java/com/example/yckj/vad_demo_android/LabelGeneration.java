package com.example.yckj.vad_demo_android;

public abstract class LabelGeneration {
    public abstract float[] Generate(float[] data);
    public LabelGeneration(int[] input_dim) {
    }
    public abstract float[] GetInputCache();
    public abstract int[] GetOutputDimension();
}
