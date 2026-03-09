package com.radiant.mesh.mesh;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.radiant.mesh.data.MessageStore;
import com.radiant.mesh.model.MeshMessage;
import com.radiant.mesh.services.AdvertiserManager;

import java.util.List;

public class MeshEngine {

    private static final String TAG = "MeshEngine";
    private static MeshEngine instance;

    private final MessageRouter router;
    private final MessageStore messageStore;
    private final StoreForwardBuffer forwardBuffer;

    private AdvertiserManager advertiserManager;
    private OnMessageReceivedListener uiCallback;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public interface OnMessageReceivedListener {
        void onMessageReceived(MeshMessage msg);
        void onMessagesCleared();
    }

    private MeshEngine() {
        this.router = new MessageRouter();
        this.messageStore = new MessageStore();
        this.forwardBuffer = new StoreForwardBuffer();
    }

    public static synchronized MeshEngine getInstance() {
        if (instance == null) {
            instance = new MeshEngine();
        }
        return instance;
    }

    public void setAdvertiserManager(AdvertiserManager manager) {
        this.advertiserManager = manager;
    }

    public void setUiCallback(OnMessageReceivedListener callback) {
        this.uiCallback = callback;
    }

    // CLEAR FEATURE
    public void clearAllMessages() {
        messageStore.clear();
        forwardBuffer.clear();

        if (uiCallback != null) {
            mainHandler.post(() -> uiCallback.onMessagesCleared());
        }
        updateAdvertiser();
        Log.d(TAG, "All messages cleared.");
    }

    public void sendLocalMessage(MeshMessage msg) {
        if (msg == null) return;
        messageStore.addMessage(msg);
        router.isNewMessage(msg.msgId);
        forwardBuffer.add(msg);
        updateAdvertiser();
    }

    public void processIncomingMessage(MeshMessage msg) {
        if (msg == null) return;
        if (!router.isNewMessage(msg.msgId)) return;

        messageStore.addMessage(msg);
        notifyUI(msg);

        if (router.shouldRelay(msg)) {
            MeshMessage relayedMsg = router.decrementTtl(msg);
            forwardBuffer.add(relayedMsg);
            updateAdvertiser();
        }
    }

    public List<MeshMessage> getMessagesForPeer() {
        return forwardBuffer.getMessagesToSend();
    }

    public List<MeshMessage> getAllMessages() {
        return messageStore.getAllMessages();
    }

    private void updateAdvertiser() {
        if (advertiserManager != null) {
            byte[] newHash = messageStore.getLatestMessageHash();
            advertiserManager.updateContext(newHash);
        }
    }

    private void notifyUI(MeshMessage msg) {
        if (uiCallback != null) {
            mainHandler.post(() -> uiCallback.onMessageReceived(msg));
        }
    }
}