package com.example.yckj.vad_demo_android;
import org.tensorflow.contrib.android.TensorFlowInferenceInterface;
import android.content.res.AssetManager;

public class VADModel {
    private TensorFlowInferenceInterface inferenceInterface;
    private String INPUT_NAME;
    private String OUTPUT_NAME;
    public FeatureExtraction extarctor;
    public LabelGeneration generator;
    public int dimension = 64;
    public VADModel(AssetManager manager,String modelName, String inputName, String outputName, FeatureExtraction extractor, LabelGeneration generator){
        inferenceInterface = new TensorFlowInferenceInterface(manager, modelName);
        INPUT_NAME = inputName;
        OUTPUT_NAME = outputName;
        this.extarctor = extractor;
        this.generator = generator;

    }

    public void SetParameters(String name, float[] data, long... dim){
        inferenceInterface.feed(name, data, dim);
    }

    public void SetParameters(String name, int[] data, long... dim){
        inferenceInterface.feed(name, data, dim);
    }

    public float[] Inference(short[] data, long[] dim){
        float[] features = null;
        long[] feature_dim;
        if (extarctor == null){
//            features= data;
            feature_dim = dim;
        }
        else{
            extarctor.SetData(data, dim);
            features = extarctor.GetFeature();
            feature_dim = extarctor.GetDim();
        }

        inferenceInterface.feed(INPUT_NAME,features,feature_dim);

        float[] model_output = generator.GetInputCache();
        inferenceInterface.run(new String[]{OUTPUT_NAME});
        inferenceInterface.fetch(OUTPUT_NAME,model_output);
        return generator.Generate(model_output);
    }

}
