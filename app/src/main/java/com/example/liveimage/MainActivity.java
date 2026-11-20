package com.example.liveimage;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.net.Uri;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.segmentation.Segmentation;
import com.google.mlkit.vision.segmentation.Segmenter;
import com.google.mlkit.vision.segmentation.selfie.SelfieSegmenterOptions;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.FloatBuffer;

public class MainActivity extends Activity {
    private static final int PICK_IMAGE = 41;
    private FrameLayout preview;
    private ImageView image;
    private TextView hint;
    private SeekBar size;
    private File imageFile;
    private float normX = 0.08f, normY = 0.08f, downX, downY, startX, startY;
    private int imageSizeDp = 120;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_main);
        preview = findViewById(R.id.preview);
        image = findViewById(R.id.image);
        hint = findViewById(R.id.hint);
        size = findViewById(R.id.size);
        findViewById(R.id.choose).setOnClickListener(v -> {
            Intent i = new Intent(Intent.ACTION_GET_CONTENT);
            i.setType("image/*");
            startActivityForResult(Intent.createChooser(i, "Selecionar imagem"), PICK_IMAGE);
        });
        findViewById(R.id.remove).setOnClickListener(v -> removeBackground());
        findViewById(R.id.start).setOnClickListener(v -> startOverlay());
        findViewById(R.id.stop).setOnClickListener(v -> stopService(new Intent(this, OverlayService.class)));
        size.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar bar, int progress, boolean user) {
                imageSizeDp = 48 + progress;
                updatePreviewSize();
            }
            public void onStartTrackingTouch(SeekBar bar) {}
            public void onStopTrackingTouch(SeekBar bar) {}
        });
        image.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                downX = event.getRawX(); downY = event.getRawY();
                startX = image.getX(); startY = image.getY(); return true;
            }
            if (event.getAction() == MotionEvent.ACTION_MOVE) {
                float x = startX + event.getRawX() - downX;
                float y = startY + event.getRawY() - downY;
                image.setX(Math.max(0, Math.min(x, preview.getWidth() - image.getWidth())));
                image.setY(Math.max(0, Math.min(y, preview.getHeight() - image.getHeight())));
                updateNormalizedPosition(); return true;
            }
            return true;
        });
    }

    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request == PICK_IMAGE && result == RESULT_OK && data != null && data.getData() != null) {
            try {
                Bitmap bitmap;
                try (InputStream in = getContentResolver().openInputStream(data.getData())) {
                    bitmap = BitmapFactory.decodeStream(in);
                }
                if (bitmap == null) throw new Exception("Invalid image");
                imageFile = new File(getFilesDir(), "overlay.png");
                saveBitmap(bitmap, imageFile);
                image.setImageBitmap(bitmap);
                image.setVisibility(View.VISIBLE); hint.setVisibility(View.GONE);
                image.post(() -> { image.setX(preview.getWidth() * normX); image.setY(preview.getHeight() * normY); updatePreviewSize(); });
            } catch (Exception e) { toast("Não consegui abrir essa imagem."); }
        }
    }

    private void saveBitmap(Bitmap bitmap, File file) throws Exception {
        try (FileOutputStream out = new FileOutputStream(file)) {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
        }
    }
    private void updatePreviewSize() {
        int px = (int)(imageSizeDp * getResources().getDisplayMetrics().density);
        image.setLayoutParams(new FrameLayout.LayoutParams(px, px));
    }
    private void updateNormalizedPosition() {
        if (preview.getWidth() > 0 && preview.getHeight() > 0) {
            normX = image.getX() / (float)preview.getWidth();
            normY = image.getY() / (float)preview.getHeight();
        }
    }

    private void removeBackground() {
        if (imageFile == null) { toast("Escolha uma imagem primeiro."); return; }
        Bitmap bitmap = BitmapFactory.decodeFile(imageFile.getAbsolutePath());
        SelfieSegmenterOptions options = new SelfieSegmenterOptions.Builder()
            .setDetectorMode(SelfieSegmenterOptions.SINGLE_IMAGE_MODE).build();
        Segmenter segmenter = Segmentation.getClient(options);
        segmenter.process(InputImage.fromBitmap(bitmap, 0)).addOnSuccessListener(mask -> {
            try {
                FloatBuffer buffer = mask.getBuffer();
                int mw = mask.getWidth(), mh = mask.getHeight();
                int w = bitmap.getWidth(), h = bitmap.getHeight();
                int[] pixels = new int[w * h];
                bitmap.getPixels(pixels, 0, w, 0, 0, w, h);
                for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
                    float confidence = buffer.get(Math.min(mh-1, y*mh/h)*mw + Math.min(mw-1, x*mw/w));
                    int index = y*w+x, alpha = Math.max(0, Math.min(255, (int)(confidence*255)));
                    pixels[index] = (alpha << 24) | (pixels[index] & 0x00ffffff);
                }
                Bitmap output = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
                output.setPixels(pixels, 0, w, 0, 0, w, h);
                saveBitmap(output, imageFile);
                image.setImageBitmap(output);
                toast("Fundo removido. Melhor resultado com pessoas.");
            } catch (Exception e) { toast("Erro ao salvar imagem."); }
            finally { segmenter.close(); }
        }).addOnFailureListener(e -> { segmenter.close(); toast("Não foi possível remover o fundo."); });
    }

    private void startOverlay() {
        if (imageFile == null) { toast("Escolha uma imagem primeiro."); return; }
        if (!android.provider.Settings.canDrawOverlays(this)) {
            Intent settings = new Intent(android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:" + getPackageName()));
            startActivity(settings);
            toast("Permita aparecer sobre outros apps e toque novamente.");
            return;
        }
        updateNormalizedPosition();
        Intent service = new Intent(this, OverlayService.class);
        service.putExtra("path", imageFile.getAbsolutePath());
        service.putExtra("x", normX); service.putExtra("y", normY); service.putExtra("size", imageSizeDp);
        startService(service);
        toast("Sobreposição ativada.");
    }
    private void toast(String message) { Toast.makeText(this, message, Toast.LENGTH_LONG).show(); }
}