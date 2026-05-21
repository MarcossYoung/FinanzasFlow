package com.example.demo.service;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

@Service
public class FileStorageService {

    @Autowired(required = false)
    private Cloudinary cloudinary;

    public String saveFile(MultipartFile file) throws IOException {
        if (cloudinary == null) throw new IllegalStateException("Cloudinary not configured (check env vars)");
        Map uploadResult = cloudinary.uploader().upload(
                file.getBytes(),
                ObjectUtils.asMap("folder", "muebles/productos")
        );
        return (String) uploadResult.get("secure_url");
    }
}
