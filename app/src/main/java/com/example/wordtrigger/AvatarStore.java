package com.example.wordtrigger;

import android.content.ContentResolver;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.net.Uri;
import android.util.Base64;
import android.widget.ImageView;

import com.google.firebase.firestore.DocumentSnapshot;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

public final class AvatarStore {
    public static final int SIZE = 256;
    private static final String FILE = "avatar.jpg";

    private AvatarStore() {}

    public static Bitmap decodeSquare(ContentResolver cr, Uri uri) throws IOException {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        try (InputStream in = cr.openInputStream(uri)) {
            BitmapFactory.decodeStream(in, null, bounds);
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw new IOException("not an image");

        int sample = 1;
        int shortSide = Math.min(bounds.outWidth, bounds.outHeight);
        while (shortSide / (sample * 2) >= SIZE) sample *= 2;

        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inSampleSize = sample;
        Bitmap bmp;
        try (InputStream in = cr.openInputStream(uri)) {
            bmp = BitmapFactory.decodeStream(in, null, opts);
        }
        if (bmp == null) throw new IOException("decode failed");

        int rotation = 0;
        try (InputStream in = cr.openInputStream(uri)) {
            int o = new ExifInterface(in).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
            if (o == ExifInterface.ORIENTATION_ROTATE_90) rotation = 90;
            else if (o == ExifInterface.ORIENTATION_ROTATE_180) rotation = 180;
            else if (o == ExifInterface.ORIENTATION_ROTATE_270) rotation = 270;
        } catch (Exception ignored) { }
        if (rotation != 0) {
            Matrix m = new Matrix();
            m.postRotate(rotation);
            bmp = Bitmap.createBitmap(bmp, 0, 0, bmp.getWidth(), bmp.getHeight(), m, true);
        }

        int side = Math.min(bmp.getWidth(), bmp.getHeight());
        Bitmap square = Bitmap.createBitmap(bmp, (bmp.getWidth() - side) / 2, (bmp.getHeight() - side) / 2, side, side);
        return Bitmap.createScaledBitmap(square, SIZE, SIZE, true);
    }

    public static String toBase64(Bitmap bmp) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        bmp.compress(Bitmap.CompressFormat.JPEG, 82, out);
        return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP);
    }

    public static Bitmap fromBase64(String data) {
        try {
            byte[] bytes = Base64.decode(data, Base64.DEFAULT);
            return BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
        } catch (Exception e) {
            return null;
        }
    }


    private static File file(Context c) {
        return new File(c.getFilesDir(), FILE);
    }

    public static void saveLocal(Context c, Bitmap bmp) {
        try (FileOutputStream out = new FileOutputStream(file(c))) {
            bmp.compress(Bitmap.CompressFormat.JPEG, 82, out);
        } catch (IOException ignored) { }
    }

    public static Bitmap loadLocal(Context c) {
        File f = file(c);
        return f.exists() ? BitmapFactory.decodeFile(f.getAbsolutePath()) : null;
    }

    public static void deleteLocal(Context c) {

        file(c).delete();
    }

    public static void show(ImageView view, Bitmap bmp) {
        if (bmp != null) view.setImageBitmap(bmp);
        else view.setImageResource(R.drawable.ic_user_circle);
    }

    public static void showLocal(Context c, ImageView view) {
        show(view, loadLocal(c));
    }

    public static void syncFromDoc(Context c, DocumentSnapshot doc, ImageView view) {
        if (doc == null || !doc.exists()) return;
        String data = doc.getString("avatar");
        if (data == null || data.isEmpty()) {
            deleteLocal(c);
            show(view, null);
            return;
        }
        Bitmap bmp = fromBase64(data);
        if (bmp != null) {
            saveLocal(c, bmp);
            show(view, bmp);
        }
    }
}
