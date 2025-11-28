# File Storage Folder Structure Best Practices

## Executive Summary

This document outlines best practices for organizing encrypted statement files in a scalable, maintainable, and performant manner. The recommendations address filesystem limitations, performance optimization, disaster recovery, and operational efficiency.

---

## 1. Current Implementation Analysis

**Current Structure:**
```
/Users/devonrichards/data/files/
├── 44568188-d4a4-418b-8a8b-e45ffd5a1be5.pdf.enc
├── 550e8400-e29b-41d4-a716-446655440000.pdf.enc
└── ... (all files in single directory)
```

**Issues with Current Approach:**
- ❌ Single directory with potentially millions of files
- ❌ Poor filesystem performance (directory listing becomes slow)
- ❌ Difficult to manage and backup
- ❌ No logical organization
- ❌ Hard to implement retention policies
- ❌ Challenging to scale horizontally

---

## 2. Recommended Folder Structure Strategies

### Strategy 1: Date-Based Hierarchical Structure (RECOMMENDED)

**Structure:**
```
/data/statements/
├── 2025/
│   ├── 01/                          # Month
│   │   ├── 15/                      # Day
│   │   │   ├── 550e8400-e29b-41d4-a716-446655440000.pdf.enc
│   │   │   ├── 661f9511-f3ab-52e5-b827-557766551111.pdf.enc
│   │   │   └── metadata/
│   │   │       ├── 550e8400-e29b-41d4-a716-446655440000.json
│   │   │       └── 661f9511-f3ab-52e5-b827-557766551111.json
│   │   └── 16/
│   │       └── ...
│   ├── 02/
│   └── ...
├── 2024/
└── archive/                         # For archived statements
    └── 2018/
        └── ...
```

**Advantages:**
- ✅ Natural organization by time
- ✅ Easy to implement retention policies
- ✅ Simplified backup strategies (backup by date range)
- ✅ Better filesystem performance (smaller directories)
- ✅ Intuitive for operations team
- ✅ Supports time-based queries efficiently

**Implementation:**
```java
@Service
public class DateBasedFileStorageService implements FileStorageService {
    
    @Value("${statement.storage.base-dir}")
    private String baseDir;
    
    public FileStorageResult storeEncrypted(UUID id, MultipartFile file, LocalDate statementDate) {
        // Build path: /base/YYYY/MM/DD/uuid.pdf.enc
        Path filePath = buildDateBasedPath(id, statementDate);
        
        // Ensure directory exists
        Files.createDirectories(filePath.getParent());
        
        byte[] iv = encryptionService.generateIv();
        encryptionService.encryptToFile(file.getInputStream(), filePath.toFile(), iv);
        
        // Optionally store metadata
        storeMetadata(id, file, filePath);
        
        return new FileStorageResult(filePath.toFile(), iv);
    }
    
    private Path buildDateBasedPath(UUID id, LocalDate date) {
        return Paths.get(
            baseDir,
            String.valueOf(date.getYear()),
            String.format("%02d", date.getMonthValue()),
            String.format("%02d", date.getDayOfMonth()),
            id + ".pdf.enc"
        );
    }
    
    private void storeMetadata(UUID id, MultipartFile file, Path filePath) {
        Path metadataDir = filePath.getParent().resolve("metadata");
        Files.createDirectories(metadataDir);
        
        Map<String, Object> metadata = Map.of(
            "statementId", id.toString(),
            "originalFileName", file.getOriginalFilename(),
            "contentType", file.getContentType(),
            "size", file.getSize(),
            "uploadedAt", OffsetDateTime.now().toString(),
            "checksum", computeChecksum(file)
        );
        
        Path metadataFile = metadataDir.resolve(id + ".json");
        objectMapper.writeValue(metadataFile.toFile(), metadata);
    }
}
```

---

### Strategy 2: Hash-Based Sharding (For Very Large Scale)

**Structure:**
```
/data/statements/
├── 00/
│   ├── 00/
│   │   └── 00xxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx.pdf.enc
│   ├── 01/
│   └── ...
├── 01/
├── 02/
└── ff/
```

**Advantages:**
- ✅ Excellent for massive scale (billions of files)
- ✅ Uniform distribution across directories
- ✅ Predictable performance
- ✅ Easy to implement horizontal sharding

**Implementation:**
```java
public class HashBasedFileStorageService implements FileStorageService {
    
    private Path buildHashBasedPath(UUID id) {
        String idStr = id.toString().replace("-", "");
        
        // Use first 4 characters for two-level sharding
        String level1 = idStr.substring(0, 2);  // 00-ff (256 dirs)
        String level2 = idStr.substring(2, 4);  // 00-ff (256 dirs)
        
        return Paths.get(
            baseDir,
            level1,
            level2,
            id + ".pdf.enc"
        );
    }
    
    // Results in ~65,536 leaf directories (256 * 256)
    // Each directory contains manageable number of files
}
```

---

### Strategy 3: Hybrid Approach (Date + Hash)

**Structure:**
```
/data/statements/
├── 2025/
│   ├── 01/
│   │   ├── 0/                       # First char of UUID
│   │   │   ├── 0a/                  # Second char
│   │   │   │   └── 0a1b2c3d-....pdf.enc
│   │   │   └── 0b/
│   │   ├── 1/
│   │   └── ...
│   └── 02/
└── 2024/
```

**Advantages:**
- ✅ Combines benefits of both approaches
- ✅ Time-based organization for operations
- ✅ Hash-based distribution for performance
- ✅ Best of both worlds

**Implementation:**
```java
public class HybridFileStorageService implements FileStorageService {
    
    private Path buildHybridPath(UUID id, LocalDate statementDate) {
        String idStr = id.toString();
        String level1 = idStr.substring(0, 1);   // 0-f (16 dirs)
        String level2 = idStr.substring(0, 2);   // 00-ff (256 dirs)
        
        return Paths.get(
            baseDir,
            String.valueOf(statementDate.getYear()),
            String.format("%02d", statementDate.getMonthValue()),
            level1,
            level2,
            id + ".pdf.enc"
        );
    }
}
```

---

## 3. Account-Based Organization (Alternative)

**Structure:**
```
/data/statements/
├── accounts/
│   ├── 123/                         # First 3 digits of account
│   │   ├── 456/                     # Next 3 digits
│   │   │   ├── 789/                 # Last 3 digits
│   │   │   │   ├── 2025-01-15_550e8400.pdf.enc
│   │   │   │   └── 2025-02-15_661f9511.pdf.enc
│   │   │   └── ...
│   │   └── ...
│   └── ...
└── orphaned/                        # For statements without account
```

**Use Case:**
- When queries are primarily by account number
- When account-based access control is needed
- When statements need to be grouped by customer

**Implementation:**
```java
public class AccountBasedFileStorageService implements FileStorageService {
    
    private Path buildAccountBasedPath(String accountNumber, LocalDate date, UUID id) {
        // Shard by account number: 123456789 -> 123/456/789
        String level1 = accountNumber.substring(0, 3);
        String level2 = accountNumber.substring(3, 6);
        String level3 = accountNumber.substring(6, 9);
        
        String fileName = String.format("%s_%s.pdf.enc", 
            date.toString(), 
            id.toString().substring(0, 8)
        );
        
        return Paths.get(
            baseDir,
            "accounts",
            level1,
            level2,
            level3,
            fileName
        );
    }
}
```

---

## 4. Complete Implementation Example

### 4.1 Enhanced FileStorageService

```java
@Service
@Slf4j
public class EnhancedFileStorageService {
    
    private final EncryptionService encryptionService;
    private final ObjectMapper objectMapper;
    
    @Value("${statement.storage.base-dir}")
    private String baseDir;
    
    @Value("${statement.storage.strategy:date-based}")
    private String storageStrategy;
    
    @Value("${statement.storage.metadata.enabled:true}")
    private boolean metadataEnabled;
    
    public FileStorageResult storeEncrypted(UUID id, MultipartFile file, 
                                           LocalDate statementDate, 
                                           String accountNumber) {
        Path filePath = buildFilePath(id, statementDate, accountNumber);
        
        try {
            // Ensure parent directories exist
            Files.createDirectories(filePath.getParent());
            
            // Generate IV and encrypt
            byte[] iv = encryptionService.generateIv();
            encryptionService.encryptToFile(file.getInputStream(), filePath.toFile(), iv);
            
            // Store metadata if enabled
            if (metadataEnabled) {
                storeMetadata(id, file, filePath, statementDate, accountNumber);
            }
            
            log.info("Stored encrypted file: {}", filePath);
            return new FileStorageResult(filePath.toFile(), iv);
            
        } catch (IOException e) {
            throw new StatementUploadException("Failed to store file: " + filePath, e);
        }
    }
    
    private Path buildFilePath(UUID id, LocalDate date, String accountNumber) {
        return switch (storageStrategy) {
            case "date-based" -> buildDateBasedPath(id, date);
            case "hash-based" -> buildHashBasedPath(id);
            case "hybrid" -> buildHybridPath(id, date);
            case "account-based" -> buildAccountBasedPath(accountNumber, date, id);
            default -> throw new IllegalStateException("Unknown storage strategy: " + storageStrategy);
        };
    }
    
    private Path buildDateBasedPath(UUID id, LocalDate date) {
        return Paths.get(
            baseDir,
            String.valueOf(date.getYear()),
            String.format("%02d", date.getMonthValue()),
            String.format("%02d", date.getDayOfMonth()),
            id + ".pdf.enc"
        );
    }
    
    private Path buildHashBasedPath(UUID id) {
        String idStr = id.toString().replace("-", "");
        return Paths.get(
            baseDir,
            idStr.substring(0, 2),
            idStr.substring(2, 4),
            id + ".pdf.enc"
        );
    }
    
    private Path buildHybridPath(UUID id, LocalDate date) {
        String idStr = id.toString();
        return Paths.get(
            baseDir,
            String.valueOf(date.getYear()),
            String.format("%02d", date.getMonthValue()),
            idStr.substring(0, 1),
            idStr.substring(0, 2),
            id + ".pdf.enc"
        );
    }
    
    private Path buildAccountBasedPath(String accountNumber, LocalDate date, UUID id) {
        if (accountNumber.length() < 9) {
            throw new IllegalArgumentException("Account number too short for sharding");
        }
        
        String fileName = String.format("%s_%s.pdf.enc", 
            date.toString(), 
            id.toString().substring(0, 8)
        );
        
        return Paths.get(
            baseDir,
            "accounts",
            accountNumber.substring(0, 3),
            accountNumber.substring(3, 6),
            accountNumber.substring(6, 9),
            fileName
        );
    }
    
    private void storeMetadata(UUID id, MultipartFile file, Path filePath, 
                              LocalDate date, String accountNumber) throws IOException {
        Path metadataDir = filePath.getParent().resolve(".metadata");
        Files.createDirectories(metadataDir);
        
        StatementMetadata metadata = StatementMetadata.builder()
            .statementId(id)
            .accountNumber(accountNumber)
            .statementDate(date)
            .originalFileName(file.getOriginalFilename())
            .contentType(file.getContentType())
            .sizeBytes(file.getSize())
            .storedAt(OffsetDateTime.now())
            .storagePath(filePath.toString())
            .checksum(computeChecksum(file))
            .build();
        
        Path metadataFile = metadataDir.resolve(id + ".json");
        objectMapper.writeValue(metadataFile.toFile(), metadata);
    }
    
    private String computeChecksum(MultipartFile file) throws IOException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(file.getBytes());
        return Base64.getEncoder().encodeToString(hash);
    }
    
    public InputStream retrieveDecrypted(UUID id, LocalDate date, String accountNumber) 
            throws IOException {
        Path filePath = buildFilePath(id, date, accountNumber);
        
        if (!Files.exists(filePath)) {
            throw new FileNotFoundException("Statement file not found: " + filePath);
        }
        
        return encryptionService.decryptFileToStream(filePath.toFile());
    }
    
    public boolean deleteFile(UUID id, LocalDate date, String accountNumber) {
        Path filePath = buildFilePath(id, date, accountNumber);
        
        try {
            // Delete metadata first
            if (metadataEnabled) {
                Path metadataFile = filePath.getParent()
                    .resolve(".metadata")
                    .resolve(id + ".json");
                Files.deleteIfExists(metadataFile);
            }
            
            // Delete encrypted file
            boolean deleted = Files.deleteIfExists(filePath);
            
            // Clean up empty directories
            cleanupEmptyDirectories(filePath.getParent());
            
            return deleted;
        } catch (IOException e) {
            log.error("Failed to delete file: {}", filePath, e);
            return false;
        }
    }
    
    private void cleanupEmptyDirectories(Path directory) throws IOException {
        // Walk up the tree and remove empty directories
        while (!directory.equals(Paths.get(baseDir))) {
            try (var stream = Files.list(directory)) {
                if (stream.findAny().isEmpty()) {
                    Files.delete(directory);
                    directory = directory.getParent();
                } else {
                    break;
                }
            }
        }
    }
}
```

### 4.2 Metadata Model

```java
@Data
@Builder
public class StatementMetadata {
    private UUID statementId;
    private String accountNumber;
    private LocalDate statementDate;
    private String originalFileName;
    private String contentType;
    private Long sizeBytes;
    private OffsetDateTime storedAt;
    private String storagePath;
    private String checksum;
    private Map<String, Object> customAttributes;
}
```

### 4.3 Configuration

```yaml
statement:
  storage:
    base-dir: ${STATEMENT_STORAGE_DIR:/data/statements}
    strategy: date-based  # Options: date-based, hash-based, hybrid, account-based
    metadata:
      enabled: true
      compression: true
    cleanup:
      empty-directories: true
    performance:
      buffer-size: 8192
      use-direct-io: false
```

---

## 5. Migration Strategy

### 5.1 Migrating from Flat to Hierarchical Structure

```java
@Service
@Slf4j
public class StorageMigrationService {
    
    private final StatementRepository statementRepository;
    private final EnhancedFileStorageService newStorageService;
    
    @Value("${statement.storage.legacy-dir}")
    private String legacyDir;
    
    @Transactional
    public void migrateToNewStructure() {
        log.info("Starting storage migration...");
        
        List<Statement> statements = statementRepository.findAll();
        int total = statements.size();
        int migrated = 0;
        int failed = 0;
        
        for (Statement statement : statements) {
            try {
                migrateStatement(statement);
                migrated++;
                
                if (migrated % 100 == 0) {
                    log.info("Migration progress: {}/{}", migrated, total);
                }
            } catch (Exception e) {
                log.error("Failed to migrate statement: {}", statement.getId(), e);
                failed++;
            }
        }
        
        log.info("Migration complete. Migrated: {}, Failed: {}", migrated, failed);
    }
    
    private void migrateStatement(Statement statement) throws IOException {
        // Read from old location
        Path oldPath = Paths.get(statement.getFilePath());
        
        if (!Files.exists(oldPath)) {
            log.warn("File not found at old location: {}", oldPath);
            return;
        }
        
        // Build new path
        Path newPath = newStorageService.buildFilePath(
            statement.getId(),
            statement.getStatementDate(),
            statement.getAccountNumber()
        );
        
        // Create parent directories
        Files.createDirectories(newPath.getParent());
        
        // Move file
        Files.move(oldPath, newPath, StandardCopyOption.ATOMIC_MOVE);
        
        // Update database
        statement.setFilePath(newPath.toString());
        statementRepository.save(statement);
        
        log.debug("Migrated: {} -> {}", oldPath, newPath);
    }
    
    @Scheduled(cron = "0 0 2 * * *") // Daily at 2 AM
    public void scheduledMigration() {
        // Migrate in batches to avoid overwhelming the system
        migrateBatch(1000);
    }
    
    private void migrateBatch(int batchSize) {
        List<Statement> unmigrated = statementRepository
            .findByFilePathStartingWith(legacyDir)
            .stream()
            .limit(batchSize)
            .toList();
        
        unmigrated.forEach(this::migrateStatement);
    }
}
```

---

## 6. Performance Considerations

### 6.1 Directory Size Limits

**Filesystem Limits:**
- **ext4**: ~10 million files per directory (practical limit: 100k)
- **XFS**: Virtually unlimited (practical limit: 1 million)
- **NTFS**: ~4 billion files per directory (practical limit: 100k)

**Recommendation:** Keep directories under 10,000 files for optimal performance

### 6.2 Inode Optimization

```bash
# Check inode usage
df -i

# Create filesystem with more inodes
mkfs.ext4 -N 100000000 /dev/sdb1

# Monitor inode usage
watch -n 60 'df -i | grep statements'
```

### 6.3 I/O Optimization

```yaml
statement:
  storage:
    performance:
      buffer-size: 8192          # 8KB buffer
      use-direct-io: false       # Bypass OS cache (for very large files)
      async-writes: true         # Asynchronous file writes
      compression: false         # Already encrypted, compression ineffective
```

---

## 7. Backup and Disaster Recovery

### 7.1 Backup Strategy by Structure

**Date-Based Structure:**
```bash
#!/bin/bash
# Backup specific date range
BACKUP_DATE="2025/01"
rsync -av --progress \
  /data/statements/${BACKUP_DATE}/ \
  s3://backups/statements/${BACKUP_DATE}/

# Incremental backup
rsync -av --progress --link-dest=/backup/previous \
  /data/statements/ \
  /backup/current/
```

**Hash-Based Structure:**
```bash
#!/bin/bash
# Backup specific shard
SHARD="00"
rsync -av --progress \
  /data/statements/${SHARD}/ \
  s3://backups/statements/${SHARD}/
```

### 7.2 Disaster Recovery

```java
@Service
public class DisasterRecoveryService {
    
    public void verifyIntegrity() {
        // Verify all files against database records
        List<Statement> statements = statementRepository.findAll();
        
        statements.parallelStream().forEach(statement -> {
            Path filePath = Paths.get(statement.getFilePath());
            
            if (!Files.exists(filePath)) {
                log.error("Missing file: {}", filePath);
                // Trigger recovery from backup
                recoverFromBackup(statement);
            } else {
                // Verify checksum
                verifyChecksum(statement, filePath);
            }
        });
    }
    
    private void recoverFromBackup(Statement statement) {
        // Implement backup recovery logic
        log.info("Recovering file from backup: {}", statement.getId());
    }
}
```

---

## 8. Monitoring and Maintenance

### 8.1 Storage Metrics

```java
@Component
public class StorageMetrics {
    
    private final MeterRegistry registry;
    
    @Scheduled(fixedRate = 60000) // Every minute
    public void collectMetrics() {
        // Total storage used
        long totalSize = calculateTotalSize();
        registry.gauge("storage.total.bytes", totalSize);
        
        // Files per directory
        Map<String, Long> filesPerDir = countFilesPerDirectory();
        filesPerDir.forEach((dir, count) -> 
            registry.gauge("storage.directory.files", 
                Tags.of("directory", dir), count)
        );
        
        // Oldest file age
        long oldestFileAge = findOldestFileAge();
        registry.gauge("storage.oldest.file.days", oldestFileAge);
    }
}
```

### 8.2 Cleanup Jobs

```java
@Service
public class StorageCleanupService {
    
    @Scheduled(cron = "0 0 3 * * *") // Daily at 3 AM
    public void cleanupOldStatements() {
        LocalDate cutoffDate = LocalDate.now().minusYears(7);
        
        List<Statement> oldStatements = statementRepository
            .findByStatementDateBefore(cutoffDate);
        
        oldStatements.forEach(statement -> {
            try {
                // Archive to cold storage
                archiveToColdStorage(statement);
                
                // Delete from primary storage
                fileStorageService.deleteFile(
                    statement.getId(),
                    statement.getStatementDate(),
                    statement.getAccountNumber()
                );
                
                // Mark as archived in database
                statement.setArchived(true);
                statementRepository.save(statement);
                
            } catch (Exception e) {
                log.error("Failed to archive statement: {}", statement.getId(), e);
            }
        });
    }
    
    @Scheduled(cron = "0 0 4 * * *") // Daily at 4 AM
    public void cleanupOrphanedFiles() {
        // Find files not in database
        Set<UUID> dbStatementIds = statementRepository.findAllIds();
        
        Files.walk(Paths.get(baseDir))
            .filter(Files::isRegularFile)
            .filter(path -> path.toString().endsWith(".pdf.enc"))
            .forEach(path -> {
                UUID fileId = extractIdFromPath(path);
                if (!dbStatementIds.contains(fileId)) {
                    log.warn("Orphaned file found: {}", path);
                    // Move to quarantine or delete
                    quarantineFile(path);
                }
            });
    }
}
```

---

## 9. Cloud Storage Adaptation

### 9.1 S3-Compatible Structure

```java
@Service
public class S3FileStorageService implements FileStorageService {
    
    private final AmazonS3 s3Client;
    
    @Value("${statement.storage.s3.bucket}")
    private String bucketName;
    
    public FileStorageResult storeEncrypted(UUID id, MultipartFile file, 
                                           LocalDate date, String accountNumber) {
        // S3 key structure: statements/YYYY/MM/DD/uuid.pdf.enc
        String s3Key = buildS3Key(id, date);
        
        // Encrypt locally first
        byte[] iv = encryptionService.generateIv();
        byte[] encryptedData = encryptionService.encrypt(file.getBytes(), iv);
        
        // Upload to S3 with metadata
        ObjectMetadata metadata = new ObjectMetadata();
        metadata.setContentLength(encryptedData.length);
        metadata.setContentType("application/octet-stream");
        metadata.addUserMetadata("statement-id", id.toString());
        metadata.addUserMetadata("account-number", accountNumber);
        metadata.addUserMetadata("statement-date", date.toString());
        
        // Use S3 server-side encryption as additional layer
        metadata.setSSEAlgorithm(ObjectMetadata.AES_256_SERVER_SIDE_ENCRYPTION);
        
        PutObjectRequest request = new PutObjectRequest(
            bucketName,
            s3Key,
            new ByteArrayInputStream(encryptedData),
            metadata
        );
        
        s3Client.putObject(request);
        
        return new FileStorageResult(s3Key, iv);
    }
    
    private String buildS3Key(UUID id, LocalDate date) {
        return String.format("statements/%d/%02d/%02d/%s.pdf.enc",
            date.getYear(),
            date.getMonthValue(),
            date.getDayOfMonth(),
            id
        );
    }
    
    // Implement lifecycle policies
    public void configureLifecyclePolicy() {
        BucketLifecycleConfiguration.Rule archiveRule = 
            new BucketLifecycleConfiguration.Rule()
                .withId("archive-old-statements")
                .withFilter(new LifecycleFilter(
                    new LifecyclePrefixPredicate("statements/")))
                .withTransitions(List.of(
                    // Move to Infrequent Access after 90 days
                    new Transition()
                        .withDays(90)
                        .withStorageClass(StorageClass.StandardInfrequentAccess),
                    // Move to Glacier after 1 year
                    new Transition()
                        .withDays(365)
                        .withStorageClass(StorageClass.Glacier)
                ))
                .withStatus(BucketLifecycleConfiguration.ENABLED);
        
        BucketLifecycleConfiguration config = 
            new BucketLifecycleConfiguration()
                .withRules(archiveRule);
        
        s3Client.setBucketLifecycleConfiguration(bucketName, config);
    }
}
```

---

## 10. Recommendation Summary

### For Current Scale (< 1 million statements):
**✅ Use Date-Based Hierarchical Structure**
- Easy to implement
- Natural organization
- Simple backup/archival
- Good performance

### For Medium Scale (1-10 million statements):
**✅ Use Hybrid Approach (Date + Hash)**
- Balanced performance
- Operational flexibility
- Scalable

### For Large Scale (> 10 million statements):
**✅ Use Hash-Based Sharding + Cloud Storage**
- Maximum scalability
- Predictable performance
- Cloud-native features

### Migration Path:
1. **Phase 1**: Implement date-based structure
2. **Phase 2**: Add metadata storage
3. **Phase 3**: Migrate to hybrid if needed
4. **Phase 4**: Move to cloud storage (S3/Azure Blob)

---

## 11. Implementation Checklist

- [ ] Choose storage strategy based on scale
- [ ] Update FileStorageService implementation
- [ ] Add metadata storage
- [ ] Implement migration script
- [ ] Configure backup strategy
- [ ] Add monitoring and metrics
- [ ] Implement cleanup jobs
- [ ] Test disaster recovery
- [ ] Document operational procedures
- [ ] Update configuration management

---

## 12. Configuration Example

```yaml
statement:
  storage:
    # Storage strategy
    strategy: date-based  # date-based, hash-based, hybrid, account-based
    
    # Base directory
    base-dir: ${STATEMENT_STORAGE_DIR:/data/statements}
    
    # Metadata
    metadata:
      enabled: true
      directory-name: .metadata
      compression: false
    
    # Performance
    performance:
      buffer-size: 8192
      async-writes: true
      parallel-processing: true
      max-threads: 10
    
    # Cleanup
    cleanup:
      empty-directories: true
      orphaned-files: true
      retention-years: 7
    
    # Cloud storage (optional)
    cloud:
      enabled: false
      provider: s3  # s3, azure-blob, gcs
      bucket: statement-storage
      region: us-east-1
      lifecycle-enabled: true
```

---

*Document Version: 1.0*  
*Last Updated: 2025-11-24*  
*Author: AI Architecture Consultant*