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
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.Button;
import android.widget.HorizontalScrollView;


/** Main activity of MediaPipe Face Mesh app. */
public class MainActivity extends AppCompatActivity {

    HorizontalScrollView scroll;
    public static final String COMPLETE = "complete";
    public static final String APP_PREFERENCES = "mysettings";
    SharedPreferences mSettings;
    SharedPreferences setings;


    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);

        mSettings = getSharedPreferences(COMPLETE, Context.MODE_PRIVATE);

        Button calibration = findViewById(R.id.calibration);
        calibration.setOnClickListener(view -> {
            Intent intent = new Intent(getApplicationContext(), Calibration.class);
            startActivity(intent);
        });

        Button fifth = findViewById(R.id.Fifth_excercise);
        fifth.setOnClickListener(view ->
        {
            Intent intent = new Intent(getApplicationContext(), FifthExcercise.class);
            startActivity(intent);
        });

        Button third = findViewById(R.id.third_excercise);
        third.setOnClickListener(view -> {
            Intent activitynew = new Intent(getApplicationContext(), ThirdExercise.class);
            startActivity(activitynew);
        });

//        Button fourth = findViewById(R.id.fourth_excercise);
//        fourth.setOnClickListener(view -> {
//            Intent activityFourth = new Intent(getApplicationContext(), FourthExcercise.class);
//            startActivity(activityFourth);
//        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menu_main, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        switch(item.getItemId()) {
            case R.id.Calibration:
                setings = getSharedPreferences(APP_PREFERENCES, MODE_PRIVATE);
                setings.edit().remove("IrisDiameterLeft").apply();
                setings.edit().remove("IrisDiameterRight").apply();
                setings.edit().remove("HorizontalSideLeft").apply();
                setings.edit().remove("HorizontalSideRight").apply();
                setings.edit().remove("VerticalSide").apply();
                setings.edit().remove("Depth").apply();
                return(true);
        }
        return(super.onOptionsItemSelected(item));
    }
}
