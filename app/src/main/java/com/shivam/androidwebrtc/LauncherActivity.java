package com.shivam.androidwebrtc;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.myhexaville.androidwebrtc.R;
import com.shivam.androidwebrtc.tutorial.CompleteActivity;

public class LauncherActivity extends AppCompatActivity {
    EditText edtNodeServer,edtStun,edtTurn,edtUser,edtPass;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_launch);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        edtNodeServer = findViewById(R.id.edtNodeServer);
        edtStun = findViewById(R.id.edtStun);
        edtTurn = findViewById(R.id.edtTurn);
        edtUser = findViewById(R.id.edtUser);
        edtPass = findViewById(R.id.edtPass);
        edtNodeServer.setText("http://35.220.164.0/");
        edtStun.setText("stun:dungtv.tk");
        edtTurn.setText("turn:dungtv.tk");
        edtUser.setText("dungtv");
        edtPass.setText("1234");
    }
    public void openSampleSocketActivity(View view) {
        Intent t =new Intent(this, CompleteActivity.class);
        t.putExtra("node" , edtNodeServer.getText().toString());
        t.putExtra("stun", edtStun.getText().toString());
        t.putExtra("turn", edtTurn.getText().toString());
        t.putExtra("user", edtUser.getText().toString());
        t.putExtra("pass", edtPass.getText().toString());
        startActivity(t);

    }
}
