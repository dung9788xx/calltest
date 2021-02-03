package com.shivam.androidwebrtc;

import android.util.Log;

import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;

import io.socket.client.IO;
import io.socket.client.Socket;

import static io.socket.client.Socket.EVENT_CONNECT;
import static io.socket.client.Socket.EVENT_DISCONNECT;

public class SocketManager {
    List<SocketListener> socketListeners = new ArrayList<>();
    public Socket socket;
    private static SocketManager instance;
    public void addListener(SocketListener listener){
        socketListeners.add(listener);
        Log.d("connected", socketListeners.size()+"");
    }
    String server = "http://172.16.10.170/";
    SocketManager(){
        try {
            socket = IO.socket(server);
            socket.on(EVENT_CONNECT, args -> {
                Log.d("joiiiii","conneced");
                for(SocketListener socket:socketListeners){
                    socket.onConnection();
                }
            }).on(EVENT_DISCONNECT, args -> {
                for(SocketListener socket:socketListeners){
                    socket.onDisconnect();
                }
            }).on("joined", args -> {
                Log.d("lol","aaaaa");

                for(SocketListener socket:socketListeners){
                    socket.onJoined();
                }
            }).on("letStartCall", args -> {
                String roomId  =  (String) args[0];
                boolean isDoCall  =  (Integer) args[1]==1 ? true:false;
                for(SocketListener socket:socketListeners){
                    socket.onLetStartCall(roomId, isDoCall);
                }
            }).on("buddyLeft", args -> {
                for(SocketListener socket:socketListeners){
                    socket.onBuddyLeft();
                }
            }).on("message", args -> {
                for(SocketListener socket:socketListeners){
                    socket.onMessage(args);
                }
            });
            socket.connect();

        } catch (URISyntaxException e) {
            e.printStackTrace();
        }
    }
    public synchronized static SocketManager getInstance() {
        if (instance == null) {
            instance = new SocketManager();
        }
        return instance;
    }
    public synchronized void sendMessage(Object message){
        socket.emit("message", message);
    }
}
