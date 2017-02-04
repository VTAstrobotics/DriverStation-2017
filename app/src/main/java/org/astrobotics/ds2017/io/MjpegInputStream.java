package org.astrobotics.ds2017.io;

/**
 * Created by Skylar on 2/8/2016.
 */

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Properties;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

public class MjpegInputStream {
    private static final byte[] SOI_MARKER = {(byte) 0xFF, (byte) 0xD8};
    private DataInputStream mStream;
    private byte[] mImageBytes = new byte[0];

    public static MjpegInputStream read(String url) {
        MjpegInputStream stream = null;
        try {
            URL urll = new URL(url);
            try {
                HttpURLConnection htuc = (HttpURLConnection) urll.openConnection();
                stream = new MjpegInputStream(htuc.getInputStream());
            } catch(IOException e) {
                e.printStackTrace();
            }
        } catch(MalformedURLException e) {
            e.printStackTrace();
        }
        return stream;
    }

    public MjpegInputStream(InputStream in) {
        mStream = new DataInputStream(in);
    }

    public Bitmap readMjpegFrame() throws IOException {
        PipedInputStream pInput = new PipedInputStream();
        PipedOutputStream pOutput = new PipedOutputStream(pInput);

        // Read until JPEG start sequence
        int index = 0;
        byte[] buf = new byte[1];
        while(index != SOI_MARKER.length) {
            mStream.read(buf);
            if(buf[0] == SOI_MARKER[index]) {
                index++;
            } else {
                index = 0;
            }
            pOutput.write(buf);
        }

        pOutput.close();
        Properties props = new Properties();
        props.load(pInput);
        int length = Integer.parseInt(props.getProperty("Content-Length"));

        if(mImageBytes.length < length) {
            mImageBytes = new byte[length];
        }
        System.arraycopy(SOI_MARKER, 0, mImageBytes, 0, SOI_MARKER.length);
        mStream.readFully(mImageBytes, SOI_MARKER.length, length - SOI_MARKER.length);

        return BitmapFactory.decodeStream(new ByteArrayInputStream(mImageBytes));
    }
}