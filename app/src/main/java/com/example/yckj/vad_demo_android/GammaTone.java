package com.example.yckj.vad_demo_android;

import java.math.*;

import static java.lang.Math.cos;

public class GammaTone extends FeatureExtraction {

    private short[] raw_data;
    private long[] output_dim;
    private float[] factors;
    private int[] ifac;
    private float[] filters;

    public int sample_rate = 16000;
    public int filter_min_frequency = 50;
    public int filter_max_frequency = 8000;
    public float filter_width = 0.5f;
    public float time_length = 0.025f;
    public float time_interval = 0.01f;

    private int frame_length;
    private int frame_interval;
    public int num_filters = 64;
    private int frame_num;

    private int nfft;
    private double[] win;


    static {
        System.loadLibrary("native-lib");
    }

    public GammaTone(){
//        nfft = (int)Math.pow(2, (int)Math.ceil(Math.log((int)(frame_length * sample_rate)) / Math.log(2)));
//        frame_length = (int)(time_length*sample_rate);
//        frame_length = (int)(time_interval*sample_rate);
    }

    public void initialize(){

        frame_length = (int)(time_length*sample_rate);
        frame_interval = (int)(time_interval*sample_rate);
        nfft = (int)Math.pow(2, (int)Math.ceil(Math.log((int)(frame_length )) / Math.log(2)));

        //Filters factors calculation
        filters = GammaToneFilters(nfft,sample_rate,num_filters,filter_width,filter_min_frequency,filter_max_frequency);

        // Factors of rfft
        factors = new float[4*nfft+15];
        ifac = new int[nfft+15];
        float[] res = FFTFactors(nfft);
        int dim = 4*nfft+15;
        int ifc_dim = nfft + 15;
        for(int i = 0; i < dim; i++)
        {
            factors[i] = res[i];
        }
        for(int i = 0; i < ifc_dim; i++)
            ifac[i] = (int)res[dim+i];

        //Hamming window calculation
        hamming_win();
    }

//    public GammaTone(int n){
//        factors = new float[4*n+15];
//        ifac = new int[n+15];
//        float[] res = FFTFactors(n);
//        int dim = 4*n+15;
//        int ifc_dim = n + 15;
//        for(int i = 0; i < dim; i++)
//        {
//            factors[i] = res[i];
//        }
//        for(int i = 0; i < ifc_dim; i++)
//            ifac[i] = (int)res[dim+i];
//
//    }

    @Override
    public void SetData(short[] data, long[] dim) {
        raw_data = data;
        frame_num = 1+(int)(Math.floor((data.length-frame_length)/frame_interval));
        output_dim = new long[]{frame_num,num_filters};
    }

    @Override
    public float[] GetFeature() {
        return GammaToneFeature(raw_data, factors, ifac, frame_length,frame_interval, filters,win);
    }

    private void hamming_win(){
        int hamming_len = frame_length;
        win= new double[hamming_len];

        for( int i=0; i<hamming_len; ++i )
        {
            win[i] =(0.54 - 0.46 * Math.cos((2.0*3.141592653589793*i/(hamming_len-1.0f))));
        }

    }

    @Override
    public long[] GetDim() {
        return new long[0];
    }

    private native float[] GammaToneFeature(short[] data, float[] factors, int[] ifac, int frame_length, int frame_interval, float[] filters, double[] win);
    private native float[] FFTFactors(int n);
    private native float[] GammaToneFilters(int nfft, int sample_rate, int num_features, float width, int min_frequency, int max_frequency);

}
