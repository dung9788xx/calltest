package com.shivam.androidwebrtc.tutorial;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.Surface;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.databinding.DataBindingUtil;
import androidx.fragment.app.Fragment;

import com.google.firebase.firestore.FirebaseFirestore;
import com.myhexaville.androidwebrtc.R;
import com.myhexaville.androidwebrtc.databinding.ActivitySamplePeerConnectionBinding;
import com.shivam.androidwebrtc.LauncherActivity;
import com.shivam.androidwebrtc.SocketListener;
import com.shivam.androidwebrtc.SocketManager;

import org.json.JSONException;
import org.json.JSONObject;
import org.webrtc.AudioSource;
import org.webrtc.AudioTrack;
import org.webrtc.Camera1Enumerator;
import org.webrtc.Camera2Enumerator;
import org.webrtc.CameraEnumerator;
import org.webrtc.DataChannel;
import org.webrtc.EglBase;
import org.webrtc.IceCandidate;
import org.webrtc.MediaConstraints;
import org.webrtc.MediaStream;
import org.webrtc.PeerConnection;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.SessionDescription;
import org.webrtc.SurfaceTextureHelper;
import org.webrtc.VideoCapturer;
import org.webrtc.VideoRenderer;
import org.webrtc.VideoSource;
import org.webrtc.VideoTrack;

import java.lang.reflect.Array;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;

import io.socket.client.IO;
import io.socket.client.Socket;
import pub.devrel.easypermissions.AfterPermissionGranted;
import pub.devrel.easypermissions.EasyPermissions;

import static io.socket.client.Socket.EVENT_CONNECT;
import static io.socket.client.Socket.EVENT_DISCONNECT;
import static org.webrtc.SessionDescription.Type.ANSWER;
import static org.webrtc.SessionDescription.Type.OFFER;

public class CompleteActivity extends AppCompatActivity implements SocketListener {
    private static final String TAG = "CompleteActivity";
    private static final int RC_CALL = 111;
    public static final String VIDEO_TRACK_ID = "ARDAMSv0";
    public static final int VIDEO_RESOLUTION_WIDTH = 1280;
    public static final int VIDEO_RESOLUTION_HEIGHT = 720;
    public static final int FPS = 30;

    private Socket socket;
    private boolean isInitiator;
    private boolean isChannelReady;
    private boolean isStarted;

    boolean isFirstTime = true;

    MediaConstraints audioConstraints;
    AudioSource audioSource;
    AudioTrack localAudioTrack;
    private ActivitySamplePeerConnectionBinding binding;
    private PeerConnection peerConnection;
    private EglBase rootEglBase;
    private PeerConnectionFactory factory;
    private VideoTrack videoTrackFromCamera;
    private VideoTrack videoTrackFromRemote;
    VideoCapturer videoCapturer;
    String roomId = "";
    MediaStream localMediaStream;
    AudioTrack remoteAudioTrack;
    SharedPreferences sharedPreferences ;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = DataBindingUtil.setContentView(this, R.layout.activity_sample_peer_connection);
        setSupportActionBar(binding.toolbar);
        sharedPreferences = this.getSharedPreferences("checkStarted", Context.MODE_PRIVATE);
        roomId=getIntent().getStringExtra("roomId");
        socket=SocketManager.getInstance().socket;
        SocketManager.getInstance().addListener(this);
        start();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        EasyPermissions.onRequestPermissionsResult(requestCode, permissions, grantResults, this);
    }


    @AfterPermissionGranted(RC_CALL)
    private void start() {
        String[] perms = {Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO};
        if (EasyPermissions.hasPermissions(this, perms)) {
            initializeSurfaceViews();
            initializePeerConnectionFactory();
            createVideoTrackFromCameraAndShowIt();
            initializePeerConnections();
            startStreamingVideo();

        } else {
            EasyPermissions.requestPermissions(this, "Need some permissions", RC_CALL, perms);
        }

    }
    public void showMessage(String message){
        CompleteActivity.this.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(CompleteActivity.this, message, Toast.LENGTH_SHORT).show();
            }
        });
    }
//MirtDPM4
    private void doAnswer() {
        showMessage("do anser");
        if(peerConnection != null)
        peerConnection.createAnswer(new SimpleSdpObserver() {
            @Override
            public void onCreateSuccess(SessionDescription sessionDescription) {
                peerConnection.setLocalDescription(new SimpleSdpObserver(){
                    @Override
                    public void onCreateFailure(String s) {
                        showMessage("fail");
                    }

                    @Override
                    public void onSetFailure(String s) {
                        showMessage("fail");
                    }
                }, sessionDescription);
                JSONObject message = new JSONObject();
                try {
                    message.put("type", "answer");
                    message.put("roomId", roomId);
                    message.put("sdp", sessionDescription.description);
                    sendMessage(message);
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        }, new MediaConstraints());

    }

    private void maybeStart() {
//        Log.d(TAG, "maybeStart: " + isStarted + " " + isChannelReady);
//        if (!isStarted && isChannelReady) {
//            isStarted = true;
//            if (isInitiator) {
                doCall();
//            }
//        }
    }

    private void doCall() {
        showMessage("do call");
        MediaConstraints sdpMediaConstraints = new MediaConstraints();

        sdpMediaConstraints.mandatory.add(
                new MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"));
        sdpMediaConstraints.mandatory.add(
                new MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"));
        peerConnection.createOffer(new SimpleSdpObserver() {
            @Override
            public void onCreateSuccess(SessionDescription sessionDescription) {
                Log.d(TAG, "onCreateSuccess: ");
                peerConnection.setLocalDescription(new SimpleSdpObserver(){
                    @Override
                    public void onCreateFailure(String s) {
                        showMessage("fail");
                    }

                    @Override
                    public void onSetFailure(String s) {
                        showMessage("fail");
                    }
                }, sessionDescription);
                JSONObject message = new JSONObject();
                try {
                    message.put("roomId", roomId);
                    message.put("type", "offer");
                    message.put("sdp", sessionDescription.description);
                    sendMessage(message);
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }

            @Override
            public void onCreateFailure(String s) {
                 showMessage("Created fail :"+s);
            }

            @Override
            public void onSetFailure(String s) {
                showMessage("On set Fail :"+s);
            }

        }, sdpMediaConstraints);
    }

    private void sendMessage(Object message) {
        SocketManager.getInstance().sendMessage(message);
    }

    private void initializeSurfaceViews() {
        rootEglBase = EglBase.create();
        binding.surfaceView.init(rootEglBase.getEglBaseContext(), null);
        binding.surfaceView.setEnableHardwareScaler(true);
        binding.surfaceView.setMirror(true);

        binding.surfaceView2.init(rootEglBase.getEglBaseContext(), null);
        binding.surfaceView2.setEnableHardwareScaler(true);
        binding.surfaceView2.setMirror(true);

        //add one more
    }

    private void initializePeerConnectionFactory() {
        PeerConnectionFactory.initializeAndroidGlobals(this, true, true, true);
        factory = new PeerConnectionFactory(null);
        factory.setVideoHwAccelerationOptions(rootEglBase.getEglBaseContext(), rootEglBase.getEglBaseContext());
    }

    private void createVideoTrackFromCameraAndShowIt() {
        audioConstraints = new MediaConstraints();
        videoCapturer = createVideoCapturer();

        VideoSource videoSource = factory.createVideoSource(videoCapturer);
        videoCapturer.startCapture(VIDEO_RESOLUTION_WIDTH, VIDEO_RESOLUTION_HEIGHT, FPS);

        videoTrackFromCamera = factory.createVideoTrack(VIDEO_TRACK_ID, videoSource);
        videoTrackFromCamera.setEnabled(true);
        videoTrackFromCamera.addRenderer(new VideoRenderer(binding.surfaceView));

        //create an AudioSource instance
        audioSource = factory.createAudioSource(audioConstraints);
        localAudioTrack = factory.createAudioTrack("101", audioSource);
    }

    private void initializePeerConnections() {
        peerConnection = createPeerConnection(factory);
    }

    private void startStreamingVideo() {
        MediaStream mediaStream = factory.createLocalMediaStream("ARDAMS");
        mediaStream.addTrack(videoTrackFromCamera);
        mediaStream.addTrack(localAudioTrack);
        peerConnection.addStream(mediaStream);
        localMediaStream  = mediaStream;
        if(getIntent().getBooleanExtra("isDoCall", false) == true){
            maybeStart();
        }
       // showMessage("got meadia");
    }
    private PeerConnection createPeerConnection(PeerConnectionFactory factory) {
        ArrayList<PeerConnection.IceServer> iceServers = new ArrayList<>();
        iceServers.add(new PeerConnection.IceServer(getIntent().getStringExtra("stun")));
        iceServers.add(new PeerConnection.IceServer(getIntent().getStringExtra("turn"), getIntent().getStringExtra("user"),getIntent().getStringExtra("pass")));
        PeerConnection.RTCConfiguration rtcConfig = new PeerConnection.RTCConfiguration(iceServers);
        MediaConstraints pcConstraints = new MediaConstraints();

        PeerConnection.Observer pcObserver = new PeerConnection.Observer() {
            @Override
            public void onSignalingChange(PeerConnection.SignalingState signalingState) {
                Log.d(TAG, "onSignalingChange: "+signalingState.name().toString());
              //  showMessage("onSignalingChange: "+signalingState.name());
            }

            @Override
            public void onIceConnectionChange(PeerConnection.IceConnectionState iceConnectionState) {
                Log.d(TAG, "onIceConnectionChange: maybeStart: "+iceConnectionState.toString());
                //showMessage("onIceConnectionChange:"+iceConnectionState.toString());
                if (iceConnectionState.toString().equals("FAILED")||iceConnectionState.toString().equals("DISCONNECTED")|| iceConnectionState.toString().equals("CLOSED")) {
                    onBuddyLeft();
                }

            }

            @Override
            public void onIceConnectionReceivingChange(boolean b) {
                Log.d(TAG, "onIceConnectionReceivingChange: ");
                //showMessage("onIceConnectionChange:");
            }

            @Override
            public void onIceGatheringChange(PeerConnection.IceGatheringState iceGatheringState) {
                Log.d(TAG, "onIceGatheringChange: ");
                //showMessage("gathering");
            }

            @Override
            public void onIceCandidate(IceCandidate iceCandidate) {
                Log.d(TAG, "onIceCandidate: ");
                JSONObject message = new JSONObject();

                try {
                    message.put("roomId", roomId);
                    message.put("type", "candidate");
                    message.put("label", iceCandidate.sdpMLineIndex);
                    message.put("id", iceCandidate.sdpMid);
                    message.put("candidate", iceCandidate.sdp);

                    Log.d(TAG, "onIceCandidate: sending candidate " + message);
                       sendMessage(message);
                    //   showMessage("send candidate");
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }

            @Override
            public void onIceCandidatesRemoved(IceCandidate[] iceCandidates) {
                Log.d(TAG, "onIceCandidatesRemoved: ");
               // showMessage("Remove candidate");
            }

            @Override
            public void onAddStream(MediaStream mediaStream) {
           //     showMessage("Have meadi stream from server"+mediaStream.videoTracks.get(0).state().toString());
                Log.d(TAG, "onAddStream: " + mediaStream.videoTracks.size());
                VideoTrack remoteVideoTrack = mediaStream.videoTracks.get(0);
                remoteAudioTrack = mediaStream.audioTracks.get(0);
                remoteAudioTrack.setEnabled(true);
                remoteVideoTrack.setEnabled(true);
                remoteVideoTrack.addRenderer(new VideoRenderer(binding.surfaceView2));
                videoTrackFromRemote = remoteVideoTrack;
            }

            @Override
            public void onRemoveStream(MediaStream mediaStream) {
                Log.d(TAG, "onDataChannel: ");
              //  showMessage("onRemove stream");
            }

            @Override
            public void onDataChannel(DataChannel dataChannel) {
                Log.d(TAG, "onDataChannel: ");
            //    showMessage("onDataChannel");
            }

            @Override
            public void onRenegotiationNeeded() {
                Log.d(TAG, "onRenegotiationNeeded: ");
              //  showMessage("onRenegotiationNeeded");
            }
        };

        return factory.createPeerConnection(rtcConfig, pcConstraints, pcObserver);
    }

    private VideoCapturer createVideoCapturer() {
        VideoCapturer videoCapturer;
        if (useCamera2()) {
            videoCapturer = createCameraCapturer(new Camera2Enumerator(this));
        } else {
            videoCapturer = createCameraCapturer(new Camera1Enumerator(true));
        }
        return videoCapturer;
    }

    private VideoCapturer createCameraCapturer(CameraEnumerator enumerator) {
        final String[] deviceNames = enumerator.getDeviceNames();

        for (String deviceName : deviceNames) {
            if (enumerator.isFrontFacing(deviceName)) {
                VideoCapturer videoCapturer = enumerator.createCapturer(deviceName, null);

                if (videoCapturer != null) {
                    return videoCapturer;
                }
            }
        }

        for (String deviceName : deviceNames) {
            if (!enumerator.isFrontFacing(deviceName)) {
                VideoCapturer videoCapturer = enumerator.createCapturer(deviceName, null);

                if (videoCapturer != null) {
                    return videoCapturer;
                }
            }
        }

        return null;
    }

    private boolean useCamera2() {
        return Camera2Enumerator.isSupported(this);
    }


    @Override
    public void onConnection() {


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
    }

    @Override
    public void onMessage(Object... args) {
        Log.d("joiii","recivemessgae");
        try {
            if (args[0] instanceof String) {
//                showMessage("recemessage");
//                String message = (String) args[0];
//                if (message.equals("letStartCall")) {
//                    maybeStart();
//                }
//                if (message.equals("retry")) {
//
//                    doCall();
//                }
            } else {
                JSONObject message = (JSONObject) args[0];
                Log.d(TAG, "connectToSignallingServer: got message " + message);
                if (message.getString("type").equals("offer")) {
//                    isStarted=true;
//                    // showMessage("Have offer");
//                    Log.d(TAG, "connectToSignallingServer: received an offer " + isInitiator + " " + isStarted);
//                    if (!isInitiator && !isStarted) {
//                        maybeStart();
//                    }
                    if(peerConnection != null)
                    peerConnection.setRemoteDescription(new SimpleSdpObserver(), new SessionDescription(OFFER, message.getString("sdp")));
                    doAnswer();
                } else if (message.getString("type").equals("answer")) {
                    // showMessage("Have answer");
                    Log.d("lol","have answer");

                    if(peerConnection != null)
                    peerConnection.setRemoteDescription(new SimpleSdpObserver(), new SessionDescription(ANSWER, message.getString("sdp")));
                } else if (message.getString("type").equals("candidate") ) {

                    // showMessage("Have candidate");
                    Log.d("lol","reviced candidate");

                    IceCandidate candidate = new IceCandidate(message.getString("id"), message.getInt("label"), message.getString("candidate"));
                    Log.d("lol","have candidate");
                  if(peerConnection != null)
                    peerConnection.addIceCandidate(candidate);
                }else{
                    showMessage("LOLLLLLLL");
//                            Log.d("LOL:",message.toString());
//                            if (message.getString("type").equals("candidate") && isStarted) {
//                                Log.d("LOL1aaaaaa:","sra:"+isStarted);
//                            }
//
//
//                            if (message.getString("message").equals("retry")){
//                                showMessage("Retry");
//                                doCall();
//                            }
                }
                        /*else if (message === 'bye' && isStarted) {
                        handleRemoteHangup();
                    }*/
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onBuddyLeft() {
        showMessage("Buddy left");
       // SocketManager.getInstance().socket.emit("joinCallQueue");
//        socket.emit("leftRoom",roomId);
//        Intent intent = new Intent(getApplicationContext(), CallingQueueActivity.class);
//        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
//        startActivity(intent);\
        sharedPreferences.edit().putBoolean("isStarted",false).apply();
        finishAndRemoveTask();
    }

    @Override
    protected void onDestroy() {
        peerConnection.close();
        socket.emit("leftRoom",roomId);
        sharedPreferences.edit().putBoolean("isStarted",false).apply();
        super.onDestroy();
    }
    @Override
    public void onBackPressed() {
        super.onBackPressed();
    }

}
