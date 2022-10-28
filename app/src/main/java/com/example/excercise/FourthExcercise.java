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
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

// ContentResolver dependency
import com.google.mediapipe.formats.proto.LandmarkProto.NormalizedLandmark;
import com.google.mediapipe.solutioncore.CameraInput;
import com.google.mediapipe.solutioncore.SolutionGlSurfaceView;
import com.google.mediapipe.solutions.facemesh.FaceMesh;
import com.google.mediapipe.solutions.facemesh.FaceMeshOptions;
import com.google.mediapipe.solutions.facemesh.FaceMeshResult;

import java.util.List;

/** Main activity of MediaPipe Face Mesh app. */
public class FourthExcercise extends AppCompatActivity {
    private static final String TAG = "MainActivity";

    private float FOCAL_LENGTH;

    private static boolean flag = true;

    private static float ScaleAbs;

    private boolean FlagFirstCheckR = false;

    private boolean FlagSecondCheckR = false;

    private boolean FlagFirstCheckL = false;

    private boolean FlagSecondCheckL = false;

    private static float Depth;

    SharedPreferences mSettings;

    public static final String APP_PREFERENCES = "mysettings";

    public static final String COMPLETE = "complete";

    private static float IrisDiameterRight = 0;

    private static float IrisDiameterLeft = 0;

    private boolean start = false;

    private boolean readyToStart = false;

    int duration = Toast.LENGTH_SHORT;

    long elapsedTime;

    long startTime;

    private int count = 0;

    private float ratior = 0;

    private float ratiol = 0;

    private FaceMesh facemesh;

    private float Scale;

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
    static float sqr(float a){
        return a*a;
    }

    private float DistanceToIris(float iris_size){
        return FOCAL_LENGTH * 11.8f / iris_size;
    }

    private double VectLength(double B, double A){
        return B-A;
    }

    private double Angle(double Ax, double Bx, double Cx, double Ay, double By, double Cy){
        double scalar = VectLength(Bx, Ax) * VectLength(Cx, Ax) + VectLength(By,Ay) * VectLength(Cy, Ay);
        double vectAbs = Math.sqrt(sqr((float) VectLength(Bx, Ax)) + sqr((float) VectLength(By,Ay))) *
                Math.sqrt(sqr((float) VectLength(Cx, Ax)) + sqr((float) VectLength(Cy,Ay)));
        return Math.acos(scalar / vectAbs);
    }

    private InputSource inputSource = InputSource.UNKNOWN;
    // Live camera demo UI and camera components.
    private CameraInput cameraInput;

    private SolutionGlSurfaceView<FaceMeshResult> glSurfaceView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_fourth_excercise);
        mSettings = getSharedPreferences(APP_PREFERENCES, Context.MODE_PRIVATE);
        if(mSettings.contains("VerticalSide"))
            Depth = mSettings.getFloat("Depth", 0);
        if(mSettings.contains("IrisDiameterRight"))
            IrisDiameterRight = mSettings.getFloat("IrisDiameterRight", 0);
        if (mSettings.contains("IrisDiameterLeft"))
            IrisDiameterLeft = mSettings.getFloat("IrisDiameterLeft", 0);
        if (mSettings.contains("noseScale"))
            Scale = mSettings.getFloat("noseScale", 0);
        if(Depth == 0){
            Alert();
        }
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
            FOCAL_LENGTH = (float) (characteristics.get(CameraCharacteristics
                    .LENS_INFO_AVAILABLE_FOCAL_LENGTHS)[0] * 303.03);
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
                    logNoseLandmark(faceMeshResult, /*showPixelValues=*/ false);
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

    private void logNoseLandmark(FaceMeshResult result, boolean showPixelValues) {
        if (result == null || result.multiFaceLandmarks().isEmpty()) {
            return;
        }

        List<NormalizedLandmark> landmarks = result.multiFaceLandmarks().get(0).getLandmarkList();
        float leftIrisX = Unnormalize(true, landmarks.get(474).getX());
        float leftIrisY = Unnormalize(false,landmarks.get(474).getY());
        float rightIrisX = Unnormalize(true,landmarks.get(476).getX());
        float rightIrisY = Unnormalize(false,landmarks.get(476).getY());
        float leftcenterX = Unnormalize(true, landmarks.get(473).getX());
        float leftcenterY = Unnormalize(false, landmarks.get(473).getY());
        float noseBridgeX = Unnormalize(true, landmarks.get(168).getX());
        float noseBridgeY = Unnormalize(false, landmarks.get(168).getY());
        //float leftSideX = Unnormalize(true, landmarks.get(323).getX());
        float leftSideX = Unnormalize(true, landmarks.get(33).getX());
        float leftSideY = Unnormalize(false, landmarks.get(33).getY());
        float rightIrisRSX = Unnormalize(true, landmarks.get(469).getX());
        float rightIrisRSY = Unnormalize(false, landmarks.get(469).getY());
        float rightIrisLSX = Unnormalize(true, landmarks.get(471).getX());
        float rightIrisLSY = Unnormalize(false, landmarks.get(471).getY());
        float nose1 = Unnormalize(false, landmarks.get(6).getY());
        float nose2 = Unnormalize(false, landmarks.get(197).getY());
        float ry1 = landmarks.get(5).getY()*1920f;
        float ry2 = landmarks.get(4).getY()*1920f;

        float ray1 = landmarks.get(373).getY()*1920f;
        float ray2 = landmarks.get(386).getY()*1920f;
        float lay1 = landmarks.get(144).getY()*1920f;
        float lay2 = landmarks.get(160).getY()*1920f;

        ratior = (ray1 - ray2) / (ry2 - ry1);

        ratiol = (lay1 - lay2) / (ry2 - ry1);

        float ScaleNew = nose2 - nose1;

        float irisDist = Distance(leftIrisX, rightIrisX, leftIrisY, rightIrisY);

        float dist = DistanceToIris(irisDist);

        float depth = landmarks.get(323).getZ() * 100;

        float irisDiameterRightNew = Distance(rightIrisRSX, rightIrisLSX, rightIrisRSY, rightIrisLSY);

        float irisDiameterLeftNew = Distance(leftIrisX, rightIrisX, leftIrisY, rightIrisY);

        if(dist < 330 && dist > 270 && !start)
            Accept();
        else
            Decline();

        if(readyToStart){
            Button startbtn = findViewById(R.id.button4);
            startbtn.setOnClickListener(view -> {
                start = true;
                startTime = System.currentTimeMillis();
            });
        }else{
            Button startbtn = findViewById(R.id.button4);
            startbtn.setOnClickListener(view -> {
                PopUp2();
            });
        }

        // Log.v("eaf", "PRAVO   " + IrisDiameterRight + "   " + irisDiameterRightNew);
        if(start) {
            Accept();

            ScaleAbs = Scale / ScaleNew;

            elapsedTime = System.currentTimeMillis() - startTime;
            long elapsedSeconds = elapsedTime / 1000;
            long secondsDisplay = elapsedSeconds % 60;

            if (depth - 5 > Depth && !FlagFirstCheckR) {
                //  Log.v("eaf", "PRAVO");
                if (ratior < 0.8) {
                    Log.v("eaf", "YEEEEEEEEE");
                    FlagFirstCheckR = true;
                }
            }
            if (depth + 10 < Depth && !FlagFirstCheckL) {
                if (ratiol < 0.8) {
                    Log.v("eaf", "YEEEEEEEEE");
                    FlagFirstCheckL = true;
                }
            }
            if (depth - 6 > Depth && !FlagSecondCheckR) {
                Log.v("eaf", IrisDiameterRight + "     " + irisDiameterRightNew * ScaleAbs);
                if (IrisDiameterRight + 5 > irisDiameterRightNew
                        && IrisDiameterRight - 5 < irisDiameterRightNew && ratior > 0.8) {
                    //  Log.v("eaf", "PRAVO");
                    Log.v("eaf", "YEEEEEEEEE");
                    FlagSecondCheckR = true;
                }
            }

            if (depth + 13 < Depth && !FlagSecondCheckL) {
                Log.v("eaf", IrisDiameterLeft + "     " + irisDiameterLeftNew * ScaleAbs + "    " + ratiol);
                if (IrisDiameterLeft + 5 > irisDiameterLeftNew
                        && IrisDiameterLeft - 5 < irisDiameterLeftNew && ratiol > 0.8) {
                    //    Log.v("eaf", "LEVO");
                    Log.v("eaf", "YEEEEEEEEE");
                    FlagSecondCheckL = true;
                }
            }


            if (FlagFirstCheckR && FlagSecondCheckR && FlagFirstCheckL && FlagSecondCheckL && flag) {
                //   flag = false;
                FlagFirstCheckL = false;
                FlagSecondCheckL = false;
                FlagSecondCheckR = false;
                FlagFirstCheckR = false;
                count += 1;
                //ExComplete();
            }

            if (secondsDisplay == 30) {
                start = false;
                count /= 2;
                if (count % 2 != 0)
                    count += 1;
                ExComplete();
            }
        }
    }

    private void Accept(){
        runOnUiThread (new Thread(new Runnable() {
            public void run() {
                TextView accept = findViewById(R.id.accept);
                accept.setBackgroundResource(R.color.green);
                readyToStart = true;
            }
        }));
    }

    private void Decline(){
        runOnUiThread(new Thread(new Runnable() {
            @Override
            public void run() {
                TextView accept = findViewById(R.id.accept);
                accept.setBackgroundResource(R.color.red);
                readyToStart = false;
            }
        }));
    }

    private void PopUp2(){
        runOnUiThread(new Thread(new Runnable() {
            @Override
            public void run() {
                Toast toast = Toast.makeText(getApplicationContext(),
                        "Подвиньтесь ближе или дальше!", duration);
                toast.show();
            }
        }));
    }

    private void PopUp1(){
        runOnUiThread(new Thread(new Runnable() {
            @Override
            public void run() {
                Toast toast = Toast.makeText(getApplicationContext(),
                        "Камера вас не видит!", duration);
                toast.show();
            }
        }));
    }

    private void ExComplete(){
        runOnUiThread(new Thread(new Runnable() {
            @Override
            public void run() {
                //CheckBox mCheckBox = (CheckBox) findViewById(R.id.checkBox2);
                // mCheckBox.setChecked(true);
                Intent intent = new Intent(getApplicationContext(), MainActivity.class);
                mSettings = getSharedPreferences(COMPLETE, Context.MODE_PRIVATE);;
                SharedPreferences.Editor ed = mSettings.edit();
                ed.putBoolean("FourthComplete", true);
                ed.apply();
                AlertDialog.Builder a_builder = new AlertDialog.Builder(FourthExcercise.this);
                a_builder.setMessage("Вы выполнили упражнение " + count + " раз")
                        .setCancelable(false)
                        .setPositiveButton("Назад", (dialogInterface, i) ->
                                startActivity(intent));
                AlertDialog alert = a_builder.create();
                alert.show();
            }
        }));
    }

    private void Alert(){
        runOnUiThread(new Thread(new Runnable() {
            @Override
            public void run() {
                Intent intent = new Intent(getApplicationContext(), Calibration.class);
                AlertDialog.Builder a_builder = new AlertDialog.Builder(FourthExcercise.this);
                a_builder.setMessage("Вы не прошли калибровку!")
                        .setCancelable(false)
                        .setPositiveButton("К калибровке", (dialogInterface, i) ->
                                startActivity(intent));
                AlertDialog alert = a_builder.create();
                alert.show();
            }
        }));
    }
}