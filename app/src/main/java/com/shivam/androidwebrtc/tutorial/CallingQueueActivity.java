package com.shivam.androidwebrtc.tutorial;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import android.util.Log;
import android.view.View;
import android.widget.Toast;

import com.myhexaville.androidwebrtc.R;
import com.shivam.androidwebrtc.LauncherActivity;
import com.shivam.androidwebrtc.SocketListener;
import com.shivam.androidwebrtc.SocketManager;

public class CallingQueueActivity extends AppCompatActivity implements SocketListener {
    SharedPreferences sharedPreferences ;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_calling_queue);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        sharedPreferences= this.getSharedPreferences("checkStarted", Context.MODE_PRIVATE);
        SocketManager.getInstance().addListener(this);
        SocketManager.getInstance().socket.emit("joinCallQueue");
        sharedPreferences.edit().putBoolean("isStarted", false).apply();
    }

    public void showMessage(String message){
        CallingQueueActivity.this.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(CallingQueueActivity.this, message, Toast.LENGTH_SHORT).show();
            }
        });
    }
    @Override
    public void onConnection() {
        showMessage("connted");
    }

    @Override
    public void onDisconnect() {
        showMessage("you have been disconect");
            startActivity(new Intent(this, LauncherActivity.class));
    }

    @Override
    public void onJoined() {

    }


    @Override
    public void onLetStartCall(String roomId, Boolean isDoCall) {
       if(!sharedPreferences.getBoolean("isStarted",false)) {
           Intent t = new Intent(this, CompleteActivity.class);
           t.putExtra("node", getIntent().getStringExtra("node"));
           t.putExtra("stun", getIntent().getStringExtra("stun"));
           t.putExtra("turn", getIntent().getStringExtra("turn"));
           t.putExtra("user", getIntent().getStringExtra("user"));
           t.putExtra("pass", getIntent().getStringExtra("pass"));
           t.putExtra("roomId", roomId);
           t.putExtra("isDoCall", isDoCall);
           startActivity(t);
           sharedPreferences.edit().putBoolean("isStarted", true).apply();
           Log.d("lol", "Let startcall queue");
       }
    }

    @Override
    public void onMessage(Object... message) {

    }

    @Override
    public void onBuddyLeft() {

    }

    @Override
    protected void onDestroy() {
        showMessage("left Call queue");
        SocketManager.getInstance().socket.emit("leftCallQueue");
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        SocketManager.getInstance().socket.emit("leftCallQueue");
        super.onBackPressed();
    }
}