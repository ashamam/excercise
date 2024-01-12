package com.example.excercise;

import java.util.Arrays;

public class median {
    public float[] Median(double[][] correct){
        float[] median = new float[8];

        for (int i = 0; i < correct.length; i++) {
            // Сортируем столбец
            Arrays.sort(correct[i]);

            // Вычисляем медиану
            int length = correct[i].length;

            if (length % 2 == 0) {
                median[i] = (float) ((correct[i][length / 2]
                        + correct[i][length / 2 - 1]) / 2.0);
            } else {
                median[i] = (float) correct[i][length / 2];
            }
        }
        return median;
    }
}
