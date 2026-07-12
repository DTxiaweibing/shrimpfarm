package com.shrimpfarm.app.qa;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.shrimpfarm.app.R;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class GalleryPickerActivity extends AppCompatActivity {

    public static final String EXTRA_MAX_COUNT = "max_count";
    private static final int PERMISSION_REQUEST_CODE = 1001;

    private RecyclerView recyclerView;
    private TextView tvSelectedCount;
    private View btnDone;
    private ImageAdapter adapter;
    private final Set<Uri> selectedUris = new LinkedHashSet<>();
    private int maxCount = 5;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_gallery_picker);

        maxCount = getIntent().getIntExtra(EXTRA_MAX_COUNT, 5);

        recyclerView = findViewById(R.id.recycler_view);
        tvSelectedCount = findViewById(R.id.tv_selected_count);
        btnDone = findViewById(R.id.btn_done);

        int screenWidthDp = getResources().getConfiguration().screenWidthDp;
        int spanCount = screenWidthDp >= 600 ? 4 : 3;
        recyclerView.setLayoutManager(new GridLayoutManager(this, spanCount));

        float density = getResources().getDisplayMetrics().density;
        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        int itemSize = (int)((screenWidth - 4 * density) / spanCount - 4 * density + 0.5f);

        adapter = new ImageAdapter(itemSize);
        recyclerView.setAdapter(adapter);

        btnDone.setOnClickListener(v -> {
            Intent result = new Intent();
            result.putExtra("uris", new ArrayList<>(selectedUris));
            setResult(RESULT_OK, result);
            finish();
        });

        findViewById(R.id.btn_back).setOnClickListener(v -> finish());

        if (hasPermission()) {
            loadImages();
        } else {
            requestPermission();
        }
    }

    private boolean hasPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES)
                    == PackageManager.PERMISSION_GRANTED;
        } else {
            return ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                    == PackageManager.PERMISSION_GRANTED;
        }
    }

    private void requestPermission() {
        String perm = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                ? Manifest.permission.READ_MEDIA_IMAGES
                : Manifest.permission.READ_EXTERNAL_STORAGE;
        ActivityCompat.requestPermissions(this, new String[]{perm}, PERMISSION_REQUEST_CODE);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                loadImages();
            } else {
                Toast.makeText(this, "需要存储权限才能读取图片", Toast.LENGTH_SHORT).show();
                finish();
            }
        }
    }

    private void loadImages() {
        executor.execute(() -> {
            List<ImageEntry> entries = new ArrayList<>();
            Uri queryUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
            String sortOrder = MediaStore.Images.Media.DATE_ADDED + " DESC";

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                String[] projection = {
                        MediaStore.Images.Media._ID,
                        MediaStore.Images.Media.WIDTH,
                        MediaStore.Images.Media.HEIGHT,
                        MediaStore.Images.Media.DATE_ADDED
                };
                String selection = MediaStore.Images.Media.WIDTH + " >= 200 AND "
                        + MediaStore.Images.Media.HEIGHT + " >= 200";

                try (Cursor cursor = getContentResolver().query(queryUri, projection, selection, null, sortOrder)) {
                    if (cursor != null) {
                        int idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID);
                        while (cursor.moveToNext()) {
                            long id = cursor.getLong(idCol);
                            Uri imageUri = Uri.withAppendedPath(
                                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                                    String.valueOf(id));
                            entries.add(new ImageEntry(id, imageUri));
                        }
                    }
                } catch (Exception e) {
                    mainHandler.post(() -> {
                        Toast.makeText(this, "读取图片失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        finish();
                    });
                    return;
                }
            } else {
                String[] projection = {
                        MediaStore.Images.Media._ID,
                        MediaStore.Images.Media.DATE_ADDED
                };

                try (Cursor cursor = getContentResolver().query(queryUri, projection, null, null, sortOrder)) {
                    if (cursor != null) {
                        int idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID);
                        while (cursor.moveToNext()) {
                            long id = cursor.getLong(idCol);
                            Uri imageUri = Uri.withAppendedPath(
                                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                                    String.valueOf(id));
                            if (isLargeEnough(imageUri)) {
                                entries.add(new ImageEntry(id, imageUri));
                            }
                        }
                    }
                } catch (Exception e) {
                    mainHandler.post(() -> {
                        Toast.makeText(this, "读取图片失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        finish();
                    });
                    return;
                }
            }

            mainHandler.post(() -> {
                adapter.setImages(entries);
                if (entries.isEmpty()) {
                    Toast.makeText(this, "未找到图片", Toast.LENGTH_SHORT).show();
                }
            });
        });
    }

    private boolean isLargeEnough(Uri uri) {
        try {
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inJustDecodeBounds = true;
            BitmapFactory.decodeStream(getContentResolver().openInputStream(uri), null, opts);
            return opts.outWidth >= 200 && opts.outHeight >= 200;
        } catch (Exception e) {
            return true;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdown();
    }

    private void updateSelectionUI() {
        int count = selectedUris.size();
        tvSelectedCount.setText(count > 0 ? "已选 " + count + "/" + maxCount : "选择图片");
        btnDone.setEnabled(count > 0);
        btnDone.setAlpha(count > 0 ? 1.0f : 0.5f);
    }

    private class ImageAdapter extends RecyclerView.Adapter<ImageAdapter.ViewHolder> {

        private List<ImageEntry> images = new ArrayList<>();
        private final int itemSize;

        ImageAdapter(int itemSize) {
            this.itemSize = itemSize;
        }

        void setImages(List<ImageEntry> images) {
            this.images = images;
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_gallery_image, parent, false);
            return new ViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            ImageEntry entry = images.get(position);

            holder.ivThumb.getLayoutParams().height = itemSize;

            Glide.with(GalleryPickerActivity.this)
                    .load(entry.uri)
                    .override(itemSize, itemSize)
                    .centerCrop()
                    .into(holder.ivThumb);

            boolean isSelected = selectedUris.contains(entry.uri);
            holder.checkBox.setChecked(isSelected);
            holder.overlay.setVisibility(isSelected ? View.VISIBLE : View.GONE);

            holder.itemView.setOnClickListener(v -> {
                boolean selected = selectedUris.contains(entry.uri);
                if (selected) {
                    selectedUris.remove(entry.uri);
                    holder.checkBox.setChecked(false);
                    holder.overlay.setVisibility(View.GONE);
                } else {
                    if (selectedUris.size() >= maxCount) {
                        Toast.makeText(GalleryPickerActivity.this,
                                "最多选择" + maxCount + "张", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    selectedUris.add(entry.uri);
                    holder.checkBox.setChecked(true);
                    holder.overlay.setVisibility(View.VISIBLE);
                }
                updateSelectionUI();
            });
        }

        @Override
        public int getItemCount() {
            return images.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            ImageView ivThumb;
            CheckBox checkBox;
            View overlay;

            ViewHolder(View itemView) {
                super(itemView);
                ivThumb = itemView.findViewById(R.id.iv_thumb);
                checkBox = itemView.findViewById(R.id.checkbox);
                overlay = itemView.findViewById(R.id.v_selected_overlay);
            }
        }
    }

    private static class ImageEntry {
        final long id;
        final Uri uri;

        ImageEntry(long id, Uri uri) {
            this.id = id;
            this.uri = uri;
        }
    }
}
