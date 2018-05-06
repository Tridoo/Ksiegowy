package tridoo.ksiegowy;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.Image;
import android.util.SparseArray;

import com.google.android.gms.vision.Frame;
import com.google.android.gms.vision.text.TextBlock;
import com.google.android.gms.vision.text.TextRecognizer;

import java.nio.ByteBuffer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class ImageProcessor implements Runnable {

    private Image image;
    private TextRecognizer textRecognizer;
    private ScreenController screenController;


    public ImageProcessor(Image image, TextRecognizer textRecognizer, ScreenController screenController) {
        this.image = image;
        this.textRecognizer = textRecognizer;
        this.screenController = screenController;
    }

    @Override
    public void run() {
        ByteBuffer byteBuffer = image.getPlanes()[0].getBuffer();
        byte[] bytes = new byte[byteBuffer.remaining()];
        byteBuffer.get(bytes);

        Bitmap bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);

        Frame frame = new Frame.Builder().setBitmap(cropBitmap(bmp)).build();
        SparseArray<TextBlock> textBlocks = textRecognizer.detect(frame);
        String text = "";
        for (int i = 0; i < textBlocks.size(); i++) {
            if (textBlocks.get(i) == null) continue;
            text += textBlocks.get(i).getValue();
        }

        final Pattern pattern = Pattern.compile(Config.REGEXP);
        Matcher matcher = pattern.matcher(text);
        if (matcher.find()) {
            final String finalText = matcher.group(0);
            screenController.geteGross().post(new Runnable() {
                @Override
                public void run() {
                    screenController.geteGross().setText(finalText);
                }
            });
        }
    }

    private Bitmap cropBitmap(Bitmap bitmap) {
        Bitmap result;
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        boolean isRoteted = false;
        int scaledWidth, scaledHeight, dx, dy;

        if (height < width) isRoteted = true;

        if (isRoteted) {
            scaledWidth = width / Config.SCALE_Y;
            scaledHeight = height / Config.SCALE_X;
        } else {
            scaledWidth = width / Config.SCALE_X;
            scaledHeight = height / Config.SCALE_Y;
        }

        dx = (int) ((width - scaledWidth) * 0.5);
        dy = (int) ((height - scaledHeight) * 0.5);//to do sprawdzic bo troche za wysoko wycina
        if (isRoteted) {
            dy = dy + scaledHeight / 4;
        } else {
            dx = dx + scaledWidth / 4;
        }

        result = Bitmap.createBitmap(bitmap, dx, dy, scaledWidth / 2, scaledHeight / 2);
        if (isRoteted)
            result = fixOrientation(result); //nie obracac przed wycieciem, OutOfMemory czeste
        return result;
    }

    private Bitmap fixOrientation(Bitmap bmp) {
        Matrix matrix = new Matrix();
        matrix.postRotate(90);
        return Bitmap.createBitmap(bmp, 0, 0, bmp.getWidth(), bmp.getHeight(), matrix, true);
    }
}