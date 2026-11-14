package com.aqaqeer.app;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;

/** Java-only because this test-APK provider runs separately from the target app's class loader. */
public final class M3ImportFixtureProvider extends ContentProvider {
    @Override public boolean onCreate() { return true; }
    @Override public String getType(Uri uri) { return "application/octet-stream"; }
    @Override public Cursor query(Uri uri, String[] projection, String selection, String[] args, String sort) {
        MatrixCursor cursor = new MatrixCursor(new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE});
        cursor.addRow(new Object[]{"public-test.aqz", 1L}); // Deliberately unreliable metadata.
        return cursor;
    }
    @Override public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        if (!"r".equals(mode)) throw new FileNotFoundException();
        final String path = uri.getPath();
        if (!"/valid".equals(path) && !"/invalid".equals(path) && !"/oversize".equals(path)) throw new FileNotFoundException();
        try {
            final ParcelFileDescriptor[] pipe = ParcelFileDescriptor.createPipe();
            new Thread(() -> {
                try (OutputStream out = new ParcelFileDescriptor.AutoCloseOutputStream(pipe[1])) {
                    if ("/valid".equals(path)) {
                        try (InputStream in = getContext().getAssets().open("m3-import.aqz")) {
                            byte[] buffer = new byte[8192]; int size;
                            while ((size = in.read(buffer)) != -1) out.write(buffer, 0, size);
                        }
                    } else if ("/oversize".equals(path)) {
                        byte[] buffer = new byte[8192]; Arrays.fill(buffer, (byte) ' ');
                        for (int i = 0; i < 1281; i++) out.write(buffer); // Actual stream exceeds 10 MiB.
                    } else out.write(new byte[]{'n', 'o', 't', '-', 'j', 's', 'o', 'n'});
                } catch (IOException expectedWhenReaderRejectsOrCancels) { /* No logs or user files. */ }
            }, "m3-public-fixture").start();
            return pipe[0];
        } catch (IOException error) { throw new FileNotFoundException("Fixture unavailable"); }
    }
    @Override public Uri insert(Uri uri, ContentValues values) { throw new UnsupportedOperationException(); }
    @Override public int delete(Uri uri, String selection, String[] args) { throw new UnsupportedOperationException(); }
    @Override public int update(Uri uri, ContentValues values, String selection, String[] args) { throw new UnsupportedOperationException(); }
}
