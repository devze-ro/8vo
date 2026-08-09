package ro.devze.octavo;

import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Locale;

final class OctavoLibraryStore {
    private static final int CATALOG_MAGIC = 0x4F364C42;
    private static final int CATALOG_VERSION = 1;
    private static final int CATALOG_ENTRY_CAP = 64;
    private static final int CATALOG_FILE_CAP = 128 * 1024;
    private static final int TITLE_CODE_POINT_CAP = 256;
    private static final long DOCUMENT_FILE_CAP = 512L * 1024L * 1024L;
    private static final String ROOT_DIRECTORY = "port6";
    private static final String DOCUMENT_DIRECTORY = "documents";
    private static final String CATALOG_FILE = "library.v1";

    private static final int LEGACY_SESSION_MAGIC = 0x4F355253;
    private static final int LEGACY_SESSION_VERSION = 1;
    private static final int LEGACY_SESSION_FILE_CAP = 512;
    private static final String LEGACY_ROOT_DIRECTORY = "port5";
    private static final String LEGACY_DOCUMENT_DIRECTORY = "documents";
    private static final String LEGACY_SESSION_FILE = "reader_session.v1";

    static final class Book {
        final File file;
        final String key;
        final long byteCount;
        final boolean imported;
        String title;
        long addedTime;
        long lastOpenedTime;
        boolean hasPosition;
        long spineIndex;
        long byteOffset;

        Book(File file,
             String key,
             long byteCount,
             boolean imported,
             String title,
             long addedTime,
             long lastOpenedTime,
             boolean hasPosition,
             long spineIndex,
             long byteOffset) {
            this.file = file;
            this.key = key;
            this.byteCount = byteCount;
            this.imported = imported;
            this.title = title;
            this.addedTime = addedTime;
            this.lastOpenedTime = lastOpenedTime;
            this.hasPosition = hasPosition;
            this.spineIndex = spineIndex;
            this.byteOffset = byteOffset;
        }
    }

    static final class Session {
        final Book book;
        final boolean hasPosition;
        final long spineIndex;
        final long byteOffset;

        Session(Book book) {
            this(book,
                 book.hasPosition,
                 book.spineIndex,
                 book.byteOffset);
        }

        Session(Book book,
                boolean hasPosition,
                long spineIndex,
                long byteOffset) {
            if (book == null
                || spineIndex < 0
                || spineIndex > 0xFFFFFFFFL
                || byteOffset < 0) {
                throw new IllegalArgumentException();
            }
            this.book = book;
            this.hasPosition = hasPosition;
            this.spineIndex = spineIndex;
            this.byteOffset = byteOffset;
        }
    }

    private final Context context;
    private final File rootDirectory;
    private final File documentDirectory;
    private final File catalogFile;
    private final ArrayList<Book> books = new ArrayList<>();
    private long importSuccessCount;
    private long importFailureCount;
    private long catalogLoadSuccessCount;
    private long catalogLoadFailureCount;
    private long catalogSaveSuccessCount;
    private long catalogSaveFailureCount;
    private long duplicateImportCount;
    private long removeSuccessCount;
    private long removeFailureCount;
    private long managedDeleteFailureCount;
    private long migrationSuccessCount;
    private long migrationFailureCount;

    OctavoLibraryStore(Context context) {
        this.context = context.getApplicationContext();
        rootDirectory = new File(this.context.getFilesDir(), ROOT_DIRECTORY);
        documentDirectory = new File(rootDirectory, DOCUMENT_DIRECTORY);
        catalogFile = new File(rootDirectory, CATALOG_FILE);
        requireDirectory(rootDirectory);
        requireDirectory(documentDirectory);
    }

    void loadCatalog(File fixture) {
        books.clear();
        Book fixtureBook = fixtureBook(fixture);
        if (!catalogFile.isFile()) {
            books.add(fixtureBook);
            if (migrateLegacyPort5()) {
                sortBooks();
                if (!saveCatalog()) {
                    books.clear();
                    books.add(fixtureBook);
                }
            }
            return;
        }
        if (catalogFile.length() <= 0
            || catalogFile.length() > CATALOG_FILE_CAP) {
            catalogLoadFailureCount += 1;
            books.add(fixtureBook);
            return;
        }

        ArrayList<Book> loaded = new ArrayList<>();
        try (DataInputStream input = new DataInputStream(
                 new BufferedInputStream(new FileInputStream(catalogFile)))) {
            int magic = input.readInt();
            int version = input.readInt();
            int entryCount = input.readInt();
            if (magic != CATALOG_MAGIC
                || version != CATALOG_VERSION
                || entryCount <= 0
                || entryCount > CATALOG_ENTRY_CAP) {
                throw new IOException("Invalid Port 6 catalog header");
            }
            boolean foundFixture = false;
            for (int index = 0; index < entryCount; ++index) {
                boolean imported = input.readBoolean();
                String key = input.readUTF();
                long byteCount = input.readLong();
                String title = input.readUTF();
                long addedTime = input.readLong();
                long lastOpenedTime = input.readLong();
                boolean hasPosition = input.readBoolean();
                long spineIndex = input.readLong();
                long byteOffset = input.readLong();
                if (!validKey(key)
                    || byteCount <= 0
                    || byteCount > DOCUMENT_FILE_CAP
                    || !validTitle(title)
                    || addedTime < 0
                    || lastOpenedTime < 0
                    || spineIndex < 0
                    || spineIndex > 0xFFFFFFFFL
                    || byteOffset < 0
                    || findByKey(loaded, key) != null) {
                    throw new IOException("Invalid Port 6 catalog entry");
                }

                File file;
                if (imported) {
                    file = new File(documentDirectory, key + ".epub");
                    if (!validManagedFile(file, byteCount)) {
                        throw new IOException("Managed EPUB is unavailable");
                    }
                } else {
                    if (foundFixture
                        || !key.equals(OctavoFixture.SHA256)
                        || byteCount != OctavoFixture.BYTE_COUNT
                        || !fixture.isFile()
                        || fixture.length() != OctavoFixture.BYTE_COUNT) {
                        throw new IOException("Fixture catalog entry is invalid");
                    }
                    foundFixture = true;
                    file = fixture;
                }
                loaded.add(new Book(file,
                                    key,
                                    byteCount,
                                    imported,
                                    title,
                                    addedTime,
                                    lastOpenedTime,
                                    hasPosition,
                                    spineIndex,
                                    byteOffset));
            }
            if (input.read() != -1 || !foundFixture) {
                throw new IOException("Port 6 catalog shape is invalid");
            }
            books.addAll(loaded);
            sortBooks();
            catalogLoadSuccessCount += 1;
        } catch (EOFException exception) {
            catalogLoadFailureCount += 1;
            books.clear();
            books.add(fixtureBook);
        } catch (IOException | RuntimeException exception) {
            catalogLoadFailureCount += 1;
            books.clear();
            books.add(fixtureBook);
        }
    }

    Book importDocument(Uri uri) throws IOException {
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
        Book existing = findBook(key);
        if (existing != null) {
            duplicateImportCount += 1;
            return existing;
        }
        long now = System.currentTimeMillis();
        return new Book(destination,
                        key,
                        byteCount,
                        true,
                        "Imported EPUB",
                        now,
                        0,
                        false,
                        0,
                        0);
    }

    boolean recordOpened(Book candidate, String readerTitle) {
        if (candidate == null || !validBook(candidate)) {
            catalogSaveFailureCount += 1;
            return false;
        }
        Book existing = findBook(candidate.key);
        boolean created = existing == null;
        if (created) {
            if (books.size() >= CATALOG_ENTRY_CAP) {
                catalogSaveFailureCount += 1;
                return false;
            }
            existing = candidate;
            books.add(existing);
        }

        String previousTitle = existing.title;
        long previousLastOpened = existing.lastOpenedTime;
        existing.title = boundedTitle(readerTitle);
        existing.lastOpenedTime = System.currentTimeMillis();
        sortBooks();
        if (saveCatalog()) {
            return true;
        }

        if (created) {
            books.remove(existing);
        } else {
            existing.title = previousTitle;
            existing.lastOpenedTime = previousLastOpened;
        }
        sortBooks();
        return false;
    }

    boolean savePresented(Book book,
                          long spineIndex,
                          long byteOffset) {
        Book current = book == null ? null : findBook(book.key);
        if (current == null
            || spineIndex < 0
            || spineIndex > 0xFFFFFFFFL
            || byteOffset < 0) {
            catalogSaveFailureCount += 1;
            return false;
        }
        if (current.hasPosition
            && current.spineIndex == spineIndex
            && current.byteOffset == byteOffset) {
            return true;
        }

        boolean previousHasPosition = current.hasPosition;
        long previousSpineIndex = current.spineIndex;
        long previousByteOffset = current.byteOffset;
        current.hasPosition = true;
        current.spineIndex = spineIndex;
        current.byteOffset = byteOffset;
        if (saveCatalog()) {
            return true;
        }
        current.hasPosition = previousHasPosition;
        current.spineIndex = previousSpineIndex;
        current.byteOffset = previousByteOffset;
        return false;
    }

    boolean removeBook(String key) {
        Book book = findBook(key);
        if (book == null || !book.imported) {
            removeFailureCount += 1;
            return false;
        }
        int index = books.indexOf(book);
        books.remove(index);
        if (!saveCatalog()) {
            books.add(index, book);
            sortBooks();
            removeFailureCount += 1;
            return false;
        }
        if (book.file.exists() && !book.file.delete()) {
            managedDeleteFailureCount += 1;
        }
        removeSuccessCount += 1;
        return true;
    }

    void discardUncataloged(Book book) {
        if (book != null
            && book.imported
            && findBook(book.key) == null
            && book.file.exists()) {
            book.file.delete();
        }
    }

    Session sessionFor(Book book) {
        Book current = book == null ? null : findBook(book.key);
        return current == null ? null : new Session(current);
    }

    Book fixtureBook() {
        for (Book book : books) {
            if (!book.imported) {
                return book;
            }
        }
        return null;
    }

    Book findBook(String key) {
        return findByKey(books, key);
    }

    Book[] books() {
        return books.toArray(new Book[0]);
    }

    int bookCount() {
        return books.size();
    }

    File catalogFileForTesting() {
        return catalogFile;
    }

    long importSuccessCountForTesting() {
        return importSuccessCount;
    }

    long importFailureCountForTesting() {
        return importFailureCount;
    }

    long catalogLoadSuccessCountForTesting() {
        return catalogLoadSuccessCount;
    }

    long catalogLoadFailureCountForTesting() {
        return catalogLoadFailureCount;
    }

    long catalogSaveSuccessCountForTesting() {
        return catalogSaveSuccessCount;
    }

    long catalogSaveFailureCountForTesting() {
        return catalogSaveFailureCount;
    }

    long duplicateImportCountForTesting() {
        return duplicateImportCount;
    }

    long removeSuccessCountForTesting() {
        return removeSuccessCount;
    }

    long removeFailureCountForTesting() {
        return removeFailureCount;
    }

    long managedDeleteFailureCountForTesting() {
        return managedDeleteFailureCount;
    }

    long migrationSuccessCountForTesting() {
        return migrationSuccessCount;
    }

    long migrationFailureCountForTesting() {
        return migrationFailureCount;
    }

    static void clearForTesting(Context context) {
        File root = new File(context.getFilesDir(), ROOT_DIRECTORY);
        deleteTree(root);
        File legacySession = new File(
            new File(context.getFilesDir(), LEGACY_ROOT_DIRECTORY),
            LEGACY_SESSION_FILE);
        if (legacySession.exists() && !legacySession.delete()) {
            throw new IllegalStateException(
                "Unable to clear the Port 5 migration session");
        }
    }

    private boolean saveCatalog() {
        if (books.isEmpty() || books.size() > CATALOG_ENTRY_CAP) {
            catalogSaveFailureCount += 1;
            return false;
        }
        byte[] bytes;
        try (ByteArrayOutputStream byteOutput = new ByteArrayOutputStream();
             DataOutputStream output = new DataOutputStream(byteOutput)) {
            output.writeInt(CATALOG_MAGIC);
            output.writeInt(CATALOG_VERSION);
            output.writeInt(books.size());
            for (Book book : books) {
                if (!validBook(book)) {
                    throw new IOException("Invalid Port 6 catalog state");
                }
                output.writeBoolean(book.imported);
                output.writeUTF(book.key);
                output.writeLong(book.byteCount);
                output.writeUTF(book.title);
                output.writeLong(book.addedTime);
                output.writeLong(book.lastOpenedTime);
                output.writeBoolean(book.hasPosition);
                output.writeLong(book.spineIndex);
                output.writeLong(book.byteOffset);
            }
            output.flush();
            bytes = byteOutput.toByteArray();
        } catch (IOException | RuntimeException exception) {
            catalogSaveFailureCount += 1;
            return false;
        }
        if (bytes.length <= 0 || bytes.length > CATALOG_FILE_CAP) {
            catalogSaveFailureCount += 1;
            return false;
        }

        File temporary = new File(rootDirectory, CATALOG_FILE + ".tmp");
        try (FileOutputStream output = new FileOutputStream(temporary, false)) {
            output.write(bytes);
            output.getFD().sync();
            publishAtomically(temporary, catalogFile);
        } catch (IOException | RuntimeException exception) {
            temporary.delete();
            catalogSaveFailureCount += 1;
            return false;
        }
        catalogSaveSuccessCount += 1;
        return true;
    }

    private boolean migrateLegacyPort5() {
        File legacyRoot =
            new File(context.getFilesDir(), LEGACY_ROOT_DIRECTORY);
        File legacySession = new File(legacyRoot, LEGACY_SESSION_FILE);
        if (!legacySession.isFile()) {
            return false;
        }
        if (legacySession.length() <= 0
            || legacySession.length() > LEGACY_SESSION_FILE_CAP) {
            migrationFailureCount += 1;
            return false;
        }

        try (DataInputStream input = new DataInputStream(
                 new BufferedInputStream(new FileInputStream(legacySession)))) {
            int magic = input.readInt();
            int version = input.readInt();
            boolean imported = input.readBoolean();
            String key = input.readUTF();
            long byteCount = input.readLong();
            boolean hasPosition = input.readBoolean();
            long spineIndex = input.readLong();
            long byteOffset = input.readLong();
            if (input.read() != -1
                || magic != LEGACY_SESSION_MAGIC
                || version != LEGACY_SESSION_VERSION
                || !imported
                || !validKey(key)
                || byteCount <= 0
                || byteCount > DOCUMENT_FILE_CAP
                || spineIndex < 0
                || spineIndex > 0xFFFFFFFFL
                || byteOffset < 0) {
                throw new IOException("Invalid Port 5 migration record");
            }
            File source = new File(
                new File(legacyRoot, LEGACY_DOCUMENT_DIRECTORY),
                key + ".epub");
            if (!source.isFile() || source.length() != byteCount) {
                throw new IOException("Port 5 managed EPUB is unavailable");
            }
            File destination = new File(documentDirectory, key + ".epub");
            if (!destination.isFile() || destination.length() != byteCount) {
                copyFileAtomically(source, destination, byteCount);
            }
            long now = System.currentTimeMillis();
            books.add(new Book(destination,
                               key,
                               byteCount,
                               true,
                               "Imported EPUB",
                               now,
                               now,
                               hasPosition,
                               spineIndex,
                               byteOffset));
            migrationSuccessCount += 1;
            return true;
        } catch (EOFException exception) {
            migrationFailureCount += 1;
            return false;
        } catch (IOException | RuntimeException exception) {
            migrationFailureCount += 1;
            return false;
        }
    }

    private static Book fixtureBook(File fixture) {
        if (!fixture.isFile()
            || fixture.length() != OctavoFixture.BYTE_COUNT) {
            throw new IllegalStateException("Port 6 fixture identity is invalid");
        }
        return new Book(fixture,
                        OctavoFixture.SHA256,
                        OctavoFixture.BYTE_COUNT,
                        false,
                        OctavoFixture.TITLE,
                        0,
                        0,
                        false,
                        0,
                        0);
    }

    private boolean validBook(Book book) {
        if (book == null
            || !validKey(book.key)
            || book.byteCount <= 0
            || book.byteCount > DOCUMENT_FILE_CAP
            || !validTitle(book.title)
            || book.addedTime < 0
            || book.lastOpenedTime < 0
            || book.spineIndex < 0
            || book.spineIndex > 0xFFFFFFFFL
            || book.byteOffset < 0
            || !book.file.isFile()
            || book.file.length() != book.byteCount) {
            return false;
        }
        if (!book.imported) {
            return book.key.equals(OctavoFixture.SHA256)
                && book.byteCount == OctavoFixture.BYTE_COUNT;
        }
        return validManagedFile(book.file, book.byteCount);
    }

    private boolean validManagedFile(File file, long byteCount) {
        try {
            return file.isFile()
                && file.length() == byteCount
                && documentDirectory.getCanonicalFile().equals(
                    file.getCanonicalFile().getParentFile());
        } catch (IOException exception) {
            return false;
        }
    }

    private static Book findByKey(ArrayList<Book> source, String key) {
        if (key == null) {
            return null;
        }
        for (Book book : source) {
            if (key.equals(book.key)) {
                return book;
            }
        }
        return null;
    }

    private void sortBooks() {
        books.sort(new Comparator<Book>() {
            @Override
            public int compare(Book left, Book right) {
                if (left.imported != right.imported) {
                    return left.imported ? 1 : -1;
                }
                int opened =
                    Long.compare(right.lastOpenedTime, left.lastOpenedTime);
                if (opened != 0) {
                    return opened;
                }
                int added = Long.compare(right.addedTime, left.addedTime);
                if (added != 0) {
                    return added;
                }
                int title = left.title.compareToIgnoreCase(right.title);
                return title != 0 ? title : left.key.compareTo(right.key);
            }
        });
    }

    private static String boundedTitle(String value) {
        String title = value == null ? "" : value.trim();
        if (title.isEmpty()) {
            return "Untitled EPUB";
        }
        int count = title.codePointCount(0, title.length());
        if (count <= TITLE_CODE_POINT_CAP) {
            return title;
        }
        int end = title.offsetByCodePoints(0, TITLE_CODE_POINT_CAP);
        return title.substring(0, end);
    }

    private static boolean validTitle(String title) {
        return title != null
            && !title.isEmpty()
            && title.codePointCount(0, title.length()) <= TITLE_CODE_POINT_CAP;
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

    private static void copyFileAtomically(File source,
                                           File destination,
                                           long expectedBytes)
        throws IOException {
        File temporary = new File(destination.getParentFile(),
                                  destination.getName() + ".migration.tmp");
        long copied = 0;
        try (FileInputStream input = new FileInputStream(source);
             FileOutputStream output = new FileOutputStream(temporary, false)) {
            byte[] buffer = new byte[32 * 1024];
            for (int count = input.read(buffer);
                 count >= 0;
                 count = input.read(buffer)) {
                if (count > 0) {
                    copied += count;
                    if (copied > DOCUMENT_FILE_CAP) {
                        throw new IOException("Legacy EPUB exceeds the file cap");
                    }
                    output.write(buffer, 0, count);
                }
            }
            output.getFD().sync();
        } catch (IOException | RuntimeException exception) {
            temporary.delete();
            throw exception;
        }
        if (copied != expectedBytes) {
            temporary.delete();
            throw new IOException("Legacy EPUB length changed during migration");
        }
        publishAtomically(temporary, destination);
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

    private static void deleteTree(File file) {
        if (file == null || !file.exists()) {
            return;
        }
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteTree(child);
                }
            }
        }
        if (!file.delete()) {
            throw new IllegalStateException(
                "Unable to clear " + file.getAbsolutePath());
        }
    }
}
