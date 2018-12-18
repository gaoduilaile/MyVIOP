package cn.heima.myvoip;

/*
LinphoneMiniManager.java
Copyright (C) 2017  Belledonne Communications, Grenoble, France

This program is free software; you can redistribute it and/or
modify it under the terms of the GNU General Public License
as published by the Free Software Foundation; either version 2
of the License, or (at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program; if not, write to the Free Software
Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
*/

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.IBinder;

import org.linphone.core.LinphoneAddress;
import org.linphone.core.LinphoneAuthInfo;
import org.linphone.core.LinphoneCall;
import org.linphone.core.LinphoneCall.State;
import org.linphone.core.LinphoneCallParams;
import org.linphone.core.LinphoneCallStats;
import org.linphone.core.LinphoneChatMessage;
import org.linphone.core.LinphoneChatRoom;
import org.linphone.core.LinphoneContent;
import org.linphone.core.LinphoneCore;
import org.linphone.core.LinphoneCore.EcCalibratorStatus;
import org.linphone.core.LinphoneCore.GlobalState;
import org.linphone.core.LinphoneCore.RegistrationState;
import org.linphone.core.LinphoneCore.RemoteProvisioningState;
import org.linphone.core.LinphoneCoreException;
import org.linphone.core.LinphoneCoreFactory;
import org.linphone.core.LinphoneCoreListener;
import org.linphone.core.LinphoneEvent;
import org.linphone.core.LinphoneFriend;
import org.linphone.core.LinphoneFriendList;
import org.linphone.core.LinphoneInfoMessage;
import org.linphone.core.LinphoneProxyConfig;
import org.linphone.core.PublishState;
import org.linphone.core.SubscriptionState;
import org.linphone.mediastream.Log;
import org.linphone.mediastream.video.capture.hwconf.AndroidCameraConfiguration;
import org.linphone.mediastream.video.capture.hwconf.AndroidCameraConfiguration.AndroidCamera;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Timer;
import java.util.TimerTask;

public class LinphoneMiniManager extends Service implements LinphoneCoreListener {
    private static LinphoneMiniManager mInstance;
    private Context mContext;
    private LinphoneCore mLinphoneCore;
    private Timer mTimer;

    public LinphoneMiniManager() {
    }

    public LinphoneMiniManager(Context c) {
        mContext = c;
        LinphoneCoreFactory.instance().setDebugMode(true, "Linphone Mini");

        try {
            String basePath = mContext.getFilesDir().getAbsolutePath();
            copyAssetsFromPackage(basePath);
            mLinphoneCore = LinphoneCoreFactory.instance().createLinphoneCore(this, basePath + "/.linphonerc", basePath + "/linphonerc", null, mContext);
            initLinphoneCoreValues(basePath);

            setUserAgent();
            setFrontCamAsDefault();
            startIterate();
            mInstance = this;
            mLinphoneCore.setNetworkReachable(true); // Let's assume it's true
        } catch (LinphoneCoreException e) {
        } catch (IOException e) {
        }
    }

    public static LinphoneMiniManager getInstance() {
        return mInstance;
    }

    public void destroy() {
        try {
            mTimer.cancel();
            mLinphoneCore.destroy();
        } catch (RuntimeException e) {
        } finally {
            mLinphoneCore = null;
            mInstance = null;
        }
    }

    private void startIterate() {
        TimerTask lTask = new TimerTask() {
            @Override
            public void run() {
                mLinphoneCore.iterate();
            }
        };

        /*use schedule instead of scheduleAtFixedRate to avoid iterate from being call in burst after cpu wake up*/
        mTimer = new Timer("LinphoneMini scheduler");
        mTimer.schedule(lTask, 0, 20);
    }

    private void setUserAgent() {
        try {
            String versionName = mContext.getPackageManager().getPackageInfo(mContext.getPackageName(), 0).versionName;
            if (versionName == null) {
                versionName = String.valueOf(mContext.getPackageManager().getPackageInfo(mContext.getPackageName(), 0).versionCode);
            }
            mLinphoneCore.setUserAgent("LinphoneMiniAndroid", versionName);
        } catch (NameNotFoundException e) {
        }
    }

    private void setFrontCamAsDefault() {
        int camId = 0;
        AndroidCamera[] cameras = AndroidCameraConfiguration.retrieveCameras();
        for (AndroidCamera androidCamera : cameras) {
            if (androidCamera.frontFacing)
                camId = androidCamera.id;
        }
        mLinphoneCore.setVideoDevice(camId);
    }

    private void copyAssetsFromPackage(String basePath) throws IOException {
        LinphoneMiniUtils.copyIfNotExist(mContext, R.raw.oldphone_mono, basePath + "/oldphone_mono.wav");
        LinphoneMiniUtils.copyIfNotExist(mContext, R.raw.ringback, basePath + "/ringback.wav");
        LinphoneMiniUtils.copyIfNotExist(mContext, R.raw.toy_mono, basePath + "/toy_mono.wav");
        LinphoneMiniUtils.copyIfNotExist(mContext, R.raw.linphonerc_default, basePath + "/.linphonerc");
        LinphoneMiniUtils.copyFromPackage(mContext, R.raw.linphonerc_factory, new File(basePath + "/linphonerc").getName());
        LinphoneMiniUtils.copyIfNotExist(mContext, R.raw.lpconfig, basePath + "/lpconfig.xsd");
        LinphoneMiniUtils.copyIfNotExist(mContext, R.raw.rootca, basePath + "/rootca.pem");
    }

    private void initLinphoneCoreValues(String basePath) {
        mLinphoneCore.setContext(mContext);
//        mLinphoneCore.setRing(null);
        mLinphoneCore.setRing(basePath + "/oldphone_mono.wav");//铃声需要将
        mLinphoneCore.setRootCA(basePath + "/rootca.pem");
        mLinphoneCore.setPlayFile(basePath + "/toy_mono.wav");
        mLinphoneCore.setChatDatabasePath(basePath + "/linphone-history.db");

        int availableCores = Runtime.getRuntime().availableProcessors();
        mLinphoneCore.setCpuCount(availableCores);
    }

    @Override
    public void authInfoRequested(LinphoneCore lc, String realm, String username, String s2) {

    }

    @Override
    public void globalState(LinphoneCore lc, GlobalState state, String message) {
        Log.d("Global state: " + state + "(" + message + ")");
    }

    @Override
    public void callState(LinphoneCore lc, LinphoneCall call, State cstate,
                          String message) {
        Log.d("Call state: " + cstate + "(" + message + ")");
    }

    @Override
    public void authenticationRequested(LinphoneCore linphoneCore, LinphoneAuthInfo linphoneAuthInfo, LinphoneCore.AuthMethod authMethod) {

    }

    @Override
    public void callStatsUpdated(LinphoneCore lc, LinphoneCall call,
                                 LinphoneCallStats stats) {

    }

    @Override
    public void callEncryptionChanged(LinphoneCore lc, LinphoneCall call,
                                      boolean encrypted, String authenticationToken) {

    }

    @Override
    public void registrationState(LinphoneCore lc, LinphoneProxyConfig cfg,
                                  RegistrationState cstate, String smessage) {
        Log.d("Registration state: " + cstate + "(" + smessage + ")");

        Intent intent = new Intent(MainActivity.RECEIVE_MAIN_ACTIVITY);
        intent.putExtra("action", "reg_state");
        intent.putExtra("data", smessage);
        sendBroadcast(intent);
    }

    @Override
    public void newSubscriptionRequest(LinphoneCore lc, LinphoneFriend lf,
                                       String url) {

    }

    @Override
    public void notifyPresenceReceived(LinphoneCore lc, LinphoneFriend lf) {

    }

    @Override
    public void messageReceived(LinphoneCore lc, LinphoneChatRoom cr,
                                LinphoneChatMessage message) {
        Log.d("Message received from " + cr.getPeerAddress().asString() + " : " + message.getText() + "(" + message.getExternalBodyUrl() + ")");
    }

    @Override
    public void isComposingReceived(LinphoneCore lc, LinphoneChatRoom cr) {
        Log.d("Composing received from " + cr.getPeerAddress().asString());
    }

    @Override
    public void dtmfReceived(LinphoneCore lc, LinphoneCall call, int dtmf) {

    }

    @Override
    public void ecCalibrationStatus(LinphoneCore lc, EcCalibratorStatus status,
                                    int delay_ms, Object data) {

    }

    @Override
    public void uploadProgressIndication(LinphoneCore linphoneCore, int i, int i1) {

    }

    @Override
    public void uploadStateChanged(LinphoneCore linphoneCore, LinphoneCore.LogCollectionUploadState logCollectionUploadState, String s) {

    }

    @Override
    public void friendListCreated(LinphoneCore linphoneCore, LinphoneFriendList linphoneFriendList) {

    }

    @Override
    public void friendListRemoved(LinphoneCore linphoneCore, LinphoneFriendList linphoneFriendList) {

    }

    @Override
    public void notifyReceived(LinphoneCore lc, LinphoneCall call,
                               LinphoneAddress from, byte[] event) {

    }

    @Override
    public void transferState(LinphoneCore lc, LinphoneCall call,
                              State new_call_state) {

    }

    @Override
    public void infoReceived(LinphoneCore lc, LinphoneCall call,
                             LinphoneInfoMessage info) {

    }

    @Override
    public void subscriptionStateChanged(LinphoneCore lc, LinphoneEvent ev,
                                         SubscriptionState state) {

    }

    @Override
    public void notifyReceived(LinphoneCore lc, LinphoneEvent ev,
                               String eventName, LinphoneContent content) {
        Log.d("Notify received: " + eventName + " -> " + content.getDataAsString());
    }

    @Override
    public void publishStateChanged(LinphoneCore lc, LinphoneEvent ev,
                                    PublishState state) {

    }

    @Override
    public void configuringStatus(LinphoneCore lc,
                                  RemoteProvisioningState state, String message) {
        Log.d("Configuration state: " + state + "(" + message + ")");
    }

    @Override
    public void show(LinphoneCore lc) {

    }

    @Override
    public void displayStatus(LinphoneCore lc, String message) {

    }

    @Override
    public void displayMessage(LinphoneCore lc, String message) {

    }

    @Override
    public void displayWarning(LinphoneCore lc, String message) {

    }

    @Override
    public void fileTransferProgressIndication(LinphoneCore linphoneCore, LinphoneChatMessage linphoneChatMessage, LinphoneContent linphoneContent, int i) {

    }

    @Override
    public void fileTransferRecv(LinphoneCore linphoneCore, LinphoneChatMessage linphoneChatMessage, LinphoneContent linphoneContent, byte[] bytes, int i) {

    }

    @Override
    public int fileTransferSend(LinphoneCore linphoneCore, LinphoneChatMessage linphoneChatMessage, LinphoneContent linphoneContent, ByteBuffer byteBuffer, int i) {
        return 0;
    }


    /**
     * 注册
     *
     * @param sipAddress sip地址
     * @param password   密码
     * @param port       端口号
     */
    public void lilin_reg(String sipAddress, String password, String port) {
        try {
            LinphoneAddress address = lcFactory.createLinphoneAddress(sipAddress);
            String username = address.getUserName();

            String domain = address.getDomain();

            LinphoneProxyConfig[] proxyConfigList = mLinphoneCore.getProxyConfigList();
            for (LinphoneProxyConfig linphoneProxyConfig : proxyConfigList) {
                mLinphoneCore.removeProxyConfig(linphoneProxyConfig);
            }//删除原来的

            mLinphoneCore.addAuthInfo(lcFactory.createAuthInfo(username, password, null, domain + ":" + port));
            // create proxy config

            LinphoneProxyConfig proxyCfg = mLinphoneCore.createProxyConfig(sipAddress, domain + ":" + port, null, true);
            proxyCfg.enablePublish(true);
            proxyCfg.setExpires(2000);
            mLinphoneCore.addProxyConfig(proxyCfg); // add it to linphone
            mLinphoneCore.setDefaultProxyConfig(proxyCfg);//注册一次就好了  下次启动就不用注册
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 拨打电话
     *
     * @param username
     * @param host
     * @param isVideoCall
     */
    public void lilin_call(String username, String host, boolean isVideoCall) {
        try {
            LinphoneAddress address = mLinphoneCore.interpretUrl(username + "@" + host);
            address.setDisplayName(username);

            LinphoneCallParams params = mLinphoneCore.createCallParams(null);
            if (isVideoCall) {
                params.setVideoEnabled(true);
                params.enableLowBandwidth(false);
            } else {
                params.setVideoEnabled(false);
            }
            LinphoneCall call = mLinphoneCore.inviteAddressWithParams(address, params);
            if (call == null) {
                Log.e("lilin error", "Could not place call to " + username);
                return;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    /**
     * 接听
     */

    public void lilin_jie() {
        try {
            //instance.getLC().setVideoPolicy(true, instance.getLC().getVideoAutoAcceptPolicy());/*设置初始话视频电话，设置了这个你拨号的时候就默认为使用视频发起通话了*/
            getLC().setVideoPolicy(getLC().getVideoAutoInitiatePolicy(), true);/*设置自动接听视频通话的请求，也就是说只要是视频通话来了，直接就接通，不用按键确定，这是我们的业务流，不用理会*/
            /*这是允许视频通话，这个选了false就彻底不能接听或者拨打视频电话了*/
            getLC().enableVideo(true, true);
            LinphoneCall currentCall = getLC().getCurrentCall();
            if (currentCall != null) {
                LinphoneCallParams params = getLC().createCallParams(currentCall);
                getLC().acceptCallWithParams(currentCall, params);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }


    private LinphoneCoreFactory lcFactory;

    public static boolean isReady() {
        return mInstance != null;
    }

    @Override
    public void onCreate() {
        super.onCreate();

        mContext = this;
        lcFactory = LinphoneCoreFactory.instance();
        lcFactory.setDebugMode(true, "lilinaini 1");
        try {
            String basePath = mContext.getFilesDir().getAbsolutePath();
            copyAssetsFromPackage(basePath);
            mLinphoneCore = lcFactory.createLinphoneCore(this, basePath + "/.linphonerc", basePath + "/linphonerc", null, mContext);
            initLinphoneCoreValues(basePath);

            setUserAgent();
            setFrontCamAsDefault();
            startIterate();
            mInstance = this;
            mLinphoneCore.setNetworkReachable(true); // Let's assume it's true
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    public static LinphoneCore getLC() {
        if (null == mInstance) {
            return null;
        }
        return mInstance.mLinphoneCore;
    }

}
