package local.quicklens;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import java.io.File;
import java.io.FileNotFoundException;

/** Unexported; Lens can only read the individual URI granted by ACTION_SEND. */
public class ImageProvider extends ContentProvider {
    static final String AUTHORITY = "local.quicklens.image";
    static Uri uri(File file) { return new Uri.Builder().scheme("content").authority(AUTHORITY).appendPath(file.getName()).build(); }
    @Override public boolean onCreate() {
        SnapshotStore.cleanup(getContext().getCacheDir(), System.currentTimeMillis());
        return true;
    }
    private File file(Uri uri) throws FileNotFoundException {
        if (!"content".equals(uri.getScheme()) || !AUTHORITY.equals(uri.getAuthority())
            || uri.getQuery() != null || uri.getFragment() != null) throw new FileNotFoundException("Unknown image");
        File file = SnapshotStore.resolve(getContext().getCacheDir(), uri.getPath());
        if (file == null || !file.isFile() || SnapshotStore.expired(file, System.currentTimeMillis())) {
            throw new FileNotFoundException("Image unavailable");
        }
        return file;
    }
    @Override public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        if (!"r".equals(mode)) throw new FileNotFoundException("Read only");
        return ParcelFileDescriptor.open(file(uri), ParcelFileDescriptor.MODE_READ_ONLY);
    }
    @Override public String getType(Uri uri) {
        try { file(uri); return "image/jpeg"; } catch (FileNotFoundException e) { return null; }
    }
    @Override public Cursor query(Uri uri, String[] projection, String selection, String[] args, String sort) {
        final File file;
        try { file = file(uri); } catch (FileNotFoundException e) { return null; }
        String[] columns = projection == null ? new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE} : projection;
        MatrixCursor result = new MatrixCursor(columns);
        Object[] row = new Object[columns.length];
        for (int i = 0; i < columns.length; i++) {
            if (OpenableColumns.DISPLAY_NAME.equals(columns[i])) row[i] = file.getName();
            if (OpenableColumns.SIZE.equals(columns[i])) row[i] = file.length();
        }
        result.addRow(row);
        return result;
    }
    @Override public Uri insert(Uri u, ContentValues v) { throw new UnsupportedOperationException(); }
    @Override public int update(Uri u, ContentValues v, String s, String[] a) { throw new UnsupportedOperationException(); }
    @Override public int delete(Uri u, String s, String[] a) { throw new UnsupportedOperationException(); }
}
