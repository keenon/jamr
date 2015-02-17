package nlp.cache;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Created by keenon on 12/29/14.
 *
 * Handles some of the irritating eccentricities of writing ints to raw streams.
 */
public class BinaryUtils {
    public static void writeInt(OutputStream os, int value) {
        byte[] buffer = new byte[4];
        buffer[0] = (byte)(value >> 24);
        buffer[1] = (byte)(value >> 16);
        buffer[2] = (byte)(value >> 8);
        buffer[3] = (byte)value;
        try {
            os.write(buffer);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static int readInt(InputStream is) {
        byte[] buffer = new byte[4];
        try {
            is.read(buffer);
        } catch (IOException e) {
            e.printStackTrace();
            return 0;
        }
        return (buffer[0] & 255) << 24 | (buffer[1] & 255) << 16 | (buffer[2] & 255) << 8 | buffer[3] & 255;
    }
}
