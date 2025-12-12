package com.example.statementservice.mapper;

import com.example.statementservice.model.api.UploadResponse;
import com.example.statementservice.model.dto.UploadResponseDto;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface UploadResponseApiMapper {
    UploadResponse toApi(UploadResponseDto dto);
}
