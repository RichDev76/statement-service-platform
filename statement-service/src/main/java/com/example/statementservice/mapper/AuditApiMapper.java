package com.example.statementservice.mapper;

import com.example.statementservice.model.api.AuditLogEntry;
import com.example.statementservice.model.api.AuditLogPage;
import com.example.statementservice.model.dto.AuditLogDto;
import java.util.List;
import java.util.stream.Collectors;
import org.mapstruct.*;

@Mapper(componentModel = "spring")
public interface AuditApiMapper {

    @Mappings({
        @Mapping(target = "id", source = "id"),
        @Mapping(target = "accountNumber", source = "accountNumber"),
        @Mapping(target = "statementId", source = "statementId"),
        @Mapping(target = "details", source = "details"),
        @Mapping(target = "ipAddress", source = "ipAddress"),
        @Mapping(target = "userAgent", source = "userAgent"),
        @Mapping(target = "action", source = "action"),
        @Mapping(target = "timestamp", source = "performedAt")
    })
    AuditLogEntry toApi(AuditLogDto dto);

    default AuditLogPage toPage(List<AuditLogDto> dtos) {
        var page = new AuditLogPage();
        page.setContent(
                dtos == null ? List.of() : dtos.stream().map(this::toApi).collect(Collectors.toList()));
        return page;
    }
}
