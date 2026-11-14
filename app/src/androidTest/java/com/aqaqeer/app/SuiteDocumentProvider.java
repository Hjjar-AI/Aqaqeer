package com.aqaqeer.app;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import java.io.File;
import java.io.FileNotFoundException;

/** Test-APK-only documents; names are random IDs, with no access to user files. */
public final class SuiteDocumentProvider extends ContentProvider {
    @Override public boolean onCreate() { return true; }
    @Override public String getType(Uri uri) { return "application/octet-stream"; }
    @Override public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        String name = uri.getLastPathSegment();
        if (name == null || !name.matches("suite-[a-zA-Z0-9-]+")) throw new FileNotFoundException();
        File root = new File(getContext().getCacheDir(), "suite-documents");
        if (!root.isDirectory() && !root.mkdirs()) throw new FileNotFoundException();
        return ParcelFileDescriptor.open(new File(root, name), ParcelFileDescriptor.parseMode(mode));
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
