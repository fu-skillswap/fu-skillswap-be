package com.fptu.exe.skillswap.infrastructure.storage;

import java.io.IOException;
import java.io.InputStream;

public interface StorageObjectReader {

    StorageGateway.ObjectMetadata headObject(String objectKey);

    InputStream openObject(String objectKey) throws IOException;
}
