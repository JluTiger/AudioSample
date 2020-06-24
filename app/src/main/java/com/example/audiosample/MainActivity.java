package com.example.audiosample;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

import kr.co.namee.permissiongen.PermissionGen;

public class MainActivity extends Activity implements View.OnClickListener {

    public static final String TAG = "AudioSample";

    //是否在录制
    private boolean isRecording = false;
    //开始录音
    private Button startAudio;
    //结束录音
    private Button stopAudio;
    //播放录音
    private Button playAudio;
    //删除文件
    private Button deleteAudio;
    // 当前录取音频的名字
    private String name;
    private ScrollView mScrollView;
    private TextView tv_audio_succeess;

    //pcm文件
    private File file;

    // listview适配
    private ListView listView;
    private DBOpenHelper dbOpenHelper;
    private List<String> recordList;
    private RecordAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        init();
        initView();
    }

    // 动态获取权限
    private void init() {
        PermissionGen.with(this)
                .addRequestCode(100)
                .permissions(Manifest.permission.RECORD_AUDIO
                        , Manifest.permission.WRITE_EXTERNAL_STORAGE
                        , Manifest.permission.WAKE_LOCK
                        , Manifest.permission.READ_EXTERNAL_STORAGE)
                .request();
    }

    // 初始化view
    public void initView() {
        listView = findViewById(R.id.record_list);
        dbOpenHelper = new DBOpenHelper(MainActivity.this);
        recordList = new ArrayList<>();
        // 获取数据库所有数据
        recordList = dbOpenHelper.getAllData();
        // 绑定
        adapter = new RecordAdapter(MainActivity.this, recordList);
        listView.setAdapter(adapter);
        // listview点击事件
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                // 点击播放音频
                playRecord(recordList.get(position));
            }
        });

        listView.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> parent, View view, final int position, long id) {
                // 长按删除
                AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
                builder.setTitle("删除提示");
                builder.setMessage("一旦删除，无法恢复！");
                builder.setPositiveButton("确定", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        // 删除文件
                        deleteRecord(recordList.get(position));
                        //点击确定后删除数据库，更新listview
                        dbOpenHelper.delete(recordList.get(position));
                        recordList.remove(position);
                        adapter.notifyDataSetChanged();
                    }
                });
                builder.setNegativeButton("取消", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {

                    }
                });
                builder.create().show();
                return true;
            }
        });


        mScrollView = (ScrollView) findViewById(R.id.mScrollView);
        tv_audio_succeess = (TextView) findViewById(R.id.tv_audio_succeess);
        printLog("初始化成功");
        startAudio = (Button) findViewById(R.id.startAudio);
        startAudio.setOnClickListener(this);
        stopAudio = (Button) findViewById(R.id.stopAudio);
        stopAudio.setOnClickListener(this);
        playAudio = (Button) findViewById(R.id.playAudio);
        playAudio.setOnClickListener(this);
        deleteAudio = (Button) findViewById(R.id.deleteAudio);
        deleteAudio.setOnClickListener(this);
    }

    // 打印log
    private void printLog(final String resultString) {
        tv_audio_succeess.post(new Runnable() {
            @Override
            public void run() {
                tv_audio_succeess.append(resultString + "\n");
                mScrollView.fullScroll(ScrollView.FOCUS_DOWN);
            }
        });
    }

    //获取/失去焦点
    private void ButtonEnabled(boolean start, boolean stop, boolean play) {
        startAudio.setEnabled(start);
        stopAudio.setEnabled(stop);
        playAudio.setEnabled(play);
    }

    // 更新listview
    private void updateRecordList() {
        recordList.clear();
        recordList.addAll(dbOpenHelper.getAllData());
        adapter.notifyDataSetChanged();
    }


    //点击事件
    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.startAudio:
                Thread thread = new Thread(new Runnable() {
                    @Override
                    public void run() {
                        StartRecord();
                        Log.e(TAG, "start");
                    }
                });
                thread.start();
                printLog("开始录音");
                ButtonEnabled(false, true, false);
                break;
            case R.id.stopAudio:
                isRecording = false;
                ButtonEnabled(true, false, true);
                printLog("停止录音");
                updateRecordList();
                break;
            case R.id.playAudio:
                PlayRecord();
                ButtonEnabled(true, false, false);
                printLog("播放录音");
                break;
            case R.id.deleteAudio:
                deleteFile();
                break;
        }
    }

    //开始录音
    public void StartRecord() {
        //16K采集率
        int frequency = 16000;
        //格式
        int channelConfiguration = AudioFormat.CHANNEL_CONFIGURATION_MONO;
        //16Bit
        int audioEncoding = AudioFormat.ENCODING_PCM_16BIT;
        name = String.valueOf(System.currentTimeMillis());
        // 将当前音频名称加入数据库
        dbOpenHelper.add(name);
        //生成PCM文件
        file = new File(Environment.getExternalStorageDirectory().getAbsolutePath() + "/" + name + ".pcm");
        //如果存在，就先删除再创建
        if (file.exists())
            file.delete();
        try {
            file.createNewFile();
        } catch (IOException e) {
            throw new IllegalStateException("未能创建" + file.toString());
        }
        try {
            //输出流
            OutputStream os = new FileOutputStream(file);
            BufferedOutputStream bos = new BufferedOutputStream(os);
            DataOutputStream dos = new DataOutputStream(bos);
            int bufferSize = AudioRecord.getMinBufferSize(frequency, channelConfiguration, audioEncoding);
            AudioRecord audioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC, frequency, channelConfiguration, audioEncoding, bufferSize);

            short[] buffer = new short[bufferSize];
            audioRecord.startRecording();
            isRecording = true;
            while (isRecording) {
                int bufferReadResult = audioRecord.read(buffer, 0, bufferSize);
                for (int i = 0; i < bufferReadResult; i++) {
                    dos.writeShort(buffer[i]);
                }
            }
            audioRecord.stop();
            dos.close();
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    //播放文件
    public void PlayRecord() {
        if (file == null) {
            printLog("录音文件不存在，请重新录制！");
            return;
        }

        //读取文件
        int musicLength = (int) (file.length() / 2);
        short[] music = new short[musicLength];
        try {
            InputStream is = new FileInputStream(file);
            BufferedInputStream bis = new BufferedInputStream(is);
            DataInputStream dis = new DataInputStream(bis);
            int i = 0;
            while (dis.available() > 0) {
                music[i] = dis.readShort();
                i++;
            }
            dis.close();
            AudioTrack audioTrack = new AudioTrack(AudioManager.STREAM_MUSIC,
                    16000, AudioFormat.CHANNEL_CONFIGURATION_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    musicLength * 2,
                    AudioTrack.MODE_STREAM);
            audioTrack.play();
            audioTrack.write(music, 0, musicLength);
            audioTrack.stop();
        } catch (Throwable t) {
            printLog("播放失败，最新文件可能已被删除！");
            Log.e(TAG, "播放失败");
        }
    }

    //删除文件
    private void deleteFile() {
        if (file == null) {
            return;
        }

        // 删除最新音频时，先更新数据库和listview
        dbOpenHelper.delete(file.getName().split("\\.")[0]);
        recordList.clear();
        recordList.addAll(dbOpenHelper.getAllData());
        adapter.notifyDataSetChanged();

        file.delete();
        printLog("文件删除成功");
    }

    // 播放选中的音频文件
    private void playRecord(String n) {
        String file_name = new StringBuffer(n).append(".pcm").toString();
        File play_file = new File(Environment.getExternalStorageDirectory().getAbsolutePath() + "/" + file_name);
        if (play_file == null) {
            printLog("录音文件不存在！");
            return;
        }
        //读取文件
        int musicLength = (int) (play_file.length() / 2);
        short[] music = new short[musicLength];
        try {
            InputStream is = new FileInputStream(play_file);
            BufferedInputStream bis = new BufferedInputStream(is);
            DataInputStream dis = new DataInputStream(bis);
            int i = 0;
            while (dis.available() > 0) {
                music[i] = dis.readShort();
                i++;
            }
            dis.close();
            AudioTrack audioTrack = new AudioTrack(AudioManager.STREAM_MUSIC,
                    16000, AudioFormat.CHANNEL_CONFIGURATION_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    musicLength * 2,
                    AudioTrack.MODE_STREAM);
            audioTrack.play();
            audioTrack.write(music, 0, musicLength);
            audioTrack.stop();
        } catch (Throwable t) {
            printLog("播放失败，此文件可能已被删除！");
            Log.e(TAG, "播放失败");
        }
    }

    // 删除选中的音频文件
    private void deleteRecord(String n) {
        String file_name = new StringBuffer(n).append(".pcm").toString();
        //生成PCM文件
        File del_file = new File(Environment.getExternalStorageDirectory().getAbsolutePath() + "/" + file_name);
        //如果存在，就删除
        if (del_file.exists()) {
            del_file.delete();
        }
    }

}