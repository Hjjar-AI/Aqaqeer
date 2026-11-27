package com.aqaqeer.app;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileInputStream;
import java.io.IOException;
import android.system.ErrnoException;
import android.system.Os;
import java.io.DataInputStream;

/** Test-APK-only documents; names are random IDs, with no access to user files. */
public final class SuiteDocumentProvider extends ContentProvider {
    @Override public boolean onCreate() { return true; }
    @Override public String getType(Uri uri) { return "application/octet-stream"; }
    @Override public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        String name = uri.getLastPathSegment();
        if (name == null || !name.matches("suite-[a-zA-Z0-9-]+")) throw new FileNotFoundException();
        File root = new File(getContext().getCacheDir(), "suite-documents");
        if (!root.isDirectory() && !root.mkdirs()) throw new FileNotFoundException();
        File document = new File(root, name);
        if (mode.equals("r") && "interrupt".equals(uri.getQueryParameter("read"))) {
            return interruptedRead(document);
        }
        return ParcelFileDescriptor.open(document, ParcelFileDescriptor.parseMode(mode));
    }

    private ParcelFileDescriptor interruptedRead(File document) throws FileNotFoundException {
        if (!document.isFile()) throw new FileNotFoundException();
        final ParcelFileDescriptor[] pipe;
        try {
            pipe = ParcelFileDescriptor.createReliablePipe();
        } catch (IOException error) {
            throw new FileNotFoundException(error.getMessage());
        }
        new Thread(() -> {
            try (DataInputStream input = new DataInputStream(new FileInputStream(document))) {
                byte[] prefix = new byte[48];
                input.readFully(prefix);
                int written = Os.write(pipe[1].getFileDescriptor(), prefix, 0, prefix.length);
                if (written != prefix.length) throw new IOException("Incomplete pipe write");
                pipe[1].closeWithError("Suite CSV read interrupted after 48 bytes");
            } catch (IOException | ErrnoException error) {
                try {
                    pipe[1].closeWithError(error.getMessage());
                } catch (IOException ignored) {
                    // The consumer may already have closed its descriptor.
                }
            }
        }, "suite-csv-provider").start();
        return pipe[0];
    }
    @Override public int delete(Uri uri, String selection, String[] args) {
        String name = uri.getLastPathSegment();
        if (name == null || !name.matches("suite-[a-zA-Z0-9-]+")) throw new IllegalArgumentException();
        return new File(new File(getContext().getCacheDir(), "suite-documents"), name).delete() ? 1 : 0;
    }
    @Override public Cursor query(Uri uri, String[] projection, String selection, String[] args, String sort) { return null; }
    @Override public Uri insert(Uri uri, ContentValues values) { throw new UnsupportedOperationException(); }
    @Override public int update(Uri uri, ContentValues values, String selection, String[] args) { throw new UnsupportedOperationException(); }
}
