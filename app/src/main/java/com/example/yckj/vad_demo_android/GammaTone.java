package com.example.yckj.vad_demo_android;

import android.util.Log;

import java.util.LinkedList;

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
    public float[] mean;
    public float[] std;
    public LinkedList<float[]> history;
    public int size_count = 0;
    private int quque_size = 900;


    static {
        System.loadLibrary("native-lib");
    }

    public GammaTone(){

    }

    public void initialize(){

        frame_length = (int)(time_length*sample_rate);
        frame_interval = (int)(time_interval*sample_rate);
        nfft = (int)Math.pow(2, (int)Math.ceil(Math.log((int)(frame_length )) / Math.log(2)));
        history = new LinkedList<>();

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
        mean = new float[num_filters];
        std = new float[num_filters];
        for(int i = 0; i < num_filters;i++)
        {
            mean[i] = 0;
            std[i] = 1;
        }
    }

    @Override
    public synchronized void SetData(short[] data, long[] dim) {
        if(raw_data == null || raw_data.length!= data.length)
            raw_data = new short[data.length];

        for(int i = 0; i < data.length;i++)
            raw_data[i] = data[i];
        frame_num = 1+(int)(Math.floor((data.length-frame_length)/frame_interval));
        output_dim = new long[]{frame_num,num_filters};
    }

    @Override
    public synchronized float[] GetFeature() {
        float[] tmp = null;
        try{
            tmp = GammaToneFeature(raw_data, factors, ifac, frame_length,frame_interval, filters,win);
            float[] tmp_mean = new float[num_filters];
            float[] tmp_var = new float[num_filters];
            float[] tmp_var2 = new float[num_filters];

            if(size_count == 0)
            {
                history.offer(tmp);
//                for (int j = 0; j < num_filters; j++) {
//                    mean[j] = 0;
//                    for (int k = 0; k < output_dim[0]; k++) {
//                        mean[j] += tmp[k * num_filters + j];
//                    }
//                    mean[j] = mean[j] / output_dim[0];
//
//                    std[j] = 0;
//                    for (int k = 0; k < output_dim[0]; k++) {
//                        std[j] += Math.pow(tmp[k * num_filters + j] - mean[j], 2);
//                    }
//                    std[j] = (float) Math.sqrt(std[j] / output_dim[0]);
//                }
//                size_count =(int)output_dim[0];

                float[] history_raw = new float[(size_count+(int)output_dim[0])*num_filters];
                int list_cout = 0;
                for(float[] seg:history) {
                    for(int i = 0; i < seg.length;i++)
                        history_raw[i+list_cout] = seg[i];
                    list_cout+=seg.length;
                }
                int fram_num_his = history_raw.length/num_filters;
                for(int i = 0; i < num_filters;i++)
                {
                    mean[i] = 0;
                    for(int j = 0; j <fram_num_his;j++)
                        mean[i]+=history_raw[j*num_filters+i];
                    mean[i] = mean[i]/fram_num_his;

                    std[i] = 0;
                    for(int j = 0; j < fram_num_his;j++)
                        std[i] += (float)Math.pow(history_raw[j*num_filters+i]-mean[i],2);
                    std[i] =(float) Math.sqrt(std[i]/fram_num_his);
                }
//                std = test_std;
//                mean = test_mean;

                size_count += output_dim[0];
            }
            else if(size_count<quque_size)
            {
                history.offer(tmp);

////                float[] test_mean = new float[num_filters];
////                float[] test_std = new float[num_filters];
//                for(int i = 0; i < num_filters; i++)
//                {
//                    tmp_mean[i] = mean[i];
//                    mean[i] = 0;
//                    for(int j = 0; j < output_dim[0]; j++)
//                        mean[i] += tmp[j*num_filters+i];
//                    mean[i] = (mean[i]+ (tmp_mean[i]*size_count))/(output_dim[0]+size_count);
//
//                    tmp_var[i] = 0;
//                    for(int j = 0; j<output_dim[0];j++)
//                        tmp_var[i] += tmp[j*num_filters+i]*tmp[j*num_filters+i];
//                    std[i] =(float) Math.sqrt(((std[i]*std[i])*size_count+tmp_var[i]-(output_dim[0]+size_count)*mean[i]*mean[i]+size_count*tmp_mean[i]*tmp_mean[i])/(size_count+output_dim[0]));
//                }

//                history.offer(tmp);

                float[] history_raw = new float[(size_count+(int)output_dim[0])*num_filters];
                int list_cout = 0;
                for(float[] seg:history) {
                    for(int i = 0; i < seg.length;i++)
                        history_raw[i+list_cout] = seg[i];
                    list_cout+=seg.length;
                }
                int fram_num_his = history_raw.length/num_filters;
                for(int i = 0; i < num_filters;i++)
                {
                    mean[i] = 0;
                    for(int j = 0; j <fram_num_his;j++)
                        mean[i]+=history_raw[j*num_filters+i];

                    mean[i] = mean[i]/fram_num_his;
                    std[i] = 0;
                    for(int j = 0; j < fram_num_his;j++)
                        std[i] += (float)Math.pow(history_raw[j*num_filters+i]-mean[i],2);
                    std[i] =(float) Math.sqrt(std[i]/fram_num_his);
                }
                size_count += output_dim[0];

            }else{

                history.offer(tmp);
                float[] replaced_tmp = history.poll();

                float[] history_raw = new float[size_count*num_filters];
                int list_cout = 0;
                for(float[] seg:history) {
                    for(int i = 0; i < seg.length;i++)
                        history_raw[i+list_cout] = seg[i];
                    list_cout+=seg.length;
                }

                int fram_num_his = history_raw.length/num_filters;
                for(int i = 0; i < num_filters;i++)
                {
                    mean[i] = 0;
                    for(int j = 0; j <fram_num_his;j++)
                        mean[i]+=history_raw[j*num_filters+i];
                    mean[i] = mean[i]/fram_num_his;

                    std[i] = 0;
                    for(int j = 0; j < fram_num_his;j++)
                        std[i] += (float)Math.pow(history_raw[j*num_filters+i]-mean[i],2);
                    std[i] =(float) Math.sqrt(std[i]/fram_num_his);
                }

//                String mean_change_output = "mean_change: ";
//                Log.d("MainActivity", "#################");
//                for(int i = 0; i < num_filters; i++)
//                {
//                    tmp_mean[i] = mean[i];
//                    mean[i] = 0;
//                    for(int j = 0; j < output_dim[0];j++) {
//                        mean[i] = mean[i]+((tmp[j * num_filters + i] - replaced_tmp[j * num_filters + i]));
//                    mean[i]  = tmp_mean[i]+(mean[i]/size_count);
////                        Log.d("MainActivity", Float.toString(replaced_tmp[j * num_filters + i])+"-"+Float.toString(tmp[j * num_filters + i]));
//                    }
//                    mean_change_output = mean_change_output+ "raw: "+Float.toString(mean[i])+" test: "+test_mean[i]+" ";
//                   // mean[i] = mean[i]/size_count+tmp_mean[i];
//
//                    tmp_var[i] = 0;
//                    tmp_var2[i] = 0;
//
//                    for(int j = 0; j < output_dim[0]; j++)
//                    {
//                        tmp_var[i] += replaced_tmp[j*num_filters+i]*replaced_tmp[j*num_filters];
//                        tmp_var2[i] += tmp[j*num_filters+i]*tmp[j*num_filters+i];
//                    }
//                    std[i] = (float)Math.sqrt((std[i]*std[i]*size_count+size_count*(-tmp_mean[i]*tmp_mean[i]+mean[i]*mean[i])-(-tmp_var[i]+tmp_var2[i]))/size_count);
//                }
//                Log.d("MainActivity", mean_change_output);
            }

            for(int i = 0; i < num_filters;i++)
                for(int j =0; j <output_dim[0];j++)
                    tmp[j*num_filters+i] = (tmp[j*num_filters+i]-mean[i])/std[i];


        }catch (Exception e)
        {
            e.printStackTrace();
        }
        return tmp;
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
        return output_dim;
    }

    private native float[] GammaToneFeature(short[] data, float[] factors, int[] ifac, int frame_length, int frame_interval, float[] filters, double[] win);
    private native float[] FFTFactors(int n);
    private native float[] GammaToneFilters(int nfft, int sample_rate, int num_features, float width, int min_frequency, int max_frequency);

}
