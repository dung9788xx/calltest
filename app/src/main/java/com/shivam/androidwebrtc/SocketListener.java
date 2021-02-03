package com.shivam.androidwebrtc;

import java.util.Arrays;

public interface SocketListener {
    void onConnection();
    void onDisconnect();
    void onJoined();
    void onLetStartCall(String roomId, Boolean isDoCall);
    void onMessage(Object... message);
    void onBuddyLeft();
}
