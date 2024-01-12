// Copyright 2021 The MediaPipe Authors.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.example.excercise;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.os.Bundle;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.Toast;
// ContentResolver dependency
import com.google.mediapipe.formats.proto.LandmarkProto.NormalizedLandmark;
import com.google.mediapipe.solutioncore.CameraInput;
import com.google.mediapipe.solutioncore.SolutionGlSurfaceView;
import com.google.mediapipe.solutions.facemesh.FaceMesh;
import com.google.mediapipe.solutions.facemesh.FaceMeshOptions;
import com.google.mediapipe.solutions.facemesh.FaceMeshResult;

import java.util.Arrays;
import java.util.List;

/** Main activity of MediaPipe Face Mesh app. */
public class Calibration extends AppCompatActivity {
    private static final String TAG = "MainActivity";

    public static final String APP_PREFERENCES = "mysettings";

    SharedPreferences mSettings;

    private float FOCAL_LENGTH;

    private static float RightEyeDiameter;

    private static float HorizontalSideLeft;

    private static float HorizontalSideRight;

    private static float LeftEyeDiameter;

    private static float VerticalSide;

    private static float DepthForRightSide;

    private static float DepthUpDown;

    private static float Scale;

    private FaceMesh facemesh;
    // Run the pipeline and the model inference on GPU or CPU.
    private static final boolean RUN_ON_GPU = true;

    private enum InputSource {
        UNKNOWN,
        CAMERA,
    }
    private float Unnormalize(boolean id, float point)
    {
        if(id)
        {
            return point * 1080;
        }
        else{
            return point * 1920;
        }
    }
    private float Distance(float X1,float X2, float Y1, float Y2)
    {
        return (float)Math.sqrt(sqr(X1 - X2) + sqr(Y1 - Y2));
    }

    private static float sqr(double a){
        return (float) (a*a);
    }

    private float DistanceToIris(float iris_size){
        return FOCAL_LENGTH * 11.8f / iris_size;
    }

    private double VectLength(double B, double A){
        return B-A;
    }

    private double Angle(double Ax, double Bx, double Cx, double Ay, double By, double Cy){
        double scalar = VectLength(Bx, Ax) * VectLength(Cx, Ax) + VectLength(By,Ay) * VectLength(Cy, Ay);
        double vectAbs = Math.sqrt(sqr(VectLength(Bx, Ax)) + sqr(VectLength(By,Ay))) *
                Math.sqrt(sqr(VectLength(Cx, Ax)) + sqr(VectLength(Cy,Ay)));
        return Math.acos(scalar / vectAbs);
    }

    private InputSource inputSource = InputSource.UNKNOWN;

    // Live camera demo UI and camera components.
    private CameraInput cameraInput;

    private SolutionGlSurfaceView<FaceMeshResult> glSurfaceView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        getSupportActionBar().hide();
        super.onCreate(savedInstanceState);
        setContentView(R.layout.calib);
        // TODO: Add a toggle to switch between the original face mesh and attention mesh.
        setupLiveDemoUiComponents();

    }

    @Override
    protected void onResume() {
        super.onResume();
        try {
            Context context = getApplicationContext();
            CameraManager manager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
            String[] cameraIds = manager.getCameraIdList();
            CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraIds[1]);
            //303 gain in scientific way!
            FOCAL_LENGTH = (float) (characteristics.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)[0] * 303.03);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }

        // Restarts the camera and the opengl surface rendering.
        cameraInput = new CameraInput(this);
        cameraInput.setNewFrameListener(textureFrame -> facemesh.send(textureFrame));
        glSurfaceView.post(this::startCamera);
        glSurfaceView.setVisibility(View.VISIBLE);

    }

    @Override
    protected void onPause() {
        super.onPause();
        glSurfaceView.setVisibility(View.GONE);
        cameraInput.close();
        finish();
    }

    /**
     * Sets up the UI components for the live demo with camera input.
     */
    private void setupLiveDemoUiComponents() {
        stopCurrentPipeline();
        setupStreamingModePipeline(InputSource.CAMERA);
    }

    /**
     * Sets up core workflow for streaming mode.
     */
    private void setupStreamingModePipeline(InputSource inputSource) {
        this.inputSource = inputSource;
        // Initializes a new MediaPipe Face Mesh solution instance in the streaming mode.
        facemesh =
                new FaceMesh(
                        this,
                        FaceMeshOptions.builder()
                                .setStaticImageMode(false)
                                .setRefineLandmarks(true)
                                .setRunOnGpu(RUN_ON_GPU)
                                .build());
        facemesh.setErrorListener((message, e) -> Log.e(TAG, "MediaPipe Face Mesh error:" + message));
        cameraInput = new CameraInput(this);
        cameraInput.setNewFrameListener(textureFrame -> facemesh.send(textureFrame));

        // Initializes a new Gl surface view with a user-defined FaceMeshResultGlRenderer.
        glSurfaceView =
                new SolutionGlSurfaceView<>(this, facemesh.getGlContext(), facemesh.getGlMajorVersion());
        glSurfaceView.setSolutionResultRenderer(new FaceMeshResultGlRenderer());
        glSurfaceView.setRenderInputImage(true);

        facemesh.setResultListener(
                faceMeshResult -> {
                    logNoseLandmark(faceMeshResult /*showPixelValues=*/);
                    glSurfaceView.setRenderData(faceMeshResult);
                    glSurfaceView.requestRender();
                });

        // The runnable to start camera after the gl surface view is attached.
        // For video input source, videoInput.start() will be called when the video uri is available.
        if (inputSource == InputSource.CAMERA) {
            glSurfaceView.post(this::startCamera);
        }

        // Updates the preview layout.
        FrameLayout frameLayout = findViewById(R.id.preview_display_layout);
        frameLayout.removeAllViewsInLayout();
        frameLayout.addView(glSurfaceView);
        glSurfaceView.setVisibility(View.VISIBLE);
        frameLayout.requestLayout();
    }

    private void startCamera() {
        cameraInput.start(
                this,
                facemesh.getGlContext(),
                CameraInput.CameraFacing.FRONT,
                glSurfaceView.getWidth(),
                glSurfaceView.getHeight());
    }

    private void stopCurrentPipeline() {
        if (cameraInput != null) {
            cameraInput.setNewFrameListener(null);
            cameraInput.close();
        }
        if (glSurfaceView != null) {
            glSurfaceView.setVisibility(View.GONE);
        }
        if (facemesh != null) {
            facemesh.close();
        }
    }


    private int count = 0;
    private double[][] correct = new double[8][10];

    private float[] medianTrue = new float[8];
    private float[] medianFar = new float[8];
    private float[] medianPhoneTurnL = new float[8];
    private float[] medianPhoneTurnR = new float[8];
    private byte flag = 0;
    private float ratior = 0;
    private float ratiol = 0;
    private median median = new median();

    private void logNoseLandmark(FaceMeshResult result) {
        if (result == null || result.multiFaceLandmarks().isEmpty()) {
            return;
        }

        List<NormalizedLandmark> landmarks = result.multiFaceLandmarks().get(0).getLandmarkList();
        float leftIrisX = Unnormalize(true, landmarks.get(474).getX());
        float leftIrisY = Unnormalize(false, landmarks.get(474).getY());
        float rightIrisX = Unnormalize(true, landmarks.get(476).getX());
        float rightIrisY = Unnormalize(false, landmarks.get(476).getY());
        float leftSideX = Unnormalize(true, landmarks.get(33).getX());
        float leftSideY = Unnormalize(false, landmarks.get(33).getY());
        float leftcenterX = Unnormalize(true, landmarks.get(473).getX());
        float leftcenterY = Unnormalize(false, landmarks.get(473).getY());
        float noseBridgeX = Unnormalize(true, landmarks.get(168).getY());
        float noseBridgeY = Unnormalize(false, landmarks.get(168).getY());
        float rightIrisRSX = Unnormalize(true, landmarks.get(469).getX());
        float rightIrisRSY = Unnormalize(false, landmarks.get(469).getY());
        float rightIrisLSX = Unnormalize(true, landmarks.get(471).getX());
        float rightIrisLSY = Unnormalize(false, landmarks.get(471).getY());
        float rightSideX = Unnormalize(true, landmarks.get(263).getX());
        float rightSideY = Unnormalize(true, landmarks.get(263).getY());
        float foreheadUpX = Unnormalize(true, landmarks.get(9).getX());
        float foreheadUpY = Unnormalize(false, landmarks.get(9).getY());
        float foreheadDownX = Unnormalize(true, landmarks.get(10).getX());
        float foreheadDownY = Unnormalize(false, landmarks.get(10).getY());

        float ry1 = landmarks.get(5).getY()*1920f;
        float ry2 = landmarks.get(4).getY()*1920f;
        float ray1 = landmarks.get(373).getY()*1920f;
        float ray2 = landmarks.get(387).getY()*1920f;
        float lay1 = landmarks.get(144).getY()*1920f;
        float lay2 = landmarks.get(160).getY()*1920f;

        float nose1 = Unnormalize(false, landmarks.get(6).getY());
        float nose2 = Unnormalize(false, landmarks.get(197).getY());


        float irisDist = Distance(leftIrisX, rightIrisX, leftIrisY, rightIrisY);

        float dist = DistanceToIris(irisDist);


        Log.v(TAG, String.valueOf(dist));

        if (dist < 330 && dist > 270 && flag == 0) {
            if (count < 10) {

                //HorizontalSideLeft
                correct[0][count] = Distance(leftSideX, rightIrisX, leftSideY, rightIrisY);

                //HorizontalSideRight
                correct[1][count] = Distance(rightSideX, rightIrisRSX, rightSideY, rightIrisRSY) / 2;

                //LeftEyeDiameter
                correct[2][count] = Distance(leftIrisX, rightIrisX, leftIrisY, rightIrisY);

                //RightEyeDiameter
                correct[3][count] = Distance(rightIrisRSX, rightIrisLSX, rightIrisRSY, rightIrisLSY);

                //DepthForRightSide
                correct[4][count] = landmarks.get(323).getZ() * 100;

                //DepthUpDown
                correct[5][count] = Distance(foreheadUpX, foreheadDownX, foreheadUpY, foreheadDownY);

                //VerticalSide
                correct[6][count] = (float) Angle(noseBridgeX, leftcenterX, noseBridgeX + 10
                        , noseBridgeY, leftcenterY, noseBridgeY + 10);

                //Scale
                correct[7][count] = nose2 - nose1;

                count++;

                Log.v("eaf", HorizontalSideLeft + "     ");


            } else {
                Log.v("dada", Arrays.toString(correct[4]));
                medianTrue = median.Median(correct);
                count = 0;
                flag++;
            }
                Log.v("pip", "WORK!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
                PopUp("Теперь отведите камеру подальше!");
                runThread();
            }

//        if (dist < 550 && dist > 510 && flag == 1) {
//
//            if (count < 10) {
//                //HorizontalSideLeft
//                correct[0][count] = Distance(leftSideX, rightIrisX, leftSideY, rightIrisY);
//
//                //HorizontalSideRight
//                correct[1][count] = Distance(rightSideX, rightIrisRSX, rightSideY, rightIrisRSY) / 2;
//
//                //LeftEyeDiameter
//                correct[2][count] = Distance(leftIrisX, rightIrisX, leftIrisY, rightIrisY);
//
//                //RightEyeDiameter
//                correct[3][count] = Distance(rightIrisRSX, rightIrisLSX, rightIrisRSY, rightIrisLSY);
//
//                //DepthForRightSide
//                correct[4][count] = landmarks.get(323).getZ() * 100;
//
//                //DepthUpDown
//                correct[5][count] = Distance(foreheadUpX, foreheadDownX, foreheadUpY, foreheadDownY);
//
//                //VerticalSide
//                correct[6][count] = (float) Angle(noseBridgeX, leftcenterX, noseBridgeX + 10
//                        , noseBridgeY, leftcenterY, noseBridgeY + 10);
//
//                //Scale
//                correct[7][count] = dist / irisDist;
//
//                count++;
//            } else {
//                    medianFar = median.Median(correct);
//                }
//                flag++;
//                Log.v("pip", "WORKFAR!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
//                PopUp("Теперь смотрите в камеру и двигайте телефон направо");
//            }
//
//        double depth = landmarks.get(323).getZ() * 100;
//
//        double depthTrue = medianTrue[4];
//
//        if (dist < 550 && dist > 510 && flag == 2) {
//
//            if (count < 10) {
//
//                //LeftEyeDiameter
//                correct[1][count] = Distance(leftIrisX, rightIrisX, leftIrisY, rightIrisY);
//
//                //RightEyeDiameter
//                correct[2][count] = Distance(rightIrisRSX, rightIrisLSX, rightIrisRSY, rightIrisLSY);
//
//                //DepthForRightSide
//                correct[3][count] = landmarks.get(323).getZ() * 100;
//
//                //Scale
//                correct[4][count] = dist / irisDist;
//
//                count++;
//            } else {
//                medianPhoneTurnR = median.Median(correct);
//                }
//                flag++;
//                Log.v("pip", "WORKPhoneTurnR!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
//                PopUp("Теперь смотрите в камеру и двигайте телефон налево");
//            }
//
//        if (dist < 550 && dist > 510 && flag == 1) {
//
//            if (count < 10) {
//                //HorizontalSideLeft
//                correct[0][count] = Distance(leftSideX, rightIrisX, leftSideY, rightIrisY);
//
//                //HorizontalSideRight
//                correct[1][count] = Distance(rightSideX, rightIrisRSX, rightSideY, rightIrisRSY) / 2;
//
//                //LeftEyeDiameter
//                correct[2][count] = Distance(leftIrisX, rightIrisX, leftIrisY, rightIrisY);
//
//                //RightEyeDiameter
//                correct[3][count] = Distance(rightIrisRSX, rightIrisLSX, rightIrisRSY, rightIrisLSY);
//
//                //DepthForRightSide
//                correct[4][count] = landmarks.get(323).getZ() * 100;
//                Log.v("depth", String.valueOf(landmarks.get(323).getZ()*100));
//
//                //DepthUpDown
//                correct[5][count] = Distance(foreheadUpX, foreheadDownX, foreheadUpY, foreheadDownY);
//
//                //VerticalSide
//                correct[6][count] = (float) Angle(noseBridgeX, leftcenterX, noseBridgeX + 10
//                        , noseBridgeY, leftcenterY, noseBridgeY + 10);
//
//                //Scale
//                correct[7][count] = dist / irisDist;
//
//                count++;
//
//            } else {
//                medianPhoneTurnL = median.Median(correct);
//                }
//                flag++;
//                Log.v("pip", "WORKPhoneTurnL!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
//                runThread();
//            }
    }

    private void PopUp(String text){
        runOnUiThread(new Thread(new Runnable() {
            @Override
            public void run() {
                Toast toast = Toast.makeText(getApplicationContext(),
                        text, Toast.LENGTH_SHORT);
                toast.show();
            }
        }));
    }

    private void runThread(){
        runOnUiThread (new Thread(new Runnable() {
            public void run() {
                //Log.v("depth", String.valueOf(medianTrue[4]));
                Intent intent = new Intent(getApplicationContext(), MainActivity.class);
                mSettings = getSharedPreferences(APP_PREFERENCES, Context.MODE_PRIVATE);;
                SharedPreferences.Editor ed = mSettings.edit();
                ed.putFloat("IrisDiameterLeft", (float) medianTrue[2]);
                ed.putFloat("IrisDiameterRight", (float) medianTrue[3]);
                ed.putFloat("HorizontalSideLeft", (float)medianTrue[0]);
                ed.putFloat("HorizontalSideRight", (float)medianTrue[1]);
                ed.putFloat("VerticalSide", (float) medianTrue[6]);
                ed.putFloat("Depth", (float) medianTrue[4]);
                ed.putFloat("DepthUpDown", (float) medianTrue[5]);
                ed.putFloat("noseScale", (float) medianTrue[7]);
                ed.apply();
                //Log.v("CALIBRA", VerticalSide + " " + "AAAAAAAAAAAAAAAAAAAAAAA");
                AlertDialog.Builder a_builder = new AlertDialog.Builder(Calibration.this);
                a_builder.setMessage("Калибровка завершена")
                        .setCancelable(false)
                        .setPositiveButton("Назад", (dialogInterface, i) ->
                                startActivity(intent));
                AlertDialog alert = a_builder.create();
                alert.show();
            }
        }));
    }
}