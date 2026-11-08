package ar.edu.ifts2.panelrol.dto;

import java.util.Map;
import java.util.UUID;

public record PanelRolResponse(UUID id, String name, String email, Map<String, PermisoPanelDto> permissions) { }
