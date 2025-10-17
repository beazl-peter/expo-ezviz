package com.videogo.openapi;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.SurfaceTexture;
import android.os.Handler;
import android.os.Message;
import android.text.TextUtils;
import android.view.SurfaceHolder;
import com.ez.player.EZMediaPlayer;
import com.ez.player.EZVoiceTalk;
import com.ez.stream.EZError;
import com.ez.stream.EZGetPercentInfo;
import com.ez.stream.EZStreamClientManager;
import com.ez.stream.InitParam;
import com.ez.stream.SystemTransform;
import com.ez.stream.SystemTransformSim;
import com.ezviz.npcsdk.NpcPlayer;
import com.videogo.errorlayer.ErrorDefine;
import com.videogo.errorlayer.ErrorInfo;
import com.videogo.errorlayer.ErrorLayer;
import com.videogo.exception.BaseException;
import com.videogo.exception.ErrorCode;
import com.videogo.exception.InnerException;
import com.videogo.exception.PlaySDKException;
import com.videogo.openapi.bean.EZCloudRecordFile;
import com.videogo.openapi.bean.EZDeviceDetailPublicInfo;
import com.videogo.openapi.bean.EZDeviceRecordFile;
import com.videogo.openapi.bean.EZLeaveMessage;
import com.videogo.stream.EZStreamCtrl;
import com.videogo.stream.EZStreamParamHelp;
import com.videogo.stream.EZTalkback;
import com.videogo.util.LogUtil;
import com.videogo.util.VideoTransUtil;
import com.videogo.widget.CustomRect;
import java.io.File;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static com.ez.stream.EZError.EZ_OK;


/**
 * 播放器接口主类，预览和对讲可以共用一个EZPlayer对象，也可以不共用，预览和回放建议使用不同的EZPlayer对象
 *
 * @author xiaxingsuo
 * @ClassName: EZPlayer
 * @Description: 播放器接口主类，预览和对讲可以共用一个EZPlayer对象，也可以不共用，预览和回放建议使用不同的EZPlayer对象
 * @modify yudan
 * @date 2015-10-20 下午2:51:43
 */
@SuppressWarnings("UnusedReturnValue")
public class EZPlayer implements EZMediaPlayer.OnCompletionListener, EZMediaPlayer.OnErrorListener, EZMediaPlayer.OnInfoListener {

    private static final String TAG = "EZPlayer";
    private EZStreamCtrl streamCtrl = null;
    private ExecutorService cachedThreadPool;
    private EZMediaPlayer mLanPlayer;
    private Handler mHandler;
    private SurfaceHolder mSurfaceHolder;
    private SurfaceHolder mSurfaceHolder2;
    private SurfaceHolder[] mSurfaceHolders;
    private SurfaceTexture mSurfaceTexture;
    private SurfaceTexture mSurfaceTexture2;

    private NpcPlayer mNpcPlayer;


    private boolean isNpcPlayer = false;
    private String mPlayerUrl;

    private boolean isAudioOnly = false;

    private String mOutTempFilepath;
    private String mOutFilepath;
    private String mVerifyCode;

    private EZStreamParamHelp paramHelp;

    private EZOpenSDKListener.EZStreamDownloadCallback mStreamDownloadCallback;

    private ConfigLoader.PlayConfig mPlayConfig = new ConfigLoader.PlayConfig();

    /**
     * 摄像机预览、回放、对讲时使用此构造函数。可以通过EZOpenSDK.CreatePlayer创建播放对象
     *
     * @param paramHelp
     */
    public EZPlayer(EZStreamParamHelp paramHelp) {
        cachedThreadPool = Executors.newSingleThreadExecutor();
        try {
            streamCtrl = new EZStreamCtrl(paramHelp, null, mPlayConfig);
            this.paramHelp = paramHelp;

        } catch (BaseException e) {
            //e.printStackTrace();
            LogUtil.printErrStackTrace(TAG, e.fillInStackTrace());
        }
    }

    /**
     * 通过URL播放时使用此构造函数。可以通过EZOpenSDK.CreatePlayerWithUrl创建播放对象
     */
    public EZPlayer(String url) {
        cachedThreadPool = Executors.newSingleThreadExecutor();
        mPlayerUrl = url;
        if (!TextUtils.isEmpty(url) && url.startsWith("ysproto://")) {
            try {
                streamCtrl = new EZStreamCtrl(null, url, mPlayConfig);
            } catch (BaseException e) {
                e.printStackTrace();
            }
        } else {
            isNpcPlayer = true;
        }
    }

    public EZPlayer() {
        cachedThreadPool = Executors.newSingleThreadExecutor();
        try {
            streamCtrl = new EZStreamCtrl(null, mPlayConfig);
        } catch (BaseException e) {
            LogUtil.printErrStackTrace(TAG, e.fillInStackTrace());
        }
    }


    public EZPlayer(int userId, int cameraNo, int streamType) {
        cachedThreadPool = Executors.newSingleThreadExecutor();
        InitParam initParam = new InitParam();
        initParam.iNetSDKUserId = userId;
        initParam.iNetSDKChannelNumber = cameraNo;
        initParam.iStreamType = streamType;
        initParam.iStreamTimeOut = 30000;
        mLanPlayer = new EZMediaPlayerEx(EZStreamClientManager.create(PlayAPI.mApplication.getApplicationContext()), initParam);
        if (mPlayConfig != null) {
            ((EZMediaPlayerEx) mLanPlayer).setPlayConfig(mPlayConfig);
        }
        mLanPlayer.setCompletionListener(this);
        mLanPlayer.setOnErrorListener(this);
        mLanPlayer.setOnInfoListener(this);
    }

    /**
     * 获取内部播放器句柄。建议每次使用播放器句柄时均调用此方法获取，并进行有效性判断。
     *
     * @return 小于0为无效值，大于等于0为有效值
     */
    public int getPlayPort() {
        if (streamCtrl != null) {
            return streamCtrl.getPlayPort();
        }
        return -1;
    }

    public synchronized void release() {
        if (cachedThreadPool != null) {
            cachedThreadPool.shutdown();
            cachedThreadPool.shutdownNow();
            cachedThreadPool = null;
        }
        if (mLanPlayer != null) {
            mLanPlayer.release();
            return;
        }
        if (mNpcPlayer != null) {
            mNpcPlayer.stop();
            mNpcPlayer = null;
        }
        if (streamCtrl == null) {
            return;
        }
        streamCtrl.release();
        streamCtrl = null;
    }

    /**
     * 设置播放器的显示Surface
     *
     * @param surfaceTexture 用于播放的Surface
     * @return true 表示成功， false 表示失败
     */
    public boolean setSurfaceEx(SurfaceTexture surfaceTexture) {
        return setSurfaceEx(surfaceTexture, 0);
    }

    /**
     * 设置播放器的显示Surface
     *
     * @param surfaceTexture 用于播放的Surface
     * @param streamId 轨道 0：广角镜头画面轨道 1：云台镜头画面轨道
     * @return true 表示成功， false 表示失败
     */
    public boolean setSurfaceEx(SurfaceTexture surfaceTexture, int streamId) {
        if (streamId == 0) {
            if (mSurfaceTexture != null && surfaceTexture == mSurfaceTexture) {
                return false;
            }
            mSurfaceTexture = surfaceTexture;
            if (mNpcPlayer != null) {
                mNpcPlayer.setDisplay(surfaceTexture);
                return true;
            }

            if (streamCtrl == null) {
                return false;
            }
            if (mSurfaceHolder != null) {
                streamCtrl.setSurfaceHold(null);
            }
            streamCtrl.setSurfaceEx(surfaceTexture);
            return true;
        } else if (streamId == 1) {
            if (mSurfaceTexture2 != null && surfaceTexture == mSurfaceTexture2) {
                return false;
            }
            mSurfaceTexture2 = surfaceTexture;
            if (mNpcPlayer != null) {
                mNpcPlayer.setDisplay(surfaceTexture, streamId);
                return true;
            }

            if (streamCtrl == null) {
                return false;
            }
            if (mSurfaceHolder2 != null) {
                streamCtrl.setSurfaceHold(null, streamId);
            }
            streamCtrl.setSurfaceEx(surfaceTexture, streamId);
            this.paramHelp.isMultiChannelDevice = true;
            return true;
        }
        return false;
    }

    /**
     * 设置播放器的显示Surface
     *
     * @param surfaceHolder 用于播放的Surface
     * @return true 表示成功， false 表示失败
     */
    public boolean setSurfaceHold(SurfaceHolder surfaceHolder) {
        return setSurfaceHold(surfaceHolder, 0);
    }

    /**
     * 设置播放器的显示Surface
     *
     * @param surfaceHolder 用于播放的Surface
     * @param streamId 轨道 0：广角镜头画面轨道 1：云台镜头画面轨道
     * @return true 表示成功， false 表示失败
     */
    public boolean setSurfaceHold(SurfaceHolder surfaceHolder, int streamId) {
        if (surfaceHolder != null && !surfaceHolder.getSurface().isValid()) {
            return false;
        }
        if (streamId == 0) {
            mSurfaceHolder = surfaceHolder;
            if (mNpcPlayer != null) {
                mNpcPlayer.setDisplay(surfaceHolder);
                return true;
            }
            if (mLanPlayer != null) {
                mLanPlayer.setDisplay(mSurfaceHolder);
                return true;
            }

            if (streamCtrl == null) {
                return false;
            }
            if (mSurfaceTexture != null) {
                streamCtrl.setSurfaceEx(null);
            }
            streamCtrl.setSurfaceHold(surfaceHolder);
            return true;
        } else if (streamId == 1) {
            mSurfaceHolder2 = surfaceHolder;
            if (mNpcPlayer != null) {
                mNpcPlayer.setDisplay(surfaceHolder, streamId);
                return true;
            }
            if (mLanPlayer != null) {
                mLanPlayer.setDisplay(mSurfaceHolder, streamId);
                return true;
            }

            if (streamCtrl == null) {
                return false;
            }
            if (mSurfaceTexture != null) {
                streamCtrl.setSurfaceEx(null, streamId);
            }
            streamCtrl.setSurfaceHold(surfaceHolder, streamId);
            this.paramHelp.isMultiChannelDevice = true;
            return true;
        }
        return false;
    }

    /**
     * 播放器窗口大小变更时可调用该方法，减少黑屏时间
     */
    public void refreshPlay() {
        refreshPlay(0);
    }

    /**
     * 播放器窗口大小变更时可调用该方法，减少黑屏时间
     *
     * @param streamId 双目设备轨道，0：广角镜头画面轨道 1：云台镜头画面轨道
     */
    public void refreshPlay(int streamId) {
        if (streamCtrl == null) {
            return;
        }
        streamCtrl.refreshPlay(streamId);
    }

    /**
     * 设置播放器的显示Surface
     *
     * @param surfaceHolders 用于播放的Surface
     * @return true 表示成功， false 表示失败
     */
    public boolean setSurfaceHolds(SurfaceHolder[] surfaceHolders) {
//        if (surfaceHolders != null && !surfaceHolders.getSurface().isValid()) {
//            return false;
//        }
        mSurfaceHolders = surfaceHolders;
//        if (mNpcPlayer != null) {
//            mNpcPlayer.setDisplay(surfaceHolder);
//            return true;
//        }
//        if (mLanPlayer != null) {
//            mLanPlayer.setDisplay(mSurfaceHolder);
//            return true;
//        }
//        if (streamCtrl == null) {
//            return false;
//        }
//        if (mSurfaceTexture != null) {
//            streamCtrl.setSurfaceEx(null);
//        }
        streamCtrl.setSurfaceHolds(surfaceHolders);
        return true;
    }

    /**
     * 打开鱼眼矫正
     */
    public void openFecCorrect(EZConstants.EZFecCorrectType fecCorrectType, EZConstants.EZFecPlaceType fecPlaceType) {
        streamCtrl.openFecCorrect(fecCorrectType, fecPlaceType);
    }

    /**
     * 鱼眼矫正窗口点击处理
     */
    public boolean onFecTouchDown(int index, float x, float y) {
        return streamCtrl.onFecTouchDown(index, x, y);
    }

    /**
     * 鱼眼矫正窗口移动处理
     */
    public void onFecTouchMove(int index, float x, float y) {
        streamCtrl.onFecTouchMove(index, x, y);
    }

    /**
     * 鱼眼矫正窗口放开处理
     */
    public void onFecTouchUp(int index) {
        streamCtrl.onFecTouchUp(index);
    }

    /**
     * 鱼眼矫正窗口开始缩放处理
     */
    public void onFecTouchStartScale(int index, float distance) {
        streamCtrl.onFecTouchStartScale(index, distance);
    }

    /**
     * 鱼眼矫正窗口缩放处理
     */
    public void onFecTouchScale(int index, float scale, float maxScale) {
        streamCtrl.onFecTouchScale(index, scale, maxScale);
    }

    /**
     * 设置RTMP音频播放，无视频
     *
     * @param audioOnly
     */
    public void setAudioOnly(boolean audioOnly) {
        isAudioOnly = audioOnly;
    }


    /**
     * 设置Handler, 该handler将被用于从播放器向handler传递消息
     *
     * @param handler 处理消息的Handler
     * @return true 表示成功， false 表示失败
     */
    public boolean setHandler(Handler handler) {
        if (mLanPlayer != null || isNpcPlayer) {
            mHandler = handler;
            return true;
        }
        //mHandler = handler;
        if (streamCtrl == null) {
            return false;
        }
        streamCtrl.setHandler(handler);
        return true;
    }

    /**
     * 传入视频加密密码，用于加密视频的解码，该接口可以在收到ERROR_INNER_VERIFYCODE_NEED或ERROR_INNER_VERIFYCODE_ERROR错误回调时调用
     *
     * @param verifyCode 视频加密密码，默认为设备的6位验证码
     */
    public void setPlayVerifyCode(String verifyCode) {
        if (mLanPlayer != null) {
            mLanPlayer.setSecretKey(verifyCode);
            return;
        }
        if (streamCtrl == null) {
            return;
        }
        mVerifyCode = verifyCode;
        streamCtrl.setPlayKey(verifyCode);
    }

    /**
     * 开始实时预览
     *
     * @return true 表示成功， false 表示失败
     */
    public boolean startRealPlay() {
        if (isNpcPlayer) {
            mNpcPlayer = NpcPlayer.create(mPlayerUrl);
            mNpcPlayer.setHandler(mHandler);
            if (mSurfaceTexture != null) {
                mNpcPlayer.setDisplay(mSurfaceTexture);
            } else {
                mNpcPlayer.setDisplay(mSurfaceHolder);
            }
            mNpcPlayer.setAudioOnly(isAudioOnly);
            mNpcPlayer.start();
            return true;
        }
        if (mLanPlayer != null) {
            cachedThreadPool.submit(new Runnable() {
                @Override
                public void run() {
                    if (mLanPlayer != null) {
                        mLanPlayer.setDisplay(mSurfaceHolder);
                        mLanPlayer.start();
                    }
                }
            });
            return true;
        }
        if (streamCtrl == null) {
            return false;
        }
        streamCtrl.startRealPlay();

        return true;
    }

    /**
     * 结束实时预览
     *
     * @return true 表示成功， false 表示失败
     */
    public boolean stopRealPlay() {
        if (isNpcPlayer && mNpcPlayer != null) {
            mNpcPlayer.stop();
            mNpcPlayer = null;
            return true;
        }

        if (mLanPlayer != null) {
            cachedThreadPool.submit(new Runnable() {
                @Override
                public void run() {
                    if (mLanPlayer != null) {
                        mLanPlayer.stop();
                        sendMessage(EZConstants.EZRealPlayConstants.MSG_REALPLAY_STOP_SUCCESS, 0, null);
                    }
                }
            });
            return true;
        }
        if (streamCtrl == null) {
            return false;
        }
        streamCtrl.stopRealPlay();

        return true;
    }

    /**
     * 开启截屏，需要先开启预览或回放
     *
     * @return 图片数据
     */
    public Bitmap capturePicture() {
        return capturePicture(0);
    }

    /**
     * 开启截屏，需要先开启预览或回放
     *
     * @param streamId 双目设备轨道，0：广角镜头画面轨道 1：云台镜头画面轨道
     *
     * @return 图片数据
     */
    public Bitmap capturePicture(int streamId) {
        Bitmap targetBitmap = null;
        String tmpPic = PlayAPI.mApplication.getExternalCacheDir() + "/0_OpenSDK/tmp/" + System.currentTimeMillis() + ".tmp";
        int ret = capturePicture(tmpPic, streamId);
        LogUtil.d(TAG, "ret of capturePicture: " + ret);
        if (ret == EZ_OK) {
            targetBitmap = BitmapFactory.decodeFile(tmpPic);
        }
        File tmpPicFile = new File(tmpPic);
        if (tmpPicFile.exists()) {
            LogUtil.d(TAG, "delete tmpPic: " + tmpPicFile.delete());
        }
        return targetBitmap;
    }

    /**
     * 开启截屏，需要先开启预览或回放
     *
     * @param fileNameWithPath 截图路径
     *
     * @return 成功返回EZ_OK, 否则返回错误码
     */
    public int capturePicture(String fileNameWithPath) {
        return capturePicture(fileNameWithPath, 0);
    }

    /**
     * 开启截屏，需要先开启预览或回放
     *
     * @param fileNameWithPath 截图路径
     * @param streamId 双目设备轨道，0：广角镜头画面轨道 1：云台镜头画面轨道
     *
     * @return 成功返回EZ_OK, 否则返回错误码
     */
    public int capturePicture(String fileNameWithPath, int streamId) {
        File filepath = new File(fileNameWithPath);
        File parent = filepath.getParentFile();
        if (parent != null && (!parent.exists() || parent.isFile())) {
            parent.mkdirs();
        }
        if (mLanPlayer != null) {
            return mLanPlayer.capture(fileNameWithPath, streamId);
        }
        if (streamCtrl == null) {
            return -1;
        }
        return streamCtrl.capturePicture(fileNameWithPath, streamId);
    }

    /**
     * 开启声音
     *
     * @return true 表示成功， false 表示失败
     */
    public boolean openSound() {
        if (streamCtrl == null) {
            return false;
        }
        return streamCtrl.soundCtrl(true);
    }

    /**
     * 关闭声音
     *
     * @return true 表示成功， false 表示失败
     */
    public boolean closeSound() {
        if (streamCtrl == null) {
            return false;
        }
        return streamCtrl.soundCtrl(false);
    }

    /**
     * 获取当前播放时间戳
     *
     * @return true 表示成功， false 表示失败
     */
    public Calendar getOSDTime() {
        if (mLanPlayer != null) {
            EZMediaPlayer.EZOSDTime ezosdTime = mLanPlayer.getOSDTime();
            if (ezosdTime == null) {
                return null;
            }
            Calendar mOSDTime = new GregorianCalendar();
            mOSDTime.set(ezosdTime.year, ezosdTime.month - 1, ezosdTime.day, ezosdTime.hour, ezosdTime.min, ezosdTime.sec);
            return mOSDTime;
        }
        if (streamCtrl == null) {
            return null;
        }
        return streamCtrl.getOSDTime();
    }

    /**
     * 开始对讲
     *
     * @param isDeviceTalkBack true:当前设备对讲  false:nvr下的ipc设备对讲
     *
     * @return true 表示成功， false 表示失败
     */
    public boolean startVoiceTalk(boolean isDeviceTalkBack) {
        if (streamCtrl == null) {
            return false;
        }

        this.paramHelp.setIsDeviceTalkBack(isDeviceTalkBack);
        streamCtrl.startTalkback();

        return true;
    }

    public boolean startVoiceTalk() {
        return startVoiceTalk(true);
    }

    /**
     * 停止对讲
     *
     * @return true 表示成功， false 表示失败
     */
    public boolean stopVoiceTalk() {
        if (streamCtrl == null) {
            return false;
        }
        streamCtrl.stopTalkback();

        return true;
    }

    /**
     * 设置对讲本地采集音量大小回调
     * 在调用startVoiceTalk前生效
     * 注意：回调的音量单位为分贝，均为负数。在数字音频处理中，音量通常以dBFS（分贝满刻度）为单位来表示。
     * 0dBFS表示信号的最大可能幅度，即满量程刻度。由于有效的信号数值通常小于这个最大值，取对数后得到的值通常是负数。
     * 建议：[-90, -40)音量显示1格，[-40, -35)音量显示2格，[-35, -30)音量显示3格，[-30, -20)音量显示4格，[-20, 0]音量显示5格
     * 如需其他层级的音量显示效果，需开发者自行调试
     *
     * @param onLoudnessListener 本地采集音量大小回调
     * @param interval           回调间隔，单位秒，最小值、最大值与采样率有关，若过大或过小，则将自动限制在范围之内
     */
    public void setVoiceTalkLoudnessCallback(EZVoiceTalk.OnLoudnessListener onLoudnessListener, float interval) {
        if (streamCtrl == null) {
            return;
        }
        streamCtrl.setVoiceTalkLoudnessCallback(onLoudnessListener, interval);
    }

    /**
     * 设置语音对讲时的AEC方式
     *
     * @param useSystemAEC true-使用系统方式（默认） false-使用算法方式
     */
    public void setUseSystemAEC(boolean useSystemAEC) {
        this.paramHelp.useSystemAEC = useSystemAEC;
    }

    /**
     * 半双工对讲专用接口，是否切换到听说状态，startVoiceTalk对讲开启成功后才能调用
     *
     * @param pressed：true只说不停 false只听不说
     */
    public void setVoiceTalkStatus(boolean pressed) {
        if (streamCtrl == null) {
            return;
        }
        streamCtrl.setTalkbackStatus(pressed);
    }

    /**
     * 开启本地麦克风采集
     */
    public void openVoiceTalkMicrophone() {
        if (streamCtrl == null) {
            return;
        }
        streamCtrl.openVoiceTalkMicrophone();
    }

    /**
     * 关闭本地麦克风采集
     */
    public void closeVoiceTalkMicrophone() {
        if (streamCtrl == null) {
            return;
        }
        streamCtrl.closeVoiceTalkMicrophone();
    }

    /**
     * 对讲变声，对讲成功后开启，需要设备开通变声服务后才生效（只支持国内，海外不支持）
     */
    public void startVoiceChange(EZConstants.EZVoiceChangeType voiceChangeType, EZTalkback.TalkBackVoiceChangeCallback callback) {
        if (streamCtrl == null) {
            return;
        }
        streamCtrl.startVoiceChange(voiceChangeType, callback);
    }

    /**
     * 全双工对讲时设置手机端是否能听到对端的声音
     *
     * @param muted 是否静音
     */
    public void setTalkRemoteMuted(boolean muted) {
        if (streamCtrl == null) {
            return;
        }
        streamCtrl.setTalkRemoteMuted(muted);
    }

    /**
     * 设置视频录制下载回调，适用于视频播放时的视频录制结果回调
     *
     * @param mStreamDownloadCallback
     */
    public void setStreamDownloadCallback(EZOpenSDKListener.EZStreamDownloadCallback mStreamDownloadCallback) {
        this.mStreamDownloadCallback = mStreamDownloadCallback;
    }

    /**
     * 开始本地直播流录像功能
     * 注意：如本地录制5秒，网络正常、设备码流正常等理想情况下，生成的mp4文件时长大致5秒左右。以下几种情况生成的mp4文件时间会有出入
     * 1、网络异常，画面卡住了，此时就无法对码流进行录制了。应用层计时器一直在走，但是码流没有在录制。
     * 2、设备码流本身异常，存在问题，比如帧丢失、跳帧等情况，无法保证录制的mp4视频时长与录制时长是匹配的。
     *
     * @param recordFile 此路径必须指定为沙盒路径；不能指定为相册路径，新系统上有限制
     *
     * @return true 表示成功， false 表示失败
     */
    public boolean startLocalRecordWithFile(String recordFile) {
        if (streamCtrl == null) {
            return false;
        }

        mOutFilepath = recordFile;
        mOutTempFilepath = recordFile + "_temp";

        boolean isMultiChannelDevice = this.paramHelp != null && this.paramHelp.isMultiChannelDevice;
        LogUtil.d(TAG, "mOutFilepath is " + recordFile);
        LogUtil.d(TAG, "isMultiChannelDevice is " + isMultiChannelDevice);

        boolean ret = streamCtrl.startRecordFile(mOutTempFilepath, isMultiChannelDevice ? -1 : 0);
        if (ret) {
            return true;

        } else {
            File file = new File(mOutTempFilepath);
            if (file.exists()) {
                file.delete();
            }
            File out = new File(mOutFilepath);
            if (out.exists()) {
                out.delete();
            }
        }
        return false;
    }

    /**
     * 结束本地直播流录像
     * @return true 表示成功， false 表示失败
     */
    public boolean stopLocalRecord() {
        LogUtil.d(TAG, "stopLocalRecord");
        if (streamCtrl == null) {
            return false;
        }
        boolean isMultiChannelDevice = this.paramHelp != null && this.paramHelp.isMultiChannelDevice;
        streamCtrl.stopRecordFile(isMultiChannelDevice ? -1 : 0);
        VideoTransUtil.TransPsToMp4(mOutTempFilepath, mVerifyCode, mOutFilepath, isMultiChannelDevice, mStreamDownloadCallback);
        return true;
    }

    /**
     * 开始云存储远程回放
     *
     * @param cloudFile 云存储文件信息
     * @return true 表示成功， false 表示失败
     */
    public boolean startPlayback(EZCloudRecordFile cloudFile) {
        if (streamCtrl == null || cloudFile.getStartTime() == null || cloudFile.getStopTime() == null) {
            return false;
        }
        streamCtrl.startCloudPlayback(cloudFile);
        return true;
    }

    /**
     * 开始远程SD卡回放
     *
     * @param deviceFile SD卡文件信息
     * @return true 表示成功， false 表示失败
     */
    public boolean startPlayback(EZDeviceRecordFile deviceFile) {
        if (streamCtrl == null || deviceFile.getStartTime() == null || deviceFile.getStopTime() == null) {
            return false;
        }
        this.paramHelp.setAIPlayback(false);
        streamCtrl.startLocalPlayback(deviceFile.getStartTime(), deviceFile.getStopTime());
        return true;
    }

    /**
     * 开始远程SD卡回放---按时间回放
     *
     * @param startTime 开始时间
     * @param stopTime  结束时间
     * @return true 表示成功， false 表示失败
     */
    public boolean startPlayback(Calendar startTime, Calendar stopTime) {
        if (streamCtrl == null || startTime == null || stopTime == null) {
            return false;
        }
        this.paramHelp.setAIPlayback(false);
        streamCtrl.startLocalPlayback(startTime, stopTime);
        return true;
    }

    /**
     * 开始远程SD卡AI回放（专供华住私有云，其他不支持）
     *
     * @param deviceFile SD卡文件信息
     * @return true 表示成功， false 表示失败
     */
    public boolean startAIPlayback(EZDeviceRecordFile deviceFile) {
        if (streamCtrl == null || deviceFile.getStartTime() == null || deviceFile.getStopTime() == null) {
            return false;
        }
        this.paramHelp.setAIPlayback(true);
        streamCtrl.startLocalPlayback(deviceFile.getStartTime(), deviceFile.getStopTime());
        return true;
    }

    /**
     * 停止远程回放
     *
     * @return true 表示成功， false 表示失败
     */
    public boolean stopPlayback() {
        if (streamCtrl == null) {
            return false;
        }
        streamCtrl.stopPlayback();
        return true;
    }

    /**
     * 暂停远程回放播放
     *
     * @return true 表示成功， false 表示失败
     */
    public boolean pausePlayback() {
        if (streamCtrl == null) {
            return false;
        }
        return streamCtrl.pausePlayback();
    }

    /**
     * 恢复远程回放播放
     *
     * @return true 表示成功， false 表示失败
     */
    public boolean resumePlayback() {
        if (streamCtrl == null) {
            return false;
        }
        return streamCtrl.resumePlayback();
    }

    /**
     * 根据偏移时间播放
     * 拖动进度条时调用此接口。先停止当前播放，再把offsetTime作为起始时间按时间回放
     *
     * @param offsetTime 录像偏移时间
     * @return true 表示成功， false 表示失败
     */
    public boolean seekPlayback(Calendar offsetTime) {
        if (streamCtrl == null) {
            return false;
        }
        return streamCtrl.seekPlayback(offsetTime);
    }

    /**
     * 设置电子放大区域
     * left, top, right, bottom均为-1时，表示关闭电子放大
     *
     * @param left   x轴，左顶点坐标
     * @param top    y轴，上顶点坐标
     * @param right  x轴，右顶点坐标
     * @param bottom y轴，下顶点坐标
     * @return true-设置成功 false-设置失败
     */
    public boolean setDisplayRegion(long left, long top, long right, long bottom) {
        return setDisplayRegion(left, top, right, bottom, 0);
    }

    /**
     * 设置电子放大区域
     * left, top, right, bottom均为-1时，表示关闭电子放大
     *
     * @param left   x轴，左顶点坐标
     * @param top    y轴，上顶点坐标
     * @param right  x轴，右顶点坐标
     * @param bottom y轴，下顶点坐标
     * @param streamId 双目设备轨道，0：广角镜头画面轨道 1：云台镜头画面轨道
     * @return true-设置成功 false-设置失败
     */
    public boolean setDisplayRegion(long left, long top, long right, long bottom, int streamId) {
        if (streamCtrl == null) {
            LogUtil.w(TAG, "mediaPlayer is null");
            return false;
        }
        return streamCtrl.setDisplayRegion(left, top, right, bottom, streamId);
    }

    /**
     * 电子放大，用于视频缩放
     *
     * @param enable   开启/关闭电子放大
     * @param original 缩放前的区域
     * @param current  需要缩放的区域
     * @throws PlaySDKException 播放库异常
     * @throws InnerException   SDK内部异常
     * @deprecated 建议换用setDisplayRegion(long left, long top, long right, long bottom)
     */
    public void setDisplayRegion(boolean enable, CustomRect original, CustomRect current) throws PlaySDKException,
            InnerException {
        setDisplayRegion(enable, original, current, 0);
    }

    /**
     * 电子放大，用于视频缩放
     *
     * @param enable   开启/关闭电子放大
     * @param original 缩放前的区域
     * @param current  需要缩放的区域
     * @param streamId 双目设备轨道 0：广角镜头画面轨道 1：云台镜头画面轨道
     * @throws PlaySDKException 播放库异常
     * @throws InnerException   SDK内部异常
     * @deprecated 建议换用setDisplayRegion(long left, long top, long right, long bottom)
     */
    public void setDisplayRegion(boolean enable, CustomRect original, CustomRect current, int streamId) throws PlaySDKException,
            InnerException {
        if (streamCtrl == null) {
            return;
        }
        streamCtrl.setDisplayRegion(enable, original, current, streamId);
    }

    /**
     * 获取留言数据
     *
     * @param msg                      留言信息
     * @param leaveMessageFlowCallback 流回调
     */
    public void getLeaveMessageData(EZLeaveMessage msg, EZOpenSDKListener.EZLeaveMessageFlowCallback leaveMessageFlowCallback) {
        if (streamCtrl == null) {
            return;
        }
        streamCtrl.startLeaveMsgDownload(msg);
    }

    /**
     * 设置留言下载回调
     *
     * @param leaveMessageFlowCallback 回调
     */
    public void setLeaveMessageFlowCallback(EZOpenSDKListener.EZLeaveMessageFlowCallback leaveMessageFlowCallback) {
        if (streamCtrl == null) {
            return;
        }
        streamCtrl.setLeaveMsgCallback(leaveMessageFlowCallback);
    }

    /**
     * 获取流量数据
     *
     * @return 返回获取流量数据
     * @see
     * @since V4.3
     */
    public long getStreamFlow() {
        if (mLanPlayer != null) {
            return mLanPlayer.getStreamFlow();
        }
        if (streamCtrl == null) {
            return 0;
        }
        return streamCtrl.getStreamFlow();
    }

    /**
     * 获取当前取流类型
     *
     * @return
     */
    public int getStreamFetchType() {
        if (streamCtrl == null) {
            return -1;
        }
        return streamCtrl.getStreamFetchType();
    }

    /**
     * 获取设备部分详情信息（出画面后才有回调）
     * @return
     */
    public EZDeviceDetailPublicInfo getDeviceDetailInfo() {
        if (streamCtrl == null) {
            return null;
        }
        return streamCtrl.getDeviceDetailInfo();
    }

    /**
     * 设置sdcard录像和云存储录像回放速度（倍数后播放没有声音，这个是正常的，不是问题）
     * sd卡及云存储倍速回放接口
     * 1.支持抽帧快放的设备最高支持16倍速快放（所有取流方式，包括P2P）
     * 2.不支持抽帧快放的设备，仅支持内外网直连快放，最高支持8倍
     * 3.HCNetSDK取流没有快放概念，全速推流，只改变播放库速率
     *
     * @param rate ,
     *             EZ_PLAYBACK_RATE_1,             // 1倍速
     *             EZ_PLAYBACK_RATE_4,             // 4倍速
     *             EZ_PLAYBACK_RATE_4_1,           // 1/4倍速     sdcard录像回放专用
     *             EZ_PLAYBACK_RATE_8,             // 8倍速
     *             EZ_PLAYBACK_RATE_8_1,           // 1/8倍速     sdcard录像回放专用
     *             EZ_PLAYBACK_RATE_16,            // 16倍速
     *             EZ_PLAYBACK_RATE_16_1;          // 1/16倍速    sdcard录像回放专用
     *             EZ_PLAYBACK_RATE_32;            // 32倍速      云存储回放专用
     * @return 成功返回true, 否则false并恢复成正常速度播放
     * @since V1.8.2
     */
    public boolean setPlaybackRate(EZConstants.EZPlaybackRate rate) {
        return setPlaybackRate(rate, 0);
    }

    /**
     * 设置sdcard录像和云存储录像回放速度
     *
     * @param rate ,
     *             EZ_PLAYBACK_RATE_1,             // 1倍速
     *             EZ_PLAYBACK_RATE_4,             // 4倍速
     *             EZ_PLAYBACK_RATE_4_1,           // 1/4倍速     sdcard录像回放专用
     *             EZ_PLAYBACK_RATE_8,             // 8倍速
     *             EZ_PLAYBACK_RATE_8_1,           // 1/8倍速     sdcard录像回放专用
     *             EZ_PLAYBACK_RATE_16,            // 16倍速
     *             EZ_PLAYBACK_RATE_16_1;          // 1/16倍速    sdcard录像回放专用
     *             EZ_PLAYBACK_RATE_32;            // 32倍速      云存储回放专用
     * @param mode 0代表4倍速及其以下全帧，以上则抽帧；1代表均使用抽帧；2代表均使用全帧，达不到要求则降速
     * @return 成功返回true, 否则false并恢复成正常速度播放
     * @since V1.8.2
     */
    public boolean setPlaybackRate(EZConstants.EZPlaybackRate rate, int mode) {
        LogUtil.d(TAG, "EZPlaybackRate: " + rate);
        LogUtil.d(TAG, "mode: " + mode);
        if (streamCtrl == null) {
            return false;
        }
        return streamCtrl.setPlaybackRate(rate.value, mode);
    }

    /**
     * 设置云存储录像回放速度
     *
     * @param rate ,
     *             EZ_CLOUD_PLAYBACK_RATE_1(0),         // 正常模式
     *             EZ_CLOUD_PLAYBACK_RATE_4(1),         // 4倍速
     *             EZ_CLOUD_PLAYBACK_RATE_8(2),         // 8倍速
     *             EZ_CLOUD_PLAYBACK_RATE_16(3),        // 16倍速
     *             EZ_CLOUD_PLAYBACK_RATE_32(4);        // 32倍速
     * @return 成功返回true, 否则false并恢复成正常速度播放
     * @since V1.8.2
     * @deprecated 自4.8.8版本开始，请直接使用setPlaybackRate设置云存储回放速度，setCloudPlaybackRate接口不再维护，可能无法正常使用
     */
    public boolean setCloudPlaybackRate(EZConstants.EZCloudPlaybackRate rate) {
        if (streamCtrl == null) {
            return false;
        }
        return streamCtrl.setPlaybackRate(rate.value, 0);
    }

    @Override
    public void onCompletion(EZMediaPlayer mp) {
        LogUtil.d(TAG, "stop success");
        sendMessage(EZConstants.EZPlaybackConstants.MSG_REMOTEPLAYBACK_PLAY_FINISH, 0, null);
    }

    @Override
    public boolean onError(EZMediaPlayer mp, EZMediaPlayer.MediaError error, int errorCode) {
        LogUtil.w(TAG, "mediaplayer onError. error is " + error + ", error code is " + errorCode);
        if (EZMediaPlayer.MediaError.MEDIA_ERROR_TIMEOUT == error) {
            ErrorInfo errorInfo = ErrorLayer.getErrorLayer(ErrorDefine.OPERATION_INNER, ErrorCode.ERROR_INNER_STREAM_TIMEOUT);
            sendMessage(EZConstants.EZRealPlayConstants.MSG_REALPLAY_PLAY_FAIL, errorInfo.errorCode, errorInfo);
        } else {
            ErrorInfo errorInfo = ErrorLayer.getErrorLayer(ErrorDefine.OPERATION_STREAM_STREAMSDK, errorCode);
            sendMessage(EZConstants.EZRealPlayConstants.MSG_REALPLAY_PLAY_FAIL, errorInfo.errorCode, errorInfo);
        }
        return false;
    }

    @Override
    public boolean onInfo(EZMediaPlayer mp, EZMediaPlayer.MediaInfo info, int index) {
        LogUtil.w(TAG, "mediaplayer onInfo. info is " + info);
        if (EZMediaPlayer.MediaInfo.MEDIA_INFO_VIDEO_SIZE_CHANGED == info) {
            sendMessage(EZConstants.MSG_VIDEO_SIZE_CHANGED, 0, new StringBuffer().append(mp.getVideoWidth()).append(":").append(mp.getVideoHeight()).toString());
            sendMessage(EZConstants.EZRealPlayConstants.MSG_REALPLAY_PLAY_SUCCESS, 0, null);
        }
        return true;
    }

    protected void sendMessage(int msg, int arg1, Object obj) {
        if (mHandler != null) {
            Message message = Message.obtain();
            message.what = msg;
            message.arg1 = arg1;
            message.obj = obj;
            mHandler.sendMessage(message);
        }
    }

    /**
     * 后续重构使用回调不使用handle
     */
    private interface EZPlayCallBack {
        void handlePlaySuccess();

        void handlePlayFail(ErrorInfo errorInfo);

        void handleVideoSizeChange(int VideoWidth, int VideoHeight);
    }

    /**
     * 启用硬件加速功能,在start之前调用；默认软解
     *
     * @param enable true启用硬件加速,false关闭硬件加速
     * @see
     * @since V4.8.7
     */
    public void setHard(boolean enable) {
        if (streamCtrl == null) {
            return;
        }
        streamCtrl.setHard(enable);
    }

    /**
     * 切换模式
     * false:耳机模式
     * true：听筒模式
     */
    public void setSpeakerphoneOn(boolean on) {
        if (streamCtrl != null) {
            streamCtrl.setSpeakerphoneOn(on);
        }
    }

    /**
     * 获取声音模式
     * false:耳机模式
     * true：听筒模式
     */
    public boolean isSpeakerphoneOn() {
        boolean isSpeakerphoneOn = true;
        if (streamCtrl != null) {
            isSpeakerphoneOn = streamCtrl.isSpeakerphoneOn();
        }
        return isSpeakerphoneOn;
    }

    /**
     * 设置使用硬件解码器优先，需在startRealPlay之前调用；默认软解
     * 建议开启，提升解密效率。设备出流分辨率越高，硬解速度越快。
     *
     * @param enable true启用，false关闭
     * @since V4.8.6.1
     */
    public synchronized void setHardDecode(boolean enable) {
        mPlayConfig.isHardDecodeFirst = enable;
    }

    /**
     * （用于调试）
     * 设置原始码流数据回调
     * 在调用startRealPlay或startPlayback前生效
     *
     * @since V4.8.7
     */
    public synchronized void setOriginDataCallback(EZOpenSDKListener.OriginDataCallback callback) {
        mPlayConfig.mOriginDataCallback = callback;
    }

    /**
     * 开始本地直播流录像功能（保存为ps文件）
     * <p>
     * 需要保证以下权限：
     * {@link android.Manifest.permission#WRITE_EXTERNAL_STORAGE}
     *
     * @return true 表示成功， false 表示失败
     */
    public boolean startLocalRecordWithFileEx(String recordFile) {
        if (streamCtrl == null) {
            return false;
        }
        mOutFilepath = recordFile;
        LogUtil.d(TAG, "mOutFilepath is " + mOutFilepath);
        if (streamCtrl.startRecordFile(mOutFilepath, 0)) {
            return true;
        } else {
            File file = new File(mOutFilepath);
            if (file.exists()) {
                boolean isSuc = file.delete();
                LogUtil.i(TAG, "try to delete old record file " + isSuc);
            }
        }
        return false;
    }

    /**
     * 结束本地直播流录像（保存为ps文件）
     * <p>
     * 需要保证以下权限：
     * {@link android.Manifest.permission#WRITE_EXTERNAL_STORAGE}
     *
     * @return true 表示成功， false 表示失败
     */
    public boolean stopLocalRecordEx() {
        if (streamCtrl == null) {
            return false;
        }
        streamCtrl.stopRecordFile(0);
        File file = new File(mOutFilepath);
        if (file.exists()) {
            if (mStreamDownloadCallback != null) {
                mStreamDownloadCallback.onSuccess(mOutFilepath);
            }
        } else {
            if (mStreamDownloadCallback != null) {
                mStreamDownloadCallback.onError(EZOpenSDKListener.EZStreamDownloadError.ERROR_EZSTREAM_DOWNLOAD_STOP);
            }
            return false;
        }
        return true;
    }

    public boolean tryTransPsToMp4(String psFilePath, String mp4FilePath) {
        return tryTransPsToMp4(psFilePath, mp4FilePath, null);
    }

    /**
     * 转换ps文件为mp4文件
     *
     * @param psFilePath  资源ps文件所在路径
     * @param mp4FilePath 目标mp4文件所在路径
     * @param verifyCode  ps文件加密秘钥
     * @return 转换结果 true-成功，false-失败
     */
    public boolean tryTransPsToMp4(String psFilePath, String mp4FilePath, String verifyCode) {
        boolean finishedToTrans = false;
        SystemTransformSim trans = null;
        try {
            trans = SystemTransformSim.create(SystemTransform.TRANS_SYSTEM_MPEG4, psFilePath, mp4FilePath);
            int errCode = trans.start(verifyCode);
            if (errCode != EZ_OK) {
                LogUtil.e(TAG, "failed to start trans, error code is " + errCode);
                return false;
            }

            // 在未完成转换，且未发生任何错误的情况下，循环获取转换进度
            boolean notOccurredError = true;
            do {
                EZGetPercentInfo percent = trans.getPercent();
                LogUtil.d(TAG, "percent of trans is " + percent.percent);
                if (percent.ret == EZError.EZ_TRANSFORM_ERROR_RES_CHANGED) {
                    LogUtil.e(TAG, "unexpected resolution");
                    notOccurredError = false;
                }
                // 转码失败
                if (percent.ret != 0 || percent.percent == -1) {
                    LogUtil.e(TAG, "unexpected error");
                    notOccurredError = false;
                }
                // 转码成功
                if (percent.percent == 100) {
                    finishedToTrans = true;
                }
                Thread.sleep(100);
            } while (!finishedToTrans && notOccurredError);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (trans != null) {
                trans.stop();
                trans.release();
            }
        }
        return finishedToTrans;
    }

    public void startPlaybackV2(EZPlaybackStreamParam param) {
        if (param == null) {
            LogUtil.e(TAG, "invalid param!");
            return;
        }
        switch (param.recordSource) {
            // 云端录像
            case 1:
                startPlayback(param.ezCloudRecordFile);
                break;
            // 本地录像
            case 2:
                startPlayback(param.ezDeviceRecordFile);
                break;
            default:
                LogUtil.e(TAG, "unknown record source!");
                break;
        }
    }

    /**
     * 设置打开播放库的智能分析，当前温感相机的框框使用了该智能分析数据，预览成功后调用，在播放过程中随时开关
     *
     * @param enable true：开  false：关
     *
     * @return 返回状态
     */
    public boolean setIntelAnalysis(boolean enable) {
        if (streamCtrl == null) {
            return false;
        }
        return streamCtrl.setIntelAnalysis(enable);
    }

    /**
     * 全局p2p开启的情况下，该播放器禁用p2p取流。startRealPlay之前调用
     */
    public void setPlayerDisableP2P() {
        this.paramHelp.isPlayerDisableP2P = true;
    }

    /**
     * 是否开启自动清晰度网络检测开关；此api未调用时，不会回调以下消息
     * @see EZConstants.EZRealPlayConstants#MSG_VIDEO_LEVEL_AUTO_IMPROVE  网络好，会回调此消息，建议切换高一级清晰度
     * @see EZConstants.EZRealPlayConstants#MSG_VIDEO_LEVEL_AUTO_REDUCE   网络差，会回调此消息，建议切换低一级清晰度
     */
    public void enableDeviceAutoVideoLevel() {
        this.paramHelp.isAutoDeviceVideoLevel = true;
    }

    /**
     * 设置性能优先或画质优先，startRealPlay之前调用，播放前会先设置清晰度再发起取流，会增加首帧画面耗时
     * 因为涉及到清晰度切换，取流成功后必须设置下清晰度UI
     * @param videoQuality 视频质量，指定性能优先或画质优先；VIDEO_PERFORMANCE_PRIORITY:设置为最低清晰度 VIDEO_QUALITY_PRIORITY:设置为最高清晰度
     */
    public void setVideoQuality(EZConstants.EZVideoQuality videoQuality) {
        this.paramHelp.videoQuality = videoQuality;
    }

    /**
     * 设置取流小权限token，取流用
     * EZOpenSDK.enableSDKWithTKToken开启后，需要设置取流小权限token
     * 注意：IPC设备对讲使用的是0通道，对讲streamToken生成请使用0通道
     *
     * @param streamToken
     */
    public void setStreamToken(String streamToken) {
        this.paramHelp.streamToken = streamToken;
    }

    /**
     * 设置播放画面的旋转角度，旋转90°、270°时需调整播放器的尺寸（只支持单目设备，不支持多目设备）
     * @param rotationAngle 旋转角度
     */
    public void setPlayerViewRotation(EZConstants.EZPlayerViewRotationAngle rotationAngle) {
        if (streamCtrl == null) {
            return;
        }
        streamCtrl.setPlayerViewRotation(rotationAngle);
    }

    public void setDemuxModel(int demuxModel) {
        if (streamCtrl == null) {
            return;
        }
        streamCtrl.setDemuxModel(demuxModel);
    }

    /**
     * 设置浓缩回放录像参数
     * @param videoRecordTypeEx 浓缩回放录像类型（只能设置5、6、7对应的枚举值，否则无效）；不需要时设置为EZ_VIDEO_RECORD_TYPE_NONE
     * @param frameInterval 浓缩回放帧间隔，单位为秒；不需要时设置为0
     */
//    public void setCompressVideoRecordParams(EZConstants.EZVideoRecordTypeEx videoRecordTypeEx, int frameInterval) {
//        if (streamCtrl == null) {
//            return;
//        }
//        this.paramHelp.setCompressVideoRecordParams(videoRecordTypeEx, frameInterval);
//    }

}
