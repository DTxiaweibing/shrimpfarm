package com.shrimpfarm.app.qa;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.shrimpfarm.app.R;
import com.shrimpfarm.app.qa.api.QaApi;

import java.util.ArrayList;
import java.util.List;

public class QaPostActivity extends AppCompatActivity {
    private EditText etTitle, etContent;
    private LinearLayout layoutPreview;
    private Button btnAddImage, btnPost;
    private QaApi api;
    private List<String> uploadedUrls = new ArrayList<>();
    private List<Uri> selectedUris = new ArrayList<>();

    private final ActivityResultLauncher<Intent> imagePicker = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getData() == null) return;
                Intent data = result.getData();
                int remaining = 9 - selectedUris.size();
                if (data.getClipData() != null) {
                    int count = data.getClipData().getItemCount();
                    for (int i = 0; i < count && remaining > 0; i++, remaining--) {
                        Uri uri = data.getClipData().getItemAt(i).getUri();
                        selectedUris.add(uri);
                        addPreviewImage(uri);
                    }
                } else if (data.getData() != null) {
                    selectedUris.add(data.getData());
                    addPreviewImage(data.getData());
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_qa_post);

        api = new QaApi(this);

        etTitle = findViewById(R.id.et_title);
        etContent = findViewById(R.id.et_content);
        layoutPreview = findViewById(R.id.layout_preview);
        btnAddImage = findViewById(R.id.btn_add_image);
        btnPost = findViewById(R.id.btn_post);

        btnAddImage.setOnClickListener(v -> {
            if (selectedUris.size() >= 9) {
                Toast.makeText(this, "最多添加9张图片", Toast.LENGTH_SHORT).show();
                return;
            }
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.setType("image/*");
            intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
            imagePicker.launch(intent);
        });

        btnPost.setOnClickListener(v -> {
            String title = etTitle.getText().toString().trim();
            String content = etContent.getText().toString().trim();
            if (title.isEmpty()) {
                Toast.makeText(this, "请输入问题标题", Toast.LENGTH_SHORT).show();
                return;
            }
            if (content.isEmpty()) {
                Toast.makeText(this, "请输入问题内容", Toast.LENGTH_SHORT).show();
                return;
            }
            btnPost.setEnabled(false);
            btnPost.setText("发布中...");
            if (selectedUris.isEmpty()) {
                doPost(title, content, new ArrayList<>());
            } else {
                uploadAllImages(title, content, 0);
            }
        });
    }

    private void uploadAllImages(String title, String content, int index) {
        if (index >= selectedUris.size()) {
            doPost(title, content, uploadedUrls);
            return;
        }
        api.uploadImage(selectedUris.get(index), new QaApi.QaCallback<String>() {
            @Override public void onSuccess(String url) {
                uploadedUrls.add(url);
                uploadAllImages(title, content, index + 1);
            }
            @Override public void onError(String error) {
                uploadAllImages(title, content, index + 1);
            }
        });
    }

    private void doPost(String title, String content, List<String> imageUrls) {
        api.postQuestion(title, content, imageUrls, new QaApi.QaCallback<com.shrimpfarm.app.qa.model.Question>() {
            @Override public void onSuccess(com.shrimpfarm.app.qa.model.Question q) {
                Toast.makeText(QaPostActivity.this, "发布成功", Toast.LENGTH_SHORT).show();
                setResult(RESULT_OK);
                finish();
            }
            @Override public void onError(String error) {
                btnPost.setEnabled(true);
                btnPost.setText("发布");
                Toast.makeText(QaPostActivity.this, error, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void addPreviewImage(Uri uri) {
        ImageView iv = new ImageView(this);
        int size = getResources().getDisplayMetrics().widthPixels / 4 - 16;
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(size, size);
        lp.setMargins(0, 0, 8, 0);
        iv.setLayoutParams(lp);
        iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
        iv.setOnClickListener(v -> {
            selectedUris.remove(uri);
            layoutPreview.removeView(iv);
        });
        Glide.with(this).load(uri).into(iv);
        layoutPreview.addView(iv);
    }
}
