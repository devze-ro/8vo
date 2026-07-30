package ro.devze.octavo;

import android.content.Context;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

final class OctavoFixture {
    static final long BYTE_COUNT = 219603L;
    static final String SHA256 =
        "f6da909cf3d2e701633d9ca37356c334055c2443920d9454c985934ff78a466c";
    private static final String ASSET_PATH = "port5/octavo_port5.epub";
    private static final String DIRECTORY_NAME = "port5/fixture";
    private static final String FILE_NAME = "octavo_port5.epub";

    private OctavoFixture() {
    }

    static String install(Context context) {
        File directory = new File(context.getFilesDir(), DIRECTORY_NAME);
        if (!directory.isDirectory() && !directory.mkdirs()) {
            throw new IllegalStateException("Unable to create the Port 5 fixture directory");
        }

        File target = new File(directory, FILE_NAME);
        File temporary = new File(directory, FILE_NAME + ".tmp");
        try (InputStream input = context.getAssets().open(ASSET_PATH);
             FileOutputStream output = new FileOutputStream(temporary, false)) {
            byte[] buffer = new byte[8192];
            for (int count = input.read(buffer); count >= 0; count = input.read(buffer)) {
                if (count > 0) {
                    output.write(buffer, 0, count);
                }
            }
            output.getFD().sync();
        } catch (IOException exception) {
            temporary.delete();
            throw new IllegalStateException("Unable to stage the Port 5 EPUB fixture",
                                            exception);
        }

        if ((target.exists() && !target.delete()) || !temporary.renameTo(target)) {
            temporary.delete();
            throw new IllegalStateException("Unable to publish the Port 5 EPUB fixture");
        }
        return target.getAbsolutePath();
    }
}
