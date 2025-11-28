package com.example.statementservice.mapper;

import com.example.statementservice.model.dto.StatementDto;
import com.example.statementservice.model.entity.Statement;
import com.example.statementservice.service.SignedLinkService;
import java.util.List;
import org.mapstruct.Context;
import org.mapstruct.IterableMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

@Mapper(componentModel = "spring")
public interface StatementEntityMapper {

    @Mapping(target = "statementId", source = "id")
    @Mapping(target = "fileName", source = "uploadFileName")
    @Mapping(target = "fileSize", source = "sizeBytes")
    @Mapping(
            target = "downloadLink",
            expression = "java(signedLinkService.buildSignedLink(entity.getUploadFileName(), entity.getId()))")
    StatementDto toDto(Statement entity, @Context SignedLinkService signedLinkService);

    @Named("withoutLink")
    @Mapping(target = "statementId", source = "id")
    @Mapping(target = "fileName", source = "uploadFileName")
    @Mapping(target = "fileSize", source = "sizeBytes")
    @Mapping(target = "downloadLink", ignore = true)
    StatementDto toDtoWithoutLink(Statement entity, @Context SignedLinkService signedLinkService);

    // Collection mapping that uses the named element mapping "withoutLink"
    @IterableMapping(qualifiedByName = "withoutLink")
    List<StatementDto> toDtos(List<Statement> entities, @Context SignedLinkService signedLinkService);
}
