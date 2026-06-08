package com.syncvault.server.service; // 🚀 THE FIX: This must match the folder exactly!

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.amazonaws.services.s3.model.DeleteObjectsRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.List;

@Service
public class S3Service {

    @Autowired
    private AmazonS3 s3Client;

    @Value("${aws.s3.bucket.name}")
    private String bucketName;

    // 🚀 Pushes a completely assembled file into your S3 Bucket
    
    // Add this method to your S3Service class
    public void deleteFilesBulk(List<String> keys) {
        if (keys == null || keys.isEmpty()) return;
    
        // Convert List<String> to array for the AWS SDK
        String[] keyArray = keys.toArray(new String[0]);
        
        // Create the batch delete request
        DeleteObjectsRequest request = new DeleteObjectsRequest(bucketName)
                .withKeys(keyArray);
                
        // Perform the operation
        s3Client.deleteObjects(request);
    }
    public void uploadFile(String cloudFileName, File localFile) {
        System.out.println("☁️ Uploading [" + cloudFileName + "] to AWS S3...");
        s3Client.putObject(new PutObjectRequest(bucketName, cloudFileName, localFile));
        System.out.println("✅ AWS Upload Complete: " + cloudFileName);
    }

    // 🗑️ Deletes a file from the cloud
    public void deleteFile(String cloudFileName) {
        System.out.println("🗑️ Deleting [" + cloudFileName + "] from AWS S3...");
        s3Client.deleteObject(bucketName, cloudFileName);
    }
}