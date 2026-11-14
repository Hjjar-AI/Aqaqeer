package com.aqaqeer.app;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import java.io.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** Only synthetic encrypted fixtures in the test APK; never opens application/user paths. */
public final class RestoreCrashDocumentProvider extends ContentProvider {
    private static final class Gate {
        final CountDownLatch release = new CountDownLatch(1);
        volatile boolean paused;
    }
    private final ConcurrentHashMap<String, Gate> gates = new ConcurrentHashMap<>();
    private File file(String name) {
        if (name == null || !name.matches("crash-[a-f0-9]{32}")) throw new IllegalArgumentException("Invalid fixture ID");
        File root = new File(getContext().getCacheDir(), "restore-crash-documents");
        if (!root.isDirectory() && !root.mkdirs()) throw new IllegalStateException("Fixture storage unavailable");
        return new File(root, name);
    }
    @Override public boolean onCreate() { return true; }
    @Override public String getType(Uri uri) { return "application/octet-stream"; }
    @Override public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        String name = uri.getLastPathSegment();
        File source = file(name);
        if (!"r".equals(mode) || !"true".equals(uri.getQueryParameter("pause")))
            return ParcelFileDescriptor.open(source, ParcelFileDescriptor.parseMode(mode));
        try {
            ParcelFileDescriptor[] pipe = ParcelFileDescriptor.createPipe();
            Gate gate = new Gate();
            if (gates.putIfAbsent(name, gate) != null) throw new IOException("Fixture already active");
            new Thread(() -> {
                try (InputStream input = new FileInputStream(source);
                     OutputStream output = new ParcelFileDescriptor.AutoCloseOutputStream(pipe[1])) {
                    byte[] prefix = new byte[32768];
                    int count = 0;
                    while (count < prefix.length) {
                        int next = input.read(prefix, count, prefix.length - count);
                        if (next < 0) throw new IOException("Fixture too small");
                        count += next;
                    }
                    output.write(prefix);
                    output.flush();
                    gate.paused = true;
                    if (!gate.release.await(300, TimeUnit.SECONDS)) return;
                    byte[] buffer = new byte[8192];
                    while ((count = input.read(buffer)) >= 0) output.write(buffer, 0, count);
                } catch (IOException | InterruptedException expectedAfterProcessDeath) {
                    // Broken pipe is expected when the target is killed. Never log fixture data.
                } finally { gates.remove(name, gate); }
            }, "restore-crash-fixture").start();
            return pipe[0];
        } catch (IOException e) { throw new FileNotFoundException("Fixture pipe unavailable"); }
    }
    @Override public Bundle call(String method, String arg, Bundle extras) {
        file(arg);
        if (!"paused".equals(method)) throw new IllegalArgumentException("Unsupported fixture operation");
        Gate gate = gates.get(arg);
        Bundle result = new Bundle();
        result.putBoolean("paused", gate != null && gate.paused);
        return result;
    }
    @Override public int delete(Uri uri, String selection, String[] args) {
        String name = uri.getLastPathSegment();
        Gate gate = gates.get(name);
        if (gate != null) gate.release.countDown();
        return file(name).delete() ? 1 : 0;
    }
    @Override public Cursor query(Uri uri, String[] p, String s, String[] a, String order) { return null; }
    @Override public Uri insert(Uri uri, ContentValues values) { throw new UnsupportedOperationException(); }
    @Override public int update(Uri uri, ContentValues values, String s, String[] a) { throw new UnsupportedOperationException(); }
}
