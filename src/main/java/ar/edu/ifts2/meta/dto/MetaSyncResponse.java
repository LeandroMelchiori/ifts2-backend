package ar.edu.ifts2.meta.dto;

import java.util.List;

public record MetaSyncResponse(int sincronizados, String message, List<MetaPostAdminResponse> posts) { }
