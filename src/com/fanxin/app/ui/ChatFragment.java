package com.fanxin.app.ui;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import android.app.Activity;
import android.content.ClipData;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Bitmap.CompressFormat;
import android.media.ThumbnailUtils;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;
import android.widget.Toast;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.easemob.redpacketui.RedPacketConstant;
import com.easemob.redpacketui.utils.RedPacketUtil;
import com.easemob.redpacketui.widget.ChatRowRedPacket;
import com.easemob.redpacketui.widget.ChatRowRedPacketAck;
import com.fanxin.app.DemoApplication;
import com.fanxin.app.main.activity.ChatSettingGroupActivity;
import com.fanxin.app.main.activity.ChatSettingSingleActivity;
import com.fanxin.app.main.FXConstant;
import com.fanxin.app.main.activity.UserDetailsActivity;
import com.fanxin.app.main.db.ACache;
import com.fanxin.app.main.utils.OkHttpManager;
import com.fanxin.app.main.utils.Param;
import com.fanxin.app.widget.ChatRowVoiceCall;
import com.fanxin.easeui.widget.chatrow.EaseChatRow;
import com.hyphenate.chat.EMClient;
import com.hyphenate.chat.EMCmdMessageBody;
import com.hyphenate.chat.EMGroup;
import com.hyphenate.chat.EMMessage;
import com.hyphenate.chat.EMTextMessageBody;
import com.fanxin.app.Constant;
import com.fanxin.app.DemoHelper;
import com.fanxin.app.R;
import com.fanxin.app.domain.EmojiconExampleGroupData;
import com.fanxin.app.domain.RobotUser;
import com.fanxin.easeui.EaseConstant;
import com.fanxin.easeui.ui.EaseChatFragment;
import com.fanxin.easeui.ui.EaseChatFragment.EaseChatFragmentHelper;
import com.fanxin.easeui.widget.chatrow.EaseCustomChatRowProvider;
import com.fanxin.easeui.widget.emojicon.EaseEmojiconMenu;
import com.hyphenate.exceptions.HyphenateException;
import com.hyphenate.util.PathUtil;

public class ChatFragment extends EaseChatFragment implements EaseChatFragmentHelper{

	// constant start from 11 to avoid conflict with constant in base class
    private static final int ITEM_VIDEO = 11;
    private static final int ITEM_FILE = 12;
    private static final int ITEM_VOICE_CALL = 13;
    private static final int ITEM_VIDEO_CALL = 14;
    private static final int ITEM_RED_PACKET = 16;

    private static final int REQUEST_CODE_SELECT_VIDEO = 11;
    private static final int REQUEST_CODE_SELECT_FILE = 12;
    private static final int REQUEST_CODE_GROUP_DETAIL = 13;
    private static final int REQUEST_CODE_CONTEXT_MENU = 14;
    private static final int REQUEST_CODE_SELECT_AT_USER = 15;
    
    private static final int REQUEST_CODE_SEND_MONEY = 16;

    private static final int MESSAGE_TYPE_SENT_VOICE_CALL = 1;
    private static final int MESSAGE_TYPE_RECV_VOICE_CALL = 2;
    private static final int MESSAGE_TYPE_SENT_VIDEO_CALL = 3;
    private static final int MESSAGE_TYPE_RECV_VIDEO_CALL = 4;

    private static final int MESSAGE_TYPE_RECV_MONEY = 5;
    private static final int MESSAGE_TYPE_SEND_MONEY = 6;
    private static final int MESSAGE_TYPE_SEND_LUCKY = 7;
    private static final int MESSAGE_TYPE_RECV_LUCKY = 8;
    
    
    /**
     * if it is chatBot 
     */
    private boolean isRobot;


    private TextView tvName;
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate( R.layout.fx_fragment_chat, container, false);
    }

    @Override
    protected void setUpView() {
        setChatFragmentListener(this);
        tvName= (TextView) getView().findViewById(R.id.name);
        if (chatType == Constant.CHATTYPE_SINGLE) { 
            Map<String,RobotUser> robotMap = DemoHelper.getInstance().getRobotList();
            if(robotMap!=null && robotMap.containsKey(toChatUsername)){
                isRobot = true;
            }
            getView().findViewById(R.id.iv_setting_single).setVisibility(View.VISIBLE);
            getView().findViewById(R.id.iv_setting_single).setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                   startActivity(new Intent(getActivity(), ChatSettingSingleActivity.class).putExtra("userId",toChatUsername));

                }
            });
            getView().findViewById(R.id.iv_setting_group).setVisibility(View.GONE);
            if(DemoHelper.getInstance().getContactList().containsKey(toChatUsername)){

                tvName.setText(DemoHelper.getInstance().getContactList().get(toChatUsername).getNick());
            }

        }
        super.setUpView();
        hideTitleBar();

        ((EaseEmojiconMenu)inputMenu.getEmojiconMenu()).addEmojiconGroup(EmojiconExampleGroupData.getData());
        if(chatType == EaseConstant.CHATTYPE_GROUP){
            getView().findViewById(R.id.iv_setting_group).setVisibility(View.VISIBLE);
            getView().findViewById(R.id.iv_setting_group).setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    startActivity(new Intent(getActivity(), ChatSettingGroupActivity.class).putExtra("groupId",toChatUsername));
                }
            });
            getView().findViewById(R.id.iv_setting_single).setVisibility(View.GONE);
            inputMenu.getPrimaryMenu().getEditText().addTextChangedListener(new TextWatcher() {
                
                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                    if(count == 1 && "@".equals(String.valueOf(s.charAt(start)))){
                        startActivityForResult(new Intent(getActivity(), PickAtUserActivity.class).
                                putExtra("groupId", toChatUsername), REQUEST_CODE_SELECT_AT_USER);
                    }
                }
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                    
                }
                @Override
                public void afterTextChanged(Editable s) {
                    
                } 
            });
            EMGroup group = EMClient.getInstance().groupManager().getGroup(toChatUsername);
            if(group == null || group.getAffiliationsCount() <= 0){
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            EMClient.getInstance().groupManager().getGroupFromServer(toChatUsername);
                        } catch (HyphenateException e) {
                            e.printStackTrace();
                        }
                    }
                }).start();
                //获取群成员信息
                getGroupMembersInServer(group.getGroupId());
            }
            tvName.setText("群聊("+group.getAffiliationsCount()+")");

        }
    }
    
    @Override
    protected void registerExtendMenuItem() {
        //use the menu in base class
        super.registerExtendMenuItem();
        //extend menu items
        inputMenu.registerExtendMenuItem(R.string.attach_video, R.drawable.em_chat_video_selector, ITEM_VIDEO, extendMenuItemClickListener);
        inputMenu.registerExtendMenuItem(R.string.attach_file, R.drawable.em_chat_file_selector, ITEM_FILE, extendMenuItemClickListener);
        if(chatType == Constant.CHATTYPE_SINGLE){
            inputMenu.registerExtendMenuItem(R.string.attach_voice_call, R.drawable.em_chat_voice_call_selector, ITEM_VOICE_CALL, extendMenuItemClickListener);
            inputMenu.registerExtendMenuItem(R.string.attach_video_call, R.drawable.em_chat_video_call_selector, ITEM_VIDEO_CALL, extendMenuItemClickListener);
        }
        //no red packet in chatroom
        if (chatType != Constant.CHATTYPE_CHATROOM) {
            inputMenu.registerExtendMenuItem(R.string.attach_red_packet, R.drawable.em_chat_red_packet_selector, ITEM_RED_PACKET, extendMenuItemClickListener);
        }
    }
    
    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CODE_CONTEXT_MENU) {
            switch (resultCode) {
            case ContextMenuActivity.RESULT_CODE_COPY: // copy
                clipboard.setPrimaryClip(ClipData.newPlainText(null, 
                        ((EMTextMessageBody) contextMenuMessage.getBody()).getMessage()));
                break;
            case ContextMenuActivity.RESULT_CODE_DELETE: // delete
                conversation.removeMessage(contextMenuMessage.getMsgId());
                messageList.refresh();
                break;

            case ContextMenuActivity.RESULT_CODE_FORWARD: // forward
                Intent intent = new Intent(getActivity(), ForwardMessageActivity.class);
                intent.putExtra("forward_msg_id", contextMenuMessage.getMsgId());
                startActivity(intent);
                
                break;

            default:
                break;
            }
        }
        if(resultCode == Activity.RESULT_OK){
            switch (requestCode) {
            case REQUEST_CODE_SELECT_VIDEO: //send the video
                if (data != null) {
                    int duration = data.getIntExtra("dur", 0);
                    String videoPath = data.getStringExtra("path");
                    File file = new File(PathUtil.getInstance().getImagePath(), "thvideo" + System.currentTimeMillis());
                    try {
                        FileOutputStream fos = new FileOutputStream(file);
                        Bitmap ThumbBitmap = ThumbnailUtils.createVideoThumbnail(videoPath, 3);
                        ThumbBitmap.compress(CompressFormat.JPEG, 100, fos);
                        fos.close();
                        sendVideoMessage(videoPath, file.getAbsolutePath(), duration);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
                break;
            case REQUEST_CODE_SELECT_FILE: //send the file
                if (data != null) {
                    Uri uri = data.getData();
                    if (uri != null) {
                        sendFileByUri(uri);
                    }
                }
                break;
            case REQUEST_CODE_SELECT_AT_USER:
                if(data != null){
                    String username = data.getStringExtra("username");
                    inputAtUsername(username, false);
                }
                break;

            case REQUEST_CODE_SEND_MONEY:
                if (data != null){
                    sendMessage(RedPacketUtil.createRPMessage(getActivity(), data, toChatUsername));
                }
                break;
            default:
                break;
            }
        }
        
    }
    
    @Override
    public void onSetMessageAttributes(EMMessage message) {
        if(isRobot){
            //set message extension
            message.setAttribute("em_robot_message", isRobot);
        }
        message.setAttribute(FXConstant.KEY_USER_INFO, DemoApplication.getInstance().getUserJson().toJSONString());

    }
    
    @Override
    public EaseCustomChatRowProvider onSetCustomChatRowProvider() {
        return new CustomChatRowProvider();
    }
  

    @Override
    public void onEnterToChatDetails() {
        if (chatType == Constant.CHATTYPE_GROUP) {
            EMGroup group = EMClient.getInstance().groupManager().getGroup(toChatUsername);
            if (group == null) {
                Toast.makeText(getActivity(), R.string.gorup_not_found,Toast.LENGTH_SHORT).show();
                return;
            }
            startActivityForResult(
                    (new Intent(getActivity(), GroupDetailsActivity.class).putExtra("groupId", toChatUsername)),
                    REQUEST_CODE_GROUP_DETAIL);
        }else if(chatType == Constant.CHATTYPE_CHATROOM){
        	startActivityForResult(new Intent(getActivity(), ChatRoomDetailsActivity.class).putExtra("roomId", toChatUsername), REQUEST_CODE_GROUP_DETAIL);
        }
    }

    @Override
    public void onAvatarClick(String username) {
        //handling when user click avatar
        Intent intent = new Intent(getActivity(), UserDetailsActivity.class);
        intent.putExtra(FXConstant.JSON_KEY_HXID, username);
        startActivity(intent);
    }
    
    @Override
    public void onAvatarLongClick(String username) {
        inputAtUsername(username);
    }
    
    
    @Override
    public boolean onMessageBubbleClick(EMMessage message) {
    	//open red packet if the message is red packet
        if (message.getBooleanAttribute(RedPacketConstant.MESSAGE_ATTR_IS_RED_PACKET_MESSAGE, false)){
            RedPacketUtil.openRedPacket(getActivity(), chatType, message, toChatUsername, messageList);
            return true;
        }
        return false;
    }

    @Override
    public void onCmdMessageReceived(List<EMMessage> messages) {
        for (EMMessage message : messages) {
            EMCmdMessageBody cmdMsgBody = (EMCmdMessageBody) message.getBody();
            String action = cmdMsgBody.action();//get user defined action
            if (action.equals(RedPacketConstant.REFRESH_GROUP_RED_PACKET_ACTION) && message.getChatType() == EMMessage.ChatType.GroupChat){
                RedPacketUtil.receiveRedPacketAckMessage(message);
                messageList.refresh();
            }
        }
        super.onCmdMessageReceived(messages);
    }

    @Override
    public void onMessageBubbleLongClick(EMMessage message) {
    	// no message forward when in chat room
        startActivityForResult((new Intent(getActivity(), ContextMenuActivity.class)).putExtra("message",message)
                .putExtra("ischatroom", chatType == EaseConstant.CHATTYPE_CHATROOM),
                REQUEST_CODE_CONTEXT_MENU);
    }

    @Override
    public boolean onExtendMenuItemClick(int itemId, View view) {
        switch (itemId) {
        case ITEM_VIDEO:
            Intent intent = new Intent(getActivity(), ImageGridActivity.class);
            startActivityForResult(intent, REQUEST_CODE_SELECT_VIDEO);
            break;
        case ITEM_FILE: //file
            selectFileFromLocal();
            break;
        case ITEM_VOICE_CALL:
            startVoiceCall();
            break;
        case ITEM_VIDEO_CALL:
            startVideoCall();
            break;
        case ITEM_RED_PACKET:
            RedPacketUtil.startRedPacketActivityForResult(this, chatType, toChatUsername, REQUEST_CODE_SEND_MONEY);
            break;
        default:
            break;
        }
        //keep exist extend menu
        return false;
    }
    
    /**
     * select file
     */
    protected void selectFileFromLocal() {
        Intent intent = null;
        if (Build.VERSION.SDK_INT < 19) { //api 19 and later, we can't use this way, demo just select from images
            intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.setType("*/*");
            intent.addCategory(Intent.CATEGORY_OPENABLE);

        } else {
            intent = new Intent(Intent.ACTION_PICK, android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        }
        startActivityForResult(intent, REQUEST_CODE_SELECT_FILE);
    }
    
    /**
     * make a voice call
     */
    protected void startVoiceCall() {
        if (!EMClient.getInstance().isConnected()) {
            Toast.makeText(getActivity(), R.string.not_connect_to_server, Toast.LENGTH_SHORT).show();
        } else {
            startActivity(new Intent(getActivity(), VoiceCallActivity.class).putExtra("username", toChatUsername)
                    .putExtra("isComingCall", false));
            // voiceCallBtn.setEnabled(false);
            inputMenu.hideExtendMenuContainer();
        }
    }
    
    /**
     * make a video call
     */
    protected void startVideoCall() {
        if (!EMClient.getInstance().isConnected())
            Toast.makeText(getActivity(), R.string.not_connect_to_server, Toast.LENGTH_SHORT).show();
        else {
            startActivity(new Intent(getActivity(), VideoCallActivity.class).putExtra("username", toChatUsername)
                    .putExtra("isComingCall", false));
            // videoCallBtn.setEnabled(false);
            inputMenu.hideExtendMenuContainer();
        }
    }
    
    /**
     * chat row provider 
     *
     */
    private final class CustomChatRowProvider implements EaseCustomChatRowProvider {
        @Override
        public int getCustomChatRowTypeCount() {
            //here the number is the message type in EMMessage::Type
        	//which is used to count the number of different chat row
            return 8;
        }

        @Override
        public int getCustomChatRowType(EMMessage message) {
            if(message.getType() == EMMessage.Type.TXT){
                //voice call
                if (message.getBooleanAttribute(Constant.MESSAGE_ATTR_IS_VOICE_CALL, false)){
                    return message.direct() == EMMessage.Direct.RECEIVE ? MESSAGE_TYPE_RECV_VOICE_CALL : MESSAGE_TYPE_SENT_VOICE_CALL;
                }else if (message.getBooleanAttribute(Constant.MESSAGE_ATTR_IS_VIDEO_CALL, false)){
                    //video call
                    return message.direct() == EMMessage.Direct.RECEIVE ? MESSAGE_TYPE_RECV_VIDEO_CALL : MESSAGE_TYPE_SENT_VIDEO_CALL;
                }else if (message.getBooleanAttribute(RedPacketConstant.MESSAGE_ATTR_IS_RED_PACKET_MESSAGE, false)) {
                    //sent redpacket message
                    return message.direct() == EMMessage.Direct.RECEIVE ? MESSAGE_TYPE_RECV_MONEY : MESSAGE_TYPE_SEND_MONEY;
                } else if (message.getBooleanAttribute(RedPacketConstant.MESSAGE_ATTR_IS_RED_PACKET_ACK_MESSAGE, false)) {
                    //received redpacket message
                    return message.direct() == EMMessage.Direct.RECEIVE ? MESSAGE_TYPE_RECV_LUCKY : MESSAGE_TYPE_SEND_LUCKY;
                }
            }
            return 0;
        }

        @Override
        public EaseChatRow getCustomChatRow(EMMessage message, int position, BaseAdapter adapter) {
            if(message.getType() == EMMessage.Type.TXT){
                // voice call or video call
                if (message.getBooleanAttribute(Constant.MESSAGE_ATTR_IS_VOICE_CALL, false) ||
                    message.getBooleanAttribute(Constant.MESSAGE_ATTR_IS_VIDEO_CALL, false)){
                    return new ChatRowVoiceCall(getActivity(), message, position, adapter);
                }else if (message.getBooleanAttribute(RedPacketConstant.MESSAGE_ATTR_IS_RED_PACKET_MESSAGE, false)) {//send redpacket
                    return new ChatRowRedPacket(getActivity(), message, position, adapter);
                } else if (message.getBooleanAttribute(RedPacketConstant.MESSAGE_ATTR_IS_RED_PACKET_ACK_MESSAGE, false)) {//open redpacket message
                    return new ChatRowRedPacketAck(getActivity(), message, position, adapter);
                }
            }
            return null;
        }

    }


    private  void   getGroupMembersInServer(final String groupId){

        List<Param> params = new ArrayList<>();
        params.add(new Param("groupId", groupId));
        OkHttpManager.getInstance().post(params, FXConstant.URL_GROUP_MEMBERS, new OkHttpManager.HttpCallBack() {
            @Override
            public void onResponse(JSONObject jsonObject) {
                if(jsonObject.containsKey("code")&&jsonObject.get("code") instanceof Integer){
                    int code = jsonObject.getIntValue("code");
                    if (code == 1000) {
                        if (jsonObject.containsKey("data") && jsonObject.get("data") instanceof JSONArray) {
                            JSONArray jsonArray = jsonObject.getJSONArray("data");
                            ACache.get(getActivity()).put(groupId,jsonArray);
                        }
                    }

                }

            }

            @Override
            public void onFailure(String errorMsg) {

            }
        });


    }


}
