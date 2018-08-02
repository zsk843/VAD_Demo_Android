/**************************************************************************************************
*                                                                                                 *
* This file is part of DeepNet.                                                                   *
*                                                                                                 *
* DeepNet -- Deep ConvNet Forward Tool With Embedded Optimization.                                *
* Copyright (C) 2016-2018 by ZhangDanfeng.                                                        *
* Developed at CloudWalk (ShangHai China).                                                        *
* All rights reserved.                                                                            *
**************************************************************************************************/
#pragma once

#define kc 256
#define mc 512
#define nc 4096

#ifdef __cplusplus
extern "C" {
#endif

/**
 * @brief single float matrix multiplication
 * @details
 * @param a_rows the rows of matrix a
 * @param b_cols the cols of matrix b
 * @param a_cols the cols of matrix a
 * @param a input left matrix
 * @param swap_a mem space of size mc*kc for packing a
 * @param b input right matrix
 * @param swap_b mem space of size kc*nc for packing b
 * @param c output matrix
 */
void single_mmult(size_t a_rows
    , size_t b_cols
    , size_t a_cols
    , float *a
    , float *swap_a
    , float *b
    , float *swap_b
    , float *c);

void single_mmult_pack_a(size_t a_rows
    , size_t a_cols
    , float *a
    , size_t *num_of_packed_a
    , float *packed_a);

void single_mmult_with_packed_a(size_t a_rows
    , size_t b_cols
    , size_t a_cols
    , float *a
    , float *b
    , float *swap_b
    , float *c);

void single_mmult_pack_b(size_t b_rows
    , size_t b_cols
    , float *b
    , size_t *num_of_packed_b
    , float *packed_b);

void single_mmult_with_packed_b(size_t a_rows
    , size_t b_cols
    , size_t a_cols
    , float *a
    , float *swap_a
    , float *b
    , float *c);

#ifdef __cplusplus
} /* extern "C" */
#endif
