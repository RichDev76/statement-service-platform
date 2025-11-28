# "Stream Closed" Bug Analysis and Fix

## Root Cause Analysis

The `java.io.IOException: Stream Closed` error in `downloadStatementByFileName` is caused by a **critical resource management bug** in [`EncryptionService.decryptFileToStream()`](src/main/java/com/example/statementservice/service/EncryptionService.java:59).

---

## The Bug

**Location:** [`EncryptionService.java:59-76`](src/main/java/com/example/statementservice/service/EncryptionService.java:59)

```java
public InputStream decryptFileToStream(File encFile) throws IOException {
    
    try (FileInputStream encFileInputStream = new FileInputStream(encFile)) {  // ❌ BUG HERE!
        byte[] iv = new byte[INITIALIZATION_VECTOR_LENGTH];
        int read = encFileInputStream.read(iv);
        if (read != INITIALIZATION_VECTOR_LENGTH) {
            encFileInputStream.close();
            throw new IOException("Invalid encrypted file format: initialization vector missing");
        }
        SecretKeySpec keySpec = new SecretKeySpec(masterKeyProvider.getKey(), ALGORITHM_AES);
        Cipher cipher = Cipher.getInstance(ALGO);
        GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
        cipher.init(Cipher.DECRYPT_MODE, keySpec, spec);
        return new CipherInputStream(encFileInputStream, cipher);  // ❌ Returns stream
    } catch (Exception e) {  // ❌ try-with-resources closes encFileInputStream here!
        throw new IOException("Decryption failed", e);
    }
}
```

### What Happens:

1. **Line 61**: `FileInputStream` is created in try-with-resources block
2. **Line 72**: `CipherInputStream` wrapping the `FileInputStream` is returned
3. **Line 73**: try-with-resources **automatically closes** `encFileInputStream`
4. **Controller tries to read**: Stream is already closed → `IOException: Stream Closed`

---

## Execution Flow

```
1. Controller calls downloadService.validateAndStreamDetailed()
   ↓
2. DownloadService calls encryptionService.decryptFileToStream(file)
   ↓
3. EncryptionService creates FileInputStream in try-with-resources
   ↓
4. Returns CipherInputStream wrapping FileInputStream
   ↓
5. try-with-resources CLOSES FileInputStream ❌
   ↓
6. Controller tries to read from CipherInputStream
   ↓
7. ERROR: Stream Closed (underlying FileInputStream is closed)
```

---

## Why This Happens

**Try-with-resources** automatically closes resources when exiting the block:

```java
try (FileInputStream fis = new FileInputStream(file)) {
    return new CipherInputStream(fis, cipher);
} // ← fis.close() is called here automatically!
```

The `CipherInputStream` depends on the underlying `FileInputStream`, but that stream is closed before the `CipherInputStream` can be used.

---

## The Fix

### Option 1: Remove try-with-resources (Quick Fix)

```java
public InputStream decryptFileToStream(File encFile) throws IOException {
    FileInputStream encFileInputStream = null;
    try {
        encFileInputStream = new FileInputStream(encFile);
        byte[] iv = new byte[INITIALIZATION_VECTOR_LENGTH];
        int read = encFileInputStream.read(iv);
        if (read != INITIALIZATION_VECTOR_LENGTH) {
            encFileInputStream.close();
            throw new IOException("Invalid encrypted file format: initialization vector missing");
        }
        SecretKeySpec keySpec = new SecretKeySpec(masterKeyProvider.getKey(), ALGORITHM_AES);
        Cipher cipher = Cipher.getInstance(ALGO);
        GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
        cipher.init(Cipher.DECRYPT_MODE, keySpec, spec);
        
        // Return CipherInputStream - caller is responsible for closing
        return new CipherInputStream(encFileInputStream, cipher);
    } catch (Exception e) {
        // Only close if we're not returning the stream
        if (encFileInputStream != null) {
            try {
                encFileInputStream.close();
            } catch (IOException closeEx) {
                // Ignore close exception
            }
        }
        throw new IOException("Decryption failed", e);
    }
}
```

**Pros:**
- Simple fix
- Minimal code changes

**Cons:**
- Caller must close the stream (not enforced)
- Resource leak risk if caller forgets to close

---

### Option 2: Wrap in CloseableInputStream (Better)

```java
public InputStream decryptFileToStream(File encFile) throws IOException {
    FileInputStream encFileInputStream = null;
    try {
        encFileInputStream = new FileInputStream(encFile);
        byte[] iv = new byte[INITIALIZATION_VECTOR_LENGTH];
        int read = encFileInputStream.read(iv);
        if (read != INITIALIZATION_VECTOR_LENGTH) {
            encFileInputStream.close();
            throw new IOException("Invalid encrypted file format: initialization vector missing");
        }
        SecretKeySpec keySpec = new SecretKeySpec(masterKeyProvider.getKey(), ALGORITHM_AES);
        Cipher cipher = Cipher.getInstance(ALGO);
        GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
        cipher.init(Cipher.DECRYPT_MODE, keySpec, spec);
        
        CipherInputStream cipherInputStream = new CipherInputStream(encFileInputStream, cipher);
        
        // Wrap to ensure underlying stream is closed when CipherInputStream is closed
        return new CloseShieldInputStream(cipherInputStream) {
            @Override
            public void close() throws IOException {
                try {
                    super.close();
                } finally {
                    encFileInputStream.close();
                }
            }
        };
    } catch (Exception e) {
        if (encFileInputStream != null) {
            try {
                encFileInputStream.close();
            } catch (IOException closeEx) {
                // Ignore
            }
        }
        throw new IOException("Decryption failed", e);
    }
}
```

---

### Option 3: Return Resource Wrapper (Best - Recommended)

Create a proper resource wrapper that ensures cleanup:

```java
// New class: DecryptedStreamResource.java
public class DecryptedStreamResource implements AutoCloseable {
    private final FileInputStream fileInputStream;
    private final CipherInputStream cipherInputStream;
    
    public DecryptedStreamResource(File encFile, MasterKeyProvider keyProvider) throws IOException {
        try {
            this.fileInputStream = new FileInputStream(encFile);
            byte[] iv = new byte[INITIALIZATION_VECTOR_LENGTH];
            int read = fileInputStream.read(iv);
            if (read != INITIALIZATION_VECTOR_LENGTH) {
                fileInputStream.close();
                throw new IOException("Invalid encrypted file format");
            }
            
            SecretKeySpec keySpec = new SecretKeySpec(keyProvider.getKey(), "AES");
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            GCMParameterSpec spec = new GCMParameterSpec(128, iv);
            cipher.init(Cipher.DECRYPT_MODE, keySpec, spec);
            
            this.cipherInputStream = new CipherInputStream(fileInputStream, cipher);
        } catch (Exception e) {
            if (fileInputStream != null) {
                try { fileInputStream.close(); } catch (IOException ex) {}
            }
            throw new IOException("Decryption failed", e);
        }
    }
    
    public InputStream getInputStream() {
        return cipherInputStream;
    }
    
    @Override
    public void close() throws IOException {
        try {
            if (cipherInputStream != null) {
                cipherInputStream.close();
            }
        } finally {
            if (fileInputStream != null) {
                fileInputStream.close();
            }
        }
    }
}

// Updated EncryptionService
public DecryptedStreamResource decryptFileToResource(File encFile) throws IOException {
    return new DecryptedStreamResource(encFile, masterKeyProvider);
}
```

**Usage in Controller:**
```java
@Override
public ResponseEntity<Resource> downloadStatementByFileName(...) {
    try (DecryptedStreamResource resource = encryptionService.decryptFileToResource(file)) {
        return ResponseEntity.ok()
            .contentType(MediaType.APPLICATION_PDF)
            .body(new InputStreamResource(resource.getInputStream()));
    }
}
```

---

## Immediate Fix (Apply Now)

**File:** [`EncryptionService.java`](src/main/java/com/example/statementservice/service/EncryptionService.java:59)

Replace lines 59-76 with:

```java
public InputStream decryptFileToStream(File encFile) throws IOException {
    FileInputStream encFileInputStream = null;
    try {
        encFileInputStream = new FileInputStream(encFile);
        byte[] iv = new byte[INITIALIZATION_VECTOR_LENGTH];
        int read = encFileInputStream.read(iv);
        if (read != INITIALIZATION_VECTOR_LENGTH) {
            if (encFileInputStream != null) {
                try { encFileInputStream.close(); } catch (IOException e) { /* ignore */ }
            }
            throw new IOException("Invalid encrypted file format: initialization vector missing");
        }
        SecretKeySpec keySpec = new SecretKeySpec(masterKeyProvider.getKey(), ALGORITHM_AES);
        Cipher cipher = Cipher.getInstance(ALGO);
        GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
        cipher.init(Cipher.DECRYPT_MODE, keySpec, spec);
        
        // Return CipherInputStream - caller MUST close it
        return new CipherInputStream(encFileInputStream, cipher);
    } catch (Exception e) {
        // Close stream only on error
        if (encFileInputStream != null) {
            try { encFileInputStream.close(); } catch (IOException closeEx) { /* ignore */ }
        }
        throw new IOException("Decryption failed", e);
    }
    // DO NOT use try-with-resources - stream must stay open for caller
}
```

**Add JavaDoc warning:**
```java
/**
 * Decrypts a file and returns an InputStream for reading the decrypted content.
 * 
 * IMPORTANT: The caller is responsible for closing the returned InputStream.
 * Failure to close the stream will result in resource leaks.
 * 
 * @param encFile The encrypted file to decrypt
 * @return InputStream containing decrypted data
 * @throws IOException if decryption fails or file cannot be read
 */
public InputStream decryptFileToStream(File encFile) throws IOException {
    // ...
}
```

---

## Testing the Fix

```java
@Test
void shouldNotCloseStreamPrematurely() throws IOException {
    // Given
    File encryptedFile = createTestEncryptedFile();
    
    // When
    InputStream stream = encryptionService.decryptFileToStream(encryptedFile);
    
    // Then - should be able to read from stream
    assertNotNull(stream);
    byte[] buffer = new byte[1024];
    int bytesRead = stream.read(buffer);  // Should NOT throw "Stream Closed"
    assertTrue(bytesRead > 0);
    
    // Cleanup
    stream.close();
}

@Test
void shouldCloseStreamOnError() throws IOException {
    // Given
    File invalidFile = createInvalidEncryptedFile();
    
    // When/Then
    assertThrows(IOException.class, () -> {
        encryptionService.decryptFileToStream(invalidFile);
    });
    
    // Verify no file descriptors leaked
    // (This would require OS-level checks or monitoring)
}
```

---

## Related Issues

This bug is part of the broader resource management issues I identified in [`DOWNLOAD_SERVICE_IMPROVEMENTS.md`](DOWNLOAD_SERVICE_IMPROVEMENTS.md:1):

1. **Resource Leak Risk** - InputStream not properly managed
2. **No AutoCloseable Pattern** - Caller responsible for cleanup
3. **Missing Documentation** - No warning about closing streams

---

## Prevention

To prevent similar bugs in the future:

1. **Never use try-with-resources when returning the resource**
2. **Document resource ownership clearly**
3. **Use AutoCloseable wrappers for complex resources**
4. **Add integration tests that verify streams work end-to-end**
5. **Use static analysis tools to detect resource leaks**

---

## Summary

**Root Cause:** try-with-resources closes `FileInputStream` before `CipherInputStream` can be used

**Immediate Fix:** Remove try-with-resources, let caller close stream

**Long-term Fix:** Implement proper resource wrapper with AutoCloseable

**Priority:** CRITICAL - Breaks download functionality completely

---

## References

- Bug Location: [`EncryptionService.java:59-76`](src/main/java/com/example/statementservice/service/EncryptionService.java:59)
- Related Review: [`DOWNLOAD_SERVICE_IMPROVEMENTS.md`](DOWNLOAD_SERVICE_IMPROVEMENTS.md:1)
- Java Docs: [Try-with-resources](https://docs.oracle.com/javase/tutorial/essential/exceptions/tryResourceClose.html)