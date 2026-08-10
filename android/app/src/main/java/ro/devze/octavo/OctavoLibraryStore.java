package ro.devze.octavo;

import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Locale;
import java.util.zip.CRC32;

final class OctavoLibraryStore {
    enum LoadStatus {
        MISSING,
        LOADED,
        LOADED_WITH_REPAIR,
        CORRUPT_QUARANTINED,
        FUTURE_VERSION_BLOCKED,
        CORRUPT_BLOCKED,
        PUBLISH_UNCERTAIN_BLOCKED,
        IMPORT_ASSOCIATION_PENDING,
        IMPORT_RECOVERY_BLOCKED
    }

    enum IdentityCheckStatus {
        VERIFIED,
        PENDING,
        FAILED
    }

    enum TransferredBookStatus {
        READY,
        BYTES_UNAVAILABLE,
        CATALOG_BLOCKED,
        CATALOG_FULL,
        LOCAL_CONFLICT
    }

    enum TransferredBookStepStatus {
        PENDING,
        COMPLETED,
        IDENTITY_FAILED,
        CATALOG_RETRY,
        STALE
    }

    private static final int CATALOG_MAGIC = 0x4F364C42;
    private static final int CATALOG_LEGACY_VERSION = 1;
    private static final int CATALOG_VERSION = 2;
    private static final int CATALOG_ENTRY_CAP = 64;
    private static final int CATALOG_FILE_CAP = 128 * 1024;
    private static final int TITLE_CODE_POINT_CAP = 256;
    private static final long DOCUMENT_FILE_CAP = 512L * 1024L * 1024L;
    private static final String ROOT_DIRECTORY = "port6";
    private static final String DOCUMENT_DIRECTORY = "documents";
    private static final String CATALOG_FILE = "library.v1";
    private static final String CATALOG_TEMPORARY_FILE = "library.v1.tmp";
    private static final String IMPORT_TEMPORARY_FILE = "library-import.tmp";
    private static final int IMPORT_JOURNAL_MAGIC = 0x4F36494A;
    private static final int IMPORT_JOURNAL_VERSION = 1;
    private static final int IMPORT_PHASE_READY_TO_PUBLISH = 1;
    private static final int IMPORT_PHASE_AWAITING_CATALOG = 2;
    private static final int IMPORT_JOURNAL_BYTES = 88;
    private static final int IMPORT_JOURNAL_CAP = 1024;
    private static final String IMPORT_JOURNAL_FILE =
        "library-import-association.v1";
    private static final String IMPORT_JOURNAL_TEMPORARY_FILE =
        "library-import-association.v1.tmp";
    private static final String CATALOG_QUARANTINE_PREFIX =
        "library.v1.corrupt.";
    private static final int CATALOG_QUARANTINE_SLOTS = 3;
    private static final String CATALOG_PUBLISH_UNCERTAIN_ERROR =
        "The local Library publication outcome is uncertain";

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
        boolean repairRequired;
        boolean identityVerified;
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
             boolean repairRequired,
             boolean identityVerified,
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
            this.repairRequired = repairRequired;
            this.identityVerified = identityVerified;
            this.title = title;
            this.addedTime = addedTime;
            this.lastOpenedTime = lastOpenedTime;
            this.hasPosition = hasPosition;
            this.spineIndex = spineIndex;
            this.byteOffset = byteOffset;
        }
    }

    static final class TransferredBookOutcome {
        final TransferredBookStatus status;
        final Book book;

        private TransferredBookOutcome(TransferredBookStatus status,
                                       Book book) {
            if (status == null
                || ((status == TransferredBookStatus.READY)
                    != (book != null))) {
                throw new IllegalArgumentException();
            }
            this.status = status;
            this.book = book;
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
                || book.repairRequired
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
    private final File importStagingFile;
    private final File importJournalFile;
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
    private LoadStatus loadStatus = LoadStatus.MISSING;
    private String lastError;
    private IdentityCheck identityCheck;
    private Book verifiedPendingImport;
    private Book activeStagedImport;
    private Book activeTransferredBook;
    private Book verifiedTransferredBook;
    private IdentityFileLease verifiedTransferredLease;
    private boolean lastTransferredRecordProofFailed;
    private boolean failNextCatalogMoveAfterReplaceForTesting;

    OctavoLibraryStore(Context context) {
        this.context = context.getApplicationContext();
        rootDirectory = new File(this.context.getFilesDir(), ROOT_DIRECTORY);
        documentDirectory = new File(rootDirectory, DOCUMENT_DIRECTORY);
        catalogFile = new File(rootDirectory, CATALOG_FILE);
        importStagingFile = new File(
            documentDirectory, IMPORT_TEMPORARY_FILE);
        importJournalFile = new File(rootDirectory, IMPORT_JOURNAL_FILE);
        requireDirectory(rootDirectory);
        requireDirectory(documentDirectory);
    }

    private static final class ImportJournal {
        final String key;
        final long byteCount;
        final int phase;

        ImportJournal(String key, long byteCount, int phase) {
            this.key = key;
            this.byteCount = byteCount;
            this.phase = phase;
        }
    }

    void loadCatalog(File fixture) {
        cancelBookIdentityVerification();
        activeStagedImport = null;
        books.clear();
        lastError = null;
        Book fixtureBook = fixtureBook(fixture);
        if (!catalogFile.isFile()) {
            loadStatus = LoadStatus.MISSING;
            books.add(fixtureBook);
            if (migrateLegacyPort5()) {
                sortBooks();
                if (!saveCatalog()) {
                    books.clear();
                    books.add(fixtureBook);
                } else {
                    loadStatus = LoadStatus.LOADED;
                }
            } else if (hasCatalogQuarantine()) {
                if (saveCatalog()) {
                    loadStatus = LoadStatus.CORRUPT_QUARANTINED;
                    lastError =
                        "An earlier invalid Library catalog remains quarantined";
                } else if (loadStatus
                           != LoadStatus.PUBLISH_UNCERTAIN_BLOCKED) {
                    loadStatus = LoadStatus.CORRUPT_BLOCKED;
                    lastError =
                        "The quarantined Library catalog could not be reset";
                }
            }
            reconcileImportOnLoad();
            return;
        }
        if (catalogFile.length() <= 0) {
            catalogLoadFailureCount += 1;
            recoverCorruptCatalog(fixtureBook);
            reconcileImportOnLoad();
            return;
        }
        if (catalogFile.length() > CATALOG_FILE_CAP) {
            catalogLoadFailureCount += 1;
            loadStatus = LoadStatus.CORRUPT_BLOCKED;
            lastError =
                "The over-bound local Library catalog was preserved and blocked";
            books.add(fixtureBook);
            reconcileImportOnLoad();
            return;
        }

        ArrayList<Book> loaded = new ArrayList<>();
        try (DataInputStream input = new DataInputStream(
                 new BufferedInputStream(new FileInputStream(catalogFile)))) {
            int magic = input.readInt();
            int version = input.readInt();
            if (magic == CATALOG_MAGIC
                && Integer.compareUnsigned(version, CATALOG_VERSION) > 0) {
                loadStatus = LoadStatus.FUTURE_VERSION_BLOCKED;
                lastError = "The local Library was created by a newer version";
                books.add(fixtureBook);
                reconcileImportOnLoad();
                return;
            }
            int entryCount = input.readInt();
            if (magic != CATALOG_MAGIC
                || (version != CATALOG_LEGACY_VERSION
                    && version != CATALOG_VERSION)
                || entryCount <= 0
                || entryCount > CATALOG_ENTRY_CAP) {
                throw new IOException("Invalid Port 6 catalog header");
            }
            boolean foundFixture = false;
            for (int index = 0; index < entryCount; ++index) {
                boolean imported = input.readBoolean();
                boolean persistedRepairRequired =
                    version >= CATALOG_VERSION && input.readBoolean();
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
                boolean repairRequired = persistedRepairRequired;
                if (imported) {
                    file = new File(documentDirectory, key + ".epub");
                    if (!validManagedFileShape(file, byteCount)) {
                        repairRequired = true;
                    }
                } else {
                    if (foundFixture
                        || repairRequired
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
                                     repairRequired,
                                     false,
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
            boolean repairRequired = false;
            for (Book book : loaded) {
                repairRequired |= book.repairRequired;
            }
            loadStatus = repairRequired
                ? LoadStatus.LOADED_WITH_REPAIR
                : LoadStatus.LOADED;
            if (repairRequired) {
                lastError = "One or more Library books need repair";
            } else if (hasCatalogQuarantine()) {
                loadStatus = LoadStatus.CORRUPT_QUARANTINED;
                lastError =
                    "An earlier invalid Library catalog remains quarantined";
            }
            catalogLoadSuccessCount += 1;
        } catch (EOFException exception) {
            catalogLoadFailureCount += 1;
            recoverCorruptCatalog(fixtureBook);
        } catch (IOException | RuntimeException exception) {
            catalogLoadFailureCount += 1;
            if (loadStatus != LoadStatus.FUTURE_VERSION_BLOCKED) {
                recoverCorruptCatalog(fixtureBook);
            }
        }
        reconcileImportOnLoad();
    }

    /**
     * Reconciles a blocked in-memory publication outcome from the bounded
     * durable O6 catalog. Runtime identity capabilities are always revoked by
     * loadCatalog() before the durable state is decoded again.
     */
    void reloadCatalog(File fixture) {
        loadCatalog(fixture);
    }

    private static final class IdentityFileLease {
        final File canonicalFile;
        final long byteCount;
        final FileTime lastModifiedTime;
        final Object fileKey;

        private IdentityFileLease(File canonicalFile,
                                  long byteCount,
                                  FileTime lastModifiedTime,
                                  Object fileKey) {
            this.canonicalFile = canonicalFile;
            this.byteCount = byteCount;
            this.lastModifiedTime = lastModifiedTime;
            this.fileKey = fileKey;
        }

        static IdentityFileLease capture(File file, long expectedByteCount)
            throws IOException {
            if (file == null || expectedByteCount <= 0) {
                throw new IOException("Invalid identity file lease");
            }
            File canonical = file.getCanonicalFile();
            BasicFileAttributes attributes = Files.readAttributes(
                canonical.toPath(),
                BasicFileAttributes.class,
                LinkOption.NOFOLLOW_LINKS);
            if (!attributes.isRegularFile()
                || attributes.size() != expectedByteCount) {
                throw new IOException("Identity file shape changed");
            }
            return new IdentityFileLease(
                canonical,
                attributes.size(),
                attributes.lastModifiedTime(),
                attributes.fileKey());
        }

        boolean matches(File file, long expectedByteCount) {
            try {
                IdentityFileLease current = capture(
                    file, expectedByteCount);
                if (!canonicalFile.equals(current.canonicalFile)
                    || byteCount != current.byteCount
                    || !lastModifiedTime.equals(current.lastModifiedTime)) {
                    return false;
                }
                return fileKey == null
                    ? current.fileKey == null
                    : fileKey.equals(current.fileKey);
            } catch (IOException | RuntimeException exception) {
                return false;
            }
        }
    }

    private static final class IdentityCheck {
        final Book book;
        final FileInputStream input;
        final MessageDigest digest;
        final IdentityFileLease lease;
        long byteCount;

        IdentityCheck(Book book,
                      FileInputStream input,
                      MessageDigest digest,
                      IdentityFileLease lease) {
            this.book = book;
            this.input = input;
            this.digest = digest;
            this.lease = lease;
        }
    }

    private static IdentityCheck openIdentityCheck(Book book)
        throws IOException {
        IdentityFileLease lease = IdentityFileLease.capture(
            book.file, book.byteCount);
        FileInputStream input = new FileInputStream(lease.canonicalFile);
        try {
            // The managed directory is app-private and its only writers are
            // serialized O6/O1BQ operations. The lease detects path
            // replacement (fileKey when supplied) and ordinary in-place edits
            // (size/mtime). A privileged writer that edits bytes and restores
            // identical filesystem metadata is outside that ownership model.
            if (!lease.matches(book.file, book.byteCount)) {
                throw new IOException(
                    "Identity file changed while its stream was opened");
            }
            return new IdentityCheck(
                book, input, sha256Digest(), lease);
        } catch (IOException | RuntimeException exception) {
            try {
                input.close();
            } catch (IOException ignored) {
                // The candidate has not been accepted.
            }
            throw exception;
        }
    }

    Book importDocument(Uri uri) throws IOException {
        if (blockedForMutation()) {
            importFailureCount += 1;
            throw new IOException(lastError == null
                ? "The local Library is blocked"
                : lastError);
        }
        if (uri == null) {
            importFailureCount += 1;
            throw new IOException("No EPUB was selected");
        }
        if (importStagingFile.exists()
            || importJournalFile.exists()
            || importJournalTemporaryFile().exists()) {
            importFailureCount += 1;
            if (importJournalFile.isFile()) {
                try {
                    if (readImportJournalOrNull() != null) {
                        loadStatus = LoadStatus.IMPORT_ASSOCIATION_PENDING;
                        lastError =
                            "A previous EPUB import needs Retry before another import";
                    }
                } catch (IOException | RuntimeException exception) {
                    blockImportRecovery(
                        "The EPUB import journal is invalid and was preserved");
                }
            } else {
                lastError =
                    "Another EPUB import is already using the fixed staging file";
            }
            throw new IOException(lastError);
        }

        // Only one Activity-owned import can run at a time. A fixed staging
        // name bounds crash leftovers to one file instead of allowing every
        // rejected picker result to allocate another orphan.
        long byteCount = 0;
        MessageDigest digest = sha256Digest();
        ContentResolver resolver = context.getContentResolver();
        try (InputStream rawInput = resolver.openInputStream(uri);
             BufferedInputStream input = rawInput == null
                 ? null
                 : new BufferedInputStream(rawInput);
             FileOutputStream fileOutput =
                 new FileOutputStream(importStagingFile, false);
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
            deleteImportStagingOrBlock();
            importFailureCount += 1;
            throw exception;
        }
        if (byteCount <= 0) {
            deleteImportStagingOrBlock();
            importFailureCount += 1;
            throw new IOException("The selected EPUB is empty");
        }

        String key = hex(digest.digest());
        File destination = new File(documentDirectory, key + ".epub");
        Book existing = findBook(key);
        if (existing != null) {
            if (!existing.imported || existing.byteCount != byteCount) {
                deleteImportStagingOrBlock();
                importFailureCount += 1;
                throw new IOException(
                    "The selected EPUB conflicts with the local Library");
            }
            if (validManagedFile(destination, key, byteCount)) {
                if (!deleteImportStagingOrBlock()) {
                    importFailureCount += 1;
                    throw new IOException(lastError);
                }
                existing.repairRequired = false;
                existing.identityVerified = true;
                duplicateImportCount += 1;
                importSuccessCount += 1;
                return existing;
            }
        } else if (books.size() >= CATALOG_ENTRY_CAP) {
            deleteImportStagingOrBlock();
            importFailureCount += 1;
            throw new IOException("The Library is full");
        }
        importSuccessCount += 1;
        long now = System.currentTimeMillis();
        activeStagedImport = new Book(importStagingFile,
                                      key,
                                      byteCount,
                                      true,
                                      false,
                                      false,
                                      "Imported EPUB",
                                      now,
                                      0,
                                      false,
                                      0,
                                      0);
        return activeStagedImport;
    }

    boolean isStagedImport(Book book) {
        if (book == null
            || book != activeStagedImport
            || !book.imported
            || !validKey(book.key)
            || book.byteCount <= 0
            || book.byteCount > DOCUMENT_FILE_CAP) {
            return false;
        }
        try {
            return importStagingFile.getCanonicalFile().equals(
                book.file.getCanonicalFile());
        } catch (IOException | RuntimeException exception) {
            return false;
        }
    }

    Book publishReader0ValidatedImport(Book staged) throws IOException {
        if (blockedForMutation()) {
            throw new IOException(lastError == null
                ? "The local Library is blocked"
                : lastError);
        }
        if (!isStagedImport(staged)) {
            blockImportRecovery(
                "The managed EPUB publication lost fixed staging ownership");
            throw new IOException(lastError);
        }
        if (!staged.identityVerified
            || verifiedPendingImport != staged
            || !validManagedFileShape(
                importStagingFile, staged.byteCount)) {
            lastError =
                "The staged EPUB needs exact identity verification before managed publication";
            throw new IOException(lastError);
        }

        ImportJournal journal;
        try {
            journal = readImportJournalOrNull();
        } catch (IOException | RuntimeException exception) {
            blockImportRecovery(
                "The EPUB import journal is invalid and was preserved");
            throw new IOException(lastError, exception);
        }
        if (journal == null) {
            publishImportJournal(staged.key,
                                 staged.byteCount,
                                 IMPORT_PHASE_READY_TO_PUBLISH);
            journal = readImportJournalOrNull();
        }
        if (!matchesImportJournal(journal, staged.key, staged.byteCount)) {
            blockImportRecovery(
                "The pending EPUB import does not match the staged bytes");
            throw new IOException(lastError);
        }

        File destination = managedFile(staged.key);
        if (destination == null) {
            blockImportRecovery("The staged EPUB identity is invalid");
            throw new IOException(lastError);
        }
        try {
            publishAtomically(importStagingFile, destination);
        } catch (IOException | RuntimeException exception) {
            boolean candidateWon = validManagedFile(
                destination, staged.key, staged.byteCount);
            boolean priorRemains = fileHasIdentity(
                importStagingFile, staged.key, staged.byteCount);
            if (!candidateWon) {
                if (priorRemains) {
                    // The atomic move definitely did not publish these
                    // bytes. Roll the READY marker back while retaining
                    // its fixed nonauthoritative staging file so the live
                    // Activity recognizes and discards exactly that file;
                    // it must not route the digest destination to O1BQ.
                    clearImportJournalOrBlock();
                } else {
                    blockImportRecovery(
                        "The managed EPUB publication outcome is uncertain");
                }
                throw new IOException(
                    lastError == null
                        ? "Unable to publish the managed EPUB"
                        : lastError,
                    exception);
            }
            if (importStagingFile.exists()
                && !deleteImportStagingOrBlock()) {
                throw new IOException(lastError, exception);
            }
        }

        try {
            publishImportJournal(staged.key,
                                 staged.byteCount,
                                 IMPORT_PHASE_AWAITING_CATALOG);
        } catch (IOException exception) {
            // READY_TO_PUBLISH plus absence of the fixed staging file is
            // already enough to recover the completed atomic move. Continue
            // only when that exact durable prior marker is still readable.
            ImportJournal retained = readImportJournalOrNull();
            if (loadStatus == LoadStatus.IMPORT_RECOVERY_BLOCKED
                || !matchesImportJournal(
                    retained, staged.key, staged.byteCount)) {
                throw exception;
            }
        }
        ImportJournal durable = readImportJournalOrNull();
        if (!matchesImportJournal(durable, staged.key, staged.byteCount)) {
            blockImportRecovery(
                "The managed EPUB association could not be recovered");
            throw new IOException(lastError);
        }
        loadStatus = LoadStatus.IMPORT_ASSOCIATION_PENDING;
        lastError =
            "A validated EPUB still needs a durable Library association";
        activeStagedImport = null;
        Book existing = findBook(staged.key);
        return new Book(destination,
                        staged.key,
                        staged.byteCount,
                        true,
                        false,
                        true,
                        existing == null ? staged.title : existing.title,
                        existing == null ? staged.addedTime : existing.addedTime,
                        existing == null ? 0 : existing.lastOpenedTime,
                        existing != null && existing.hasPosition,
                        existing == null ? 0 : existing.spineIndex,
                        existing == null ? 0 : existing.byteOffset);
    }

    boolean completeImportedCatalogAssociation(Book managed) {
        if (!hasExactCatalogAssociationShape(managed)) {
            return false;
        }
        ImportJournal journal;
        try {
            journal = readImportJournalOrNull();
        } catch (IOException exception) {
            blockImportRecovery(
                "The EPUB import journal is invalid and was preserved");
            return false;
        }
        if (journal == null) {
            return true;
        }
        if (!matchesImportJournal(
                journal, managed.key, managed.byteCount)) {
            blockImportRecovery(
                "The EPUB import journal conflicts with the local Library");
            return false;
        }
        if (!clearImportJournalOrBlock()) {
            return false;
        }
        verifiedPendingImport = null;
        updateLoadedStatus();
        return true;
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
        } else if (existing.imported != candidate.imported
                   || existing.byteCount != candidate.byteCount) {
            catalogSaveFailureCount += 1;
            return false;
        }

        String previousTitle = existing.title;
        long previousLastOpened = existing.lastOpenedTime;
        boolean previousRepairRequired = existing.repairRequired;
        boolean previousIdentityVerified = existing.identityVerified;
        if (existing != candidate
            && pendingImportMatchesBook(candidate)) {
            existing.repairRequired = false;
            existing.identityVerified = true;
        }
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
            existing.repairRequired = previousRepairRequired;
            existing.identityVerified = previousIdentityVerified;
        }
        sortBooks();
        return false;
    }

    boolean recordValidatedPendingImport(Book candidate,
                                         String readerTitle) {
        if (candidate == null
            || candidate.repairRequired
            || !candidate.identityVerified
            || verifiedPendingImport != candidate
            || !pendingImportMatchesBook(candidate)
            || !validTitle(boundedTitle(readerTitle))) {
            catalogSaveFailureCount += 1;
            return false;
        }
        Book existing = findBook(candidate.key);
        boolean created = existing == null;
        if (created) {
            if (books.size() >= CATALOG_ENTRY_CAP) {
                catalogSaveFailureCount += 1;
                lastError = "The Library is full";
                return false;
            }
            existing = new Book(candidate.file,
                                candidate.key,
                                candidate.byteCount,
                                true,
                                false,
                                true,
                                boundedTitle(readerTitle),
                                candidate.addedTime,
                                0,
                                false,
                                0,
                                0);
            books.add(existing);
        } else if (!existing.imported
                   || existing.byteCount != candidate.byteCount) {
            catalogSaveFailureCount += 1;
            lastError =
                "The pending EPUB conflicts with the local Library";
            return false;
        }

        String previousTitle = existing.title;
        boolean previousRepairRequired = existing.repairRequired;
        boolean previousIdentityVerified = existing.identityVerified;
        existing.title = boundedTitle(readerTitle);
        existing.repairRequired = false;
        existing.identityVerified = true;
        sortBooks();
        if (saveCatalog()) {
            return true;
        }
        if (created) {
            books.remove(existing);
        } else {
            existing.title = previousTitle;
            existing.repairRequired = previousRepairRequired;
            existing.identityVerified = previousIdentityVerified;
        }
        sortBooks();
        return false;
    }

    boolean recordTransferredBook(String key,
                                  long byteCount,
                                  String readerTitle) {
        if (blockedForMutation()
            || !validKey(key)
            || byteCount <= 0
            || byteCount > DOCUMENT_FILE_CAP) {
            catalogSaveFailureCount += 1;
            return false;
        }
        File managed = new File(documentDirectory, key + ".epub");
        if (!validManagedFile(managed, key, byteCount)) {
            catalogSaveFailureCount += 1;
            lastError = "The transferred EPUB does not match its digest";
            return false;
        }

        Book existing = findBook(key);
        boolean created = existing == null;
        if (created) {
            if (books.size() >= CATALOG_ENTRY_CAP) {
                catalogSaveFailureCount += 1;
                lastError = "The Library is full";
                return false;
            }
            existing = new Book(managed,
                                key,
                                byteCount,
                                true,
                                false,
                                true,
                                boundedTitle(readerTitle),
                                System.currentTimeMillis(),
                                0,
                                false,
                                0,
                                0);
            books.add(existing);
        } else if (!existing.imported
                   || existing.byteCount != byteCount
                   || !existing.file.equals(managed)) {
            catalogSaveFailureCount += 1;
            lastError = "The transferred EPUB conflicts with the local Library";
            return false;
        }

        String previousTitle = existing.title;
        boolean previousRepairRequired = existing.repairRequired;
        boolean previousIdentityVerified = existing.identityVerified;
        existing.repairRequired = false;
        existing.identityVerified = true;
        existing.title = boundedTitle(readerTitle);
        sortBooks();
        if (saveCatalog()) {
            return true;
        }
        if (created) {
            books.remove(existing);
        } else {
            existing.title = previousTitle;
            existing.repairRequired = previousRepairRequired;
            existing.identityVerified = previousIdentityVerified;
        }
        sortBooks();
        return false;
    }

    private boolean recordValidatedTransferredBook(Book expected,
                                                   String readerTitle) {
        lastTransferredRecordProofFailed = false;
        if (blockedForMutation()) {
            clearTransferredBookVerification(true);
            catalogSaveFailureCount += 1;
            return false;
        }
        Book current = expected == null
            ? null : findBook(expected.key);
        File managed = expected == null
            ? null : managedFile(expected.key);
        String title = boundedTitle(readerTitle);
        if (expected == null
            || expected != verifiedTransferredBook
            || !expected.imported
            || expected.repairRequired
            || !expected.identityVerified
            || managed == null
            || expected.byteCount <= 0
            || expected.byteCount > DOCUMENT_FILE_CAP
            || !validManagedFileShape(expected.file, expected.byteCount)
            || verifiedTransferredLease == null
            || !verifiedTransferredLease.matches(
                expected.file, expected.byteCount)
            || !expected.file.getAbsoluteFile().equals(
                managed.getAbsoluteFile())
            || !validTitle(title)) {
            lastTransferredRecordProofFailed = true;
            clearTransferredBookVerification(true);
            catalogSaveFailureCount += 1;
            return false;
        }
        boolean created = current == null;
        if (created) {
            if (books.size() >= CATALOG_ENTRY_CAP) {
                clearTransferredBookVerification(true);
                catalogSaveFailureCount += 1;
                lastError = "The Library is full";
                return false;
            }
            current = new Book(
                managed, expected.key, expected.byteCount,
                true, false, true, title,
                System.currentTimeMillis(), 0, false, 0, 0);
            books.add(current);
        } else if (current != expected
                   || !current.imported
                   || current.repairRequired
                   || !current.identityVerified
                   || current.byteCount != expected.byteCount
                   || !current.file.getAbsoluteFile().equals(
                       managed.getAbsoluteFile())) {
            lastTransferredRecordProofFailed = true;
            clearTransferredBookVerification(true);
            catalogSaveFailureCount += 1;
            return false;
        }

        if (!created && current.title.equals(title)) {
            clearTransferredBookVerification(false);
            return true;
        }
        clearTransferredBookVerification(false);

        // verifyBookIdentityStep() has just streamed the exact managed file
        // and marked this exact in-store capability verified. Persist only
        // the Reader0-validated projection; do not synchronously hash the
        // same up-to-512 MiB file a second time or advance last-opened state.
        String previousTitle = current.title;
        current.title = title;
        sortBooks();
        if (saveCatalog()) {
            if (created) {
                expected.identityVerified = false;
            }
            return true;
        }
        if (created) {
            books.remove(current);
        } else {
            current.title = previousTitle;
            current.identityVerified = false;
        }
        expected.identityVerified = false;
        sortBooks();
        return false;
    }

    TransferredBookStepStatus verifyAndRecordTransferredBookStep(
        Book expected, String readerTitle, int maximumBytes) {
        if (expected == null || expected != activeTransferredBook) {
            cancelBookIdentityVerification();
            return TransferredBookStepStatus.STALE;
        }
        if (blockedForMutation()) {
            cancelBookIdentityVerification();
            return TransferredBookStepStatus.CATALOG_RETRY;
        }
        IdentityCheckStatus status =
            verifyBookIdentityStep(expected, maximumBytes);
        if (status == IdentityCheckStatus.PENDING) {
            return TransferredBookStepStatus.PENDING;
        }
        if (status != IdentityCheckStatus.VERIFIED) {
            return blockedForMutation()
                ? TransferredBookStepStatus.CATALOG_RETRY
                : TransferredBookStepStatus.IDENTITY_FAILED;
        }
        if (recordValidatedTransferredBook(expected, readerTitle)) {
            return TransferredBookStepStatus.COMPLETED;
        }
        return lastTransferredRecordProofFailed
            ? TransferredBookStepStatus.IDENTITY_FAILED
            : TransferredBookStepStatus.CATALOG_RETRY;
    }

    boolean hasExactManagedBook(String key, long byteCount) {
        Book book = findBook(key);
        return book != null
            && book.imported
            && book.byteCount == byteCount
            && validManagedFile(book.file, key, byteCount);
    }

    boolean verifyManagedFile(String key, long byteCount) {
        if (!validKey(key)
            || byteCount <= 0
            || byteCount > DOCUMENT_FILE_CAP) {
            return false;
        }
        return validManagedFile(
            new File(documentDirectory, key + ".epub"), key, byteCount);
    }

    boolean verifyBookIdentity(Book requested) {
        for (;;) {
            IdentityCheckStatus status =
                verifyBookIdentityStep(requested, 4 * 1024 * 1024);
            if (status != IdentityCheckStatus.PENDING) {
                return status == IdentityCheckStatus.VERIFIED;
            }
        }
    }

    IdentityCheckStatus verifyBookIdentityStep(Book requested,
                                               int maximumBytes) {
        Book current = requested == null ? null : findBook(requested.key);
        Book book = (isStagedImport(requested)
                     || pendingImportMatchesBook(requested)
                     || requested == activeTransferredBook)
            ? requested
            : current == null ? requested : current;
        if (book == null || book.repairRequired || maximumBytes <= 0) {
            cancelBookIdentityVerification();
            return IdentityCheckStatus.FAILED;
        }
        if (book.identityVerified) {
            closeIdentityCheck();
            return IdentityCheckStatus.VERIFIED;
        }
        if (!validManagedFileShape(book.file, book.byteCount)) {
            failIdentityCheck(book);
            return IdentityCheckStatus.FAILED;
        }
        if (identityCheck == null || identityCheck.book != book) {
            closeIdentityCheck();
            verifiedPendingImport = null;
            if (book != activeTransferredBook) {
                clearTransferredBookVerification(true);
            }
            try {
                identityCheck = openIdentityCheck(book);
            } catch (IOException | RuntimeException exception) {
                failIdentityCheck(book);
                return IdentityCheckStatus.FAILED;
            }
        }
        if (!identityCheck.lease.matches(book.file, book.byteCount)) {
            failIdentityCheck(book);
            return IdentityCheckStatus.FAILED;
        }

        byte[] buffer = new byte[32 * 1024];
        int remaining = maximumBytes;
        try {
            while (remaining > 0 && identityCheck.byteCount < book.byteCount) {
                int request = Math.min(buffer.length, remaining);
                request = (int)Math.min(
                    (long)request, book.byteCount - identityCheck.byteCount);
                int count = identityCheck.input.read(buffer, 0, request);
                if (count < 0) {
                    failIdentityCheck(book);
                    return IdentityCheckStatus.FAILED;
                }
                if (count == 0) {
                    continue;
                }
                identityCheck.digest.update(buffer, 0, count);
                identityCheck.byteCount += count;
                remaining -= count;
            }
            if (!identityCheck.lease.matches(book.file, book.byteCount)) {
                failIdentityCheck(book);
                return IdentityCheckStatus.FAILED;
            }
            if (identityCheck.byteCount < book.byteCount) {
                return IdentityCheckStatus.PENDING;
            }
            if (identityCheck.input.read() != -1
                || !identityCheck.lease.matches(book.file, book.byteCount)
                || !hex(identityCheck.digest.digest()).equals(book.key)) {
                failIdentityCheck(book);
                return IdentityCheckStatus.FAILED;
            }
            IdentityFileLease completedLease = identityCheck.lease;
            closeIdentityCheck();
            book.identityVerified = true;
            if (isStagedImport(book)
                || pendingImportMatchesBook(book)) {
                verifiedPendingImport = book;
            }
            if (book == activeTransferredBook) {
                verifiedTransferredBook = book;
                verifiedTransferredLease = completedLease;
            }
            return IdentityCheckStatus.VERIFIED;
        } catch (IOException | RuntimeException exception) {
            failIdentityCheck(book);
            return IdentityCheckStatus.FAILED;
        }
    }

    void cancelBookIdentityVerification() {
        closeIdentityCheck();
        verifiedPendingImport = null;
        clearTransferredBookVerification(true);
    }

    File managedFile(String key) {
        if (!validKey(key)) {
            return null;
        }
        return new File(documentDirectory, key + ".epub");
    }

    TransferredBookOutcome transferredBookForIdentityVerification(
        String key, long byteCount) {
        if (blockedForMutation()) {
            cancelBookIdentityVerification();
            return transferredBookOutcome(
                TransferredBookStatus.CATALOG_BLOCKED, null);
        }
        if (!validKey(key)
            || byteCount <= 0
            || byteCount > DOCUMENT_FILE_CAP) {
            cancelBookIdentityVerification();
            return transferredBookOutcome(
                TransferredBookStatus.LOCAL_CONFLICT, null);
        }
        cancelBookIdentityVerification();
        Book current = findBook(key);
        if (current != null) {
            if (!current.imported) {
                return transferredBookOutcome(
                    TransferredBookStatus.LOCAL_CONFLICT, null);
            }
            current.identityVerified = false;
            if (current.repairRequired
                || current.byteCount != byteCount
                || !current.file.getAbsoluteFile().equals(
                    managedFile(key).getAbsoluteFile())) {
                return transferredBookOutcome(
                    TransferredBookStatus.LOCAL_CONFLICT, null);
            }
            if (!validManagedFileShape(current.file, byteCount)) {
                return transferredBookOutcome(
                    TransferredBookStatus.BYTES_UNAVAILABLE, null);
            }
            activeTransferredBook = current;
            return transferredBookOutcome(
                TransferredBookStatus.READY, current);
        }
        if (books.size() >= CATALOG_ENTRY_CAP) {
            lastError = "The Library is full";
            return transferredBookOutcome(
                TransferredBookStatus.CATALOG_FULL, null);
        }
        File managed = managedFile(key);
        if (!validManagedFileShape(managed, byteCount)) {
            return transferredBookOutcome(
                TransferredBookStatus.BYTES_UNAVAILABLE, null);
        }
        activeTransferredBook = new Book(
            managed, key, byteCount,
            true, false, false, "Imported EPUB",
            0, 0, false, 0, 0);
        return transferredBookOutcome(
            TransferredBookStatus.READY, activeTransferredBook);
    }

    private static TransferredBookOutcome transferredBookOutcome(
        TransferredBookStatus status, Book book) {
        return new TransferredBookOutcome(status, book);
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

    boolean removeBookRecordOnly(String key) {
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
        removeSuccessCount += 1;
        return true;
    }

    boolean discardUncataloged(Book book) {
        if (!isStagedImport(book)
            || hasPendingImportAssociation(book.key, book.byteCount)) {
            return false;
        }
        return deleteImportStagingOrBlock();
    }

    boolean discardPendingImportAssociation(Book expected) {
        if (loadStatus != LoadStatus.IMPORT_ASSOCIATION_PENDING
            || expected == null
            || expected.file == null
            || !expected.imported
            || !validKey(expected.key)
            || expected.byteCount <= 0
            || expected.byteCount > DOCUMENT_FILE_CAP) {
            return false;
        }
        ImportJournal journal;
        try {
            journal = readImportJournalOrNull();
        } catch (IOException | RuntimeException exception) {
            blockImportRecovery(
                "The EPUB import journal is invalid and was preserved");
            return false;
        }
        if (!matchesImportJournal(
                journal, expected.key, expected.byteCount)) {
            return false;
        }
        File destination = managedFile(expected.key);
        if (destination == null
            || !destination.getAbsoluteFile().equals(
                expected.file.getAbsoluteFile())) {
            return false;
        }

        Book existing = findBook(expected.key);
        if (hasExactCatalogAssociationShape(
                existing, expected.key, expected.byteCount)) {
            // A completed healthy association owns these bytes. Its journal
            // is finalized through completeImportedCatalogAssociation(), not
            // through the destructive recovery action.
            return false;
        }
        if (existing != null
            && (!existing.imported
                || existing.byteCount != expected.byteCount)) {
            blockImportRecovery(
                "The pending EPUB conflicts with the local Library");
            return false;
        }

        if (existing != null && !existing.repairRequired) {
            // A row that was healthy when the recovery surface opened may
            // become missing or unequal before the destructive action. Make
            // that surviving O6 association durably non-openable first; if
            // publication cannot be proved, retain both bytes and journal.
            boolean previousIdentityVerified = existing.identityVerified;
            existing.repairRequired = true;
            existing.identityVerified = false;
            if (!saveCatalog()) {
                existing.repairRequired = false;
                existing.identityVerified = previousIdentityVerified;
                return false;
            }
        }

        // Delete (or prove absent) the one digest-derived managed path before
        // clearing the journal. Every process-death cut is therefore safe:
        // a surviving journal can retry an absent destination, while no
        // unjournaled managed EPUB can be orphaned by this operation.
        if (destination.exists() && !destination.delete()) {
            loadStatus = LoadStatus.IMPORT_ASSOCIATION_PENDING;
            lastError =
                "The rejected EPUB could not be discarded; Retry is required";
            return false;
        }
        if (destination.exists()) {
            loadStatus = LoadStatus.IMPORT_ASSOCIATION_PENDING;
            lastError =
                "The rejected EPUB could not be proven absent; Retry is required";
            return false;
        }

        File temporary = importJournalTemporaryFile();
        if ((temporary.exists() && !temporary.delete())
            || (importJournalFile.exists() && !importJournalFile.delete())) {
            loadStatus = LoadStatus.IMPORT_ASSOCIATION_PENDING;
            lastError =
                "The rejected EPUB was removed, but its recovery journal still needs Retry";
            return false;
        }
        cancelBookIdentityVerification();
        activeStagedImport = null;
        updateLoadedStatus();
        return true;
    }

    Session sessionFor(Book book) {
        Book current = book == null ? null : findBook(book.key);
        return current == null || current.repairRequired
            ? null : new Session(current);
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

    LoadStatus loadStatus() {
        return loadStatus;
    }

    String lastError() {
        return lastError;
    }

    boolean mutationBlocked() {
        return blockedForMutation();
    }

    int bookCount() {
        return books.size();
    }

    File catalogFileForTesting() {
        return catalogFile;
    }

    File catalogQuarantineFileForTesting(int slot) {
        if (slot < 1 || slot > CATALOG_QUARANTINE_SLOTS) {
            throw new IllegalArgumentException();
        }
        return new File(rootDirectory, CATALOG_QUARANTINE_PREFIX + slot);
    }

    File documentDirectoryForTesting() {
        return documentDirectory;
    }

    File importStagingFileForTesting() {
        return importStagingFile;
    }

    File importJournalFileForTesting() {
        return importJournalFile;
    }

    File importJournalTemporaryFileForTesting() {
        return importJournalTemporaryFile();
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

    void failNextCatalogMoveAfterReplaceForTesting() {
        failNextCatalogMoveAfterReplaceForTesting = true;
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

    boolean hasPendingImportAssociation(String key, long byteCount) {
        if (!validKey(key)
            || byteCount <= 0
            || byteCount > DOCUMENT_FILE_CAP) {
            return false;
        }
        try {
            return matchesImportJournal(
                readImportJournalOrNull(), key, byteCount);
        } catch (IOException | RuntimeException exception) {
            blockImportRecovery(
                "The EPUB import journal is invalid and was preserved");
            return false;
        }
    }

    Book pendingImportedBook() {
        if (loadStatus == LoadStatus.IMPORT_RECOVERY_BLOCKED
            || catalogStateBlocksImportReconciliation()
            || importStagingFile.exists()) {
            return null;
        }
        ImportJournal journal;
        try {
            journal = readImportJournalOrNull();
        } catch (IOException | RuntimeException exception) {
            blockImportRecovery(
                "The EPUB import journal is invalid and was preserved");
            return null;
        }
        if (journal == null) {
            return null;
        }
        Book existing = findBook(journal.key);
        if (hasExactCatalogAssociationShape(
                existing, journal.key, journal.byteCount)) {
            return null;
        }
        if (existing != null
            && (!existing.imported
                || existing.byteCount != journal.byteCount)) {
            blockImportRecovery(
                "The pending EPUB conflicts with the local Library");
            return null;
        }
        File destination = managedFile(journal.key);
        if (destination == null) {
            blockImportRecovery("The pending EPUB identity is invalid");
            return null;
        }
        return new Book(destination,
                        journal.key,
                        journal.byteCount,
                        true,
                        false,
                        false,
                        existing == null ? "Imported EPUB" : existing.title,
                        existing == null
                            ? System.currentTimeMillis() : existing.addedTime,
                        existing == null ? 0 : existing.lastOpenedTime,
                        existing != null && existing.hasPosition,
                        existing == null ? 0 : existing.spineIndex,
                        existing == null ? 0 : existing.byteOffset);
    }

    private void reconcileImportOnLoad() {
        File journalTemporary = importJournalTemporaryFile();
        if (journalTemporary.exists() && !journalTemporary.delete()) {
            blockImportRecovery(
                "The interrupted EPUB import journal could not be cleared");
            return;
        }

        ImportJournal journal;
        try {
            journal = readImportJournalOrNull();
        } catch (IOException | RuntimeException exception) {
            blockImportRecovery(
                "The EPUB import journal is invalid and was preserved");
            return;
        }
        if (journal == null) {
            if (importStagingFile.exists()) {
                deleteImportStagingOrBlock();
            }
            return;
        }
        if (catalogStateBlocksImportReconciliation()) {
            blockImportRecovery(
                "The pending EPUB import was preserved because the local Library is blocked");
            return;
        }

        Book associated = findBook(journal.key);
        if (hasExactCatalogAssociationShape(
                associated, journal.key, journal.byteCount)) {
            if (importStagingFile.exists()
                && !deleteImportStagingOrBlock()) {
                return;
            }
            if (clearImportJournalOrBlock()) {
                updateLoadedStatus();
            }
            return;
        }

        if (journal.phase == IMPORT_PHASE_READY_TO_PUBLISH
            && importStagingFile.exists()) {
            // The durable intent preceded the managed atomic move. When the
            // fixed staging name still exists, publication was not proved;
            // discard only that nonauthoritative bounded file and leave any
            // preexisting digest destination untouched.
            if (deleteImportStagingOrBlock()) {
                clearImportJournalOrBlock();
            }
            return;
        }

        // The journal proves which bounded path needs attention, but file
        // shape alone is never identity proof. Do not synchronously hash up
        // to 512 MiB on startup and do not manufacture an O6 row. Activity
        // explicitly resumes the incremental SHA-256 + Reader0 validation.
        if (importStagingFile.exists()
            && !deleteImportStagingOrBlock()) {
            return;
        }
        if (associated != null
            && (!associated.imported
                || associated.byteCount != journal.byteCount)) {
            blockImportRecovery(
                "The recovered EPUB conflicts with the local Library");
            return;
        }
        loadStatus = LoadStatus.IMPORT_ASSOCIATION_PENDING;
        lastError =
            "A validated EPUB still needs identity and Library association Retry";
    }

    private boolean pendingImportMatchesBook(Book book) {
        if (book == null || !book.imported) {
            return false;
        }
        try {
            File destination = managedFile(book.key);
            return destination != null
                && destination.getCanonicalFile().equals(
                    book.file.getCanonicalFile())
                && validManagedFileShape(destination, book.byteCount)
                && matchesImportJournal(
                    readImportJournalOrNull(), book.key, book.byteCount);
        } catch (IOException | RuntimeException exception) {
            blockImportRecovery(
                "The EPUB import journal is invalid and was preserved");
            return false;
        }
    }

    private boolean hasExactCatalogAssociationShape(Book candidate) {
        return candidate != null
            && hasExactCatalogAssociationShape(
                findBook(candidate.key), candidate.key, candidate.byteCount);
    }

    private boolean hasExactCatalogAssociationShape(Book book,
                                                     String key,
                                                     long byteCount) {
        if (book == null
            || !book.imported
            || book.repairRequired
            || !key.equals(book.key)
            || book.byteCount != byteCount
            || !validManagedFileShape(book.file, byteCount)) {
            return false;
        }
        File expected = managedFile(key);
        try {
            return expected != null
                && expected.getCanonicalFile().equals(
                    book.file.getCanonicalFile());
        } catch (IOException | RuntimeException exception) {
            return false;
        }
    }

    private ImportJournal readImportJournalOrNull() throws IOException {
        if (!importJournalFile.exists()) {
            return null;
        }
        byte[] bytes = readBoundedBytes(
            importJournalFile, IMPORT_JOURNAL_CAP);
        if (bytes == null || bytes.length < 8) {
            throw new IOException("Invalid EPUB import journal bounds");
        }
        try (DataInputStream prefix = new DataInputStream(
                 new ByteArrayInputStream(bytes))) {
            int magic = prefix.readInt();
            int version = prefix.readInt();
            if (magic == IMPORT_JOURNAL_MAGIC
                && Integer.compareUnsigned(
                    version, IMPORT_JOURNAL_VERSION) > 0) {
                throw new IOException(
                    "A newer EPUB import journal was preserved");
            }
        }
        if (bytes.length != IMPORT_JOURNAL_BYTES) {
            throw new IOException("Invalid EPUB import journal length");
        }

        CRC32 crc = new CRC32();
        crc.update(bytes, 0, bytes.length - 4);
        try (DataInputStream input = new DataInputStream(
                 new ByteArrayInputStream(bytes))) {
            int magic = input.readInt();
            int version = input.readInt();
            int phase = input.readInt();
            byte[] keyBytes = new byte[64];
            input.readFully(keyBytes);
            String key = new String(keyBytes, StandardCharsets.US_ASCII);
            long byteCount = input.readLong();
            int expectedCrc = input.readInt();
            if (input.read() != -1
                || magic != IMPORT_JOURNAL_MAGIC
                || version != IMPORT_JOURNAL_VERSION
                || (phase != IMPORT_PHASE_READY_TO_PUBLISH
                    && phase != IMPORT_PHASE_AWAITING_CATALOG)
                || !validKey(key)
                || byteCount <= 0
                || byteCount > DOCUMENT_FILE_CAP
                || expectedCrc != (int)crc.getValue()) {
                throw new IOException("Invalid EPUB import journal");
            }
            return new ImportJournal(key, byteCount, phase);
        }
    }

    private void publishImportJournal(String key,
                                      long byteCount,
                                      int phase) throws IOException {
        byte[] candidate = encodeImportJournal(key, byteCount, phase);
        byte[] prior = readBoundedBytes(importJournalFile, IMPORT_JOURNAL_CAP);
        boolean priorMissing = !importJournalFile.exists();
        if (!priorMissing) {
            ImportJournal current;
            try {
                current = readImportJournalOrNull();
            } catch (IOException | RuntimeException exception) {
                blockImportRecovery(
                    "The EPUB import journal is invalid and was preserved");
                throw new IOException(lastError, exception);
            }
            if (!matchesImportJournal(current, key, byteCount)) {
                blockImportRecovery(
                    "The EPUB import journal conflicts with the selected book");
                throw new IOException(lastError);
            }
        }

        File temporary = importJournalTemporaryFile();
        if (temporary.exists() && !temporary.delete()) {
            blockImportRecovery(
                "The EPUB import journal staging file could not be cleared");
            throw new IOException(lastError);
        }
        try (FileOutputStream output =
                 new FileOutputStream(temporary, false)) {
            output.write(candidate);
            output.getFD().sync();
            publishAtomically(temporary, importJournalFile);
        } catch (IOException | RuntimeException exception) {
            boolean temporaryCleared =
                !temporary.exists() || temporary.delete();
            if (fileEquals(
                    importJournalFile, candidate, IMPORT_JOURNAL_CAP)) {
                return;
            }
            boolean priorRemains = priorMissing
                ? !importJournalFile.exists()
                : fileEquals(importJournalFile, prior, IMPORT_JOURNAL_CAP);
            if (!priorRemains || !temporaryCleared) {
                blockImportRecovery(
                    "The EPUB import journal publication outcome is uncertain");
            }
            throw new IOException(
                lastError == null
                    ? "Unable to publish the EPUB import journal"
                    : lastError,
                exception);
        }
    }

    private static byte[] encodeImportJournal(String key,
                                              long byteCount,
                                              int phase) throws IOException {
        if (!validKey(key)
            || byteCount <= 0
            || byteCount > DOCUMENT_FILE_CAP
            || (phase != IMPORT_PHASE_READY_TO_PUBLISH
                && phase != IMPORT_PHASE_AWAITING_CATALOG)) {
            throw new IOException("Invalid EPUB import journal state");
        }
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream();
             DataOutputStream output = new DataOutputStream(bytes)) {
            output.writeInt(IMPORT_JOURNAL_MAGIC);
            output.writeInt(IMPORT_JOURNAL_VERSION);
            output.writeInt(phase);
            output.write(key.getBytes(StandardCharsets.US_ASCII));
            output.writeLong(byteCount);
            output.flush();
            byte[] prefix = bytes.toByteArray();
            CRC32 crc = new CRC32();
            crc.update(prefix);
            output.writeInt((int)crc.getValue());
            output.flush();
            byte[] result = bytes.toByteArray();
            if (result.length != IMPORT_JOURNAL_BYTES) {
                throw new IOException("Invalid EPUB import journal length");
            }
            return result;
        }
    }

    private static boolean matchesImportJournal(ImportJournal journal,
                                                String key,
                                                long byteCount) {
        return journal != null
            && journal.key.equals(key)
            && journal.byteCount == byteCount;
    }

    private File importJournalTemporaryFile() {
        return new File(rootDirectory, IMPORT_JOURNAL_TEMPORARY_FILE);
    }

    private boolean clearImportJournalOrBlock() {
        File temporary = importJournalTemporaryFile();
        if (temporary.exists() && !temporary.delete()) {
            blockImportRecovery(
                "The EPUB import journal staging file could not be cleared");
            return false;
        }
        if (importJournalFile.exists() && !importJournalFile.delete()) {
            blockImportRecovery(
                "The completed EPUB import journal could not be cleared");
            return false;
        }
        return true;
    }

    private boolean deleteImportStagingOrBlock() {
        if (!importStagingFile.exists() || importStagingFile.delete()) {
            activeStagedImport = null;
            return true;
        }
        blockImportRecovery(
            "The fixed EPUB import staging file could not be cleared");
        return false;
    }

    private boolean catalogStateBlocksImportReconciliation() {
        return loadStatus == LoadStatus.FUTURE_VERSION_BLOCKED
            || loadStatus == LoadStatus.CORRUPT_BLOCKED
            || loadStatus == LoadStatus.PUBLISH_UNCERTAIN_BLOCKED;
    }

    private void blockImportRecovery(String message) {
        loadStatus = LoadStatus.IMPORT_RECOVERY_BLOCKED;
        lastError = message;
    }

    private boolean saveCatalog() {
        if (blockedForMutation()
            || books.isEmpty()
            || books.size() > CATALOG_ENTRY_CAP) {
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
                if (!validCatalogBook(book)) {
                    throw new IOException("Invalid Port 6 catalog state");
                }
                output.writeBoolean(book.imported);
                output.writeBoolean(book.repairRequired);
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

        byte[] prior = readBoundedBytes(catalogFile, CATALOG_FILE_CAP);
        boolean priorMissing = !catalogFile.exists();
        File temporary = new File(rootDirectory, CATALOG_TEMPORARY_FILE);
        boolean injectedUncertainReplace = false;
        try (FileOutputStream output = new FileOutputStream(temporary, false)) {
            output.write(bytes);
            output.getFD().sync();
            publishAtomically(temporary, catalogFile);
            if (failNextCatalogMoveAfterReplaceForTesting) {
                failNextCatalogMoveAfterReplaceForTesting = false;
                injectedUncertainReplace = true;
                throw new IOException(
                    "Injected uncertain Port 6 catalog replace result");
            }
        } catch (IOException | RuntimeException exception) {
            temporary.delete();
            if (injectedUncertainReplace) {
                loadStatus = LoadStatus.PUBLISH_UNCERTAIN_BLOCKED;
                lastError = CATALOG_PUBLISH_UNCERTAIN_ERROR;
                catalogSaveFailureCount += 1;
                return false;
            }
            if (fileEquals(catalogFile, bytes, CATALOG_FILE_CAP)) {
                updateLoadedStatus();
                catalogSaveSuccessCount += 1;
                return true;
            }
            if (!(priorMissing && !catalogFile.exists())
                && (prior == null
                    || !fileEquals(catalogFile, prior, CATALOG_FILE_CAP))) {
                loadStatus = LoadStatus.PUBLISH_UNCERTAIN_BLOCKED;
                lastError = CATALOG_PUBLISH_UNCERTAIN_ERROR;
            }
            catalogSaveFailureCount += 1;
            return false;
        }
        updateLoadedStatus();
        catalogSaveSuccessCount += 1;
        return true;
    }

    private void updateLoadedStatus() {
        if (loadStatus == LoadStatus.IMPORT_RECOVERY_BLOCKED) {
            return;
        }
        if (importJournalFile.exists()) {
            try {
                if (readImportJournalOrNull() != null) {
                    loadStatus = LoadStatus.IMPORT_ASSOCIATION_PENDING;
                    lastError =
                        "A validated EPUB still needs a durable Library association";
                    return;
                }
            } catch (IOException | RuntimeException exception) {
                blockImportRecovery(
                    "The EPUB import journal is invalid and was preserved");
                return;
            }
        }
        for (Book book : books) {
            if (book.repairRequired) {
                loadStatus = LoadStatus.LOADED_WITH_REPAIR;
                lastError = "One or more Library books need repair";
                return;
            }
        }
        if (hasCatalogQuarantine()) {
            loadStatus = LoadStatus.CORRUPT_QUARANTINED;
            lastError =
                "An earlier invalid Library catalog remains quarantined";
        } else {
            loadStatus = LoadStatus.LOADED;
            lastError = null;
        }
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
            if (!fileHasIdentity(source, key, byteCount)) {
                throw new IOException("Port 5 managed EPUB is unavailable");
            }
            File destination = new File(documentDirectory, key + ".epub");
            if (!validManagedFile(destination, key, byteCount)) {
                copyFileAtomically(source, destination, key, byteCount);
            }
            if (!validManagedFile(destination, key, byteCount)) {
                throw new IOException(
                    "Migrated EPUB failed identity verification");
            }
            long now = System.currentTimeMillis();
            books.add(new Book(destination,
                               key,
                               byteCount,
                               true,
                               false,
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
        if (!fileHasIdentity(fixture,
                             OctavoFixture.SHA256,
                             OctavoFixture.BYTE_COUNT)) {
            throw new IllegalStateException("Port 6 fixture identity is invalid");
        }
        return new Book(fixture,
                        OctavoFixture.SHA256,
                        OctavoFixture.BYTE_COUNT,
                        false,
                        false,
                        true,
                        OctavoFixture.TITLE,
                        0,
                        0,
                        false,
                        0,
                        0);
    }

    private boolean validBook(Book book) {
        if (book == null
            || book.repairRequired
            || !book.identityVerified
            || !validKey(book.key)
            || book.byteCount <= 0
            || book.byteCount > DOCUMENT_FILE_CAP
            || !validTitle(book.title)
            || book.addedTime < 0
            || book.lastOpenedTime < 0
            || book.spineIndex < 0
            || book.spineIndex > 0xFFFFFFFFL
            || book.byteOffset < 0
            || !validManagedFileShape(book.file, book.byteCount)) {
            return false;
        }
        if (!book.imported) {
            return book.key.equals(OctavoFixture.SHA256)
                && book.byteCount == OctavoFixture.BYTE_COUNT
                && fileHasIdentity(book.file,
                                   OctavoFixture.SHA256,
                                   OctavoFixture.BYTE_COUNT);
        }
        return validManagedFileShape(book.file, book.byteCount);
    }

    private boolean validCatalogBook(Book book) {
        if (book == null
            || !validKey(book.key)
            || book.byteCount <= 0
            || book.byteCount > DOCUMENT_FILE_CAP
            || !validTitle(book.title)
            || book.addedTime < 0
            || book.lastOpenedTime < 0
            || book.spineIndex < 0
            || book.spineIndex > 0xFFFFFFFFL
            || book.byteOffset < 0) {
            return false;
        }
        if (!book.imported) {
            return !book.repairRequired
                && book.key.equals(OctavoFixture.SHA256)
                && book.byteCount == OctavoFixture.BYTE_COUNT
                && fileHasIdentity(book.file,
                                   OctavoFixture.SHA256,
                                   OctavoFixture.BYTE_COUNT);
        }
        try {
            return documentDirectory.getCanonicalFile().equals(
                book.file.getCanonicalFile().getParentFile())
                && book.file.getName().equals(book.key + ".epub")
                && (book.repairRequired
                    || validManagedFileShape(book.file, book.byteCount));
        } catch (IOException | RuntimeException exception) {
            return false;
        }
    }

    private void recoverCorruptCatalog(Book fixtureBook) {
        books.clear();
        books.add(fixtureBook);
        File temporary = new File(rootDirectory, CATALOG_TEMPORARY_FILE);
        if (temporary.exists()) {
            temporary.delete();
        }
        if (quarantineCorruptCatalog()) {
            loadStatus = LoadStatus.MISSING;
            lastError = null;
            if (saveCatalog()) {
                loadStatus = LoadStatus.CORRUPT_QUARANTINED;
                lastError =
                    "The invalid local Library catalog was quarantined and reset";
            } else if (loadStatus
                       != LoadStatus.PUBLISH_UNCERTAIN_BLOCKED) {
                loadStatus = LoadStatus.CORRUPT_BLOCKED;
                lastError =
                    "The invalid Library was quarantined, but its reset failed";
            }
        } else {
            loadStatus = LoadStatus.CORRUPT_BLOCKED;
            lastError =
                "The invalid local Library catalog could not be quarantined";
        }
    }

    private boolean quarantineCorruptCatalog() {
        if (!catalogFile.isFile()) {
            return false;
        }
        try {
            for (int index = 1;
                 index <= CATALOG_QUARANTINE_SLOTS;
                 ++index) {
                File quarantine = new File(
                    rootDirectory, CATALOG_QUARANTINE_PREFIX + index);
                if (quarantine.exists()) {
                    continue;
                }
                Files.move(catalogFile.toPath(),
                           quarantine.toPath(),
                           StandardCopyOption.ATOMIC_MOVE);
                return true;
            }
        } catch (IOException | RuntimeException exception) {
            return false;
        }
        return false;
    }

    private boolean hasCatalogQuarantine() {
        for (int index = 1; index <= CATALOG_QUARANTINE_SLOTS; ++index) {
            File quarantine = new File(
                rootDirectory, CATALOG_QUARANTINE_PREFIX + index);
            if (quarantine.exists()) {
                return true;
            }
        }
        return false;
    }

    private boolean validManagedFile(File file,
                                     String key,
                                     long byteCount) {
        try {
            return validManagedFileShape(file, byteCount)
                && documentDirectory.getCanonicalFile().equals(
                    file.getCanonicalFile().getParentFile())
                && fileHasIdentity(file, key, byteCount);
        } catch (IOException | RuntimeException exception) {
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
                                           String expectedKey,
                                           long expectedBytes)
        throws IOException {
        File temporary = new File(destination.getParentFile(),
                                  destination.getName() + ".migration.tmp");
        long copied = 0;
        MessageDigest digest = sha256Digest();
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
                    digest.update(buffer, 0, count);
                    output.write(buffer, 0, count);
                }
            }
            output.getFD().sync();
        } catch (IOException | RuntimeException exception) {
            temporary.delete();
            throw exception;
        }
        if (copied != expectedBytes
            || !hex(digest.digest()).equals(expectedKey)) {
            temporary.delete();
            throw new IOException("Legacy EPUB identity changed during migration");
        }
        try {
            publishAtomically(temporary, destination);
        } catch (IOException exception) {
            boolean candidateWon = fileHasIdentity(
                destination, expectedKey, expectedBytes);
            boolean temporaryCleared = !temporary.exists() || temporary.delete();
            if (!candidateWon || !temporaryCleared) {
                throw exception;
            }
        }
    }

    private static boolean validManagedFileShape(File file, long byteCount) {
        return file != null
            && file.isFile()
            && file.length() == byteCount;
    }

    private static void publishAtomically(File temporary, File destination)
        throws IOException {
        Files.move(temporary.toPath(),
                   destination.toPath(),
                   StandardCopyOption.ATOMIC_MOVE,
                   StandardCopyOption.REPLACE_EXISTING);
    }

    private boolean blockedForMutation() {
        return loadStatus == LoadStatus.FUTURE_VERSION_BLOCKED
            || loadStatus == LoadStatus.CORRUPT_BLOCKED
            || loadStatus == LoadStatus.PUBLISH_UNCERTAIN_BLOCKED
            || loadStatus == LoadStatus.IMPORT_RECOVERY_BLOCKED;
    }

    private void failIdentityCheck(Book book) {
        closeIdentityCheck();
        verifiedPendingImport = null;
        boolean transferredCapability = book != null
            && book == activeTransferredBook;
        Book current = book == null ? null : findBook(book.key);
        boolean mutationWasBlocked = blockedForMutation();
        clearTransferredBookVerification(true);
        if (mutationWasBlocked) {
            // A different durable mutation already has an unresolved outcome.
            // Revoke runtime identity trust, but preserve its exact typed
            // state and diagnostic and never layer another catalog write over
            // the unresolved publication.
            if (book != null) {
                book.identityVerified = false;
                book.repairRequired = true;
            }
            return;
        }
        if (transferredCapability && current != book) {
            // A published transfer that has not yet been associated with O6
            // is owned by O1BQ.  A failed proof must not manufacture a
            // phantom Port 6 repair row/status or weaken an existing blocked
            // catalog state; Activity converts the exact O1BQ attempt into
            // its durable repair-cleanup intent.
            if (book != null) {
                book.repairRequired = true;
            }
            lastError =
                "The transferred EPUB failed digest verification";
            return;
        }
        if (book != null) {
            book.identityVerified = false;
            book.repairRequired = true;
        }
        loadStatus = LoadStatus.LOADED_WITH_REPAIR;
        lastError =
            "A Library book failed digest verification and needs repair";
        if (book != null
            && findBook(book.key) != book
            && hasPendingImportAssociation(book.key, book.byteCount)) {
            loadStatus = LoadStatus.IMPORT_ASSOCIATION_PENDING;
            lastError =
                "The pending EPUB failed digest verification and needs Retry";
        } else if (book != null && findBook(book.key) == book) {
            // Port 6 v2 records the repair bit so a same-length substitution
            // cannot look healthy again after process death. A failed repair
            // publication remains visible and never accepts the bytes.
            if (!saveCatalog()) {
                if (!blockedForMutation()) {
                    loadStatus = LoadStatus.LOADED_WITH_REPAIR;
                    lastError =
                        "A Library book failed digest verification; its repair "
                        + "state could not be saved";
                }
            }
        }
    }

    private void clearTransferredBookVerification(boolean invalidate) {
        Book active = activeTransferredBook;
        Book verified = verifiedTransferredBook;
        activeTransferredBook = null;
        verifiedTransferredBook = null;
        verifiedTransferredLease = null;
        if (invalidate) {
            if (active != null) {
                active.identityVerified = false;
            }
            if (verified != null) {
                verified.identityVerified = false;
            }
        }
    }

    private void closeIdentityCheck() {
        IdentityCheck current = identityCheck;
        identityCheck = null;
        if (current != null) {
            try {
                current.input.close();
            } catch (IOException ignored) {
                // The content has not been accepted unless hashing completed.
            }
        }
    }

    private static boolean fileHasIdentity(File file,
                                           String expectedKey,
                                           long expectedBytes) {
        if (file == null
            || !validKey(expectedKey)
            || expectedBytes <= 0
            || expectedBytes > DOCUMENT_FILE_CAP
            || !file.isFile()
            || file.length() != expectedBytes) {
            return false;
        }
        MessageDigest digest = sha256Digest();
        long read = 0;
        try (FileInputStream input = new FileInputStream(file)) {
            byte[] buffer = new byte[32 * 1024];
            for (int count = input.read(buffer);
                 count >= 0;
                 count = input.read(buffer)) {
                if (count == 0) {
                    continue;
                }
                read += count;
                if (read > DOCUMENT_FILE_CAP) {
                    return false;
                }
                digest.update(buffer, 0, count);
            }
        } catch (IOException | RuntimeException exception) {
            return false;
        }
        return read == expectedBytes
            && hex(digest.digest()).equals(expectedKey);
    }

    private static byte[] readBoundedBytes(File file, int cap) {
        if (file == null || !file.isFile()
            || file.length() < 0 || file.length() > cap) {
            return null;
        }
        try {
            byte[] bytes = Files.readAllBytes(file.toPath());
            return bytes.length <= cap ? bytes : null;
        } catch (IOException | RuntimeException exception) {
            return null;
        }
    }

    private static boolean fileEquals(File file, byte[] expected, int cap) {
        if (expected == null) {
            return false;
        }
        byte[] actual = readBoundedBytes(file, cap);
        return actual != null && Arrays.equals(actual, expected);
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
