package com.example.statementservice.mapper;

import com.example.statementservice.model.api.StatementSummary;
import com.example.statementservice.model.dto.StatementDto;
import java.util.ArrayList;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Mappings;

@Mapper(componentModel = "spring")
public interface StatementApiMapper {

    @Mappings({
        @Mapping(target = "statementId", source = "statementId"),
        @Mapping(target = "accountNumber", source = "accountNumber"),
        @Mapping(target = "uploadedAt", source = "uploadedAt"),
        @Mapping(target = "fileSize", source = "fileSize"),
        @Mapping(target = "fileName", source = "fileName"),
        @Mapping(target = "downloadLink", source = "downloadLink"),
        @Mapping(
                target = "date",
                expression = "java(dto.getStatementDate() != null ? dto.getStatementDate().toString() : null)")
    })
    StatementSummary toApi(StatementDto dto);

    List<StatementSummary> toApis(List<StatementDto> dtos);
}
