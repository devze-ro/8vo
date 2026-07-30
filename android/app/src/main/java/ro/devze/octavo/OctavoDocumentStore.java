package ro.devze.octavo;

import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;

final class OctavoDocumentStore {
    private static final int SESSION_MAGIC = 0x4F355253;
    private static final int SESSION_VERSION = 1;
    private static final int SESSION_FILE_CAP = 512;
    private static final long DOCUMENT_FILE_CAP = 512L * 1024L * 1024L;
    private static final String ROOT_DIRECTORY = "port5";
    private static final String DOCUMENT_DIRECTORY = "documents";
    private static final String SESSION_FILE = "reader_session.v1";

    static final class Document {
        final File file;
        final String key;
        final long byteCount;
        final boolean imported;

        Document(File file, String key, long byteCount, boolean imported) {
            this.file = file;
            this.key = key;
            this.byteCount = byteCount;
            this.imported = imported;
        }
    }

    static final class Session {
        final Document document;
        final boolean hasPosition;
        final long spineIndex;
        final long byteOffset;

        Session(Document document,
                boolean hasPosition,
                long spineIndex,
                long byteOffset) {
            this.document = document;
            this.hasPosition = hasPosition;
            this.spineIndex = spineIndex;
            this.byteOffset = byteOffset;
        }
    }

    private final Context context;
    private final File rootDirectory;
    private final File documentDirectory;
    private final File sessionFile;
    private String lastSavedSignature;
    private long importSuccessCount;
    private long importFailureCount;
    private long sessionLoadSuccessCount;
    private long sessionLoadFailureCount;
    private long sessionSaveSuccessCount;
    private long sessionSaveFailureCount;

    OctavoDocumentStore(Context context) {
        this.context = context.getApplicationContext();
        rootDirectory = new File(this.context.getFilesDir(), ROOT_DIRECTORY);
        documentDirectory = new File(rootDirectory, DOCUMENT_DIRECTORY);
        sessionFile = new File(rootDirectory, SESSION_FILE);
        requireDirectory(rootDirectory);
        requireDirectory(documentDirectory);
    }

    Session loadSession(File fixture) {
        Document fallback = fixtureDocument(fixture);
        if (!sessionFile.isFile()) {
            return new Session(fallback, false, 0, 0);
        }
        if (sessionFile.length() <= 0
            || sessionFile.length() > SESSION_FILE_CAP) {
            sessionLoadFailureCount += 1;
            return new Session(fallback, false, 0, 0);
        }

        try (DataInputStream input = new DataInputStream(
                 new BufferedInputStream(new FileInputStream(sessionFile)))) {
            int magic = input.readInt();
            int version = input.readInt();
            boolean imported = input.readBoolean();
            String key = input.readUTF();
            long byteCount = input.readLong();
            boolean hasPosition = input.readBoolean();
            long spineIndex = input.readLong();
            long byteOffset = input.readLong();
            if (input.read() != -1
                || magic != SESSION_MAGIC
                || version != SESSION_VERSION
                || !validKey(key)
                || byteCount <= 0
                || byteCount > DOCUMENT_FILE_CAP
                || spineIndex < 0
                || spineIndex > 0xFFFFFFFFL
                || byteOffset < 0) {
                throw new IOException("Invalid Port 5 session record");
            }

            Document document;
            if (imported) {
                File file = new File(documentDirectory, key + ".epub");
                if (!file.isFile() || file.length() != byteCount) {
                    throw new IOException("Imported EPUB is unavailable");
                }
                document = new Document(file, key, byteCount, true);
            } else {
                if (!key.equals(OctavoFixture.SHA256)
                    || byteCount != OctavoFixture.BYTE_COUNT
                    || !fixture.isFile()
                    || fixture.length() != OctavoFixture.BYTE_COUNT) {
                    throw new IOException("Fixture session identity is invalid");
                }
                document = fallback;
            }
            sessionLoadSuccessCount += 1;
            lastSavedSignature =
                signature(document, hasPosition, spineIndex, byteOffset);
            return new Session(document,
                               hasPosition,
                               spineIndex,
                               byteOffset);
        } catch (EOFException exception) {
            sessionLoadFailureCount += 1;
            return new Session(fallback, false, 0, 0);
        } catch (IOException | RuntimeException exception) {
            sessionLoadFailureCount += 1;
            return new Session(fallback, false, 0, 0);
        }
    }

    Document importDocument(Uri uri) throws IOException {
        if (uri == null) {
            importFailureCount += 1;
            throw new IOException("No EPUB was selected");
        }

        File temporary = File.createTempFile("import-", ".tmp", rootDirectory);
        long byteCount = 0;
        MessageDigest digest = sha256Digest();
        ContentResolver resolver = context.getContentResolver();
        try (InputStream rawInput = resolver.openInputStream(uri);
             BufferedInputStream input = rawInput == null
                 ? null
                 : new BufferedInputStream(rawInput);
             FileOutputStream fileOutput =
                 new FileOutputStream(temporary, false);
             BufferedOutputStream output =
                 new BufferedOutputStream(fileOutput)) {
            if (input == null) {
                throw new IOException("The selected EPUB could not be opened");
            }
            byte[] buffer = new byte[32 * 1024];
            for (int count = input.read(buffer);
                 count >= 0;
                 count = input.read(buffer)) {
                if (count == 0) {
                    continue;
                }
                byteCount += count;
                if (byteCount > DOCUMENT_FILE_CAP) {
                    throw new IOException("The selected EPUB exceeds 512 MiB");
                }
                digest.update(buffer, 0, count);
                output.write(buffer, 0, count);
            }
            output.flush();
            fileOutput.getFD().sync();
        } catch (IOException | RuntimeException exception) {
            temporary.delete();
            importFailureCount += 1;
            throw exception;
        }
        if (byteCount <= 0) {
            temporary.delete();
            importFailureCount += 1;
            throw new IOException("The selected EPUB is empty");
        }

        String key = hex(digest.digest());
        File destination = new File(documentDirectory, key + ".epub");
        try {
            if (destination.isFile() && destination.length() == byteCount) {
                if (!temporary.delete()) {
                    throw new IOException("Unable to discard duplicate import");
                }
            } else {
                publishAtomically(temporary, destination);
            }
        } catch (IOException | RuntimeException exception) {
            temporary.delete();
            importFailureCount += 1;
            throw exception;
        }

        importSuccessCount += 1;
        return new Document(destination, key, byteCount, true);
    }

    boolean savePresented(Document document,
                          long spineIndex,
                          long byteOffset) {
        if (document == null
            || !document.file.isFile()
            || document.file.length() != document.byteCount
            || !validKey(document.key)
            || spineIndex < 0
            || spineIndex > 0xFFFFFFFFL
            || byteOffset < 0) {
            sessionSaveFailureCount += 1;
            return false;
        }

        String signature = signature(document, true, spineIndex, byteOffset);
        if (signature.equals(lastSavedSignature)) {
            return true;
        }

        File temporary = new File(rootDirectory, SESSION_FILE + ".tmp");
        try (FileOutputStream fileOutput =
                 new FileOutputStream(temporary, false);
             DataOutputStream output = new DataOutputStream(
                 new BufferedOutputStream(fileOutput))) {
            output.writeInt(SESSION_MAGIC);
            output.writeInt(SESSION_VERSION);
            output.writeBoolean(document.imported);
            output.writeUTF(document.key);
            output.writeLong(document.byteCount);
            output.writeBoolean(true);
            output.writeLong(spineIndex);
            output.writeLong(byteOffset);
            output.flush();
            fileOutput.getFD().sync();
        } catch (IOException | RuntimeException exception) {
            temporary.delete();
            sessionSaveFailureCount += 1;
            return false;
        }

        try {
            publishAtomically(temporary, sessionFile);
        } catch (IOException | RuntimeException exception) {
            temporary.delete();
            sessionSaveFailureCount += 1;
            return false;
        }
        lastSavedSignature = signature;
        sessionSaveSuccessCount += 1;
        return true;
    }

    File sessionFileForTesting() {
        return sessionFile;
    }

    long importSuccessCountForTesting() {
        return importSuccessCount;
    }

    long importFailureCountForTesting() {
        return importFailureCount;
    }

    long sessionLoadSuccessCountForTesting() {
        return sessionLoadSuccessCount;
    }

    long sessionLoadFailureCountForTesting() {
        return sessionLoadFailureCount;
    }

    long sessionSaveSuccessCountForTesting() {
        return sessionSaveSuccessCount;
    }

    long sessionSaveFailureCountForTesting() {
        return sessionSaveFailureCount;
    }

    static void clearSessionForTesting(Context context) {
        File root = new File(context.getFilesDir(), ROOT_DIRECTORY);
        File session = new File(root, SESSION_FILE);
        File temporary = new File(root, SESSION_FILE + ".tmp");
        if (session.exists() && !session.delete()) {
            throw new IllegalStateException("Unable to clear the Port 5 session");
        }
        if (temporary.exists() && !temporary.delete()) {
            throw new IllegalStateException(
                "Unable to clear the Port 5 temporary session");
        }
    }

    private static Document fixtureDocument(File fixture) {
        if (!fixture.isFile()
            || fixture.length() != OctavoFixture.BYTE_COUNT) {
            throw new IllegalStateException("Port 5 fixture identity is invalid");
        }
        return new Document(fixture,
                            OctavoFixture.SHA256,
                            OctavoFixture.BYTE_COUNT,
                            false);
    }

    private static String signature(Document document,
                                    boolean hasPosition,
                                    long spineIndex,
                                    long byteOffset) {
        return (document.imported ? "i:" : "f:")
            + document.key + ":" + document.byteCount + ":"
            + (hasPosition ? "1:" : "0:") + spineIndex + ":" + byteOffset;
    }

    private static boolean validKey(String key) {
        return key != null && key.matches("[0-9a-f]{64}");
    }

    private static MessageDigest sha256Digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static String hex(byte[] bytes) {
        StringBuilder result = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            result.append(String.format(Locale.ROOT, "%02x", value & 0xFF));
        }
        return result.toString();
    }

    private static void requireDirectory(File directory) {
        if (!directory.isDirectory() && !directory.mkdirs()) {
            throw new IllegalStateException(
                "Unable to create " + directory.getAbsolutePath());
        }
    }

    private static void publishAtomically(File temporary, File destination)
        throws IOException {
        try {
            Files.move(temporary.toPath(),
                       destination.toPath(),
                       StandardCopyOption.ATOMIC_MOVE,
                       StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(temporary.toPath(),
                       destination.toPath(),
                       StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
