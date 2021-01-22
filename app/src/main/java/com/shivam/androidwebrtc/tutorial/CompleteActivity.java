package com.shivam.androidwebrtc.tutorial;

import android.Manifest;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.databinding.DataBindingUtil;

import com.google.firebase.firestore.FirebaseFirestore;
import com.myhexaville.androidwebrtc.R;
import com.myhexaville.androidwebrtc.databinding.ActivitySamplePeerConnectionBinding;

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

import java.net.URISyntaxException;
import java.util.ArrayList;

import io.socket.client.IO;
import io.socket.client.Socket;
import pub.devrel.easypermissions.AfterPermissionGranted;
import pub.devrel.easypermissions.EasyPermissions;

import static io.socket.client.Socket.EVENT_CONNECT;
import static io.socket.client.Socket.EVENT_DISCONNECT;
import static org.webrtc.SessionDescription.Type.ANSWER;
import static org.webrtc.SessionDescription.Type.OFFER;

public class CompleteActivity extends AppCompatActivity {
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


    MediaConstraints audioConstraints;
    MediaConstraints videoConstraints;
    MediaConstraints sdpConstraints;
    VideoSource videoSource;
    VideoTrack localVideoTrack;
    AudioSource audioSource;
    AudioTrack localAudioTrack;
    SurfaceTextureHelper surfaceTextureHelper;

    private ActivitySamplePeerConnectionBinding binding;
    private PeerConnection peerConnection;
    private EglBase rootEglBase;
    private PeerConnectionFactory factory;
    private VideoTrack videoTrackFromCamera;

    //Firestore
    FirebaseFirestore db = FirebaseFirestore.getInstance();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = DataBindingUtil.setContentView(this, R.layout.activity_sample_peer_connection);
        setSupportActionBar(binding.toolbar);
        start();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        EasyPermissions.onRequestPermissionsResult(requestCode, permissions, grantResults, this);
    }

    @Override
    protected void onDestroy() {
        if (socket != null) {
            sendMessage("bye");
            socket.disconnect();
        }
        super.onDestroy();
    }

    @AfterPermissionGranted(RC_CALL)
    private void start() {
        String[] perms = {Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO};
        if (EasyPermissions.hasPermissions(this, perms)) {
            connectToSignallingServer();
        } else {
            EasyPermissions.requestPermissions(this, "Need some permissions", RC_CALL, perms);
        }

    }

    private void connectToSignallingServer() {
        try {
            socket = IO.socket(getIntent().getStringExtra("node"));

            socket.on(EVENT_CONNECT, args -> {
                Log.d(TAG, "connectToSignallingServer: connect");
                showMessage("Create or join room");
                socket.emit("create or join", "foo");
            }).on("ipaddr", args -> {
                Log.d(TAG, "connectToSignallingServer: ipaddr");
            }).on("created", args -> {
                showMessage("Creatd room");
                Log.d(TAG, "connectToSignallingServer: created");
                isInitiator = true;
            }).on("full", args -> {
                showMessage("Full");
                Log.d(TAG, "connectToSignallingServer: full");
            }).on("join", args -> {
                showMessage("Somebody joining");
                Log.d(TAG, "connectToSignallingServer: join");
                Log.d(TAG, "connectToSignallingServer: Another peer made a request to join room");
                Log.d(TAG, "connectToSignallingServer: This peer is the initiator of room");
                isChannelReady = true;
            }).on("joined", args -> {
                Log.d(TAG, "connectToSignallingServer: joined");
                showMessage("Joined");
                isChannelReady = true;
            }).on("log", args -> {
                for (Object arg : args) {
                    Log.d(TAG, "connectToSignallingServer: " + String.valueOf(arg));
                }
            }).on("message", args -> {
                Log.d(TAG, "connectToSignallingServer: got a message");
            }).on("ready", args ->{
                isChannelReady = true;
                if (!isInitiator && !isStarted) {
                    maybeStart();
                }
            }).on("disconnection", args ->{
                if(peerConnection !=null) {
                    peerConnection.close();
                }
               // showMessage("Closeed Peaer");

            }).on("message", args -> {

                try {
                    if (args[0] instanceof String) {
                        String message = (String) args[0];
                        if (message.equals("got user media")) {
                            maybeStart();
                        }
                    } else {
                        JSONObject message = (JSONObject) args[0];
                        Log.d(TAG, "connectToSignallingServer: got message " + message);
                        if (message.getString("type").equals("offer")) {
                           // showMessage("Have offer");
                            Log.d(TAG, "connectToSignallingServer: received an offer " + isInitiator + " " + isStarted);
                            if (!isInitiator && !isStarted) {
                                maybeStart();
                            }
                            peerConnection.setRemoteDescription(new SimpleSdpObserver(), new SessionDescription(OFFER, message.getString("sdp")));
                            doAnswer();
                        } else if (message.getString("type").equals("answer") && isStarted) {
                           // showMessage("Have answer");
                            peerConnection.setRemoteDescription(new SimpleSdpObserver(), new SessionDescription(ANSWER, message.getString("sdp")));
                        } else if (message.getString("type").equals("candidate") && isStarted) {
                           // showMessage("Have candidate");
                            Log.d(TAG, "connectToSignallingServer: receiving candidates");
                            IceCandidate candidate = new IceCandidate(message.getString("id"), message.getInt("label"), message.getString("candidate"));
                            peerConnection.addIceCandidate(candidate);
                        }
                        /*else if (message === 'bye' && isStarted) {
                        handleRemoteHangup();
                    }*/
                    }
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }).on(EVENT_DISCONNECT, args -> {
             //   showMessage("Disonnected");
                Log.d(TAG, "connectToSignallingServer: disconnect");
            });
            socket.connect();
        } catch (URISyntaxException e) {
            e.printStackTrace();
        }
        initializeSurfaceViews();
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
        peerConnection.createAnswer(new SimpleSdpObserver() {
            @Override
            public void onCreateSuccess(SessionDescription sessionDescription) {
                peerConnection.setLocalDescription(new SimpleSdpObserver(), sessionDescription);
                JSONObject message = new JSONObject();
                try {
                    message.put("type", "answer");
                    message.put("sdp", sessionDescription.description);
                    sendMessage(message);
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        }, new MediaConstraints());
    }

    private void maybeStart() {
        Log.d(TAG, "maybeStart: " + isStarted + " " + isChannelReady);
        if (!isStarted && isChannelReady) {
            isStarted = true;
            if (isInitiator) {
                doCall();
            }
        }
    }

    private void doCall() {
        MediaConstraints sdpMediaConstraints = new MediaConstraints();

        sdpMediaConstraints.mandatory.add(
                new MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"));
        sdpMediaConstraints.mandatory.add(
                new MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"));
        peerConnection.createOffer(new SimpleSdpObserver() {
            @Override
            public void onCreateSuccess(SessionDescription sessionDescription) {
                Log.d(TAG, "onCreateSuccess: ");
                peerConnection.setLocalDescription(new SimpleSdpObserver(), sessionDescription);
                JSONObject message = new JSONObject();
                try {
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
        socket.emit("message", message);
    }

    private void initializeSurfaceViews() {
        rootEglBase = EglBase.create();
        binding.surfaceView.init(rootEglBase.getEglBaseContext(), null);
        binding.surfaceView.setEnableHardwareScaler(true);
        binding.surfaceView.setMirror(true);

        binding.surfaceView2.init(rootEglBase.getEglBaseContext(), null);
        binding.surfaceView2.setEnableHardwareScaler(true);
        binding.surfaceView2.setMirror(true);
        initializePeerConnectionFactory();
        //add one more
    }

    private void initializePeerConnectionFactory() {
        PeerConnectionFactory.initializeAndroidGlobals(this, true, true, true);
        factory = new PeerConnectionFactory(null);
        factory.setVideoHwAccelerationOptions(rootEglBase.getEglBaseContext(), rootEglBase.getEglBaseContext());
        createVideoTrackFromCameraAndShowIt();
    }

    private void createVideoTrackFromCameraAndShowIt() {
        audioConstraints = new MediaConstraints();
        VideoCapturer videoCapturer = createVideoCapturer();
        VideoSource videoSource = factory.createVideoSource(videoCapturer);
        videoCapturer.startCapture(VIDEO_RESOLUTION_WIDTH, VIDEO_RESOLUTION_HEIGHT, FPS);

        videoTrackFromCamera = factory.createVideoTrack(VIDEO_TRACK_ID, videoSource);
        videoTrackFromCamera.setEnabled(true);
        videoTrackFromCamera.addRenderer(new VideoRenderer(binding.surfaceView));

        //create an AudioSource instance
        audioSource = factory.createAudioSource(audioConstraints);
        localAudioTrack = factory.createAudioTrack("101", audioSource);
        initializePeerConnections();
    }

    private void initializePeerConnections() {
        peerConnection = createPeerConnection(factory);
        startStreamingVideo();
    }

    private void startStreamingVideo() {
        MediaStream mediaStream = factory.createLocalMediaStream("ARDAMS");
        mediaStream.addTrack(videoTrackFromCamera);
        mediaStream.addTrack(localAudioTrack);
        peerConnection.addStream(mediaStream);
        showMessage("added user media");
        sendMessage("got user media");
    }

    private PeerConnection createPeerConnection(PeerConnectionFactory factory) {
        ArrayList<PeerConnection.IceServer> iceServers = new ArrayList<>();
        iceServers.add(new PeerConnection.IceServer(getIntent().getStringExtra("stun")));
        iceServers.add(new PeerConnection.IceServer(getIntent().getStringExtra("turn"), getIntent().getStringExtra("user"),getIntent().getStringExtra("pass")));
        PeerConnection.RTCConfiguration rtcConfig = new PeerConnection.RTCConfiguration(iceServers);
       // rtcConfig.iceTransportsType = PeerConnection.IceTransportsType.RELAY;
        //rtcConfig.tcpCandidatePolicy = PeerConnection.TcpCandidatePolicy.DISABLED;
        //rtcConfig.continualGatheringPolicy=PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY;
        MediaConstraints pcConstraints = new MediaConstraints();

        PeerConnection.Observer pcObserver = new PeerConnection.Observer() {
            @Override
            public void onSignalingChange(PeerConnection.SignalingState signalingState) {
                Log.d(TAG, "onSignalingChange: ");
              //  showMessage("onSignalingChange: "+signalingState.name());
            }

            @Override
            public void onIceConnectionChange(PeerConnection.IceConnectionState iceConnectionState) {
                Log.d(TAG, "onIceConnectionChange: ");
                //showMessage("onIceConnectionChange:"+iceConnectionState.toString());
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
              //  showMessage("On candidate ");
                JSONObject message = new JSONObject();

                try {
                    message.put("type", "candidate");
                    message.put("label", iceCandidate.sdpMLineIndex);
                    message.put("id", iceCandidate.sdpMid);
                    message.put("candidate", iceCandidate.sdp);

                    Log.d(TAG, "onIceCandidate: sending candidate " + message);
                    sendMessage(message);
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
                showMessage("Have meadi stream from server"+mediaStream.videoTracks.get(0).state().toString());
                Log.d(TAG, "onAddStream: " + mediaStream.videoTracks.size());
                VideoTrack remoteVideoTrack = mediaStream.videoTracks.get(0);
                AudioTrack remoteAudioTrack = mediaStream.audioTracks.get(0);
                remoteAudioTrack.setEnabled(true);
                remoteVideoTrack.setEnabled(true);
                remoteVideoTrack.addRenderer(new VideoRenderer(binding.surfaceView2));

            }

            @Override
            public void onRemoveStream(MediaStream mediaStream) {
                Log.d(TAG, "onDataChannel: ");
               // showMessage("onRemove stream");
            }

            @Override
            public void onDataChannel(DataChannel dataChannel) {
                Log.d(TAG, "onDataChannel: ");
                showMessage("onDataChannel");
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
    public void onBackPressed() {
        super.onBackPressed();
        peerConnection.close();
        socket.disconnect();
    }
}
