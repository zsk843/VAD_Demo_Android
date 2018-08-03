#include <jni.h>

#include<iostream>

#include <time.h>
#include <cmath>
#include "fft.c"
#include <Eigen/Dense>
#include <Eigen/Core>
#include <complex>
#include <mmult/single_mmult.h>

#define SAMPLE_RATE 16000
#define MAX_FREQUENCY 8000
#define MIN_FREQUENCY 50
#define NUM_FILTERS 64
#define WIDTH 0.5
#define FFT_SIZE 512


using namespace std;
using namespace Eigen;

extern "C" JNIEXPORT jfloatArray
JNICALL
Java_com_example_yckj_vad_1demo_1android_MainActivity_stringFromJNI(
        JNIEnv *env,
        jobject /* this */) {

    float a[9];
    float b[9];
    for(int i = 0; i < 9;i++){
        a[i] = 3;
        b[i] = 2;
    }
    float c[9];
    float a_swap[mc*kc];
    float b_swap[kc*nc];

    single_mmult(3,3,3,a,a_swap,b,b_swap,c);

    jfloatArray res = (*env).NewFloatArray(9);
    (*env).SetFloatArrayRegion(res,0,9,c);

    return res;
}


extern "C"
JNIEXPORT jfloatArray JNICALL
Java_com_example_yckj_vad_1demo_1android_GammaTone_GammaToneFeature(JNIEnv *env, jobject instance,
                                                                    jshortArray data_,
                                                                    jfloatArray factors_, jintArray ifac_, jint frame_length, jint frame_interval, jfloatArray filters_, jdoubleArray win_) {
//    int start = clock();
    float g_floor = (float)exp(-50);
    float pre_emp = 0.97;
    int nfft = (int)pow(2, (int)(ceil(log(frame_length) / log(2))));

    int data_len = (*env).GetArrayLength(data_);
    short* data = (*env).GetShortArrayElements(data_,NULL);
    float* factors = (*env).GetFloatArrayElements(factors_, NULL);
    int* ifac = (*env).GetIntArrayElements(ifac_,NULL);

    float* filters = (*env).GetFloatArrayElements(filters_,NULL);
    int filters_len = (*env).GetArrayLength(filters_);
    int num_filters = filters_len / (nfft/2);

    int samples_len = data_len;
    float samples[samples_len];
    samples[0] = data[0]/32768.0f;
    for(int i = 0; i < samples_len-1;i++)
        samples[i+1] = (data[i+1] - (pre_emp* data[i]))/32768.0f;


    double* win = (*env).GetDoubleArrayElements(win_,NULL);

//    int hamming_len = frame_length;
//    for( int i=0; i<hamming_len; ++i )
//    {
//        win[i] = ( 0.54 - 0.46 * cos(2.0*3.141592653589793*(double)i/(double)(hamming_len-1)));
//    }

    int frame_num = 1+(int)(floor((samples_len-frame_length)/frame_interval));
    float* tmp_data = new float[nfft];
    int j,k;
    int gt_len = nfft/2;
    float real,img;

    float res [frame_num*gt_len];


    for(int i = 0; i < frame_num; i++)
    {

        for(j = 0;j < frame_length; j++)
            tmp_data[j] =(float)(samples[j+i*frame_interval]*win[j]);
        memset(tmp_data+frame_length,0,sizeof(float)*(nfft-frame_length));

        drftf1(nfft, tmp_data, factors,factors+nfft,ifac);

        res[i*gt_len] = 0;

        for(k =0; k < gt_len-1;k++){
            real = tmp_data[k*2+1];
            img = tmp_data[k*2+2];
            float tmp = sqrt(real*real+img*img);
            res[i*gt_len+k+1] = tmp;
        }
    }

    float a_swap[mc*kc];
    float b_swap[kc*nc];
    float dot_res[frame_num*num_filters];
    single_mmult(frame_num,num_filters,gt_len,res,a_swap,filters,b_swap,dot_res);
    for(int i = 0; i < frame_num*num_filters;i++)
    {
       if(dot_res[i]<g_floor)
           dot_res[i] = g_floor;
        dot_res[i] =(float) pow(dot_res[i],1.0/3);
    }
//    cout << "done";
    jfloatArray final_res = (*env).NewFloatArray(frame_num*num_filters);
    (*env).SetFloatArrayRegion(final_res, 0, frame_num*num_filters,dot_res);

//    int end = clock();
//    double dur = (double)(end-start)/CLOCKS_PER_SEC*1000;
//    cout << dur;
    return final_res;
}

extern "C"
JNIEXPORT jfloatArray JNICALL
Java_com_example_yckj_vad_1demo_1android_GammaTone_FFTFactors(JNIEnv *env, jobject instance,
                                                              jint n) {
    int output_dim = 4*n + 15;
    int ifc_dim = n+15;

    float factors[output_dim];
    int ifa[ifc_dim];

    drfti1(n,factors+n,ifa);

    jfloatArray res = env->NewFloatArray(output_dim+ifc_dim);

    float ifac[ifc_dim+output_dim];
    for(int i = 0; i < output_dim; i++)
        ifac[i] = factors[i];
    for(int i = 0; i <ifc_dim; i++)
        ifac[i+output_dim] = ifa[i];

    env->SetFloatArrayRegion(res ,0, output_dim+ifc_dim, ifac);

    return res;
}

extern "C"
JNIEXPORT jfloatArray JNICALL
Java_com_example_yckj_vad_1demo_1android_GammaTone_GammaToneFilters(JNIEnv *env, jobject instance,
                                                                    jint nfft, jint sample_rate,
                                                                    jint num_features, jfloat width,
                                                                    jint min_frequency,
                                                                    jint max_frequency) {
    int output_size = nfft / 2 + 1;
    double EarQ = 9.26449;
    double pi =  3.141592653589793;
    double minBW = 24.7;
    double order = 1;
    double GTord = 4;
    Eigen::ArrayXd channel_indices;
    channel_indices = Eigen::ArrayXd(num_features);


    double res_double[output_size];
    for(int j = 0;j < num_features; j++){
        channel_indices(j) = (double)(64-j);
    }


    ArrayXd cfreqs = (-EarQ) * minBW + (channel_indices * (log(min_frequency + EarQ * minBW)-log(max_frequency + EarQ * minBW)) / num_features).exp() * (max_frequency + EarQ * minBW);

    Eigen::ArrayXd ucirc1 = Eigen::ArrayXd::LinSpaced(output_size,0,output_size-1);

    complex<double> j = complex<double>(0.0,1.0);
    j = j * pi * 2.0;
    ArrayXcd tmp1 = ucirc1 * j;

    ArrayXcd ucirc = (tmp1 / nfft).exp();

    float* res = new float [num_features*output_size];

    for(int i = 0; i < num_features; i++){

        double cf = cfreqs(i);
        double ERB = (double)width * (pow((cf / EarQ),order) + pow(pow(minBW,order),(1.0 / order)));

        double B = 1.019 * 2.0 * pi * ERB;
        complex <double> exp_tmp = (-B) / (double)sample_rate;
        complex<double> r = exp(exp_tmp);
        double theta = 2.0 * pi * cf / (double)sample_rate;
        j = complex<double>(0.0,1.0);
        complex<double> tmp_com1 = j*theta;
        complex<double> pole = r* exp(tmp_com1);

        double T = 1.0f / sample_rate;
        double A11 = -(2.0 * T * cos(2.0 * cf * pi * T) / exp(B * T) + 2.0 * sqrt(3.0 + pow(2.0 ,1.5)) * T * sin(2.0 * cf * pi * T) / exp(B * T)) / 2.0;
        double A12 = -(2.0 * T * cos(2.0 * cf * pi * T) / exp(B * T) - 2.0 * sqrt(3.0 + pow(2.0 ,1.5)) * T * sin(2.0 * cf * pi * T) / exp(B * T)) / 2.0;
        double A13 = -(2.0 * T * cos(2.0 * cf * pi * T) / exp(B * T) + 2.0 * sqrt(3.0 - pow(2.0 ,1.5)) * T * sin(2.0 * cf * pi * T) / exp(B * T)) / 2.0;
        double A14 = -(2.0 * T * cos(2.0 * cf * pi * T) / exp(B * T) - 2.0 * sqrt(3.0 - pow(2.0 ,1.5)) * T * sin(2.0 * cf * pi * T) / exp(B * T)) / 2.0;


        j = complex<double>(0.0,1.0);
        complex<double> T1 = exp(4.0 *    cf * pi * T * j  );
        complex<double>T2 = exp(-(B * T) + 2.0 * j * cf * pi * T);
        double T3 = cos(2.0 * cf * pi * T);
        double T41 = sqrt(3.0 + pow(2.0, 1.5)) * sin(2.0 * cf * pi * T);
        double T42 = sqrt(3.0 - pow(2.0, 1.5)) * sin(2.0 * cf * pi * T);

        complex<double>T51 = -2.0 * T1 * T + 2.0 * T2 * T * (T3 + T41);
        complex<double>T52 = -2.0 * T1 * T + 2.0 * T2 * T * (T3 - T41);
        complex<double>T53 = -2.0 * T1 * T + 2.0 * T2 * T * (T3 + T42);
        complex<double>T54 = -2.0 * T1 * T + 2.0 * T2 * T * (T3 - T42);
        complex<double>T6 = -2.0 / exp(2.0 * B * T) - 2.0 * T1 + 2.0 * (1.0 + T1) / exp(B * T);

        complex<double> gain = abs((T51 * T52 * T53 * T54) / pow(T6 , 4.0));
        complex<double> conjugate_pole = complex<double>(pole.real(), -pole.imag());
        Eigen::ArrayXcd tmp_array1 = (pow(T , 4.0) / gain)*(ucirc + complex<double>((A11 / T),0)).abs()*(ucirc + A12 / T).abs()*(ucirc + A13 / T).abs()*(ucirc + A14 / T).abs();

        Eigen::ArrayXcd tmp_array2 = ((-ucirc+pole)*(-ucirc+conjugate_pole)).abs();

        Eigen::ArrayXcd res1 = tmp_array1*(tmp_array2.pow(-GTord));


        for(int k = 0;k < output_size; k++) {
            res[k*num_features+i] = (float)res1(k).real();
        }
    }


    jfloatArray result = (*env).NewFloatArray(output_size*num_features);
    (*env).SetFloatArrayRegion(result,0,output_size*num_features,res);

    return result;
}









