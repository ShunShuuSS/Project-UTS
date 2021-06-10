package umn.ac.id.projectuts;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;
import java.util.Dictionary;
import java.util.List;

public class LoginActivity extends AppCompatActivity {
    private String user = "uasmobile";
    private String pass = "uasmobilegenap";
    private EditText username,password;
    private Button submitLogin;
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.login);
        init();
        setOnClick();
    }
    private void init(){
        username = findViewById(R.id.et_username);
        password = findViewById(R.id.et_password);
        submitLogin = findViewById(R.id.btn_submit_login);
    }

    private void setOnClick(){
        submitLogin.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String tempUser = username.getText().toString(), tempPass = password.getText().toString();

                if(tempUser.equals(user) && tempPass.equals(pass)){
                    Intent listLaguIntent = new Intent(LoginActivity.this, ListSongActivity.class);
                    startActivity(listLaguIntent);
                }else{
                    Toast toast=Toast.makeText(getApplicationContext(),"Username atau password salah",Toast.LENGTH_SHORT);
                    toast.show();
                }
            }
        });
    }

}
