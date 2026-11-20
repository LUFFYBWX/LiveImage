package com.example.liveimage;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.net.Uri;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

public class MainActivity extends Activity {
    private static final int PICK_IMAGE = 41;
    private FrameLayout preview;
    private ImageView image;
    private TextView hint;
    private SeekBar size, strength;
    private File imageFile, sourceFile;
    private float normX = 0.08f, normY = 0.08f, downX, downY, startX, startY;
    private int imageSizeDp = 120;
    private int selectedColor;
    private boolean hasSelectedColor = false, selectingColor = false;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_main);
        preview = findViewById(R.id.preview);
        image = findViewById(R.id.image);
        hint = findViewById(R.id.hint);
        size = findViewById(R.id.size);
        strength = findViewById(R.id.strength);

        sourceFile = new File(getFilesDir(), "overlay_original.png");
        imageFile = new File(getFilesDir(), "overlay.png");

        findViewById(R.id.choose).setOnClickListener(v -> {
            Intent i = new Intent(Intent.ACTION_GET_CONTENT);
            i.setType("image/*");
            startActivityForResult(Intent.createChooser(i, "Selecionar imagem"), PICK_IMAGE);
        });
        findViewById(R.id.remove).setOnClickListener(v -> {
            if (image.getDrawable() == null) { toast("Escolha uma imagem primeiro."); return; }
            selectingColor = true;
            toast("Toque na cor da imagem que deseja remover.");
        });
        findViewById(R.id.rotate).setOnClickListener(v -> rotateImage());
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
        strength.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar bar, int progress, boolean user) {
                if (user && hasSelectedColor) applyChromaKey();
            }
            public void onStartTrackingTouch(SeekBar bar) {}
            public void onStopTrackingTouch(SeekBar bar) {}
        });

        image.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                downX = event.getRawX(); downY = event.getRawY();
                startX = image.getX(); startY = image.getY();
                return true;
            }
            if (event.getAction() == MotionEvent.ACTION_UP) {
                float moved = Math.abs(event.getRawX() - downX) + Math.abs(event.getRawY() - downY);
                if (selectingColor && moved < 18f) {
                    sampleColor(event.getX(), event.getY());
                    return true;
                }
                return true;
            }
            if (event.getAction() == MotionEvent.ACTION_MOVE && !selectingColor) {
                float x = startX + event.getRawX() - downX;
                float y = startY + event.getRawY() - downY;
                image.setX(Math.max(0, Math.min(x, preview.getWidth() - image.getWidth())));
                image.setY(Math.max(0, Math.min(y, preview.getHeight() - image.getHeight())));
                updateNormalizedPosition();
                return true;
            }
            return true;
        });
    }

    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request == PICK_IMAGE && result == RESULT_OK && data != null && data.getData() != null) {
            try (InputStream in = getContentResolver().openInputStream(data.getData())) {
                Bitmap bitmap = BitmapFactory.decodeStream(in);
                if (bitmap == null) throw new Exception("Invalid image");
                saveBitmap(bitmap, sourceFile);
                saveBitmap(bitmap, imageFile);
                hasSelectedColor = false;
                selectingColor = false;
                image.setImageBitmap(bitmap);
                image.setVisibility(View.VISIBLE);
                hint.setVisibility(View.GONE);
                image.post(() -> {
                    image.setX(preview.getWidth() * normX);
                    image.setY(preview.getHeight() * normY);
                    updatePreviewSize();
                });
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
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(px, px);
        image.setLayoutParams(params);
    }

    private void updateNormalizedPosition() {
        if (preview.getWidth() > 0 && preview.getHeight() > 0) {
            normX = image.getX() / (float)preview.getWidth();
            normY = image.getY() / (float)preview.getHeight();
        }
    }

    private void sampleColor(float touchX, float touchY) {
        Bitmap bitmap = BitmapFactory.decodeFile(sourceFile.getAbsolutePath());
        if (bitmap == null) { toast("Não foi possível ler a imagem."); return; }
        float scale = Math.min(image.getWidth() / (float)bitmap.getWidth(),
                               image.getHeight() / (float)bitmap.getHeight());
        float shownW = bitmap.getWidth() * scale;
        float shownH = bitmap.getHeight() * scale;
        float left = (image.getWidth() - shownW) / 2f;
        float top = (image.getHeight() - shownH) / 2f;
        int x = (int)((touchX - left) / scale);
        int y = (int)((touchY - top) / scale);
        if (x < 0 || y < 0 || x >= bitmap.getWidth() || y >= bitmap.getHeight()) {
            toast("Toque dentro da imagem.");
            return;
        }
        selectedColor = bitmap.getPixel(x, y);
        hasSelectedColor = true;
        selectingColor = false;
        applyChromaKey();
        toast("Cor selecionada. Ajuste a intensidade para refinar o recorte.");
    }

    private void applyChromaKey() {
        if (!sourceFile.exists() || !hasSelectedColor) return;
        try {
            Bitmap bitmap = BitmapFactory.decodeFile(sourceFile.getAbsolutePath());
            int w = bitmap.getWidth(), h = bitmap.getHeight();
            int[] pixels = new int[w * h];
            bitmap.getPixels(pixels, 0, w, 0, 0, w, h);
            int kr = (selectedColor >> 16) & 255;
            int kg = (selectedColor >> 8) & 255;
            int kb = selectedColor & 255;
            float threshold = 12f + strength.getProgress() * 1.15f;
            float feather = 24f;
            for (int i = 0; i < pixels.length; i++) {
                int c = pixels[i];
                int r = (c >> 16) & 255, g = (c >> 8) & 255, b = c & 255;
                float distance = (float)Math.sqrt((r-kr)*(r-kr) + (g-kg)*(g-kg) + (b-kb)*(b-kb));
                float alphaFactor = Math.max(0f, Math.min(1f, (distance - threshold) / feather));
                int alpha = Math.round(((c >>> 24) & 255) * alphaFactor);
                pixels[i] = (alpha << 24) | (c & 0x00ffffff);
            }
            Bitmap output = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            output.setPixels(pixels, 0, w, 0, 0, w, h);
            saveBitmap(output, imageFile);
            image.setImageBitmap(output);
        } catch (Exception e) {
            toast("Erro ao remover a cor.");
        }
    }

    private void rotateImage() {
        if (!sourceFile.exists()) { toast("Escolha uma imagem primeiro."); return; }
        try {
            Bitmap bitmap = BitmapFactory.decodeFile(sourceFile.getAbsolutePath());
            Matrix matrix = new Matrix();
            matrix.postRotate(90f);
            Bitmap rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
            saveBitmap(rotated, sourceFile);
            hasSelectedColor = false;
            Bitmap output = BitmapFactory.decodeFile(sourceFile.getAbsolutePath());
            saveBitmap(output, imageFile);
            image.setImageBitmap(output);
            toast("Imagem girada 90°.");
        } catch (Exception e) { toast("Não foi possível girar a imagem."); }
    }

    private void startOverlay() {
        if (!imageFile.exists()) { toast("Escolha uma imagem primeiro."); return; }
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
        service.putExtra("x", normX);
        service.putExtra("y", normY);
        service.putExtra("size", imageSizeDp);
        startService(service);
        toast("Sobreposição ativada.");
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }
}