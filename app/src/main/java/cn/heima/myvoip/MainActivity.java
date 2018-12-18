package cn.heima.myvoip;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;

public class MainActivity extends AppCompatActivity {

    @BindView(R.id.text_status)
    TextView textStatus;
    @BindView(R.id.btn_reg)
    Button btnReg;
    @BindView(R.id.et_tel)
    EditText etTel;
    @BindView(R.id.btn_call)
    Button btnCall;
    @BindView(R.id.btn_jie)
    Button btnJie;
    private LinphoneMiniManager mManager;

    public static final String RECEIVE_MAIN_ACTIVITY = "receive_main_activity";
    private MainActivityReceiver mReceiver;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ButterKnife.bind(this);
        mManager = new LinphoneMiniManager(this);

    }

    @OnClick({R.id.btn_reg, R.id.btn_call, R.id.btn_jie})
    public void onViewClicked(View view) {
        switch (view.getId()) {
            case R.id.btn_reg:
//                String sipAddress = "sip:1001@192.168.0.165", password = "12345";
                String sipAddress = "sip:1002@192.168.0.164", password = "12345";
                mManager.lilin_reg(sipAddress, password, "5060");
                break;
            case R.id.btn_call:
                mManager.lilin_call("sip:1002", "192.168.0.164", false);
                break;
            case R.id.btn_jie:
                mManager.lilin_jie();
                break;
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        //广播
        IntentFilter intentFilter = new IntentFilter(RECEIVE_MAIN_ACTIVITY);
        mReceiver = new MainActivityReceiver();
        registerReceiver(mReceiver, intentFilter);

    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(mReceiver);
    }

    @Override
    protected void onDestroy() {
        mManager.destroy();

        super.onDestroy();
    }

    /**
     * 在MainActivity.java添加广播接受者并在onCreate注册
     */
    public class MainActivityReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getStringExtra("action");
            switch (action) {
                case "reg_state":
                    textStatus.setText(intent.getStringExtra("data"));
                    break;
                default:
                    break;
            }
        }
    }

}
