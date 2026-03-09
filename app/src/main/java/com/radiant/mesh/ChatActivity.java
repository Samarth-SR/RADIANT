package com.radiant.mesh;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.radiant.mesh.crypto.KeyManager;
import com.radiant.mesh.mesh.MeshEngine;
import com.radiant.mesh.model.MeshMessage;
import com.radiant.mesh.utils.ByteUtils;

import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class ChatActivity extends AppCompatActivity implements MeshEngine.OnMessageReceivedListener {

    private RecyclerView recyclerView;
    private MessageAdapter adapter;
    private EditText inputField;
    private Button sendButton;
    private View clearButton;

    private KeyManager keyManager;
    private String myDeviceHashHex;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat);

        // 1. Setup Identity
        keyManager = new KeyManager(this);
        byte[] myHash = keyManager.getMyDeviceHashShort();
        myDeviceHashHex = ByteUtils.toHexString(myHash);

        // 2. Bind UI Elements
        inputField = findViewById(R.id.edit_message_input);
        sendButton = findViewById(R.id.btn_send_message);
        clearButton = findViewById(R.id.btn_clear_chat);
        recyclerView = findViewById(R.id.recycler_messages);

        // 3. Setup RecyclerView
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        layoutManager.setStackFromEnd(true);
        recyclerView.setLayoutManager(layoutManager);

        adapter = new MessageAdapter(new ArrayList<>(), myDeviceHashHex);
        recyclerView.setAdapter(adapter);

        // 4. Load Data
        loadMessages();

        // 5. Listeners
        sendButton.setOnClickListener(v -> sendMessage());

        if (clearButton != null) {
            clearButton.setOnClickListener(v -> {
                // Call the engine to clear data globally
                MeshEngine.getInstance().clearAllMessages();
            });
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        MeshEngine.getInstance().setUiCallback(this);
        loadMessages();
    }

    @Override
    protected void onPause() {
        super.onPause();
        MeshEngine.getInstance().setUiCallback(null);
    }

    // --- Interface Implementations ---

    @Override
    public void onMessageReceived(MeshMessage msg) {
        runOnUiThread(() -> {
            adapter.addMessage(msg);
            if (adapter.getItemCount() > 0) {
                recyclerView.smoothScrollToPosition(adapter.getItemCount() - 1);
            }
        });
    }

    // THIS WAS MISSING - It fixes your error
    @Override
    public void onMessagesCleared() {
        runOnUiThread(() -> {
            adapter.setMessages(new ArrayList<>());
            Toast.makeText(this, "Chat History Cleared", Toast.LENGTH_SHORT).show();
        });
    }

    private void loadMessages() {
        List<MeshMessage> history = MeshEngine.getInstance().getAllMessages();
        adapter.setMessages(history);
        if (adapter.getItemCount() > 0) {
            recyclerView.scrollToPosition(adapter.getItemCount() - 1);
        }
    }

    private void sendMessage() {
        String text = inputField.getText().toString().trim();
        if (TextUtils.isEmpty(text)) return;

        byte[] payload = text.getBytes(StandardCharsets.UTF_8);
        MeshMessage msg = MeshMessage.create(myDeviceHashHex, payload);

        MeshEngine.getInstance().sendLocalMessage(msg);

        inputField.setText("");
        adapter.addMessage(msg);
        recyclerView.smoothScrollToPosition(adapter.getItemCount() - 1);
    }

    // =========================================================================
    // Inner Class: RecyclerView Adapter
    // =========================================================================

    private static class MessageAdapter extends RecyclerView.Adapter<MessageAdapter.MessageViewHolder> {
        private final List<MeshMessage> messages;
        private final String mySenderHash;
        private final SimpleDateFormat dateFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());

        public MessageAdapter(List<MeshMessage> messages, String mySenderHash) {
            this.messages = messages;
            this.mySenderHash = mySenderHash;
        }

        @SuppressLint("NotifyDataSetChanged")
        public void setMessages(List<MeshMessage> newMessages) {
            this.messages.clear();
            this.messages.addAll(newMessages);
            notifyDataSetChanged();
        }

        public void addMessage(MeshMessage msg) {
            this.messages.add(msg);
            notifyItemInserted(messages.size() - 1);
        }

        @NonNull
        @Override
        public MessageViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_message, parent, false);
            return new MessageViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull MessageViewHolder holder, int position) {
            MeshMessage msg = messages.get(position);

            String text = new String(msg.payload, StandardCharsets.UTF_8);
            String time = dateFormat.format(new Date(msg.timestamp * 1000));

            boolean isMe = msg.senderHash.equalsIgnoreCase(mySenderHash);

            // --- UI Styling Logic ---
            if (isMe) {
                // Align Right (My Message)
                holder.rootLayout.setGravity(Gravity.END);
                holder.bubbleLayout.setBackgroundResource(R.drawable.bg_message_me);
                holder.tvMessage.setTextColor(0xFF000000);
                holder.tvInfo.setText(time + " • Sent");
                holder.tvInfo.setTextColor(0x99000000);
            } else {
                // Align Left (Peer Message)
                holder.rootLayout.setGravity(Gravity.START);
                holder.bubbleLayout.setBackgroundResource(R.drawable.bg_message_peer);
                holder.tvMessage.setTextColor(0xFFFFFFFF);
                holder.tvInfo.setText(time + " • " + msg.senderHash.substring(0, 4));
                holder.tvInfo.setTextColor(0xAAFFFFFF);
            }

            holder.tvMessage.setText(text);
        }

        @Override
        public int getItemCount() {
            return messages.size();
        }

        static class MessageViewHolder extends RecyclerView.ViewHolder {
            TextView tvMessage, tvInfo;
            LinearLayout rootLayout, bubbleLayout;

            public MessageViewHolder(@NonNull View itemView) {
                super(itemView);
                tvMessage = itemView.findViewById(R.id.tv_message_content);
                tvInfo = itemView.findViewById(R.id.tv_message_info);
                rootLayout = itemView.findViewById(R.id.layout_message_root);
                bubbleLayout = itemView.findViewById(R.id.layout_bubble);
            }
        }
    }
}