package com.shrimpfarm.app.qa;

import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import com.shrimpfarm.app.BaseActivity;

import com.bumptech.glide.Glide;
import com.shrimpfarm.app.R;
import com.shrimpfarm.app.qa.api.QaApi;
import com.shrimpfarm.app.utils.SensitiveWordFilter;

import java.util.ArrayList;
import java.util.List;

public class QaPostActivity extends BaseActivity {

    @Override
    protected int getCurrentNavId() {
        return 0;
    }
    private EditText etTitle, etContent;
    private LinearLayout layoutPreview;
    private Button btnAddImage, btnPost;
    private QaApi api;
    private List<String> uploadedUrls = new ArrayList<>();
    private List<Uri> selectedUris = new ArrayList<>();

    private final ActivityResultLauncher<PickVisualMediaRequest> photoPicker = registerForActivityResult(
            new ActivityResultContracts.PickMultipleVisualMedia(5), uris -> {
                for (Uri uri : uris) {
                    if (selectedUris.size() >= 5) break;
                    selectedUris.add(uri);
                    addPreviewImage(uri);
                }
            });

    private final ActivityResultLauncher<Intent> galleryPicker = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() != RESULT_OK || result.getData() == null) return;
                List<Uri> uris = result.getData().getParcelableArrayListExtra("uris");
                if (uris != null) {
                    for (Uri uri : uris) {
                        if (selectedUris.size() >= 5) break;
                        selectedUris.add(uri);
                        addPreviewImage(uri);
                    }
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
            if (selectedUris.size() >= 5) {
                Toast.makeText(this, getString(R.string.qa_toast_max_images), Toast.LENGTH_SHORT).show();
                return;
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                photoPicker.launch(new PickVisualMediaRequest.Builder()
                        .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE)
                        .build());
            } else {
                Intent intent = new Intent(this, GalleryPickerActivity.class);
                intent.putExtra(GalleryPickerActivity.EXTRA_MAX_COUNT,
                        5 - selectedUris.size());
                galleryPicker.launch(intent);
            }
        });

        btnPost.setOnClickListener(v -> {
            String title = etTitle.getText().toString().trim();
            String content = etContent.getText().toString().trim();
            if (title.isEmpty()) {
                Toast.makeText(this, getString(R.string.qa_toast_enter_title), Toast.LENGTH_SHORT).show();
                return;
            }
            if (content.isEmpty()) {
                Toast.makeText(this, getString(R.string.qa_toast_enter_question_content), Toast.LENGTH_SHORT).show();
                return;
            }
            if (SensitiveWordFilter.contains(title) || SensitiveWordFilter.contains(content)) {
                Toast.makeText(this, getString(R.string.qa_toast_sensitive), Toast.LENGTH_SHORT).show();
                return;
            }
            btnPost.setEnabled(false);
            btnPost.setText(getString(R.string.qa_posting));
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
                Toast.makeText(QaPostActivity.this, R.string.qa_toast_post_success, Toast.LENGTH_SHORT).show();
                setResult(RESULT_OK);
                finish();
            }
            @Override public void onError(String error) {
                btnPost.setEnabled(true);
                btnPost.setText(getString(R.string.qa_post));
                Toast.makeText(QaPostActivity.this, error, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void addPreviewImage(Uri uri) {
        // 外层容器，用于叠加删除角标
        FrameLayout container = new FrameLayout(this);
        int size = getResources().getDisplayMetrics().widthPixels / 4 - 16;
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(size, size);
        lp.setMargins(0, 0, 8, 0);
        container.setLayoutParams(lp);

        ImageView iv = new ImageView(this);
        iv.setLayoutParams(new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
        Glide.with(this).load(uri).into(iv);

        // 点击 → 放大预览
        iv.setOnClickListener(v -> showImagePreview(uri));

        // 长按 → 删除
        iv.setOnLongClickListener(v -> {
            selectedUris.remove(uri);
            layoutPreview.removeView(container);
            Toast.makeText(QaPostActivity.this, R.string.qa_toast_deleted, Toast.LENGTH_SHORT).show();
            return true;
        });

        container.addView(iv);
        layoutPreview.addView(container);
    }

    private void showImagePreview(Uri uri) {
        ImageView iv = new ImageView(this);
        iv.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        iv.setAdjustViewBounds(true);
        Glide.with(this).load(uri).into(iv);
        new AlertDialog.Builder(this)
                .setView(iv)
                .setPositiveButton(R.string.qa_btn_close, null)
                .show();
    }
}
